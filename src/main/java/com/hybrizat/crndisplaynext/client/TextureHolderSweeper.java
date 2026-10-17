package com.hybrizat.crndisplaynext.client;

import com.hybrizat.crndisplaynext.CRNDisplayNextMod;
import com.hybrizat.crndisplaynext.display.ber.BERJREGraphics;
import com.hybrizat.crndisplaynext.display.ber.BERJREPassengerVIS;
import com.hybrizat.crndisplaynext.train.ThroughTerminusCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Periodically reclaims dynamic-texture holders whose display has gone quiet
 * (backing block entity unloaded/removed, or contraption dismantled), so GL
 * resources are not leaked. Liveness is judged by renderer activity (see the
 * BER renderers), not by position lookups: carriage-mounted displays never
 * live in the level's block entity map, so position-based checks would wrongly
 * reap them.
 * Registered on the NeoForge (Minecraft) event bus during client setup only.
 */
public final class TextureHolderSweeper {

    /**
     * Monotonic client-tick counter, advanced once per level tick (main thread
     * only). Used by the BER renderers as the liveness clock for activity-based
     * holder reclamation.
     */
    public static int clientTicks;

    private static final int CLEANUP_INTERVAL_TICKS = 40;
    private static int tickCounter;
    /** Last client level seen, so per-world caches can be dropped when it changes. */
    private static ClientLevel lastLevel;

    private TextureHolderSweeper() {}

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ClientLevel level)) return;
        // A world change invalidates anything keyed by train ids: the ids and tracking
        // sessions of the previous world's trains must not be shown for this one's board.
        if (lastLevel != level) {
            lastLevel = level;
            ThroughTerminusCache.clear();
        }
        clientTicks++;
        if (++tickCounter % CLEANUP_INTERVAL_TICKS != 0) return;
        try {
            BERJREGraphics.cleanup();
            BERJREPassengerVIS.cleanup();
        } catch (Exception e) {
            CRNDisplayNextMod.LOGGER.warn("[GfxCleanup] texture holder cleanup failed", e);
        }
    }
}