package com.hybrizat.crndisplaynext.network;

import com.hybrizat.crndisplaynext.client.ImageReassembler;
import com.hybrizat.crndisplaynext.client.screen.GraphicsImageScreen;
import com.hybrizat.crndisplaynext.display.ber.BERJREGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server→Client: one chunk of an image (see FetchImagePayload).
 *
 * <p>The client reassembles chunks by position; once the final chunk arrives,
 * the full image is handed to the display texture holder and, if open, to the
 * image-settings screen (live preview).</p>
 */
public record ImageDataPayload(BlockPos pos, int seq, int total, byte[] chunk) {

    public static void toBytes(ImageDataPayload p, FriendlyByteBuf buf) {
        buf.writeBlockPos(p.pos());
        buf.writeVarInt(p.seq());
        buf.writeVarInt(p.total());
        buf.writeByteArray(p.chunk());
    }

    public static ImageDataPayload read(FriendlyByteBuf buf) {
        return new ImageDataPayload(
            buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readByteArray());
    }

    public static void handle(ImageDataPayload p, Supplier<NetworkEvent.Context> ctxSupplier) {
        ctxSupplier.get().enqueueWork(() -> {
            long key = p.pos().asLong();
            byte[] full = ImageReassembler.onChunk(key, p.seq(), p.total(), p.chunk());
            if (full == null) return; // more chunks still expected
            BERJREGraphics.onImageData(p.pos(), full);
            GraphicsImageScreen.onImageData(p.pos(), full);
        });
    }
}