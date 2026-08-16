package com.hybrizat.crndisplaynext;

import com.hybrizat.crndisplaynext.display.ModDisplayTypesExt;
import com.hybrizat.crndisplaynext.client.FontLoader;
import net.minecraft.client.Minecraft;
import com.hybrizat.crndisplaynext.network.CacheListPayload;
import com.hybrizat.crndisplaynext.network.FetchImagePayload;
import com.hybrizat.crndisplaynext.network.ImageDataPayload;
import com.hybrizat.crndisplaynext.network.GraphicsUrlPayload;
import com.hybrizat.crndisplaynext.network.RequestCachePayload;
import com.hybrizat.crndisplaynext.network.HideTechnicalStopsPacket;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(CRNDisplayNextMod.MOD_ID)
public class CRNDisplayNextMod {

    public static final String MOD_ID = "crndisplaynext";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public CRNDisplayNextMod(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("CRN Display Native Extended initializing...");
        modEventBus.addListener(this::registerPayloads);
        modEventBus.addListener(this::setup);
        modEventBus.addListener(this::clientSetup);
    }

    private void setup(FMLCommonSetupEvent event) {
        // Display-type registry must be populated on BOTH sides — the dedicated
        // server needs the DisplayTypeResourceKey objects (and settings factory)
        // to persist/read NBT and to resolve the jre_graphics type.
        event.enqueueWork(ModDisplayTypesExt::init);
        LOGGER.info("CRN Display Native Extended common setup complete.");
    }

    private void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            FontLoader.init(Minecraft.getInstance().gameDirectory);
            LOGGER.info("CRN Display Native Extended: client setup complete.");
        });
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(MOD_ID);
        registrar.playToServer(HideTechnicalStopsPacket.TYPE,
            HideTechnicalStopsPacket.STREAM_CODEC, HideTechnicalStopsPacket::handle);
        registrar.playToServer(RequestCachePayload.TYPE,
            RequestCachePayload.CODEC, RequestCachePayload::handle);
        registrar.playToServer(GraphicsUrlPayload.TYPE,
            GraphicsUrlPayload.CODEC, GraphicsUrlPayload::handle);
        registrar.playToClient(CacheListPayload.TYPE,
            CacheListPayload.CODEC, CacheListPayload::handle);
        registrar.playToServer(FetchImagePayload.TYPE,
            FetchImagePayload.CODEC, FetchImagePayload::handle);
        registrar.playToClient(ImageDataPayload.TYPE,
            ImageDataPayload.CODEC, ImageDataPayload::handle);
        LOGGER.info("CRN Display Native Extended packets registered.");
    }
}
