package com.hybrizat.crndisplaynext.mixin;

import com.hybrizat.crndisplaynext.api.IBasicTrainDisplayDataExt;
import de.mrjulsen.crn.data.train.ETrainStopState;
import de.mrjulsen.crn.data.train.portable.BasicTrainDisplayData;
import de.mrjulsen.mcdragonlib.util.DLColor;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.EnumMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Injects category color and name into BasicTrainDisplayData.
 *
 * Uses a static WeakHashMap keyed by instance rather than injecting instance
 * fields, which avoids the Mixin validation error caused by combining instance
 * fields with static method injection in the same Mixin class.
 */
@Mixin(value = BasicTrainDisplayData.class, remap = false)
public abstract class MixinBasicTrainDisplayData implements IBasicTrainDisplayDataExt {

    // Static WeakHashMap: keys are BasicTrainDisplayData instances (weak refs so GC works normally)
    @Unique
    private static final WeakHashMap<BasicTrainDisplayData, Map<ETrainStopState, DLColor>> CATEGORY_COLORS
        = new WeakHashMap<>();
    @Unique
    private static final WeakHashMap<BasicTrainDisplayData, Map<ETrainStopState, String>> CATEGORY_NAMES
        = new WeakHashMap<>();
    @Unique
    private static final WeakHashMap<BasicTrainDisplayData, String> TRAIN_ENTITY_NAMES
        = new WeakHashMap<>();

    @Unique
    private static final String NBT_CATEGORY_COLORS = "crndisplaynext.CategoryColors";
    @Unique
    private static final String NBT_CATEGORY_NAMES  = "crndisplaynext.CategoryNames";

    // ── IBasicTrainDisplayDataExt ─────────────────────────────────────────

    @Override
    @Unique
    public DLColor crndisplaynext$getCategoryColor(ETrainStopState state) {
        Map<ETrainStopState, DLColor> map = CATEGORY_COLORS.get((BasicTrainDisplayData)(Object)this);
        if (map == null) return DLColor.TRANSPARENT;
        return map.getOrDefault(state, DLColor.TRANSPARENT);
    }

    @Override
    @Unique
    public String crndisplaynext$getCategoryName(ETrainStopState state) {
        Map<ETrainStopState, String> map = CATEGORY_NAMES.get((BasicTrainDisplayData)(Object)this);
        if (map == null) return "";
        return map.getOrDefault(state, "");
    }

    @Override
    @Unique
    public boolean crndisplaynext$hasCategoryColor(ETrainStopState state) {
        DLColor c = crndisplaynext$getCategoryColor(state);
        return c != null && !c.isTransparent();
    }

    @Override
    @Unique
    public String crndisplaynext$getTrainEntityName() {
        return TRAIN_ENTITY_NAMES.getOrDefault((BasicTrainDisplayData)(Object)this, "");
    }

    @Override
    @Unique
    public void crndisplaynext$setTrainEntityName(String name) {
        TRAIN_ENTITY_NAMES.put((BasicTrainDisplayData)(Object)this, name != null ? name : "");
    }

    @Override
    @Unique
    public void crndisplaynext$setCategoryData(ETrainStopState state, String name, DLColor color) {
        BasicTrainDisplayData self = (BasicTrainDisplayData)(Object)this;
        CATEGORY_COLORS.computeIfAbsent(self, k -> new EnumMap<>(ETrainStopState.class))
            .put(state, color != null ? color : DLColor.TRANSPARENT);
        CATEGORY_NAMES.computeIfAbsent(self, k -> new EnumMap<>(ETrainStopState.class))
            .put(state, name != null ? name : "");
    }

    // ── NBT: save category data alongside CRN's data ─────────────────────

    @Inject(method = "toNbt", at = @At("RETURN"), remap = false)
    private void onToNbt(CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag nbt = cir.getReturnValue();
        CompoundTag colors = new CompoundTag();
        CompoundTag names  = new CompoundTag();
        for (ETrainStopState state : ETrainStopState.values()) {
            String key = String.valueOf(state.getId());
            colors.putInt(key, crndisplaynext$getCategoryColor(state).getAsARGB());
            names.putString(key, crndisplaynext$getCategoryName(state));
        }
        nbt.put(NBT_CATEGORY_COLORS, colors);
        nbt.put(NBT_CATEGORY_NAMES,  names);
    }
}
