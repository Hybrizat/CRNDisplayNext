package com.hybrizat.crndisplaynext.network;

import com.hybrizat.crndisplaynext.CRNDisplayNextMod;
import com.hybrizat.crndisplaynext.server.ServerImageCache;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client→Server: request image for a URL (from cache or fresh download). */
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
            var cache = ServerImageCache.get(server);
            var player = (ServerPlayer) ctx.player();

            // Always try cache first, then download if missing
            cache.getOrDownload(p.url()).thenAccept(bytes -> {
                if (bytes != null) {
                    PacketDistributor.sendToPlayer(player,
                        new ImageDataPayload(p.pos(), bytes));
                }
            });
        });
    }

    public static void request(BlockPos pos, String url) {
        PacketDistributor.sendToServer(new FetchImagePayload(pos, url));
    }
}
