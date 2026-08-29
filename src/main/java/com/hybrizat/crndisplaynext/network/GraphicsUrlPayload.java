package com.hybrizat.crndisplaynext.network;

import com.hybrizat.crndisplaynext.CRNDisplayNextMod;
import com.hybrizat.crndisplaynext.display.settings.GraphicsDisplaySettings;
import de.mrjulsen.crn.block.blockentity.AdvancedDisplayBlockEntity;
import com.hybrizat.crndisplaynext.server.ServerImageCache;
import com.hybrizat.crndisplaynext.util.ImageUrlPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client→Server: set image URL for graphics display.
 */
public record GraphicsUrlPayload(BlockPos pos, String url) {

    public static void toBytes(GraphicsUrlPayload p, FriendlyByteBuf buf) {
        buf.writeBlockPos(p.pos());
        buf.writeUtf(p.url(), 32767);
    }

    public static GraphicsUrlPayload read(FriendlyByteBuf buf) {
        return new GraphicsUrlPayload(buf.readBlockPos(), buf.readUtf(32767));
    }

    public static void handle(GraphicsUrlPayload p, Supplier<NetworkEvent.Context> ctxSupplier) {
        ServerPlayer player = ctxSupplier.get().getSender();
        if (player == null) return;
        ctxSupplier.get().enqueueWork(() -> {
            if (!ImageUrlPolicy.isPlausibleImageUrl(p.url())) {
                CRNDisplayNextMod.LOGGER.warn("[GfxUrl] rejected non-image URL from {}: {}",
                    player.getName().getString(), p.url());
                return;
            }
            BlockEntity be = player.level().getBlockEntity(p.pos());
            if (be instanceof AdvancedDisplayBlockEntity adbe) {
                var settings = adbe.getSettings();
                if (settings instanceof GraphicsDisplaySettings gs) {
                    gs.setImageUrl(p.url());
                    if (player.getServer() != null) {
                        CRNDisplayNextMod.LOGGER.info("[GfxUrl] Caching URL: {}", p.url());
                        var cache = ServerImageCache.get(player.getServer());
                        cache.downloadAndCache(p.url()).thenAccept(entry -> {
                            CRNDisplayNextMod.LOGGER.info("[GfxUrl] Cached: {} -> {}",
                                p.url(), entry != null ? entry.id() : "FAILED");
                        });
                    }
                    adbe.setDisplayType(adbe.getDisplayType(), gs);
                    adbe.setChanged();
                    if (be.getLevel() instanceof ServerLevel sl) {
                        sl.sendBlockUpdated(p.pos(), be.getBlockState(), be.getBlockState(), 3);
                        // Dedicated-server fix: explicitly push the BE NBT (incl. imgUrl)
                        // to every online player so the client reads the updated settings.
                        var updatePacket = adbe.getUpdatePacket();
                        if (updatePacket != null) {
                            for (ServerPlayer sp : sl.getServer().getPlayerList().getPlayers()) {
                                sp.connection.send(updatePacket);
                            }
                        }
                    }
                }
            }
        });
    }
}