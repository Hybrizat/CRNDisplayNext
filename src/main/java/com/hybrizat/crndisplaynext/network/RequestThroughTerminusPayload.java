package com.hybrizat.crndisplaynext.network;

import com.hybrizat.crndisplaynext.CRNDisplayNextMod;
import de.mrjulsen.crn.api.core.RailwayBackendApi;
import de.mrjulsen.crn.api.core.StopVisitState;
import de.mrjulsen.crn.api.core.snapshot.BoardEntry;
import de.mrjulsen.crn.api.core.snapshot.JourneySnapshot;
import com.hybrizat.crndisplaynext.train.RouteResolver;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Client → Server: resolve the through-service terminus for a departure board.
 *
 * <p>A station or platform display has no journey snapshot of its own — CRN fills
 * {@code trainSnapshot}/{@code journeySnapshot} in {@code contraptionTick} and skips
 * every source other than TRAIN_INFORMATION — so the client cannot walk the section
 * chain itself. It asks the server, which holds the live backend.</p>
 *
 * <p>Needed because {@link BoardEntry#destination()} is deliberately the section's
 * passenger-facing terminus, which for a 直通区段 is the <em>handover station</em>:
 * CRN's {@code JourneyDisplayNames.sectionDestination} keeps a carrying-onward section
 * as-is and {@code terminusStop} then returns the next section's first stop. A through
 * train calling at A-B-C would therefore be announced as bound for D instead of the end
 * of the chain, which is what the same train's own displays show.</p>
 */
public record RequestThroughTerminusPayload(List<TrainCall> calls) implements CustomPacketPayload {

    /**
     * One board entry to resolve: which train, which tracking session, which section the
     * call belongs to, and whether the train is standing at that call's station.
     *
     * <p>{@code sectionIndex} is what makes the row correct: the destination of a
     * departure board entry describes the section <em>that call</em> belongs to, not the
     * section the train happens to be running at the moment the board is drawn.</p>
     */
    public record TrainCall(UUID trainId, UUID sessionId, int sectionIndex, boolean arrived) {}

    public static final Type<RequestThroughTerminusPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CRNDisplayNextMod.MOD_ID, "request_through_terminus"));

    /** Upper bound on one request, so a huge board cannot produce an oversized packet. */
    public static final int MAX_CALLS = 48;

    public static final StreamCodec<FriendlyByteBuf, RequestThroughTerminusPayload> CODEC = StreamCodec.of(
        (out, payload) -> {
            out.writeVarInt(payload.calls().size());
            for (TrainCall call : payload.calls()) {
                out.writeUUID(call.trainId());
                out.writeUUID(call.sessionId());
                out.writeVarInt(call.sectionIndex());
                out.writeBoolean(call.arrived());
            }
        },
        in -> {
            int size = Math.min(in.readVarInt(), MAX_CALLS);
            List<TrainCall> calls = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                calls.add(new TrainCall(in.readUUID(), in.readUUID(), in.readVarInt(), in.readBoolean()));
            }
            return new RequestThroughTerminusPayload(List.copyOf(calls));
        });

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(RequestThroughTerminusPayload payload, IPayloadContext ctx) {
        // The backend must not be touched from the netty thread — resolve on the main
        // thread together with the reply.
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            List<ThroughTerminusPayload.Result> results = resolve(payload.calls());
            if (CRNDisplayNextMod.LOGGER.isDebugEnabled()) {
                CRNDisplayNextMod.LOGGER.debug("[Through] S: resolving {} call(s), backend active={} -> {} resolved{}",
                    payload.calls().size(), RailwayBackendApi.isActive(), results.size(), describe(results));
            }
            if (results.isEmpty()) return;
            player.connection.send(new ThroughTerminusPayload(results));
        });
    }

    private static String describe(List<ThroughTerminusPayload.Result> results) {
        if (results.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(" [");
        for (int i = 0; i < results.size() && i < 8; i++) {
            if (i > 0) sb.append(", ");
            sb.append(results.get(i).name());
        }
        if (results.size() > 8) sb.append(", …");
        return sb.append(']').toString();
    }

    /**
     * Walks each train's through-service chain with the same {@link RouteResolver} the
     * train displays use, so a platform board and the train's own front/side/VIS
     * displays can never disagree about where the service ends.
     *
     * @return one result per call that could be resolved; a train with no live journey
     *         is simply left out, and the client keeps CRN's own destination for it
     */
    public static List<ThroughTerminusPayload.Result> resolve(List<TrainCall> calls) {
        List<ThroughTerminusPayload.Result> results = new ArrayList<>(calls.size());
        List<String> skipped = new ArrayList<>();
        StringBuilder detail = new StringBuilder();
        for (TrainCall call : calls) {
            if (call == null || call.trainId() == null) continue;
            String id = call.trainId().toString().substring(0, 8);
            JourneySnapshot journey = RailwayBackendApi.getJourney(call.trainId()).orElse(null);
            if (journey == null || journey.isEmpty()) {
                skipped.add(id + ":no-journey");
                continue;
            }
            // The row belongs to the section this call is in, so that is what decides its
            // destination. Falling back to the train's current section only matters when
            // the call carries no section at all (-1), which a section-less timetable does.
            var terminus = RouteResolver.terminusOfSection(journey, call.sectionIndex());
            if (terminus == null || !terminus.isKnown()) {
                terminus = RouteResolver.terminus(journey, RouteResolver.resolve(journey, call.arrived()));
            }
            if (terminus == null || !terminus.isKnown()) {
                skipped.add(id + ":no-terminus");
                continue;
            }
            results.add(new ThroughTerminusPayload.Result(call.trainId(), call.sessionId(), terminus.displayName()));
            // What CRN itself would have named for the same call, so the two can be
            // compared: SectionSnapshot.destination() is the raw section terminus, is not
            // filtered against the station blacklist, and for a through section is the
            // handover station rather than the end of the chain.
            String crnRaw = journey.section(call.sectionIndex())
                .map(s -> s.destination().displayName()).orElse("-");
            if (detail.length() < 400) {
                detail.append(' ').append(id).append('=').append(terminus.displayName())
                    .append("(crn:").append(crnRaw).append(')');
            }
        }
        if (CRNDisplayNextMod.LOGGER.isDebugEnabled()) {
            CRNDisplayNextMod.LOGGER.debug("[Through] S: {} call(s) -> {} resolved{}; skipped={}",
                calls.size(), results.size(), detail, skipped);
        }
        return results;
    }

    /** Convenience for building a call from a board entry. */
    public static TrainCall of(BoardEntry entry) {
        return new TrainCall(entry.trainId(), entry.sessionId(), entry.sectionIndex(),
            entry.visitState() == StopVisitState.CURRENT);
    }
}