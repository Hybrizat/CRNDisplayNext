package com.hybrizat.crndisplaynext.display.settings;

import com.hybrizat.crndisplaynext.CRNDisplayNextMod;
import com.hybrizat.crndisplaynext.display.EJRETrainTextMode;
import de.mrjulsen.crn.block.display.properties.BasicDisplaySettings;
import de.mrjulsen.crn.block.display.properties.IDisplaySettings;
import de.mrjulsen.crn.block.display.properties.components.IShowLineColorSetting;
import de.mrjulsen.crn.block.display.properties.components.IShowTrainMultipleTimes;
import de.mrjulsen.crn.block.display.properties.components.ITimeDisplaySetting;
import de.mrjulsen.crn.block.properties.ETimeDisplay;
import de.mrjulsen.crn.client.gui.widgets.create.CreateButton;
import de.mrjulsen.crn.client.gui.widgets.create.CreateItemPicker;
import de.mrjulsen.crn.client.gui.widgets.modular.GuiBuilderContext;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLCycleButton;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLPanel;
import de.mrjulsen.mcdragonlib.client.gui.widgets.layout.FlowLayout;
import de.mrjulsen.mcdragonlib.util.DLColor;
import de.mrjulsen.mcdragonlib.util.TextUtils;
import net.minecraft.nbt.CompoundTag;

import java.util.Optional;

public class JREPlatformLEDSettings extends BasicDisplaySettings
    implements IShowTrainMultipleTimes, ITimeDisplaySetting, IShowLineColorSetting {

    private static final DLColor DEFAULT_LED_COLOR  = DLColor.fromInt(0xFFFF8C00);
    private static final DLColor DEFAULT_BACK_COLOR = DLColor.fromInt(0xFF111111);

    private static final String NBT_TIME_DISPLAY    = "TimeDisplay";
    private static final String NBT_TRAIN_TEXT_MODE = "JRETrainTextMode";

    private boolean          showTrainMultipleTimes = false;
    private ETimeDisplay     timeDisplay            = ETimeDisplay.getById(0);
    private EJRETrainTextMode trainTextMode         = EJRETrainTextMode.TRAIN_NAME;
    private boolean          showLineColor          = false;

    public JREPlatformLEDSettings() {
        this.fontColor = DEFAULT_LED_COLOR;
        this.backColor = DEFAULT_BACK_COLOR;
    }

    // ── IShowTrainMultipleTimes ────────────────────────────────────────────
    @Override public boolean showTrainMultipleTimes()           { return showTrainMultipleTimes; }
    @Override public void setShowTrainMultipleTimes(boolean b)  { this.showTrainMultipleTimes = b; }

    // ── ITimeDisplaySetting ───────────────────────────────────────────────
    @Override public ETimeDisplay getTimeDisplay()              { return timeDisplay; }
    @Override public void setTimeDisplay(ETimeDisplay t)        { this.timeDisplay = t; }

    // ── IShowLineColorSetting ─────────────────────────────────────────────
    // Controls whether the color block (line or category) is shown behind the train text
    @Override public boolean showLineColor()                    { return showLineColor; }
    @Override public void setShowLineColor(boolean b)           { this.showLineColor = b; }

    // ── JRE-specific: train name vs category name ─────────────────────────
    public EJRETrainTextMode getTrainTextMode()                 { return trainTextMode; }
    public void setTrainTextMode(EJRETrainTextMode m)           { this.trainTextMode = m; }

    // ── GUI ───────────────────────────────────────────────────────────────
    @Override
    public void buildGui(GuiBuilderContext context) {
        this.buildColorGui(context);
        this.buildShowTrainMultipleTimesGui(context);
        this.buildTimeDisplayGui(context);
        this.buildShowLineColorGui(context);
        buildTrainTextModeGui(context);
    }

    /**
     * Dropdown for train name vs category name — mirrors CRN's buildTrainTextGui() pattern.
     * Translation keys:
     *   enum.crndisplaynext.jre_train_text_mode.train_name
     *   enum.crndisplaynext.jre_train_text_mode.category_name
     */
    private void buildTrainTextModeGui(GuiBuilderContext context) {
        DLPanel line = context.container().addLine("crndisplaynext.jre_train_text_mode");

        CreateItemPicker<EJRETrainTextMode> picker = new CreateItemPicker<>(0, 0, 0);
        picker.renderArrow.set(true);
        picker.title.set(TextUtils.translate(
            "gui." + CRNDisplayNextMod.MOD_ID + ".jre_train_text_mode"));
        picker.hint.set(TextUtils.translate(
            "gui." + CRNDisplayNextMod.MOD_ID + ".jre_train_text_mode.description"));
        picker.formatter.set(item ->
            item == null ? TextUtils.empty() : item.getValueTranslation());
        picker.items.addAll(java.util.List.of(EJRETrainTextMode.values()));
        picker.layoutContraint.set(FlowLayout.FlowConstraint.FILL);
        picker.selectedItem.set(Optional.ofNullable(trainTextMode));
        picker.addEventListener(DLCycleButton.SelectedItemChanged.class, (s, e) -> {
            picker.selectedItem.get().ifPresent(this::setTrainTextMode);
            return false;
        });
        line.addComponent(picker);
    }

    // ── Settings transfer ─────────────────────────────────────────────────
    @Override
    public void onChangeSettings(IDisplaySettings oldSettings) {
        this.copyColorSetting(oldSettings);
        this.copyShowTrainMultipleTimesSetting(oldSettings);
        this.copyShowLineColorSetting(oldSettings);
        if (oldSettings instanceof ITimeDisplaySetting s) this.setTimeDisplay(s.getTimeDisplay());
        if (oldSettings instanceof JREPlatformLEDSettings s) this.setTrainTextMode(s.getTrainTextMode());
    }

    // ── NBT ───────────────────────────────────────────────────────────────
    @Override
    public void serializeNbt(CompoundTag nbt) {
        super.serializeNbt(nbt);
        nbt.putBoolean(NBT_SHOW_TRAIN_MULTIPLE_TIMES, showTrainMultipleTimes);
        nbt.putByte(NBT_TIME_DISPLAY,    (byte) timeDisplay.getId());
        nbt.putByte(NBT_TRAIN_TEXT_MODE, (byte) trainTextMode.getId());
        nbt.putBoolean(NBT_SHOW_LINE_COLOR, showLineColor);
    }

    @Override
    public void deserializeNbt(CompoundTag nbt) {
        super.deserializeNbt(nbt);
        if (nbt.contains(NBT_SHOW_TRAIN_MULTIPLE_TIMES))
            this.showTrainMultipleTimes = nbt.getBoolean(NBT_SHOW_TRAIN_MULTIPLE_TIMES);
        if (nbt.contains(NBT_TIME_DISPLAY))
            this.timeDisplay = ETimeDisplay.getById(nbt.getByte(NBT_TIME_DISPLAY));
        if (nbt.contains(NBT_TRAIN_TEXT_MODE))
            this.trainTextMode = EJRETrainTextMode.getById(nbt.getByte(NBT_TRAIN_TEXT_MODE));
        if (nbt.contains(NBT_SHOW_LINE_COLOR))
            this.showLineColor = nbt.getBoolean(NBT_SHOW_LINE_COLOR);
    }
}
