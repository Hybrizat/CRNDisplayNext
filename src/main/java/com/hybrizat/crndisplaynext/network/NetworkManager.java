package com.hybrizat.crndisplaynext.network;

import com.hybrizat.crndisplaynext.CRNDisplayNextMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Forge 47 (MC 1.20.1) networking: one SimpleChannel carrying all payloads.
 *
 * <p>The 1.20.1 loader predates the CustomPacketPayload/StreamCodec API,
 * so each message class exposes {@code toBytes}/{@code read}/{@code handle}
 * and is registered with an explicit index on the channel.</p>
 */
public final class NetworkManager {

    /** Bump when the wire format of any payload changes. */
    public static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(CRNDisplayNextMod.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    static {
        // ---- client -> server ----
        CHANNEL.registerMessage(0, HideTechnicalStopsPacket.class,
            HideTechnicalStopsPacket::toBytes,
            HideTechnicalStopsPacket::read,
            HideTechnicalStopsPacket::handle);
        CHANNEL.registerMessage(1, RequestCachePayload.class,
            RequestCachePayload::toBytes,
            RequestCachePayload::read,
            RequestCachePayload::handle);
        CHANNEL.registerMessage(2, GraphicsUrlPayload.class,
            GraphicsUrlPayload::toBytes,
            GraphicsUrlPayload::read,
            GraphicsUrlPayload::handle);
        CHANNEL.registerMessage(3, FetchImagePayload.class,
            FetchImagePayload::toBytes,
            FetchImagePayload::read,
            FetchImagePayload::handle);
        // ---- server -> client ----
        CHANNEL.registerMessage(4, CacheListPayload.class,
            CacheListPayload::toBytes,
            CacheListPayload::read,
            CacheListPayload::handle);
        CHANNEL.registerMessage(5, ImageDataPayload.class,
            ImageDataPayload::toBytes,
            ImageDataPayload::read,
            ImageDataPayload::handle);
    }

    /** Touches the class so static registration runs exactly once. */
    public static void init() {
        if (CHANNEL == null) throw new IllegalStateException("Channel not initialized");
    }

    private NetworkManager() {}
}