package com.hybrizat.crndisplaynext.display.settings;

import com.hybrizat.crndisplaynext.CRNDisplayNextMod;
import de.mrjulsen.crn.block.display.properties.BasicDisplaySettings;
import de.mrjulsen.crn.block.display.properties.IDisplaySettings;
import de.mrjulsen.crn.block.display.properties.components.*;
import de.mrjulsen.crn.block.properties.ETimeDisplay;
import de.mrjulsen.crn.client.gui.widgets.create.CreateButton;
import de.mrjulsen.crn.client.gui.widgets.modular.GuiBuilderContext;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLCheckBox;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLPanel;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLToggleButton;
import de.mrjulsen.mcdragonlib.client.gui.widgets.layout.FlowLayout;
import de.mrjulsen.mcdragonlib.util.DLColor;
import de.mrjulsen.mcdragonlib.util.TextUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * Settings for the JRE Passenger VIS.
 *
 */
public class JREVISSettings extends BasicDisplaySettings implements
        ITimeDisplaySetting,
        IShowLineColorSetting,
        IShowDoNotBoardText,
        IShowNextConnections,
        IShowTrainStatsSetting,
        ICarriageIndexSetting {

    protected ETimeDisplay timeDisplay        = ETimeDisplay.ABS;
    protected boolean      showLineColor      = true;
    protected boolean      doNotBoard         = true;
    protected boolean      showConnections    = true;
    protected boolean      showStats          = true;
    protected byte         carriageIndex      = 0;
    protected boolean      overwriteCarriage  = false;
    protected boolean      circularMode       = false;
    protected int          pageIntervalSecs   = 4;
    protected byte         prefixMode         = 0;

    public JREVISSettings() {
        this.fontColor = DLColor.WHITE;
        this.backColor = DLColor.fromInt(0xFF1a1a2e);
    }

    @Override public ETimeDisplay getTimeDisplay()             { return timeDisplay; }
    @Override public void setTimeDisplay(ETimeDisplay t)       { this.timeDisplay = t; }
    @Override public boolean showLineColor()                   { return showLineColor; }
    @Override public void setShowLineColor(boolean b)          { this.showLineColor = b; }
    @Override public boolean showDoNotBoardText()              { return doNotBoard; }
    @Override public void setShowDoNotBoardText(boolean b)     { this.doNotBoard = b; }
    @Override public boolean showConnections()                 { return showConnections; }
    @Override public void setShowConnection(boolean b)         { this.showConnections = b; }
    @Override public boolean showStats()                       { return showStats; }
    @Override public void setShowStats(boolean b)              { this.showStats = b; }
    @Override public byte getCarriageIndex()                   { return carriageIndex; }
    @Override public void setCarriageIndex(byte b)             { this.carriageIndex = b; }
    @Override public boolean shouldOverwriteCarriageIndex()    { return overwriteCarriage; }
    public boolean circularMode() { return circularMode; }
    public void setCircularMode(boolean b) { this.circularMode = b; }
    public int getPageIntervalSecs() { return pageIntervalSecs; }
    public void setPageIntervalSecs(int s) { this.pageIntervalSecs = Math.max(1, s); }
    public byte getPrefixMode() { return prefixMode; }
    public void setPrefixMode(byte b) { this.prefixMode = b; }
    @Override public void setOverwriteCarriageIndex(boolean b) { this.overwriteCarriage = b; }


    @Override
    public void buildGui(GuiBuilderContext context) {
        this.buildColorGui(context);
        this.buildTimeDisplayGui(context);
        this.buildRibbonColorGui(context);
        this.buildShowDoNotBoardTextGui(context);
        this.buildShowConnectionGui(context);
        this.buildShowStatsGui(context);
        this.buildCarriageIndexGui(context);
    }

    /**
     * Which colour the upper band is painted with. {@link #showLineColor()} is the
     * standard "use the line colour" switch, but its generic label says nothing about
     * what it actually changes here, and it used to change nothing at all in the VIS —
     * the band was always painted with the 種別 colour. So this row carries the
     * setting with a label that names the band.
     */
    private void buildRibbonColorGui(GuiBuilderContext context) {
        DLPanel line = context.container().addLine("crndisplaynext.jre_vis_ribbon_color");
        DLCheckBox box = new DLCheckBox(0, 0, 0, CreateButton.HEIGHT);
        box.text.set((Component) TextUtils.translate("gui." + CRNDisplayNextMod.MOD_ID + ".jre_vis_ribbon_line"));
        box.checked.set(showLineColor);
        box.layoutContraint.set(FlowLayout.FlowConstraint.FILL);
        box.tooltip.set(new de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLTooltip(
            java.util.List.of(
                TextUtils.translate("gui." + CRNDisplayNextMod.MOD_ID + ".jre_vis_ribbon_line"),
                TextUtils.translate("gui." + CRNDisplayNextMod.MOD_ID + ".jre_vis_ribbon_line.description")),
            220));
        box.addEventListener(DLToggleButton.CheckedChangedEvent.class, (s, e) -> {
            setShowLineColor(e.checked());
            return false;
        });
        line.addComponent(box);
    }



    @Override
    public void onChangeSettings(IDisplaySettings old) {
        this.copyColorSetting(old);
        if (old instanceof ITimeDisplaySetting s) setTimeDisplay(s.getTimeDisplay());
        if (old instanceof JREVISSettings s) {
            setShowLineColor(s.showLineColor());
            setShowDoNotBoardText(s.showDoNotBoardText());
            setShowConnection(s.showConnections());
            setShowStats(s.showStats());
            setCircularMode(s.circularMode());
            setPageIntervalSecs(s.getPageIntervalSecs());
            setPrefixMode(s.getPrefixMode());
        }
    }

    @Override
    public void serializeNbt(CompoundTag nbt) {
        super.serializeNbt(nbt);
        nbt.putByte("JREVIS_Time",           timeDisplay.getId());
        nbt.putBoolean("JREVIS_LineColor",    showLineColor);
        nbt.putBoolean("JREVIS_NoBoard",      doNotBoard);
        nbt.putBoolean("JREVIS_Connect",      showConnections);
        nbt.putBoolean("JREVIS_Stats",        showStats);
        nbt.putByte("JREVIS_CarriageIdx",     carriageIndex);
        nbt.putBoolean("JREVIS_OverCarriage", overwriteCarriage);
        nbt.putBoolean("JREVIS_Circular",     circularMode);
        nbt.putInt("JREVIS_PageInterval",     pageIntervalSecs);
        nbt.putByte("JREVIS_PrefixMode",      prefixMode);
    }

    @Override
    public void deserializeNbt(CompoundTag nbt) {
        super.deserializeNbt(nbt);
        if (nbt.contains("JREVIS_Time"))        timeDisplay    = ETimeDisplay.getById(nbt.getByte("JREVIS_Time"));
        if (nbt.contains("JREVIS_LineColor"))   showLineColor  = nbt.getBoolean("JREVIS_LineColor");
        if (nbt.contains("JREVIS_NoBoard"))     doNotBoard     = nbt.getBoolean("JREVIS_NoBoard");
        if (nbt.contains("JREVIS_Connect"))     showConnections= nbt.getBoolean("JREVIS_Connect");
        if (nbt.contains("JREVIS_Stats"))       showStats      = nbt.getBoolean("JREVIS_Stats");
        if (nbt.contains("JREVIS_CarriageIdx")) carriageIndex  = nbt.getByte("JREVIS_CarriageIdx");
        if (nbt.contains("JREVIS_OverCarriage"))overwriteCarriage = nbt.getBoolean("JREVIS_OverCarriage");
        if (nbt.contains("JREVIS_Circular"))   circularMode   = nbt.getBoolean("JREVIS_Circular");
        if (nbt.contains("JREVIS_PageInterval")) pageIntervalSecs = Math.max(1, nbt.getInt("JREVIS_PageInterval"));
        if (nbt.contains("JREVIS_PrefixMode"))  prefixMode   = nbt.getByte("JREVIS_PrefixMode");
    }
}