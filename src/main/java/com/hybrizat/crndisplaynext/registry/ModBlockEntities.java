package com.hybrizat.crndisplaynext.registry;

import com.hybrizat.crndisplaynext.CRNDisplayNextMod;
import com.hybrizat.crndisplaynext.block.entity.GraphicsDisplayBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CRNDisplayNextMod.MOD_ID);

    public static final Supplier<BlockEntityType<GraphicsDisplayBlockEntity>> GRAPHICS_DISPLAY_BE =
        BLOCK_ENTITIES.register("graphics_display",
            () -> {
                var type = new BlockEntityType<>(
                    GraphicsDisplayBlockEntity::new,
                    java.util.Set.of(ModBlocks.GRAPHICS_DISPLAY.get()),
                    null);
                return type;
            });
}
