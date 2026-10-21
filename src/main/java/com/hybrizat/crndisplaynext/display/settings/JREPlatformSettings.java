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
 * The filters are compared against the platform this train calls at (and the
 * station's own/tag name) in {@code BERJREPlatform}; see that class for the
 * exact candidate list.
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
        // The caption is a real label, not just an icon tooltip: the arrow only
        // appears once a filter is typed, so the boxes have to explain themselves.
        DLPanel leftLine = context.container().addLine("crndisplaynext.jre_left_platform_input");
        leftLine.addComponent(caption("jre_left_platform"));
        leftLine.addComponent(filterBox(leftPlatform, this::setLeftPlatform));

        // ── Right platform input ──────────────────────────────────────────
        DLPanel rightLine = context.container().addLine("crndisplaynext.jre_right_platform_input");
        rightLine.addComponent(caption("jre_right_platform"));
        rightLine.addComponent(filterBox(rightPlatform, this::setRightPlatform));
    }

    /** Visible label for one side, with the matching rules as its tooltip. */
    private static DLRichTextLabel caption(String titleKey) {
        Component title = TextUtils.translate("gui." + CRNDisplayNextMod.MOD_ID + "." + titleKey);
        Component hint  = TextUtils.translate("gui." + CRNDisplayNextMod.MOD_ID + ".jre_platform_hint");
        int width = net.minecraft.client.Minecraft.getInstance().font.width(title) + 2;
        DLRichTextLabel label = new DLRichTextLabel(0, 0, width, CreateButton.HEIGHT);
        label.contentPadding.set(new de.mrjulsen.mcdragonlib.client.gui.widgets.richtext.Padding(4, 0, 0, 0));
        label.text.get().set(title.getString());
        label.tooltip.set(new de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLTooltip(
            java.util.List.of(title, hint), 220));
        return label;
    }

    /** One platform filter box, bound to the given setter. */
    private static CreateTextBox filterBox(String initial, java.util.function.Consumer<String> sink) {
        CreateTextBox box = new CreateTextBox(0, 0, 0);
        box.layoutContraint.set(FlowLayout.FlowConstraint.FILL);
        box.maxCharacters.set(64);
        box.text.get().set(initial == null ? "" : initial);
        box.addEventListener(DLRichTextLabel.TextChangedEvent.class, (s, e) -> {
            sink.accept(e.text().getPlainText());
            return false;
        });
        return box;
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
