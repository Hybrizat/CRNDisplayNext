package com.hybrizat.crndisplaynext.network;

import com.hybrizat.crndisplaynext.CRNDisplayNextMod;
import com.hybrizat.crndisplaynext.server.ServerImageCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Client→Server: request cache list. Server responds with CacheListPayload. */
public record RequestCachePayload() {

    /** Cap on how many cache entries are listed at once (newest first),
     *  keeping the one-payload response comfortably under the 2 MiB
     *  custom-packet limit even with base64 thumbnails. */
    private static final int LIST_LIMIT = 60;

    public static RequestCachePayload read(FriendlyByteBuf buf) {
        return new RequestCachePayload();
    }

    public static void toBytes(RequestCachePayload p, FriendlyByteBuf buf) {
        // no fields
    }

    public static void handle(RequestCachePayload p, Supplier<NetworkEvent.Context> ctxSupplier) {
        ServerPlayer player = ctxSupplier.get().getSender();
        if (player == null) return;
        ctxSupplier.get().enqueueWork(() -> {
            CRNDisplayNextMod.LOGGER.info("[ReqCache] Received request from {}", player.getName().getString());
            var server = player.getServer();
            if (server == null) { CRNDisplayNextMod.LOGGER.warn("[ReqCache] Server is null!"); return; }
            var cache = ServerImageCache.get(server);
            // Read disk off the main thread: the cache can hold many entries,
            // each with a PNG thumbnail.
            ServerImageCache.downloadExecutor().submit(() -> {
                List<String> list = new ArrayList<>();
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
                    NetworkManager.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new CacheListPayload(list)));
            });
        });
    }

    public static void request() {
        NetworkManager.CHANNEL.send(PacketDistributor.SERVER.noArg(), new RequestCachePayload());
    }
}