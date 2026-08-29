package com.hybrizat.crndisplaynext.network;

import com.hybrizat.crndisplaynext.CRNDisplayNextMod;
import com.hybrizat.crndisplaynext.client.screen.GraphicsImageScreen;
import com.hybrizat.crndisplaynext.display.settings.GraphicsDisplaySettings;
import de.mrjulsen.crn.block.blockentity.AdvancedDisplayBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.api.distmarker.Dist;

/**
 * Client-only command: /gfxgui — opens the image selection GUI.
 * Registered only on the client (dist = Dist.CLIENT) because it references
 * client-exclusive classes (Minecraft, GraphicsImageScreen).
 */
@Mod.EventBusSubscriber(modid = CRNDisplayNextMod.MOD_ID, value = Dist.CLIENT)
public class GfxGuiClientCommand {

    @SubscribeEvent
    public static void registerClient(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("gfxgui").executes(ctx -> {
                var p = Minecraft.getInstance().player;
                if (p == null) return 0;
                var hit = p.pick(20, 0, false);
                if (!(hit instanceof BlockHitResult bhr)) return 0;
                BlockPos pos = bhr.getBlockPos();
                String cur = "";
                if (p.level().getBlockEntity(pos) instanceof AdvancedDisplayBlockEntity adbe) {
                    var s = adbe.getSettings();
                    if (s instanceof GraphicsDisplaySettings gs) cur = gs.getImageUrl();
                }
                Minecraft.getInstance().setScreen(new GraphicsImageScreen(pos, cur));
                return 1;
            })
        );
    }
}
