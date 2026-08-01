package com.hybrizat.crndisplaynext.network;

import com.hybrizat.crndisplaynext.CRNDisplayNextMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Server→Client: list of cached image info strings.
 * Each entry: "id\turl\twidth\theight"
 */
public record CacheListPayload(List<String> entries) implements CustomPacketPayload {

    public static final Type<CacheListPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CRNDisplayNextMod.MOD_ID, "cache_list"));

    public static final StreamCodec<ByteBuf, CacheListPayload> CODEC =
        StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()),
            CacheListPayload::entries,
            CacheListPayload::new
        );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(CacheListPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            com.hybrizat.crndisplaynext.client.screen.GraphicsImageScreen.onCacheData(p.entries());
        });
    }
}
