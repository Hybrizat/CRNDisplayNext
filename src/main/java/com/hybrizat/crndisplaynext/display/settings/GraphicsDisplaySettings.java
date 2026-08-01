package com.hybrizat.crndisplaynext.display.settings;

import de.mrjulsen.crn.block.display.properties.BasicDisplaySettings;
import de.mrjulsen.mcdragonlib.util.DLColor;
import net.minecraft.nbt.CompoundTag;

public class GraphicsDisplaySettings extends BasicDisplaySettings {

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
}
