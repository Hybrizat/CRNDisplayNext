package com.hybrizat.crndisplaynext.network;

import com.hybrizat.crndisplaynext.CRNDisplayNextMod;
import com.hybrizat.crndisplaynext.display.settings.GraphicsDisplaySettings;
import com.hybrizat.crndisplaynext.server.ServerImageCache;
import com.hybrizat.crndisplaynext.util.ImageUrlPolicy;
import com.mojang.brigadier.arguments.StringArgumentType;
import de.mrjulsen.crn.block.blockentity.AdvancedDisplayBlockEntity;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Server-side command: /gfxurl &lt;url&gt; — quick set image URL on the targeted
 * display block. No client-only references (safe for dedicated servers).
 */
@EventBusSubscriber(modid = CRNDisplayNextMod.MOD_ID)
public class GfxUrlCommand {

    @SubscribeEvent
    public static void registerServer(RegisterCommandsEvent event) {
        CRNDisplayNextMod.LOGGER.info("[GfxUrl] RegisterCommandsEvent fired, registering /gfxurl");
        event.getDispatcher().register(
            Commands.literal("gfxurl")
                .then(Commands.argument("url", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        String url = StringArgumentType.getString(ctx, "url");
                        var p = ctx.getSource().getPlayerOrException();
                        CRNDisplayNextMod.LOGGER.info("[GfxUrl] executed by {} url={}",
                            p.getName().getString(), url);
                        if (!ImageUrlPolicy.isPlausibleImageUrl(url)) {
                            CRNDisplayNextMod.LOGGER.warn("[GfxUrl] rejected non-image URL: {}", url);
                            p.displayClientMessage(net.minecraft.network.chat.Component.literal(
                                "Not an image URL — use an http(s) link with an image extension (.png/.jpg/...)"), false);
                            return 0;
                        }
                        var hit = p.pick(20, 0, false);
                        if (!(hit instanceof BlockHitResult bhr)) {
                            CRNDisplayNextMod.LOGGER.info("[GfxUrl] no block in sight");
                            return 0;
                        }
                        var be = p.level().getBlockEntity(bhr.getBlockPos());
                        if (!(be instanceof AdvancedDisplayBlockEntity adbe)) {
                            CRNDisplayNextMod.LOGGER.info("[GfxUrl] not a display BE at {}", bhr.getBlockPos());
                            return 0;
                        }
                        // Diagnose: which display type / controller / settings does this block carry?
                        CRNDisplayNextMod.LOGGER.info("[GfxUrl] BE at {} displayType={} controller={} settings={}",
                            bhr.getBlockPos(),
                            adbe.getDisplayType() == null ? "null" : adbe.getDisplayType().toString(),
                            adbe.isController(),
                            adbe.getSettings() == null ? "null" : adbe.getSettings().getClass().getSimpleName());
                        // Accept existing GraphicsDisplaySettings, or auto-switch the
                        // block to the jre_graphics display type.
                        var s = adbe.getSettings();
                        com.hybrizat.crndisplaynext.display.settings.GraphicsDisplaySettings gs;
                        if (s instanceof com.hybrizat.crndisplaynext.display.settings.GraphicsDisplaySettings gs0) {
                            gs = gs0;
                        } else {
                            CRNDisplayNextMod.LOGGER.info("[GfxUrl] switching display to jre_graphics (was {})",
                                s == null ? "null" : s.getClass().getSimpleName());
                            gs = new com.hybrizat.crndisplaynext.display.settings.GraphicsDisplaySettings();
                            adbe.setDisplayType(com.hybrizat.crndisplaynext.display.ModDisplayTypesExt.JRE_GRAPHICS, gs);
                        }
                        gs.setImageUrl(url);
                        adbe.setDisplayType(adbe.getDisplayType(), gs);
                        adbe.setChanged();
                        // Cache on server
                        ServerImageCache.get(p.getServer()).downloadAndCache(url);
                        if (p.level() instanceof ServerLevel sl) {
                            sl.sendBlockUpdated(bhr.getBlockPos(),
                                be.getBlockState(), be.getBlockState(), 3);
                            // Push BE NBT (incl. imgUrl) to all online players so
                            // clients on dedicated servers pick up the new URL.
                            var updatePacket = adbe.getUpdatePacket();
                            if (updatePacket != null) {
                                for (net.minecraft.server.level.ServerPlayer sp :
                                        sl.getServer().getPlayerList().getPlayers()) {
                                    sp.connection.send(updatePacket);
                                }
                            }
                        }
                        CRNDisplayNextMod.LOGGER.info("[GfxUrl] set url {} at {}", url, bhr.getBlockPos());
                        return 1;
                    }))
        );
    }
}
