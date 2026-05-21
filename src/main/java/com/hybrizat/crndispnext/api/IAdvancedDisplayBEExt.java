package com.hybrizat.crndisplaynext.api;

/**
 * Accessor interface for the extra fields added to AdvancedDisplayBlockEntity
 * by MixinAdvancedDisplayBE.
 *
 * Instead of casting to MixinAdvancedDisplayBE directly (which is messy),
 * cast to this interface:
 *
 *   if (blockEntity instanceof IAdvancedDisplayBEExt ext) {
 *       ext.crndisplaynext$setHideTechnicalStops(true);
 *   }
 *
 * This works because the Mixin applies at class load time, so every
 * AdvancedDisplayBlockEntity instance also implements this interface at runtime.
 */
public interface IAdvancedDisplayBEExt {
    void crndisplaynext$setHideTechnicalStops(boolean value);
    boolean crndisplaynext$getHideTechnicalStops();
}
