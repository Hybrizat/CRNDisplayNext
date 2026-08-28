package com.hybrizat.crndisplaynext.network;

import com.hybrizat.crndisplaynext.CRNDisplayNextMod;
import com.hybrizat.crndisplaynext.server.ServerImageCache;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/** Client→Server: request cache list. Server responds with CacheListPayload. */
public record RequestCachePayload() implements CustomPacketPayload {

    /** Cap on how many cache entries are listed at once (newest first),
     *  keeping the one-payload response comfortably under the 2 MiB
     *  custom-packet limit even with base64 thumbnails. */
    private static final int LIST_LIMIT = 60;

    public static final Type<RequestCachePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CRNDisplayNextMod.MOD_ID, "req_cache"));

    public static final StreamCodec<ByteBuf, RequestCachePayload> CODEC =
        StreamCodec.unit(new RequestCachePayload());

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(RequestCachePayload p, IPayloadContext ctx) {
        CRNDisplayNextMod.LOGGER.info("[ReqCache] Received request from {}", ctx.player().getName().getString());
        ctx.enqueueWork(() -> {
            var server = ctx.player().getServer();
            if (server == null) { CRNDisplayNextMod.LOGGER.warn("[ReqCache] Server is null!"); return; }
            var cache = ServerImageCache.get(server);
            var player = (net.minecraft.server.level.ServerPlayer) ctx.player();
            // Read disk off the main thread: the cache can hold many entries,
            // each with a PNG thumbnail.
            ServerImageCache.downloadExecutor().submit(() -> {
                List<String> list = new java.util.ArrayList<>();
                for (var e : cache.listEntries(LIST_LIMIT)) {
                    String thumb64 = "";
                    try {
                        thumb64 = java.util.Base64.getEncoder().encodeToString(
                            cache.getThumbBytes(e.id()));
                    } catch (Exception ex) {}
                    list.add(e.id() + "\t" + e.url() + "\t" + e.width() + "\t" + e.height() + "\t" + thumb64);
                }
                CRNDisplayNextMod.LOGGER.info("[ReqCache] Sent {} cache entries", list.size());
                server.execute(() ->
                    PacketDistributor.sendToPlayer(player, new CacheListPayload(list)));
            });
        });
    }

    public static void request() {
        PacketDistributor.sendToServer(new RequestCachePayload());
    }
}
