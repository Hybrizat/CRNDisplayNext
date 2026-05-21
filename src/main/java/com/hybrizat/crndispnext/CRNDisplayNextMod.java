package com.hybrizat.crndisplaynext;

import com.hybrizat.crndisplaynext.display.ModDisplayTypesExt;
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
        LOGGER.info("CRN Display Extended initializing...");
        modEventBus.addListener(this::registerPayloads);
        modEventBus.addListener(this::setup);
        modEventBus.addListener(this::clientSetup);
    }

    private void setup(FMLCommonSetupEvent event) {
        LOGGER.info("CRN Display Extended common setup complete.");
    }

    private void clientSetup(FMLClientSetupEvent event) {
        // Register display types client-side (rendering only runs on client)
        // enqueueWork ensures thread-safe registry access
        event.enqueueWork(() -> {
            ModDisplayTypesExt.init();
            LOGGER.info("CRN Display Extended: registered {} custom display types.",
                1); // update count as we add more
        });
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(MOD_ID);
        registrar.playToServer(
            HideTechnicalStopsPacket.TYPE,
            HideTechnicalStopsPacket.STREAM_CODEC,
            HideTechnicalStopsPacket::handle
        );
        LOGGER.info("CRN Display Extended packets registered.");
    }
}
