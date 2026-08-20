package com.hybrizat.crndisplaynext.network;

import com.hybrizat.crndisplaynext.CRNDisplayNextMod;
import com.hybrizat.crndisplaynext.server.ServerImageCache;
import com.hybrizat.crndisplaynext.util.ImageUrlPolicy;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Arrays;

/**
 * Client→Server: request image for a URL (from cache or fresh download).
 * The server answers with one or more chunked ImageDataPayload packets,
 * sent on the server main thread (the download itself is async).
 */
public record FetchImagePayload(BlockPos pos, String url) implements CustomPacketPayload {

    public static final Type<FetchImagePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CRNDisplayNextMod.MOD_ID, "fetch_img"));

    public static final StreamCodec<ByteBuf, FetchImagePayload> CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, FetchImagePayload::pos,
            ByteBufCodecs.STRING_UTF8, FetchImagePayload::url,
            FetchImagePayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(FetchImagePayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var server = ctx.player().getServer();
            if (server == null) return;
            if (!ImageUrlPolicy.isPlausibleImageUrl(p.url())) {
                CRNDisplayNextMod.LOGGER.warn("[FetchImg] rejected non-image URL from {}: {}",
                    ctx.player().getName().getString(), p.url());
                return;
            }
            var cache = ServerImageCache.get(server);
            var player = (ServerPlayer) ctx.player();

            // Always try cache first, then download if missing (async thread).
            cache.getOrDownload(p.url()).thenAccept(bytes -> {
                if (bytes == null) {
                    CRNDisplayNextMod.LOGGER.warn("[FetchImg] no image available for {}", p.url());
                    return;
                }
                if (bytes.length > ServerImageCache.MAX_IMAGE_BYTES) {
                    CRNDisplayNextMod.LOGGER.warn("[FetchImg] image too large ({} bytes), not sending: {}",
                        bytes.length, p.url());
                    return;
                }
                // Send on the server main thread, split into bounded-size chunks.
                server.execute(() -> {
                    int total = (bytes.length + ServerImageCache.CHUNK_SIZE - 1) / ServerImageCache.CHUNK_SIZE;
                    for (int i = 0; i < total; i++) {
                        int off = i * ServerImageCache.CHUNK_SIZE;
                        int len = Math.min(ServerImageCache.CHUNK_SIZE, bytes.length - off);
                        byte[] chunk = Arrays.copyOfRange(bytes, off, off + len);
                        PacketDistributor.sendToPlayer(player,
                            new ImageDataPayload(p.pos(), i, total, chunk));
                    }
                });
            });
        });
    }

    public static void request(BlockPos pos, String url) {
        PacketDistributor.sendToServer(new FetchImagePayload(pos, url));
    }
}