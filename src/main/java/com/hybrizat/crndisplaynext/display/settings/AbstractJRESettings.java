package com.hybrizat.crndisplaynext.display.settings;

import com.hybrizat.crndisplaynext.CRNDisplayNextMod;
import com.hybrizat.crndisplaynext.display.EJRETrainTextMode;
import de.mrjulsen.crn.block.display.properties.BasicDisplaySettings;
import de.mrjulsen.crn.block.display.properties.IDisplaySettings;
import de.mrjulsen.crn.block.display.properties.components.IShowTrainMultipleTimes;
import de.mrjulsen.crn.block.display.properties.components.ITimeDisplaySetting;
import de.mrjulsen.crn.block.properties.ETimeDisplay;
import de.mrjulsen.crn.client.gui.widgets.create.CreateButton;
import de.mrjulsen.crn.client.gui.widgets.create.CreateItemPicker;
import de.mrjulsen.crn.client.gui.widgets.create.CreateScrollNumberInput;
import de.mrjulsen.crn.client.gui.widgets.modular.GuiBuilderContext;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLCheckBox;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLCycleButton;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLNumberPicker;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLPanel;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLToggleButton;
import de.mrjulsen.mcdragonlib.client.gui.widgets.layout.FlowLayout;
import de.mrjulsen.mcdragonlib.util.DLColor;
import de.mrjulsen.mcdragonlib.util.TextUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.util.Optional;

/**
 * Shared base settings for all JRE platform display variants.
 * Subclassed by JREStationSettings (station concourse) and JREPlatformSettings (platform edge).
 */
public abstract class AbstractJRESettings extends BasicDisplaySettings
    implements IShowTrainMultipleTimes, ITimeDisplaySetting {

    // NBT keys
    protected static final String NBT_TIME_DISPLAY       = "JRE_TimeDisplay";
    protected static final String NBT_TRAIN_TEXT_MODE    = "JRE_TrainTextMode";
    protected static final String NBT_SHOW_COLOR         = "JRE_ShowColor";
    protected static final String NBT_TRAIN_TEXT_WIDTH   = "JRE_TrainTextWidth";
    protected static final String NBT_DEST_WIDTH         = "JRE_DestWidth";
    protected static final String NBT_TIME_WIDTH         = "JRE_TimeWidth";

    // Width sentinel: -1 = auto
    public static final byte WIDTH_AUTO = -1;
    public static final byte TIME_WIDTH_DEFAULT = 14;
    public static final byte TRAIN_TEXT_WIDTH_DEFAULT = WIDTH_AUTO;
    public static final byte DEST_WIDTH_DEFAULT = WIDTH_AUTO;

    protected boolean          showTrainMultipleTimes = false;
    protected ETimeDisplay     timeDisplay            = ETimeDisplay.getById(0);
    protected EJRETrainTextMode trainTextMode         = EJRETrainTextMode.CATEGORY_NAME;
    protected boolean          showColor              = false;
    // Width in display-pixels; -1 = auto (use rendered text width)
    protected byte             timeWidth              = TIME_WIDTH_DEFAULT;
    protected byte             trainTextWidth         = TRAIN_TEXT_WIDTH_DEFAULT;
    protected byte             destWidth              = DEST_WIDTH_DEFAULT;

    protected AbstractJRESettings(DLColor defaultFont, DLColor defaultBack) {
        this.fontColor = defaultFont;
        this.backColor = defaultBack;
    }

    // ── IShowTrainMultipleTimes ───────────────────────────────────────────
    @Override public boolean showTrainMultipleTimes()           { return showTrainMultipleTimes; }
    @Override public void setShowTrainMultipleTimes(boolean b)  { showTrainMultipleTimes = b; }

    // ── ITimeDisplaySetting ──────────────────────────────────────────────
    @Override public ETimeDisplay getTimeDisplay()              { return timeDisplay; }
    @Override public void setTimeDisplay(ETimeDisplay t)        { timeDisplay = t; }

    // ── JRE-specific ─────────────────────────────────────────────────────
    public EJRETrainTextMode getTrainTextMode()                 { return trainTextMode; }
    public void setTrainTextMode(EJRETrainTextMode m)           { trainTextMode = m; }
    public boolean showColor()                                  { return showColor; }
    public void setShowColor(boolean b)                         { showColor = b; }
    public byte getTimeWidth()                                  { return timeWidth; }
    public void setTimeWidth(byte b)                            { timeWidth = b; }
    public byte getTrainTextWidth()                             { return trainTextWidth; }
    public void setTrainTextWidth(byte b)                       { trainTextWidth = b; }
    public byte getDestWidth()                                  { return destWidth; }
    public void setDestWidth(byte b)                            { destWidth = b; }

    public boolean isAutoTimeWidth()        { return timeWidth == WIDTH_AUTO; }
    public boolean isAutoTrainTextWidth()   { return trainTextWidth == WIDTH_AUTO; }
    public boolean isAutoDestWidth()        { return destWidth == WIDTH_AUTO; }

    // ── GUI helpers ───────────────────────────────────────────────────────
    protected void buildSharedGui(GuiBuilderContext context) {
        this.buildColorGui(context);
        this.buildShowTrainMultipleTimesGui(context);
        this.buildTimeDisplayGui(context);
        buildTrainTextModeGui(context);
        buildShowColorGui(context);
        buildWidthGui(context);
    }

    private void buildTrainTextModeGui(GuiBuilderContext context) {
        DLPanel line = context.container().addLine("crndisplaynext.jre_train_text_mode");
        CreateItemPicker<EJRETrainTextMode> picker = new CreateItemPicker<>(0, 0, 0);
        picker.renderArrow.set(true);
        picker.title.set(TextUtils.translate("gui." + CRNDisplayNextMod.MOD_ID + ".jre_train_text_mode"));
        picker.hint.set(TextUtils.translate("gui." + CRNDisplayNextMod.MOD_ID + ".jre_train_text_mode.description"));
        picker.formatter.set(item -> item == null ? TextUtils.empty() : item.getValueTranslation());
        picker.items.addAll(java.util.List.of(EJRETrainTextMode.values()));
        picker.layoutContraint.set(FlowLayout.FlowConstraint.FILL);
        picker.selectedItem.set(Optional.ofNullable(trainTextMode));
        picker.addEventListener(DLCycleButton.SelectedItemChanged.class, (s, e) -> {
            picker.selectedItem.get().ifPresent(this::setTrainTextMode);
            return false;
        });
        line.addComponent(picker);
    }

    private void buildShowColorGui(GuiBuilderContext context) {
        DLPanel line = context.container().addLine("crndisplaynext.jre_show_color");
        DLCheckBox box = new DLCheckBox(0, 0, 0, CreateButton.HEIGHT);
        box.text.set((Component) TextUtils.translate("gui." + CRNDisplayNextMod.MOD_ID + ".jre_show_color"));
        box.checked.set(showColor);
        box.layoutContraint.set(FlowLayout.FlowConstraint.FILL);
        box.addEventListener(DLToggleButton.CheckedChangedEvent.class, (s, e) -> {
            setShowColor(e.checked());
            return false;
        });
        line.addComponent(box);
    }

    private void buildWidthGui(GuiBuilderContext context) {
        DLPanel line = context.container().addLine("crndisplaynext.jre_widths");

        // Train text width
        CreateScrollNumberInput trainW = new CreateScrollNumberInput(0, 0, 32);
        trainW.title.set(TextUtils.translate("gui." + CRNDisplayNextMod.MOD_ID + ".jre_train_text_width"));
        trainW.hint.set(TextUtils.translate("gui." + CRNDisplayNextMod.MOD_ID + ".jre_train_text_width.description"));
        trainW.min.set(-1D);
        trainW.max.set(64D);
        trainW.shiftStep.set(4D);
        trainW.value.set((double) trainTextWidth);
        trainW.addEventListener(DLNumberPicker.ValueChangedEvent.class, (s, e) -> {
            setTrainTextWidth((byte) e.value());
            return false;
        });
        line.addComponent(trainW);

        // Destination width
        CreateScrollNumberInput destW = new CreateScrollNumberInput(0, 0, 32);
        destW.title.set(TextUtils.translate("gui." + CRNDisplayNextMod.MOD_ID + ".jre_dest_width"));
        destW.hint.set(TextUtils.translate("gui." + CRNDisplayNextMod.MOD_ID + ".jre_dest_width.description"));
        destW.min.set(-1D);
        destW.max.set(128D);
        destW.shiftStep.set(8D);
        destW.value.set((double) destWidth);
        destW.addEventListener(DLNumberPicker.ValueChangedEvent.class, (s, e) -> {
            setDestWidth((byte) e.value());
            return false;
        });
        line.addComponent(destW);
    }

    // ── Settings transfer ─────────────────────────────────────────────────
    protected void copySharedSettings(IDisplaySettings old) {
        this.copyColorSetting(old);
        this.copyShowTrainMultipleTimesSetting(old);
        if (old instanceof ITimeDisplaySetting s) setTimeDisplay(s.getTimeDisplay());
        if (old instanceof AbstractJRESettings  s) {
            setTrainTextMode(s.getTrainTextMode());
            setShowColor(s.showColor());
            setTimeWidth(s.getTimeWidth());
            setTrainTextWidth(s.getTrainTextWidth());
            setDestWidth(s.getDestWidth());
        }
    }

    // ── NBT ───────────────────────────────────────────────────────────────
    @Override
    public void serializeNbt(CompoundTag nbt) {
        super.serializeNbt(nbt);
        nbt.putBoolean(NBT_SHOW_TRAIN_MULTIPLE_TIMES, showTrainMultipleTimes);
        nbt.putByte(NBT_TIME_DISPLAY,    (byte) timeDisplay.getId());
        nbt.putByte(NBT_TRAIN_TEXT_MODE, (byte) trainTextMode.getId());
        nbt.putBoolean(NBT_SHOW_COLOR,   showColor);
        nbt.putByte(NBT_TIME_WIDTH,      timeWidth);
        nbt.putByte(NBT_TRAIN_TEXT_WIDTH, trainTextWidth);
        nbt.putByte(NBT_DEST_WIDTH,      destWidth);
    }

    @Override
    public void deserializeNbt(CompoundTag nbt) {
        super.deserializeNbt(nbt);
        if (nbt.contains(NBT_SHOW_TRAIN_MULTIPLE_TIMES)) showTrainMultipleTimes = nbt.getBoolean(NBT_SHOW_TRAIN_MULTIPLE_TIMES);
        if (nbt.contains(NBT_TIME_DISPLAY))              timeDisplay   = ETimeDisplay.getById(nbt.getByte(NBT_TIME_DISPLAY));
        if (nbt.contains(NBT_TRAIN_TEXT_MODE))           trainTextMode = EJRETrainTextMode.getById(nbt.getByte(NBT_TRAIN_TEXT_MODE));
        if (nbt.contains(NBT_SHOW_COLOR))                showColor     = nbt.getBoolean(NBT_SHOW_COLOR);
        if (nbt.contains(NBT_TIME_WIDTH))                timeWidth     = nbt.getByte(NBT_TIME_WIDTH);
        if (nbt.contains(NBT_TRAIN_TEXT_WIDTH))          trainTextWidth = nbt.getByte(NBT_TRAIN_TEXT_WIDTH);
        if (nbt.contains(NBT_DEST_WIDTH))                destWidth     = nbt.getByte(NBT_DEST_WIDTH);
    }
}
