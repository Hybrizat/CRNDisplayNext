package com.hybrizat.crndisplaynext.network;

import com.hybrizat.crndisplaynext.CRNDisplayNextMod;
import com.hybrizat.crndisplaynext.client.ImageReassembler;
import com.hybrizat.crndisplaynext.client.screen.GraphicsImageScreen;
import com.hybrizat.crndisplaynext.display.ber.BERJREGraphics;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server→Client: one chunk of an image (see FetchImagePayload).
 *
 * <p>The client reassembles chunks by position; once the final chunk arrives,
 * the full image is handed to the display texture holder and, if open, to the
 * image-settings screen (live preview).</p>
 */
public record ImageDataPayload(BlockPos pos, int seq, int total, byte[] chunk) implements CustomPacketPayload {

    public static final Type<ImageDataPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CRNDisplayNextMod.MOD_ID, "img_data"));

    public static final StreamCodec<ByteBuf, ImageDataPayload> CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC,        ImageDataPayload::pos,
            ByteBufCodecs.VAR_INT,        ImageDataPayload::seq,
            ByteBufCodecs.VAR_INT,        ImageDataPayload::total,
            ByteBufCodecs.BYTE_ARRAY,     ImageDataPayload::chunk,
            ImageDataPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ImageDataPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            long key = p.pos().asLong();
            byte[] full = ImageReassembler.onChunk(key, p.seq(), p.total(), p.chunk());
            if (full == null) return; // more chunks still expected
            BERJREGraphics.onImageData(p.pos(), full);
            GraphicsImageScreen.onImageData(p.pos(), full);
        });
    }
}