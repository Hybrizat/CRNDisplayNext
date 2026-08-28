package com.hybrizat.crndisplaynext.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reassembles chunked image data delivered by the server (see ImageDataPayload).
 *
 * <p>Transfers are keyed by block position. A transfer completes once every
 * chunk of the same generation (same total count) has arrived; the fully
 * reassembled byte array is returned exactly once.</p>
 */
public final class ImageReassembler {

    /** Defensive cap: reject absurdly large transfers (>16 MiB at 64 KiB/chunk). */
    private static final int MAX_CHUNKS = 256;
    /** Drop transfers that make no progress for this long (server died
     *  mid-transfer, player disconnected, ...). Without this, the held
     *  chunk arrays would leak until level change / display release. */
    private static final long STALE_AFTER_MS = 30_000;

    private static final class State {
        final int total;
        final byte[][] parts;
        int received;
        long lastActive;

        State(int total) {
            this.total = total;
            this.parts = new byte[total][];
            this.lastActive = System.currentTimeMillis();
        }
    }

    private static final Map<Long, State> states = new ConcurrentHashMap<>();

    private ImageReassembler() {}

    private static void sweepStale() { // caller must hold the states lock
        long now = System.currentTimeMillis();
        for (java.util.Map.Entry<Long, State> e : states.entrySet()) {
            if (now - e.getValue().lastActive > STALE_AFTER_MS) states.remove(e.getKey());
        }
    }

    /**
     * Accept one chunk of an image transfer.
     *
     * @return the reassembled byte array when the final chunk arrived,
     *         or null while more chunks are still expected.
     */
    public static byte[] onChunk(long key, int seq, int total, byte[] chunk) {
        if (chunk == null || total <= 0 || total > MAX_CHUNKS || seq < 0 || seq >= total) {
            return null;
        }
        synchronized (states) {
            sweepStale();
            State st = states.get(key);
            if (st == null || st.total != total) {
                st = new State(total);
                states.put(key, st);
            }
            if (st.parts[seq] != null) return null; // duplicate chunk
            st.parts[seq] = chunk;
            st.lastActive = System.currentTimeMillis();
            st.received++;
            if (st.received < total) return null;

            states.remove(key);
            int len = 0;
            for (byte[] part : st.parts) len += part.length;
            byte[] out = new byte[len];
            int off = 0;
            for (byte[] part : st.parts) {
                System.arraycopy(part, 0, out, off, part.length);
                off += part.length;
            }
            return out;
        }
    }

    /** Drop pending transfer state (display removed or level changed). */
    public static void drop(long key) {
        synchronized (states) { states.remove(key); }
    }

    /** Drop all pending transfer state. */
    public static void clear() {
        synchronized (states) { states.clear(); }
    }
}