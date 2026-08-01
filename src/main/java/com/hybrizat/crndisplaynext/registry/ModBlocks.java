package com.hybrizat.crndisplaynext.registry;

import com.hybrizat.crndisplaynext.CRNDisplayNextMod;
import com.hybrizat.crndisplaynext.block.GraphicsDisplayBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
        DeferredRegister.create(Registries.BLOCK, CRNDisplayNextMod.MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(Registries.ITEM, CRNDisplayNextMod.MOD_ID);

    public static final Supplier<Block> GRAPHICS_DISPLAY = BLOCKS.register("graphics_display",
        () -> new GraphicsDisplayBlock(BlockBehaviour.Properties.of()
            .strength(2f).sound(SoundType.METAL).noOcclusion()));
    public static final Supplier<Item> GRAPHICS_DISPLAY_ITEM = ITEMS.register("graphics_display",
        () -> new BlockItem(GRAPHICS_DISPLAY.get(), new Item.Properties()));
}
