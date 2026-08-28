package com.hybrizat.crndisplaynext.mixin;

import com.hybrizat.crndisplaynext.api.IBasicTrainDisplayDataExt;
import de.mrjulsen.crn.data.TrainCategory;
import de.mrjulsen.crn.data.train.ETrainStopState;
import de.mrjulsen.crn.data.train.ScheduleSection;
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
 * Populates category color/name into BasicTrainDisplayData.
 *
 * Section resolution for DEPARTURE state (mirrors ETrainStopState.resolveSection):
 *   section = getSectionForIndex(scheduleIndex)   ← index of this stop in schedule
 *   if (!section.isUsable()) section = section.previousSection()
 *
 * This is the same logic CRN uses for line color, so category will match line color.
 *
 * Key fix vs previous version:
 *   getSectionByIndex(stop.getSectionIndex())  ← returns section 1 for C in A-B-C-||-C-D-A
 *   getSectionForIndex(stop.getScheduleIndex()) ← returns section 2 (correct)
 */
@Mixin(value = BasicTrainDisplayData.class, remap = false)
public class MixinBasicTrainDisplayDataFactory {

    @Inject(
        method = "of(Lde/mrjulsen/crn/data/train/TrainStop;)Lde/mrjulsen/crn/data/train/portable/BasicTrainDisplayData;",
        at = @At("RETURN"), remap = false)
    private static void onOfTrainStop(TrainStop stop,
            CallbackInfoReturnable<BasicTrainDisplayData> cir) {

        BasicTrainDisplayData result = cir.getReturnValue();
        if (!(result instanceof IBasicTrainDisplayDataExt ext)) return;

        TrainListener.getTrainData(stop.getTrainId()).ifPresent(data -> {
            // getSectionForIndex uses the schedule entry index — same as CRN's line color logic.
            // For DEPARTURE: use current section if usable, else previousSection.
            // (This mirrors ETrainStopState.DEPARTURE branch in resolveSection())
            ScheduleSection rawSection = data.getSectionForIndex(stop.getScheduleIndex());

            for (ETrainStopState state : ETrainStopState.values()) {
                ScheduleSection section;
                if (state == ETrainStopState.ARRIVAL) {
                    // ARRIVAL: if this is the first stop of its section AND
                    // previous section includes next station, use previous section.
                    boolean isFirst = rawSection.getFirstStop()
                        .map(s -> s.getEntryIndex() == stop.getScheduleIndex())
                        .orElse(false);
                    ScheduleSection prev = rawSection.previousSection();
                    if (isFirst && (prev.shouldIncludeNextStationOfNextSection()
                            || !rawSection.isUsable())) {
                        section = prev;
                    } else {
                        section = rawSection;
                    }
                } else {
                    // DEPARTURE: use current section, fall back to previous if not usable
                    section = rawSection.isUsable() ? rawSection : rawSection.previousSection();
                }

                String name   = section.getTrainCategory().map(TrainCategory::getCategoryName).orElse("");
                DLColor color = section.getTrainCategory().map(TrainCategory::getColor).orElse(DLColor.TRANSPARENT);
                ext.crndisplaynext$setCategoryData(state, name, color);
            }
            // Store the raw Minecraft train entity name (Train.name), set by the player.
            // This is always the actual train name regardless of line configuration,
            // e.g. "やまびこ123号" even when the train has a line assigned.
            String entityName = data.getTrain().name.getString();
            ext.crndisplaynext$setTrainEntityName(entityName);
        });
    }

    @Inject(
        method = "fromNbt(Lnet/minecraft/nbt/CompoundTag;)Lde/mrjulsen/crn/data/train/portable/BasicTrainDisplayData;",
        at = @At("RETURN"), remap = false)
    private static void onFromNbt(CompoundTag nbt,
            CallbackInfoReturnable<BasicTrainDisplayData> cir) {

        BasicTrainDisplayData result = cir.getReturnValue();
        if (!(result instanceof IBasicTrainDisplayDataExt ext)) return;

        // Restore the raw train entity name (written by MixinBasicTrainDisplayData.onToNbt)
        if (nbt.contains("crndisplaynext.TrainEntityName")) {
            ext.crndisplaynext$setTrainEntityName(nbt.getString("crndisplaynext.TrainEntityName"));
        }
        if (!nbt.contains("crndisplaynext.CategoryColors")) return;
        CompoundTag colors = nbt.getCompound("crndisplaynext.CategoryColors");
        CompoundTag names  = nbt.getCompound("crndisplaynext.CategoryNames");
        for (ETrainStopState state : ETrainStopState.values()) {
            String key    = String.valueOf(state.getId());
            DLColor color = colors.contains(key) ? DLColor.fromInt(colors.getInt(key)) : DLColor.TRANSPARENT;
            String name   = names.contains(key) ? names.getString(key) : "";
            ext.crndisplaynext$setCategoryData(state, name, color);
        }
    }
}
