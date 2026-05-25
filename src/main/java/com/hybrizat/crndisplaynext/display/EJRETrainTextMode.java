package com.hybrizat.crndisplaynext.display;

import com.hybrizat.crndisplaynext.CRNDisplayNextMod;
import de.mrjulsen.mcdragonlib.data.ITranslatableEnum;
import java.util.Arrays;

/**
 * Controls what text appears in the train identifier column of JRE displays.
 *
 *  TRAIN_NAME    → BasicTrainDisplayData.getName(DEPARTURE)
 *                  (user-configured train name, e.g. "のぞみ1号")
 *                  Color: category color (our injected field)
 *
 *  CATEGORY_NAME → crndisplaynext$getCategoryName(DEPARTURE)
 *                  (service type, e.g. "特急", "快速", "普通")
 *                  Color: category color
 *
 *  LINE_NAME     → BasicTrainDisplayData.getName(DEPARTURE) when it matches
 *                  the section's line display name
 *                  Color: line color (trainData.getColor(DEPARTURE))
 */
public enum EJRETrainTextMode implements ITranslatableEnum {
    TRAIN_NAME   ((byte)0, "train_name"),
    CATEGORY_NAME((byte)1, "category_name"),
    LINE_NAME    ((byte)2, "line_name");

    private final byte   id;
    private final String name;

    EJRETrainTextMode(byte id, String name) { this.id = id; this.name = name; }

    public byte getId() { return id; }

    public static EJRETrainTextMode getById(int id) {
        return Arrays.stream(values()).filter(x -> x.id == (byte)id).findFirst().orElse(TRAIN_NAME);
    }

    @Override public String getSerializedName() { return name; }

    @Override
    public Data getTranslationData() {
        return new Data(CRNDisplayNextMod.MOD_ID, "jre_train_text_mode", name);
    }
}
