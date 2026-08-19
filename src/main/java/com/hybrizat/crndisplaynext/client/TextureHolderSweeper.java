package com.hybrizat.crndisplaynext.client;

import com.hybrizat.crndisplaynext.CRNDisplayNextMod;
import com.hybrizat.crndisplaynext.display.ber.BERJREGraphics;
import com.hybrizat.crndisplaynext.display.ber.BERJREPassengerVIS;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Periodically releases dynamic-texture holders whose backing block entity has been
 * removed or switched to another display type, so GL resources are not leaked.
 * Registered on the NeoForge (Minecraft) event bus during client setup only.
 */
public final class TextureHolderSweeper {

    private static final int SWEEP_INTERVAL_TICKS = 10;
    private static int tickCounter;

    private TextureHolderSweeper() {}

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ClientLevel)) return;
        if (++tickCounter % SWEEP_INTERVAL_TICKS != 0) return;
        try {
            BERJREGraphics.sweep();
            BERJREPassengerVIS.sweep();
        } catch (Exception e) {
            CRNDisplayNextMod.LOGGER.warn("[GfxSweep] texture holder sweep failed", e);
        }
    }
}