package com.hybrizat.crndisplaynext.network;

import com.hybrizat.crndisplaynext.CRNDisplayNextMod;
import com.hybrizat.crndisplaynext.client.screen.GraphicsImageWindow;
import com.hybrizat.crndisplaynext.display.settings.GraphicsDisplaySettings;
import de.mrjulsen.crn.block.blockentity.AdvancedDisplayBlockEntity;
import de.mrjulsen.crn.registry.ModNetworkManager;
import de.mrjulsen.crn.network.packets.AdvancedDisplayUpdatePacketData;
import de.mrjulsen.mcdragonlib.network.NetworkDirection;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * Client-only command: /gfxgui — opens the image picker for the targeted display.
 *
 * <p>The picker is a DragonLib window. With no CRN settings window open there is
 * nothing to nest it in, so this command builds a root window and persists the
 * result through CRN's own display update packet on "Apply".</p>
 */
@EventBusSubscriber(modid = CRNDisplayNextMod.MOD_ID, value = Dist.CLIENT)
public class GfxGuiClientCommand {

    @SubscribeEvent
    public static void registerClient(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("gfxgui").executes(ctx -> {
                var player = Minecraft.getInstance().player;
                if (player == null) {
                    return 0;
                }
                var hit = player.pick(20, 0, false);
                if (!(hit instanceof BlockHitResult bhr)) {
                    return 0;
                }
                BlockPos pos = bhr.getBlockPos();
                if (!(player.level().getBlockEntity(pos) instanceof AdvancedDisplayBlockEntity adbe)) {
                    return 0;
                }
                // Only meaningful for the custom image display type.
                if (!(adbe.getSettings() instanceof GraphicsDisplaySettings settings)) {
                    player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal(
                            "This display is not a Custom Image Display"), false);
                    return 0;
                }
                final boolean doubleSided = true;
                DLWindowBridge.openRoot(pos, settings, () -> {
                    var type = adbe.getDisplayType();
                    ModNetworkManager.ADVANCED_DISPLAY_UPDATE_PACKET.send(
                        NetworkDirection.toServer(),
                        new AdvancedDisplayUpdatePacketData(
                            player.level(), pos, null, type, doubleSided, settings));
                });
                return 1;
            })
        );
    }

    /** Kept separate so the window construction stays in one place. */
    private static final class DLWindowBridge {
        static void openRoot(BlockPos pos, GraphicsDisplaySettings settings, Runnable onApplied) {
            GraphicsImageWindow.openStandalone(pos, settings, onApplied);
        }
    }
}
