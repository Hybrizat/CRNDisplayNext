package com.hybrizat.crndisplaynext.mixin;

import com.hybrizat.crndisplaynext.CRNDisplayNextMod;
import com.hybrizat.crndisplaynext.api.IAdvancedDisplayBEExt;
import com.hybrizat.crndisplaynext.network.HideTechnicalStopsPacket;
import de.mrjulsen.crn.block.blockentity.AdvancedDisplayBlockEntity;
import de.mrjulsen.crn.client.gui.widgets.ModularWidgetContainer;
import de.mrjulsen.crn.client.gui.widgets.create.CreateButton;
import de.mrjulsen.crn.client.gui.windows.AdvancedDisplaySettingsWindow;
import de.mrjulsen.mcdragonlib.client.gui.widgets.base.DLWindow;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLCheckBox;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLPanel;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLToggleButton;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLTooltip;
import de.mrjulsen.mcdragonlib.client.gui.widgets.layout.FlowLayout;
import de.mrjulsen.mcdragonlib.util.TextUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Adds the CRNDisplayNext "hide technical stops" checkbox to CRN's advanced
 * display settings window.
 *
 * <p>The image-URL row for the custom image display is no longer injected here:
 * it is built by {@code GraphicsDisplaySettings#buildGui} into CRN's own
 * advanced-settings container, and its picker is a DragonLib modal window rather
 * than a replacement vanilla screen. The previous injection opened a vanilla
 * screen on top of the settings window and then reopened the settings window from
 * that screen's close callback, which left a screen whose "previous screen" was
 * the image screen itself — so the settings window could not be closed again.</p>
 */
@Mixin(value = AdvancedDisplaySettingsWindow.class, remap = false)
public abstract class MixinAdvancedDisplaySettingsWindow extends DLWindow {

    // DLWindow requires a constructor — Mixin abstract classes don't actually run this
    protected MixinAdvancedDisplaySettingsWindow() {
        super(null);
    }

    @Shadow private ModularWidgetContainer advancedSettingsContainer;
    @Shadow private AdvancedDisplayBlockEntity blockEntity;
    @Shadow private static boolean advancedSettingsExpanded;

    @Unique private static final String GUI_LINE_ID = "crndisplaynext.hide_technical_stops";

    /**
     * Height our extra settings row occupies, used to keep the window height correct.
     *
     * <p>{@code reinit()} recomputes the height from the container sizes BEFORE it
     * calls {@code settings.buildGui(...)}, so the row added just above is not part
     * of that sum and has to be accounted for here. The adjustment is idempotent:
     * {@code reinit()} runs again on every expand/collapse, variant switch, paste and
     * reset, and adding the row height unconditionally each time made the window grow
     * without bound (the confirm and help buttons drifted away from the background).</p>
     */
    @Unique private static final int CRNDISPLAYNEXT_ROW_HEIGHT = CreateButton.HEIGHT + 2;

    /** Cleared at the start of every {@code reinit()} so the row height is added once. */
    @Unique private boolean crndisplaynext$rowHeightAdded = false;

    @Unique private static final MutableComponent TEXT_LABEL =
        TextUtils.translate("gui.crndisplaynext.hide_technical_stops");
    @Unique private static final Component TEXT_TOOLTIP =
        TextUtils.translate("gui.crndisplaynext.hide_technical_stops.description");

    /**
     * reinit() recomputes the window height from the container sizes BEFORE calling
     * {@code settings.buildGui(...)}, so the row added at TAIL is not part of that sum.
     * The flag lets the tail injection add it exactly once per reinit() instead of on
     * every pass (reinit() runs again on expand/collapse, variant switch, paste and
     * reset, and growing unconditionally let the window height run away).
     */
    @Inject(method = "reinit", at = @At("HEAD"), remap = false)
    private void crndisplaynext$beginReinit(CallbackInfo ci) {
        crndisplaynext$rowHeightAdded = false;
    }

    @Inject(method = "reinit", at = @At("TAIL"), remap = false)
    private void onReinitTail(CallbackInfo ci) {
        if (!advancedSettingsExpanded) return;

        // Cast to Object first to avoid VirtualBlockEntity class resolution
        Object beAsObject = blockEntity;
        if (!(beAsObject instanceof IAdvancedDisplayBEExt ext)) {
            CRNDisplayNextMod.LOGGER.warn("[CRNExt] blockEntity does not implement IAdvancedDisplayBEExt");
            return;
        }

        try {
            addHideTechnicalStopsCheckbox(ext);
        } catch (Exception e) {
            CRNDisplayNextMod.LOGGER.error("[CRNExt] Failed to add checkbox", e);
        }
    }

    @Unique
    private void addHideTechnicalStopsCheckbox(IAdvancedDisplayBEExt ext) {
        DLPanel line = advancedSettingsContainer.addLine(GUI_LINE_ID);

        DLCheckBox checkBox = new DLCheckBox(0, 0, 0, CreateButton.HEIGHT);
        checkBox.text.set(TEXT_LABEL);
        checkBox.checked.set(ext.crndisplaynext$getHideTechnicalStops());
        checkBox.layoutContraint.set(FlowLayout.FlowConstraint.FILL);
        checkBox.tooltip.set(new DLTooltip(List.of(TEXT_TOOLTIP), 200));

        checkBox.addEventListener(DLToggleButton.CheckedChangedEvent.class, (src, e) -> {
            ext.crndisplaynext$setHideTechnicalStops(e.checked());
            net.minecraft.core.BlockPos bePos =
                ((net.minecraft.world.level.block.entity.BlockEntity)(Object) blockEntity).getBlockPos();
            HideTechnicalStopsPacket.sendToServer(bePos, e.checked());
            return false;
        });

        line.addComponent(checkBox);

        // Account for the row exactly once per reinit(); see crndisplaynext$beginReinit.
        if (!crndisplaynext$rowHeightAdded) {
            crndisplaynext$rowHeightAdded = true;
            setHeight(height() + CRNDISPLAYNEXT_ROW_HEIGHT);
        }
    }
}
