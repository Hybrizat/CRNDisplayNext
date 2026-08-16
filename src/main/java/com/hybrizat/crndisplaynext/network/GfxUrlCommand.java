package com.hybrizat.crndisplaynext.network;

import com.hybrizat.crndisplaynext.CRNDisplayNextMod;
import com.hybrizat.crndisplaynext.display.settings.GraphicsDisplaySettings;
import com.hybrizat.crndisplaynext.server.ServerImageCache;
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
        event.getDispatcher().register(
            Commands.literal("gfxurl")
                .then(Commands.argument("url", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        String url = StringArgumentType.getString(ctx, "url");
                        var p = ctx.getSource().getPlayerOrException();
                        var hit = p.pick(20, 0, false);
                        if (!(hit instanceof BlockHitResult bhr)) return 0;
                        var be = p.level().getBlockEntity(bhr.getBlockPos());
                        if (be instanceof AdvancedDisplayBlockEntity adbe) {
                            var s = adbe.getSettings();
                            if (s instanceof GraphicsDisplaySettings gs) {
                                gs.setImageUrl(url);
                                adbe.setDisplayType(adbe.getDisplayType(), gs);
                                adbe.setChanged();
                                // Cache on server
                                ServerImageCache.get(p.getServer()).downloadAndCache(url);
                                if (p.level() instanceof ServerLevel sl)
                                    sl.sendBlockUpdated(bhr.getBlockPos(),
                                        be.getBlockState(), be.getBlockState(), 3);
                                return 1;
                            }
                        }
                        return 0;
                    }))
        );
    }
}
