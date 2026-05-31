package com.hybrizat.crndisplaynext.display.settings;

import de.mrjulsen.crn.block.display.properties.BasicDisplaySettings;
import de.mrjulsen.crn.block.display.properties.IDisplaySettings;
import de.mrjulsen.crn.block.display.properties.components.IShowDoNotBoardText;
import de.mrjulsen.crn.block.display.properties.components.IShowLineColorSetting;
import de.mrjulsen.crn.block.display.properties.components.IShowNextConnections;
import de.mrjulsen.crn.block.display.properties.components.IShowTrainStatsSetting;
import de.mrjulsen.crn.block.display.properties.components.ITimeDisplaySetting;
import de.mrjulsen.crn.block.display.properties.components.ICarriageIndexSetting;
import de.mrjulsen.crn.block.properties.ETimeDisplay;
import de.mrjulsen.crn.client.gui.widgets.modular.GuiBuilderContext;
import de.mrjulsen.mcdragonlib.util.DLColor;
import net.minecraft.nbt.CompoundTag;

/**
 * Settings for the JRE Passenger VIS (Visual Information System).
 * Mirrors PassengerInformationDetailedSettings but with JRE-specific defaults.
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
    @Override public void setOverwriteCarriageIndex(boolean b) { this.overwriteCarriage = b; }

    @Override
    public void buildGui(GuiBuilderContext context) {
        this.buildColorGui(context);
        this.buildTimeDisplayGui(context);
        this.buildShowLineColorGui(context);
        this.buildShowDoNotBoardTextGui(context);
        this.buildShowConnectionGui(context);
        this.buildShowStatsGui(context);
        this.buildCarriageIndexGui(context);
    }

    @Override
    public void onChangeSettings(IDisplaySettings old) {
        this.copyColorSetting(old);
        if (old instanceof ITimeDisplaySetting s)   setTimeDisplay(s.getTimeDisplay());
        if (old instanceof JREVISSettings s) {
            setShowLineColor(s.showLineColor());
            setShowDoNotBoardText(s.showDoNotBoardText());
            setShowConnection(s.showConnections());
            setShowStats(s.showStats());
        }
    }

    @Override
    public void serializeNbt(CompoundTag nbt) {
        super.serializeNbt(nbt);
        nbt.putByte("JREVIS_Time",        timeDisplay.getId());
        nbt.putBoolean("JREVIS_LineColor", showLineColor);
        nbt.putBoolean("JREVIS_NoBoard",   doNotBoard);
        nbt.putBoolean("JREVIS_Connect",   showConnections);
        nbt.putBoolean("JREVIS_Stats",     showStats);
        nbt.putByte("JREVIS_CarriageIdx",  carriageIndex);
        nbt.putBoolean("JREVIS_OverCarriage", overwriteCarriage);
    }

    @Override
    public void deserializeNbt(CompoundTag nbt) {
        super.deserializeNbt(nbt);
        if (nbt.contains("JREVIS_Time"))       timeDisplay     = ETimeDisplay.getById(nbt.getByte("JREVIS_Time"));
        if (nbt.contains("JREVIS_LineColor"))  showLineColor   = nbt.getBoolean("JREVIS_LineColor");
        if (nbt.contains("JREVIS_NoBoard"))    doNotBoard      = nbt.getBoolean("JREVIS_NoBoard");
        if (nbt.contains("JREVIS_Connect"))    showConnections = nbt.getBoolean("JREVIS_Connect");
        if (nbt.contains("JREVIS_Stats"))      showStats       = nbt.getBoolean("JREVIS_Stats");
        if (nbt.contains("JREVIS_CarriageIdx")) carriageIndex  = nbt.getByte("JREVIS_CarriageIdx");
        if (nbt.contains("JREVIS_OverCarriage")) overwriteCarriage = nbt.getBoolean("JREVIS_OverCarriage");
    }
}
