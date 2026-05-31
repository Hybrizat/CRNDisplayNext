package com.hybrizat.crndisplaynext.display.settings;

import com.hybrizat.crndisplaynext.CRNDisplayNextMod;
import com.hybrizat.crndisplaynext.display.EJREFrontBContent;
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

/**
 * Settings for the JRE Front Destination display.
 *
 * Layout (E233 style):
 *   [A: 種別/Category]  [B: 行先 or 線路]
 *
 * A always shows the category name with category color background.
 * B content is switchable between destination and line name.
 */
public class JREFrontSettings extends BasicDisplaySettings
    implements IShowLineColorSetting, IShowDoNotBoardText {

    private static final String NBT_B_CONTENT   = "JREFront_BContent";
    private static final String NBT_SHOW_COLOR  = "JREFront_ShowColor";
    private static final String NBT_DO_NOT_BOARD = "JREFront_DoNotBoard";

    private EJREFrontBContent bContent    = EJREFrontBContent.DESTINATION;
    private boolean           showColor   = true;
    private boolean           doNotBoard  = true;

    public JREFrontSettings() {
        this.fontColor = DLColor.WHITE;
        this.backColor = DLColor.fromInt(0xFF000000);
    }

    public EJREFrontBContent getBContent()          { return bContent; }
    public void setBContent(EJREFrontBContent b)    { this.bContent = b; }

    @Override public boolean showLineColor()         { return showColor; }
    @Override public void setShowLineColor(boolean b){ this.showColor = b; }

    @Override public boolean showDoNotBoardText()    { return doNotBoard; }
    @Override public void setShowDoNotBoardText(boolean b) { this.doNotBoard = b; }

    @Override
    public void buildGui(GuiBuilderContext context) {
        this.buildColorGui(context);
        this.buildShowLineColorGui(context);
        this.buildShowDoNotBoardTextGui(context);
        buildBContentGui(context);
    }

    private void buildBContentGui(GuiBuilderContext context) {
        DLPanel line = context.container().addLine("crndisplaynext.jre_front_b_content");
        CreateItemPicker<EJREFrontBContent> picker = new CreateItemPicker<>(0, 0, 0);
        picker.renderArrow.set(true);
        picker.title.set(TextUtils.translate("gui." + CRNDisplayNextMod.MOD_ID + ".jre_front_b_content"));
        picker.hint.set(TextUtils.translate("gui." + CRNDisplayNextMod.MOD_ID + ".jre_front_b_content.description"));
        picker.formatter.set(item -> item == null ? TextUtils.empty() : item.getValueTranslation());
        picker.items.addAll(java.util.List.of(EJREFrontBContent.values()));
        picker.layoutContraint.set(FlowLayout.FlowConstraint.FILL);
        picker.selectedItem.set(Optional.ofNullable(bContent));
        picker.addEventListener(DLCycleButton.SelectedItemChanged.class, (s, e) -> {
            picker.selectedItem.get().ifPresent(this::setBContent);
            return false;
        });
        line.addComponent(picker);
    }

    @Override
    public void onChangeSettings(IDisplaySettings old) {
        this.copyColorSetting(old);
        this.copyShowLineColorSetting(old);
        this.copyShowDoNotBoardTextSetting(old);
        if (old instanceof JREFrontSettings s) setBContent(s.getBContent());
    }

    @Override
    public void serializeNbt(CompoundTag nbt) {
        super.serializeNbt(nbt);
        nbt.putByte(NBT_B_CONTENT,   bContent.getId());
        nbt.putBoolean(NBT_SHOW_COLOR, showColor);
        nbt.putBoolean(NBT_DO_NOT_BOARD, doNotBoard);
    }

    @Override
    public void deserializeNbt(CompoundTag nbt) {
        super.deserializeNbt(nbt);
        if (nbt.contains(NBT_B_CONTENT))    bContent   = EJREFrontBContent.getById(nbt.getByte(NBT_B_CONTENT));
        if (nbt.contains(NBT_SHOW_COLOR))   showColor  = nbt.getBoolean(NBT_SHOW_COLOR);
        if (nbt.contains(NBT_DO_NOT_BOARD)) doNotBoard = nbt.getBoolean(NBT_DO_NOT_BOARD);
    }
}
