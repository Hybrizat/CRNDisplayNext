package com.hybrizat.crndisplaynext.display.settings;

import com.hybrizat.crndisplaynext.CRNDisplayNextMod;
import com.hybrizat.crndisplaynext.client.screen.GraphicsImageWindow;
import de.mrjulsen.crn.block.display.properties.BasicDisplaySettings;
import de.mrjulsen.crn.block.display.properties.IDisplaySettings;
import de.mrjulsen.crn.client.gui.widgets.ModularWidgetContainer;
import de.mrjulsen.crn.client.gui.widgets.create.CreateButton;
import de.mrjulsen.crn.client.gui.widgets.modular.GuiBuilderContext;
import de.mrjulsen.mcdragonlib.client.gui.events.DLGuiStandardEvents;
import de.mrjulsen.mcdragonlib.client.gui.widgets.base.DLGuiComponent;
import de.mrjulsen.mcdragonlib.client.gui.widgets.base.DLWindow;
import de.mrjulsen.mcdragonlib.client.gui.widgets.base.DLWindowManager;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLButton;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLEditableLabel;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLPanel;
import de.mrjulsen.mcdragonlib.client.gui.widgets.components.DLTooltip;
import de.mrjulsen.mcdragonlib.client.gui.widgets.layout.FlowLayout;
import de.mrjulsen.mcdragonlib.client.gui.widgets.richtext.Padding;
import de.mrjulsen.mcdragonlib.util.DLColor;
import de.mrjulsen.mcdragonlib.util.TextUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

public class GraphicsDisplaySettings extends BasicDisplaySettings {

    /**
     * Advanced-settings row id. The row is added through CRN's own
     * {@link ModularWidgetContainer}, so it scrolls and lays out with the rest of
     * the window instead of being bolted on afterwards.
     */
    public static final String GUI_LINE_IMAGE_URL_NAME = "crndisplaynext_image_url";

    private String imageUrl = "";

    public GraphicsDisplaySettings() {
        this.fontColor = DLColor.fromInt(0xFF3b82f6);
        this.backColor = DLColor.fromInt(0xFF1a1a2e);
    }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String u) { this.imageUrl = u == null ? "" : u; }

    @Override
    public void serializeNbt(CompoundTag tag) {
        super.serializeNbt(tag);
        tag.putString("imgUrl", imageUrl);
    }

    @Override
    public void deserializeNbt(CompoundTag tag) {
        super.deserializeNbt(tag);
        imageUrl = tag.getString("imgUrl");
    }

    @Override
    public void onChangeSettings(IDisplaySettings oldSettings) {
        super.onChangeSettings(oldSettings);
        // Switching display variant away and back keeps the configured image.
        if ((imageUrl == null || imageUrl.isBlank()) && oldSettings instanceof GraphicsDisplaySettings o) {
            imageUrl = o.getImageUrl();
        }
    }

    /**
     * Builds the advanced-settings row for this display type: the image URL plus a
     * button that opens the picker.
     *
     * <p>Only runs while a CRN settings window is being built, so the client-only
     * types used here are never touched by the dedicated server.</p>
     */
    @Override
    public void buildGui(GuiBuilderContext context) {
        super.buildGui(context);

        ModularWidgetContainer container = context.container();
        DLPanel line = container.addLine(GUI_LINE_IMAGE_URL_NAME);

        DLEditableLabel field = line.addComponent(new DLEditableLabel(0, 0, 110, CreateButton.HEIGHT));
        field.text.set(imageUrl);
        field.padding.set(new Padding(3, 4, 3, 4));
        field.layoutContraint.set(FlowLayout.FlowConstraint.FILL);
        field.tooltip.set(new DLTooltip(List.of(
            TextUtils.translate("gui.crndisplaynext.image_url"),
            TextUtils.translate("gui.crndisplaynext.image_url.description")), 220));
        field.addEventListener(DLEditableLabel.TextEditedEvent.class, (s, e) -> {
            setImageUrl(e.text());
            return false;
        });

        DLButton browse = line.addComponent(new DLButton(0, 0, 52, CreateButton.HEIGHT));
        browse.text.set(TextUtils.translate("gui.crndisplaynext.image_url.browse"));
        browse.tooltip.set(new DLTooltip(List.of(
            TextUtils.translate("gui.crndisplaynext.image_url.browse.description")), 220));
        browse.addEventListener(DLGuiStandardEvents.ClickEvent.class, (s, e) -> {
            openPicker(container, field);
            return false;
        });
    }

    /**
     * Opens the picker as a modal of the settings window this row belongs to.
     *
     * <p>{@code this} is the settings object the settings window serialises when
     * it is confirmed, so whatever the picker writes here travels in CRN's own
     * display update packet — which handles plain blocks and contraptions alike,
     * and is why the image now survives on a materialised train.</p>
     */
    private void openPicker(ModularWidgetContainer container, DLEditableLabel field) {
        DLWindowManager manager = container.getWindowManager();
        if (manager == null) {
            CRNDisplayNextMod.LOGGER.warn("[Gfx] settings row is not attached to a window manager");
            return;
        }
        BlockPos pos = findDisplayPos(container);
        if (pos == null) {
            // Not fatal: the picker still edits the settings object, but it cannot
            // route the server-delivered preview back to this specific display.
            CRNDisplayNextMod.LOGGER.warn("[Gfx] could not resolve the display position; "
                + "the picker will open without a live preview");
        }
        GraphicsImageWindow.open(manager, pos, this, () -> field.text.set(imageUrl));
    }

    /**
     * Resolves the display position, which is the address CRN's display update
     * packet uses (it is the block entity's own position, inside the contraption
     * world for a materialised train).
     *
     * <p>The settings window keeps its position private and the component tree is
     * the documented way to reach it, so this walks up and then reads whichever of
     * the window's own fields is available. Both `pos` and `blockEntity` are
     * present in CRN 0.10.0; `pos` is preferred because it is exactly the value
     * the window puts in its update packet.</p>
     */
    private static BlockPos findDisplayPos(DLGuiComponent start) {
        DLGuiComponent current = start;
        while (current != null) {
            if (current instanceof DLWindow) {
                BlockPos pos = readPos(current);
                if (pos != null) {
                    return pos;
                }
            }
            current = current.getParent().orElse(null);
        }
        return null;
    }

    private static BlockPos readPos(Object window) {
        // Preferred: the window's own position field.
        BlockPos pos = readField(window, "pos", BlockPos.class);
        if (pos != null) {
            return pos;
        }
        // Fallback: the block entity's position.
        BlockEntity be = readField(window, "blockEntity", BlockEntity.class);
        return be == null ? null : be.getBlockPos();
    }

    private static <T> T readField(Object target, String name, Class<T> type) {
        Class<?> c = target.getClass();
        while (c != null) {
            try {
                var f = c.getDeclaredField(name);
                f.setAccessible(true);
                Object value = f.get(target);
                return type.isInstance(value) ? type.cast(value) : null;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            } catch (Exception e) {
                CRNDisplayNextMod.LOGGER.warn("[Gfx] could not read '{}' from the settings window", name, e);
                return null;
            }
        }
        return null;
    }
}
