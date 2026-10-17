package com.hybrizat.crndisplaynext.network;

import com.hybrizat.crndisplaynext.CRNDisplayNextMod;
import com.hybrizat.crndisplaynext.train.ThroughTerminusCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server → Client: the resolved through-service terminus of each requested train.
 *
 * <p>Carries only the station's display name. The client needs nothing else: it puts
 * this in the 行先 column in place of {@code BoardEntry.destination()}, which is the
 * handover station for a 直通区段.</p>
 *
 * @see RequestThroughTerminusPayload
 */
public record ThroughTerminusPayload(List<Result> results) implements CustomPacketPayload {

    /** @param name the terminus station's display name, never blank */
    public record Result(UUID trainId, UUID sessionId, String name) {}

    public static final Type<ThroughTerminusPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CRNDisplayNextMod.MOD_ID, "through_terminus"));

    /** Station names come from CRN's own station registry; this is only a sanity bound. */
    private static final int MAX_NAME_LENGTH = 256;

    public static final StreamCodec<FriendlyByteBuf, ThroughTerminusPayload> CODEC = StreamCodec.of(
        (out, payload) -> {
            out.writeVarInt(payload.results().size());
            for (Result result : payload.results()) {
                out.writeUUID(result.trainId());
                out.writeUUID(result.sessionId());
                out.writeUtf(result.name(), MAX_NAME_LENGTH);
            }
        },
        in -> {
            int size = Math.min(in.readVarInt(), RequestThroughTerminusPayload.MAX_CALLS);
            List<Result> results = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                results.add(new Result(in.readUUID(), in.readUUID(), in.readUtf(MAX_NAME_LENGTH)));
            }
            return new ThroughTerminusPayload(List.copyOf(results));
        });

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ThroughTerminusPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ThroughTerminusCache.accept(payload.results()));
    }
}