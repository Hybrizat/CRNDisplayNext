package com.hybrizat.crndisplaynext.train;

import com.hybrizat.crndisplaynext.CRNDisplayNextMod;
import com.hybrizat.crndisplaynext.client.TextureHolderSweeper;
import com.hybrizat.crndisplaynext.network.RequestThroughTerminusPayload;
import com.hybrizat.crndisplaynext.network.ThroughTerminusPayload;
import de.mrjulsen.crn.api.core.snapshot.BoardEntry;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side cache of the through-service terminus of each train on a departure
 * board, and the requester that fills it.
 *
 * <p>A station or platform display cannot work this out itself: CRN only attaches the
 * journey snapshot to displays on a carriage, so the section chain that
 * {@link RouteResolver} needs is simply not there. The server does hold it, behind
 * {@code RailwayBackendApi.getJourney}, so the client asks once per train and reuses
 * the answer for a while.</p>
 *
 * <p>Keyed by train <em>and</em> tracking session: CRN starts a new session whenever a
 * train is re-initialised, and a stale entry from the previous run must not be shown
 * for the new one.</p>
 *
 * <p>Traffic is negligible: the board is itself refreshed on a timer, entries are
 * deduplicated per request, one packet goes out at most every
 * {@link #REQUEST_INTERVAL_TICKS} ticks, and a cached answer is reused for
 * {@link #TTL_TICKS}.</p>
 */
public final class ThroughTerminusCache {

    /** How long a resolved terminus is reused before it is requested again. */
    private static final int TTL_TICKS = 100;
    /** Minimum ticks between two request packets, whatever else happens. */
    private static final int REQUEST_INTERVAL_TICKS = 10;

    private record Key(UUID trainId, UUID sessionId) {}
    private record Cached(String name, long expiresAt) {}

    private static final Map<Key, Cached> CACHE = new ConcurrentHashMap<>();
    /**
     * Tick of the last request packet, or {@link #NEVER} when none has been sent.
     *
     * <p>Not {@code Integer.MIN_VALUE}: the throttle test is a subtraction, and
     * {@code 0 - Integer.MIN_VALUE} overflows back to {@code Integer.MIN_VALUE}, which
     * compares as "less than the interval" and made the guard fire on every single call
     * — the request was never sent at all. An explicit sentinel keeps the arithmetic out
     * of the first call entirely.</p>
     */
    private static final long NEVER = Long.MIN_VALUE;
    private static long lastRequestTick = NEVER;
    /** Keys sent in the most recent request, so the reply can be checked for gaps. */
    private static Set<Key> lastAsked = Set.of();

    private ThroughTerminusCache() {}

    /**
     * The terminus to show for this departure, or {@code null} only while it has never
     * been resolved for this train.
     *
     * <p>A stale entry is still served on purpose. Expiry only means "ask again soon",
     * and the round trip takes a tick or two, so treating expiry as "no answer" made
     * every row flip back to CRN's own destination — the handover station — for one
     * frame in every TTL window, which read as the two conventions competing. Showing
     * yesterday's answer until the new one arrives is both stabler and closer to what a
     * departure board is for. A genuinely changed service still updates, because
     * {@link #accept} overwrites the entry with whatever the server now says.</p>
     */
    public static String terminusOf(BoardEntry entry) {
        Cached cached = CACHE.get(new Key(entry.trainId(), entry.sessionId()));
        return cached == null ? null : cached.name();
    }

    /**
     * Queues a request for every entry on the board whose terminus is missing or stale,
     * and sends one packet if anything is due. Call once per board refresh.
     */
    public static void request(List<BoardEntry> entries) {
        int now = TextureHolderSweeper.clientTicks;
        // Keyed so several rows of the same train (this board is one station, but a
        // train can call there more than once) collapse into a single call.
        Map<Key, RequestThroughTerminusPayload.TrainCall> due = new LinkedHashMap<>();
        for (BoardEntry entry : entries) {
            if (entry == null) continue;
            Key key = new Key(entry.trainId(), entry.sessionId());
            Cached cached = CACHE.get(key);
            if (cached != null && cached.expiresAt() > now) continue;
            // Keep the call of the first row that needs it; `arrived` is per train, not
            // per row, so any of them describes the situation correctly.
            due.putIfAbsent(key, RequestThroughTerminusPayload.of(entry));
        }
        if (due.isEmpty()) return;
        // One packet per interval at most, so a fast-refreshing board cannot spam.
        if (lastRequestTick != NEVER && now - lastRequestTick < REQUEST_INTERVAL_TICKS) return;
        lastRequestTick = now;

        List<RequestThroughTerminusPayload.TrainCall> calls = new ArrayList<>(Math.min(due.size(), RequestThroughTerminusPayload.MAX_CALLS));
        for (RequestThroughTerminusPayload.TrainCall call : due.values()) {
            if (calls.size() >= RequestThroughTerminusPayload.MAX_CALLS) break;
            calls.add(call);
        }
        if (!calls.isEmpty()) {
            if (CRNDisplayNextMod.LOGGER.isDebugEnabled()) {
                CRNDisplayNextMod.LOGGER.debug("[Through] C: requesting terminus for {} train(s): {}",
                    calls.size(), describe(calls));
            }
            Set<Key> asked = new HashSet<>();
            for (RequestThroughTerminusPayload.TrainCall call : calls) {
                asked.add(new Key(call.trainId(), call.sessionId()));
            }
            lastAsked = asked;
            PacketDistributor.sendToServer(new RequestThroughTerminusPayload(calls));
        }
    }

    private static String describe(List<RequestThroughTerminusPayload.TrainCall> calls) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < calls.size() && i < 8; i++) {
            if (i > 0) sb.append(", ");
            sb.append(calls.get(i).trainId().toString().substring(0, 8));
        }
        if (calls.size() > 8) sb.append(", …");
        return sb.append(']').toString();
    }

    /**
 * Stores the answers and reports any train that was asked for but came back unresolved,
 * which would otherwise show as a destination that never settles.
 */
    public static void accept(List<ThroughTerminusPayload.Result> results) {
        int expiresAt = TextureHolderSweeper.clientTicks + TTL_TICKS;
        Set<Key> answered = new HashSet<>();
        int stored = 0;
        StringBuilder names = new StringBuilder();
        for (ThroughTerminusPayload.Result result : results) {
            if (result == null || result.name() == null || result.name().isBlank()) continue;
            Key key = new Key(result.trainId(), result.sessionId());
            CACHE.put(key, new Cached(result.name(), expiresAt));
            answered.add(key);
            stored++;
            if (stored <= 8) names.append(stored > 1 ? ", " : "").append(result.name());
        }
        // Anything we asked for and did not hear back about stays on its old value; log
        // it, because a train that is only intermittently resolvable looks exactly like a
        // flickering destination.
        List<String> silent = new ArrayList<>();
        for (Key key : lastAsked) {
            if (!answered.contains(key) && silent.size() < 8) {
                silent.add(key.trainId().toString().substring(0, 8));
            }
        }
        lastAsked = answered;
        if (CRNDisplayNextMod.LOGGER.isDebugEnabled()) {
            CRNDisplayNextMod.LOGGER.debug("[Through] C: cached {} of {} result(s) [{}]{}",
                stored, results.size(), names,
                silent.isEmpty() ? "" : ", no answer for " + silent);
        }
    }

    /** Drops everything, e.g. on level change. */
    public static void clear() {
        CACHE.clear();
        lastAsked = Set.of();
    }
}