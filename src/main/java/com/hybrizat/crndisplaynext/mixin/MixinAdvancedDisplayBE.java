package com.hybrizat.crndisplaynext.mixin;

import com.hybrizat.crndisplaynext.CRNDisplayNextMod;
import com.hybrizat.crndisplaynext.api.IAdvancedDisplayBEExt;
import com.hybrizat.crndisplaynext.api.IHideTechnicalStops;
import de.mrjulsen.crn.block.blockentity.AdvancedDisplayBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(value = AdvancedDisplayBlockEntity.class, remap = false)
public abstract class MixinAdvancedDisplayBE implements IAdvancedDisplayBEExt {

    @Unique
    private boolean crndisplaynext$hideTechnicalStops = false;

    @Unique
    private static final String NBT_KEY = "crndisplaynext.hideTechnicalStops";

    // ── getSettingsAs interception ─────────────────────────────────────────
    @Inject(
        method = "getSettingsAs(Ljava/lang/Class;)Ljava/util/Optional;",
        at = @At("RETURN"),
        remap = false,
        cancellable = true
    )
    @SuppressWarnings("unchecked")
    private <S> void interceptGetSettingsAs(Class<S> clazz, CallbackInfoReturnable<Optional<S>> cir) {
        if (cir.getReturnValue().isPresent()) return;
        if (clazz != IHideTechnicalStops.class) return;

        IHideTechnicalStops impl = () -> crndisplaynext$hideTechnicalStops;
        cir.setReturnValue(Optional.of((S) impl));
    }

    // ── NBT persistence ────────────────────────────────────────────────────
    // 1.20.1 target signatures: write(CompoundTag, boolean) / read(CompoundTag, boolean)
    @Inject(method = "write", at = @At("TAIL"), remap = false)
    private void crndisplaynext$onWrite(CompoundTag tag,
                                       boolean clientPacket,
                                       CallbackInfo ci) {
        tag.putBoolean(NBT_KEY, crndisplaynext$hideTechnicalStops);
    }

    @Inject(method = "read", at = @At("TAIL"), remap = false)
    private void crndisplaynext$onRead(CompoundTag tag,
                                      boolean clientPacket,
                                      CallbackInfo ci) {
        crndisplaynext$hideTechnicalStops = tag.getBoolean(NBT_KEY);
    }

    // ── IAdvancedDisplayBEExt implementation ───────────────────────────────
    @Override
    @Unique
    public void crndisplaynext$setHideTechnicalStops(boolean value) {
        crndisplaynext$hideTechnicalStops = value;
        // Use BlockEntity cast (available from Minecraft) instead of AdvancedDisplayBlockEntity
        // to avoid triggering transitive dependency resolution (VirtualBlockEntity etc.)
        BlockPos pos = ((BlockEntity)(Object)this).getBlockPos();
        CRNDisplayNextMod.LOGGER.debug("[CRNExt] hideTechnicalStops={} at {}", value, pos);
    }

    @Override
    @Unique
    public boolean crndisplaynext$getHideTechnicalStops() {
        return crndisplaynext$hideTechnicalStops;
    }
}
