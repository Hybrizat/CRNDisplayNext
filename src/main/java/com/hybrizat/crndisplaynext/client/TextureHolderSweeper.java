package com.hybrizat.crndisplaynext.client;

import com.hybrizat.crndisplaynext.CRNDisplayNextMod;
import com.hybrizat.crndisplaynext.display.ber.BERJREGraphics;
import com.hybrizat.crndisplaynext.display.ber.BERJREPassengerVIS;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraftforge.event.TickEvent;

/**
 * Periodically releases dynamic-texture holders whose backing block entity has been
 * removed or switched to another display type, so GL resources are not leaked.
 * Registered on the Forge (Minecraft) event bus during client setup only.
 */
public final class TextureHolderSweeper {

    private static final int SWEEP_INTERVAL_TICKS = 10;
    private static int tickCounter;

    private TextureHolderSweeper() {}

    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.level instanceof ClientLevel)) return;
        if (++tickCounter % SWEEP_INTERVAL_TICKS != 0) return;
        try {
            BERJREGraphics.sweep();
            BERJREPassengerVIS.sweep();
        } catch (Exception e) {
            CRNDisplayNextMod.LOGGER.warn("[GfxSweep] texture holder sweep failed", e);
        }
    }
}