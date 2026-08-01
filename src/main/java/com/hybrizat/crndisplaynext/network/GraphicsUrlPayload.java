package com.hybrizat.crndisplaynext.network;

import com.hybrizat.crndisplaynext.CRNDisplayNextMod;
import com.hybrizat.crndisplaynext.display.settings.GraphicsDisplaySettings;
import de.mrjulsen.crn.block.blockentity.AdvancedDisplayBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.hybrizat.crndisplaynext.server.ServerImageCache;

/**
 * Client→Server: set image URL for graphics display.
 */
public record GraphicsUrlPayload(BlockPos pos, String url) implements CustomPacketPayload {

    public static final Type<GraphicsUrlPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CRNDisplayNextMod.MOD_ID, "gfx_url"));

    public static final StreamCodec<ByteBuf, GraphicsUrlPayload> CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC,      GraphicsUrlPayload::pos,
            ByteBufCodecs.STRING_UTF8,   GraphicsUrlPayload::url,
            GraphicsUrlPayload::new
        );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(GraphicsUrlPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            BlockEntity be = ctx.player().level().getBlockEntity(p.pos());
            if (be instanceof AdvancedDisplayBlockEntity adbe) {
                var settings = adbe.getSettings();
                if (settings instanceof GraphicsDisplaySettings gs) {
                    gs.setImageUrl(p.url());
                    if (ctx.player().getServer() != null) {
                        CRNDisplayNextMod.LOGGER.info("[GfxUrl] Caching URL: {}", p.url());
                        var cache = ServerImageCache.get(ctx.player().getServer());
                        cache.downloadAndCache(p.url()).thenAccept(entry -> {
                            CRNDisplayNextMod.LOGGER.info("[GfxUrl] Cached: {} -> {}",
                                p.url(), entry != null ? entry.id() : "FAILED");
                        });
                    }
                    adbe.setDisplayType(adbe.getDisplayType(), gs);
                    adbe.setChanged();
                    if (be.getLevel() instanceof ServerLevel sl)
                        sl.sendBlockUpdated(p.pos(), be.getBlockState(), be.getBlockState(), 3);
                }
            }
        });
    }
}
