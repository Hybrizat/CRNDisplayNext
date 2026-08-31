package com.hybrizat.crndisplaynext;

import com.hybrizat.crndisplaynext.client.FontLoader;
import com.hybrizat.crndisplaynext.client.TextureHolderSweeper;
import com.hybrizat.crndisplaynext.display.ModDisplayTypesExt;
import com.hybrizat.crndisplaynext.network.NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(CRNDisplayNextMod.MOD_ID)
public class CRNDisplayNextMod {

    public static final String MOD_ID = "crndisplaynext";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public CRNDisplayNextMod() {
        LOGGER.info("CRN Display Native Extended initializing...");
        // Forge 47 (1.20.1) requires a public no-arg @Mod constructor (verified against
        // Create 6.0.8 / CRN 1.20.1 Forge builds). The mod event bus is obtained
        // from the loading context, same idiom Create uses in its onCtor().
        // Forge 47: the SimpleChannel must be registered before the network
        // setup phase — registering in the mod constructor is the standard
        // pattern and guarantees correct registration order on both sides.
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        NetworkManager.init();
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
        MinecraftForge.EVENT_BUS.addListener(TextureHolderSweeper::onLevelTick);
        event.enqueueWork(() -> {
            FontLoader.init(Minecraft.getInstance().gameDirectory);
            LOGGER.info("CRN Display Native Extended: client setup complete.");
        });
    }
}

