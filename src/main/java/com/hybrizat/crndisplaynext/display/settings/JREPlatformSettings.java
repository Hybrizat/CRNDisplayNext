package com.hybrizat.crndisplaynext.display.settings;

import com.hybrizat.crndisplaynext.CRNDisplayNextMod;
import de.mrjulsen.crn.block.display.properties.IDisplaySettings;
import de.mrjulsen.crn.client.gui.widgets.create.CreateButton;
import de.mrjulsen.crn.client.gui.widgets.create.CreateTextBox;
import de.mrjulsen.crn.client.gui.widgets.modular.GuiBuilderContext;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLCheckBox;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLPanel;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLRichTextLabel;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLToggleButton;
import de.mrjulsen.mcdragonlib.client.gui.widgets.layout.FlowLayout;
import de.mrjulsen.mcdragonlib.util.DLColor;
import de.mrjulsen.mcdragonlib.util.TextUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * Settings for the JRE LED Platform display (platform edge).
 * No platform number column.
 * Adds direction arrow configuration:
 *   - checkbox to enable/disable arrows
 *   - left platform filter (station name or platform number, supports wildcards)
 *   - right platform filter
 *
 * Matching uses TrainUtils.stationMatches() at render time,
 * comparing against getRealTimeStation().info().platform() or station tag name.
 */
public class JREPlatformSettings extends AbstractJRESettings {

    private static final DLColor LED_AMBER = DLColor.fromInt(0xFFFF8C00);
    private static final DLColor LED_BACK  = DLColor.fromInt(0xFF111111);

    private static final String NBT_SHOW_ARROW     = "JRE_ShowArrow";
    private static final String NBT_LEFT_PLATFORM  = "JRE_LeftPlatform";
    private static final String NBT_RIGHT_PLATFORM = "JRE_RightPlatform";

    private boolean showArrow     = false;
    private String  leftPlatform  = "";
    private String  rightPlatform = "";

    public JREPlatformSettings() { super(LED_AMBER, LED_BACK); }

    public boolean showArrow()             { return showArrow; }
    public void setShowArrow(boolean b)    { this.showArrow = b; }
    public String getLeftPlatform()        { return leftPlatform; }
    public void setLeftPlatform(String s)  { this.leftPlatform  = s == null ? "" : s; }
    public String getRightPlatform()       { return rightPlatform; }
    public void setRightPlatform(String s) { this.rightPlatform = s == null ? "" : s; }

    @Override
    public void buildGui(GuiBuilderContext context) {
        buildSharedGui(context);
        buildArrowGui(context);
    }

    private void buildArrowGui(GuiBuilderContext context) {
        // ── Enable checkbox ───────────────────────────────────────────────
        DLPanel enableLine = context.container().addLine("crndisplaynext.jre_show_arrow");
        DLCheckBox enableBox = new DLCheckBox(0, 0, 0, CreateButton.HEIGHT);
        enableBox.text.set((Component) TextUtils.translate(
            "gui." + CRNDisplayNextMod.MOD_ID + ".jre_show_arrow"));
        enableBox.checked.set(showArrow);
        enableBox.layoutContraint.set(FlowLayout.FlowConstraint.FILL);
        enableBox.addEventListener(DLToggleButton.CheckedChangedEvent.class, (s, e) -> {
            setShowArrow(e.checked());
            return false;
        });
        enableLine.addComponent(enableBox);

        // ── Left platform input ───────────────────────────────────────────
        // CreateTextBox.title renders as a tooltip/hint on the input box,
        // consistent with CRN's own text inputs (e.g. station filter box).
        DLPanel leftLine = context.container().addLine("crndisplaynext.jre_left_platform_input");
        CreateTextBox leftBox = new CreateTextBox(0, 0, 0);
        leftBox.layoutContraint.set(FlowLayout.FlowConstraint.FILL);
        leftBox.maxCharacters.set(64);
        leftBox.text.get().set(leftPlatform);
        leftBox.addEventListener(DLRichTextLabel.TextChangedEvent.class, (s, e) -> {
            setLeftPlatform(e.text().getPlainText());
            return false;
        });
        // Use a label component before the box — mirrors CRN's station filter pattern
        de.mrjulsen.crn.client.gui.widgets.IconSlotWidget leftIcon =
            new de.mrjulsen.crn.client.gui.widgets.IconSlotWidget(0, 0);
        leftIcon.tooltip.set(new de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLTooltip(
            java.util.List.of(TextUtils.translate("gui." + CRNDisplayNextMod.MOD_ID + ".jre_left_platform"),
                              TextUtils.translate("gui." + CRNDisplayNextMod.MOD_ID + ".jre_platform_hint")),
            200));
        leftIcon.icon.set(de.mrjulsen.crn.client.gui.ModGuiIcons.TEXT.getAsSprite(16, 16));
        leftLine.addComponent(leftIcon);
        leftLine.addComponent(leftBox);

        // ── Right platform input ──────────────────────────────────────────
        DLPanel rightLine = context.container().addLine("crndisplaynext.jre_right_platform_input");
        CreateTextBox rightBox = new CreateTextBox(0, 0, 0);
        rightBox.layoutContraint.set(FlowLayout.FlowConstraint.FILL);
        rightBox.maxCharacters.set(64);
        rightBox.text.get().set(rightPlatform);
        rightBox.addEventListener(DLRichTextLabel.TextChangedEvent.class, (s, e) -> {
            setRightPlatform(e.text().getPlainText());
            return false;
        });
        de.mrjulsen.crn.client.gui.widgets.IconSlotWidget rightIcon =
            new de.mrjulsen.crn.client.gui.widgets.IconSlotWidget(0, 0);
        rightIcon.tooltip.set(new de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLTooltip(
            java.util.List.of(TextUtils.translate("gui." + CRNDisplayNextMod.MOD_ID + ".jre_right_platform"),
                              TextUtils.translate("gui." + CRNDisplayNextMod.MOD_ID + ".jre_platform_hint")),
            200));
        rightIcon.icon.set(de.mrjulsen.crn.client.gui.ModGuiIcons.TEXT.getAsSprite(16, 16));
        rightLine.addComponent(rightIcon);
        rightLine.addComponent(rightBox);
    }

    @Override
    public void onChangeSettings(IDisplaySettings old) {
        copySharedSettings(old);
        if (old instanceof JREPlatformSettings s) {
            setShowArrow(s.showArrow());
            setLeftPlatform(s.getLeftPlatform());
            setRightPlatform(s.getRightPlatform());
        }
    }

    @Override
    public void serializeNbt(CompoundTag nbt) {
        super.serializeNbt(nbt);
        nbt.putBoolean(NBT_SHOW_ARROW,     showArrow);
        nbt.putString(NBT_LEFT_PLATFORM,   leftPlatform);
        nbt.putString(NBT_RIGHT_PLATFORM,  rightPlatform);
    }

    @Override
    public void deserializeNbt(CompoundTag nbt) {
        super.deserializeNbt(nbt);
        if (nbt.contains(NBT_SHOW_ARROW))     showArrow     = nbt.getBoolean(NBT_SHOW_ARROW);
        if (nbt.contains(NBT_LEFT_PLATFORM))  leftPlatform  = nbt.getString(NBT_LEFT_PLATFORM);
        if (nbt.contains(NBT_RIGHT_PLATFORM)) rightPlatform = nbt.getString(NBT_RIGHT_PLATFORM);
    }
}
