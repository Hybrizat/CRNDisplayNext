package com.hybrizat.crndisplaynext.display;

import com.hybrizat.crndisplaynext.CRNDisplayNextMod;
import de.mrjulsen.mcdragonlib.data.ITranslatableEnum;

import java.util.Arrays;

/**
 * Controls what text is shown in the "train identifier" position of the JRE display:
 *   [TIME]  [TRAIN_NAME or CATEGORY_NAME]  [DESTINATION]方面  [PLATFORM]番線
 *
 * TRAIN_NAME  : BasicTrainDisplayData.getName(DEPARTURE)
 *               = resolveTrainDisplayName(section) for the DEPARTURE leg.
 *               This is the user-configured train name (e.g. "のぞみ1号", "快速アクティー").
 *               Color source: line color (TrainLine::getColor).
 *
 * CATEGORY_NAME: The category name + category color.
 *               Category name comes from ScheduleSection.getTrainCategory() on the server,
 *               stored via MixinBasicTrainDisplayData.
 *               This represents the service type (e.g. "快速", "特急", "普通").
 *               Color source: category color (TrainCategory::getColor).
 */
public enum EJRETrainTextMode implements ITranslatableEnum {

    TRAIN_NAME   ((byte)0, "train_name"),
    CATEGORY_NAME((byte)1, "category_name");

    private final byte   id;
    private final String name;

    EJRETrainTextMode(byte id, String name) {
        this.id   = id;
        this.name = name;
    }

    public byte getId() { return id; }

    public static EJRETrainTextMode getById(int id) {
        return Arrays.stream(values())
            .filter(x -> x.id == (byte) id)
            .findFirst()
            .orElse(TRAIN_NAME);
    }

    @Override
    public String getSerializedName() { return name; }

    @Override
    public Data getTranslationData() {
        // Translation keys:
        //   enum.crndisplaynext.jre_train_text_mode.train_name
        //   enum.crndisplaynext.jre_train_text_mode.category_name
        return new Data(CRNDisplayNextMod.MOD_ID, "jre_train_text_mode", name);
    }
}
