package com.hybrizat.crndisplaynext.mixin;

import com.hybrizat.crndisplaynext.CRNDisplayNextMod;
import com.hybrizat.crndisplaynext.api.IHideTechnicalStops;
import com.hybrizat.crndisplaynext.util.StopFilter;

import de.mrjulsen.crn.block.blockentity.AdvancedDisplayBlockEntity;
import de.mrjulsen.crn.data.train.portable.StationDisplayData;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

@Mixin(
        targets = "de.mrjulsen.crn.block.display.AdvancedDisplayTarget",
        remap = false
)
public class MixinStationDisplayPrepare {

    @Inject(
            method = "prepare(Ljava/lang/String;ILde/mrjulsen/crn/block/blockentity/AdvancedDisplayBlockEntity;)Ljava/util/List;",
            at = @At("RETURN"),
            cancellable = true,
            remap = false
    )
    private static void onPrepareReturn(
            String filter,
            int maxLines,
            AdvancedDisplayBlockEntity controller,
            CallbackInfoReturnable<List<StationDisplayData>> cir
    ) {

        boolean shouldFilter = getHideTechnicalStops(controller);

        if (!shouldFilter) {
            return;
        }

        List<StationDisplayData> filtered =
                StopFilter.filterDisplayData(
                        cir.getReturnValue(),
                        true
                );

        cir.setReturnValue(filtered);
    }

    /**
     * Uses reflection to avoid compile-time dependency resolution
     * of VirtualBlockEntity / Create internals.
     */
    @SuppressWarnings("unchecked")
    private static boolean getHideTechnicalStops(Object controller) {

        try {

            Method getSettingsAs =
                    controller.getClass()
                            .getMethod("getSettingsAs", Class.class);

            Optional<IHideTechnicalStops> result =
                    (Optional<IHideTechnicalStops>) getSettingsAs.invoke(
                            controller,
                            IHideTechnicalStops.class
                    );

            return result
                    .map(IHideTechnicalStops::hideTechnicalStops)
                    .orElse(false);

        } catch (Exception e) {

            CRNDisplayNextMod.LOGGER.warn(
                    "[CRNExt] Could not read IHideTechnicalStops setting: {}",
                    e.getMessage()
            );

            return false;
        }
    }
}