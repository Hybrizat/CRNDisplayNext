package com.hybrizat.crndisplaynext.client;

import com.hybrizat.crndisplaynext.CRNDisplayNextMod;
import com.hybrizat.crndisplaynext.display.ber.BERJREGraphics;
import com.hybrizat.crndisplaynext.display.ber.BERJREPassengerVIS;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraftforge.event.TickEvent;

/**
 * Periodically reclaims dynamic-texture holders whose display has gone quiet
 * (backing block entity unloaded/removed, or contraption dismantled), so GL
 * resources are not leaked. Liveness is judged by renderer activity (see the
 * BER renderers), not by position lookups: carriage-mounted displays never
 * live in the level's block entity map, so position-based checks would wrongly
 * reap them.
 * Registered on the Forge (Minecraft) event bus during client setup only.
 */
public final class TextureHolderSweeper {

    /**
     * Monotonic client-tick counter, advanced once per level tick (main thread
     * only). Used by the BER renderers as the liveness clock for activity-based
     * holder reclamation (1.20.1's Minecraft class exposes no global tickCount).
     */
    public static int clientTicks;

    private static final int CLEANUP_INTERVAL_TICKS = 40;
    private static int tickCounter;

    private TextureHolderSweeper() {}

    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.level instanceof ClientLevel)) return;
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
