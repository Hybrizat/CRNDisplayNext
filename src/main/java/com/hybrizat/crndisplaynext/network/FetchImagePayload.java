package com.hybrizat.crndisplaynext.network;

import com.hybrizat.crndisplaynext.CRNDisplayNextMod;
import com.hybrizat.crndisplaynext.server.ServerImageCache;
import com.hybrizat.crndisplaynext.util.ImageUrlPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.Arrays;
import java.util.function.Supplier;

/**
 * Client→Server: request image for a URL (from cache or fresh download).
 * The server answers with one or more chunked ImageDataPayload packets,
 * sent on the server main thread (the download itself is async).
 */
public record FetchImagePayload(BlockPos pos, String url) {

    public static void toBytes(FetchImagePayload p, FriendlyByteBuf buf) {
        buf.writeBlockPos(p.pos());
        buf.writeUtf(p.url(), 32767);
    }

    public static FetchImagePayload read(FriendlyByteBuf buf) {
        return new FetchImagePayload(buf.readBlockPos(), buf.readUtf(32767));
    }

    public static void handle(FetchImagePayload p, Supplier<NetworkEvent.Context> ctxSupplier) {
        ServerPlayer player = ctxSupplier.get().getSender();
        if (player == null) return;
        ctxSupplier.get().enqueueWork(() -> {
            var server = player.getServer();
            if (server == null) return;
            if (!ImageUrlPolicy.isPlausibleImageUrl(p.url())) {
                CRNDisplayNextMod.LOGGER.warn("[FetchImg] rejected non-image URL from {}: {}",
                    player.getName().getString(), p.url());
                return;
            }
            var cache = ServerImageCache.get(server);

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
                        NetworkManager.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                            new ImageDataPayload(p.pos(), i, total, chunk));
                    }
                });
            });
        });
    }

    public static void request(BlockPos pos, String url) {
        NetworkManager.CHANNEL.sendToServer(new FetchImagePayload(pos, url));
    }
}