package com.hybrizat.crndisplaynext.network;

import com.hybrizat.crndisplaynext.CRNDisplayNextMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server→Client: image bytes for a display block. */
public record ImageDataPayload(BlockPos pos, byte[] data) implements CustomPacketPayload {

    public static final Type<ImageDataPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CRNDisplayNextMod.MOD_ID, "img_data"));

    public static final StreamCodec<ByteBuf, ImageDataPayload> CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, ImageDataPayload::pos,
            ByteBufCodecs.BYTE_ARRAY, ImageDataPayload::data,
            ImageDataPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ImageDataPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            com.hybrizat.crndisplaynext.display.ber.BERJREGraphics.onImageData(p.pos(), p.data());
        });
    }
}
