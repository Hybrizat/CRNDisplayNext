package com.hybrizat.crndisplaynext.network;

import com.hybrizat.crndisplaynext.client.screen.GraphicsImageScreen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server→Client: list of cached image info strings.
 * Each entry: "id\turl\twidth\theight\tbase64thumb"
 */
public record CacheListPayload(List<String> entries) {

    public static void toBytes(CacheListPayload p, FriendlyByteBuf buf) {
        buf.writeInt(p.entries().size());
        for (String s : p.entries()) buf.writeUtf(s, 32767);
    }

    public static CacheListPayload read(FriendlyByteBuf buf) {
        int n = buf.readInt();
        List<String> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) list.add(buf.readUtf(32767));
        return new CacheListPayload(list);
    }

    public static void handle(CacheListPayload p, Supplier<NetworkEvent.Context> ctxSupplier) {
        ctxSupplier.get().enqueueWork(() -> GraphicsImageScreen.onCacheData(p.entries()));
    }
}