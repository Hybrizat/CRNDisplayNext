package com.hybrizat.crndisplaynext.mixin;

import com.hybrizat.crndisplaynext.api.IBasicTrainDisplayDataExt;
import de.mrjulsen.crn.data.TrainCategory;
import de.mrjulsen.crn.data.train.ETrainStopState;
import de.mrjulsen.crn.data.train.TrainListener;
import de.mrjulsen.crn.data.train.TrainStop;
import de.mrjulsen.crn.data.train.portable.BasicTrainDisplayData;
import de.mrjulsen.mcdragonlib.util.DLColor;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Populates category data after BasicTrainDisplayData is created (server side)
 * or loaded from NBT (client side).
 *
 * Kept separate from MixinBasicTrainDisplayData to avoid instance/static
 * method injection conflicts in the same Mixin class.
 */
@Mixin(value = BasicTrainDisplayData.class, remap = false)
public class MixinBasicTrainDisplayDataFactory {

    // ── Server: populate after of(TrainStop) ─────────────────────────────

    @Inject(
        method = "of(Lde/mrjulsen/crn/data/train/TrainStop;)Lde/mrjulsen/crn/data/train/portable/BasicTrainDisplayData;",
        at = @At("RETURN"),
        remap = false
    )
    private static void onOfTrainStop(
            TrainStop stop,
            CallbackInfoReturnable<BasicTrainDisplayData> cir) {

        BasicTrainDisplayData result = cir.getReturnValue();
        if (!(result instanceof IBasicTrainDisplayDataExt ext)) return;

        TrainListener.getTrainData(stop.getTrainId()).ifPresent(data -> {
            for (ETrainStopState state : ETrainStopState.values()) {
                var section = state.resolveSection(
                    data.getSectionForIndex(stop.getScheduleIndex()),
                    data.waitingAtStationIndex,
                    stop.getScheduleIndex()
                );
                String name = section.getTrainCategory()
                    .map(TrainCategory::getCategoryName)
                    .orElse("");
                DLColor color = section.getTrainCategory()
                    .map(TrainCategory::getColor)
                    .orElse(DLColor.TRANSPARENT);
                ext.crndisplaynext$setCategoryData(state, name, color);
            }
        });
    }

    // ── Client: restore from NBT after fromNbt() ──────────────────────────

    @Inject(
        method = "fromNbt(Lnet/minecraft/nbt/CompoundTag;)Lde/mrjulsen/crn/data/train/portable/BasicTrainDisplayData;",
        at = @At("RETURN"),
        remap = false
    )
    private static void onFromNbt(
            CompoundTag nbt,
            CallbackInfoReturnable<BasicTrainDisplayData> cir) {

        BasicTrainDisplayData result = cir.getReturnValue();
        if (!(result instanceof IBasicTrainDisplayDataExt ext)) return;

        String colorKey = "crndisplaynext.CategoryColors";
        String nameKey  = "crndisplaynext.CategoryNames";
        if (!nbt.contains(colorKey) || !nbt.contains(nameKey)) return;

        CompoundTag colors = nbt.getCompound(colorKey);
        CompoundTag names  = nbt.getCompound(nameKey);
        for (ETrainStopState state : ETrainStopState.values()) {
            String key = String.valueOf(state.getId());
            DLColor color = colors.contains(key)
                ? DLColor.fromInt(colors.getInt(key)) : DLColor.TRANSPARENT;
            String name = names.contains(key) ? names.getString(key) : "";
            ext.crndisplaynext$setCategoryData(state, name, color);
        }
    }
}
