package com.hybrizat.crndisplaynext.mixin;

import com.hybrizat.crndisplaynext.CRNDisplayNextMod;
import com.hybrizat.crndisplaynext.api.IAdvancedDisplayBEExt;
import com.hybrizat.crndisplaynext.client.screen.GraphicsImageScreen;
import com.hybrizat.crndisplaynext.display.settings.GraphicsDisplaySettings;
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
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.List;

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

    @Unique private static final MutableComponent TEXT_LABEL =
        TextUtils.translate("gui.crndisplaynext.hide_technical_stops");
    @Unique private static final Component TEXT_TOOLTIP =
        TextUtils.translate("gui.crndisplaynext.hide_technical_stops.description");

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

        // reinit() calls setHeight() BEFORE buildGui() adds any lines, so the window
        // height doesn't account for our extra line. Add one line-height manually.
        // CreateButton.HEIGHT = height of one settings row (same as all other checkboxes)
        setHeight(height() + CreateButton.HEIGHT + 2);
    }

    // ── Image button ───────────────────────────────────────────────────

    @Unique private static final String GFX_LINE = "crndisplaynext.gfx_button";

    @Inject(method = "reinit", at = @At("TAIL"), remap = false)
    private void addGfxButton(CallbackInfo ci) {
        try {
            Field sf = AdvancedDisplaySettingsWindow.class.getDeclaredField("settings");
            sf.setAccessible(true);
            if (!(sf.get(this) instanceof GraphicsDisplaySettings)) return;
        } catch (Exception e) { return; }

        DLPanel line = advancedSettingsContainer.addLine(GFX_LINE);
        var btn = new de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLButton(0, 0, 200, CreateButton.HEIGHT);
        btn.text.set(Component.literal("Configure Image..."));
        btn.addEventListener(de.mrjulsen.mcdragonlib.client.gui.events.DLGuiStandardEvents.ClickEvent.class,
            (b, evt) -> {
                BlockPos pos = ((BlockEntity)(Object)blockEntity).getBlockPos();
                Minecraft.getInstance().setScreen(new GraphicsImageScreen(pos, null));
                return false;
            });
        line.addComponent(btn);
        setHeight(height() + CreateButton.HEIGHT + 2);
        setHeight(height() + CreateButton.HEIGHT + 2);
    }
}
