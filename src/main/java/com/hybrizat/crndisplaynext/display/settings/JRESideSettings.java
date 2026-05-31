package com.hybrizat.crndisplaynext.display.settings;

import com.hybrizat.crndisplaynext.CRNDisplayNextMod;
import com.hybrizat.crndisplaynext.display.EJRESideMode;
import de.mrjulsen.crn.block.display.properties.BasicDisplaySettings;
import de.mrjulsen.crn.block.display.properties.IDisplaySettings;
import de.mrjulsen.crn.block.display.properties.components.IShowDoNotBoardText;
import de.mrjulsen.crn.block.display.properties.components.IShowLineColorSetting;
import de.mrjulsen.crn.client.gui.widgets.create.CreateItemPicker;
import de.mrjulsen.crn.client.gui.widgets.modular.GuiBuilderContext;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLCycleButton;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLPanel;
import de.mrjulsen.mcdragonlib.client.gui.widgets.layout.FlowLayout;
import de.mrjulsen.mcdragonlib.util.DLColor;
import de.mrjulsen.mcdragonlib.util.TextUtils;
import net.minecraft.nbt.CompoundTag;
import java.util.Optional;

public class JRESideSettings extends BasicDisplaySettings
    implements IShowLineColorSetting, IShowDoNotBoardText {

    private static final String NBT_SIDE_MODE   = "JRESide_Mode";
    private static final String NBT_SHOW_COLOR  = "JRESide_ShowColor";
    private static final String NBT_DO_NOT_BOARD = "JRESide_DoNotBoard";

    private EJRESideMode sideMode   = EJRESideMode.NEXT_STOP;
    private boolean      showColor  = true;
    private boolean      doNotBoard = true;

    public JRESideSettings() {
        this.fontColor = DLColor.WHITE;
        this.backColor = DLColor.fromInt(0xFF000000);
    }

    public EJRESideMode getSideMode()            { return sideMode; }
    public void setSideMode(EJRESideMode m)       { this.sideMode = m; }
    @Override public boolean showLineColor()      { return showColor; }
    @Override public void setShowLineColor(boolean b) { this.showColor = b; }
    @Override public boolean showDoNotBoardText() { return doNotBoard; }
    @Override public void setShowDoNotBoardText(boolean b) { this.doNotBoard = b; }

    @Override
    public void buildGui(GuiBuilderContext context) {
        this.buildColorGui(context);
        this.buildShowLineColorGui(context);
        this.buildShowDoNotBoardTextGui(context);
        buildSideModeGui(context);
    }

    private void buildSideModeGui(GuiBuilderContext context) {
        DLPanel line = context.container().addLine("crndisplaynext.jre_side_mode");
        CreateItemPicker<EJRESideMode> picker = new CreateItemPicker<>(0, 0, 0);
        picker.renderArrow.set(true);
        picker.title.set(TextUtils.translate("gui." + CRNDisplayNextMod.MOD_ID + ".jre_side_mode"));
        picker.hint.set(TextUtils.translate("gui." + CRNDisplayNextMod.MOD_ID + ".jre_side_mode.description"));
        picker.formatter.set(item -> item == null ? TextUtils.empty() : item.getValueTranslation());
        picker.items.addAll(java.util.List.of(EJRESideMode.values()));
        picker.layoutContraint.set(FlowLayout.FlowConstraint.FILL);
        picker.selectedItem.set(Optional.ofNullable(sideMode));
        picker.addEventListener(DLCycleButton.SelectedItemChanged.class, (s, e) -> {
            picker.selectedItem.get().ifPresent(this::setSideMode);
            return false;
        });
        line.addComponent(picker);
    }

    @Override
    public void onChangeSettings(IDisplaySettings old) {
        this.copyColorSetting(old);
        this.copyShowLineColorSetting(old);
        this.copyShowDoNotBoardTextSetting(old);
        if (old instanceof JRESideSettings s) setSideMode(s.getSideMode());
    }

    @Override
    public void serializeNbt(CompoundTag nbt) {
        super.serializeNbt(nbt);
        nbt.putByte(NBT_SIDE_MODE,    sideMode.getId());
        nbt.putBoolean(NBT_SHOW_COLOR,  showColor);
        nbt.putBoolean(NBT_DO_NOT_BOARD, doNotBoard);
    }

    @Override
    public void deserializeNbt(CompoundTag nbt) {
        super.deserializeNbt(nbt);
        if (nbt.contains(NBT_SIDE_MODE))    sideMode   = EJRESideMode.getById(nbt.getByte(NBT_SIDE_MODE));
        if (nbt.contains(NBT_SHOW_COLOR))   showColor  = nbt.getBoolean(NBT_SHOW_COLOR);
        if (nbt.contains(NBT_DO_NOT_BOARD)) doNotBoard = nbt.getBoolean(NBT_DO_NOT_BOARD);
    }
}
