package com.hybrizat.crndisplaynext.api;

import de.mrjulsen.crn.data.train.ETrainStopState;
import de.mrjulsen.mcdragonlib.util.DLColor;

public interface IBasicTrainDisplayDataExt {
    DLColor crndisplaynext$getCategoryColor(ETrainStopState state);
    String  crndisplaynext$getCategoryName(ETrainStopState state);
    boolean crndisplaynext$hasCategoryColor(ETrainStopState state);
    void    crndisplaynext$setCategoryData(ETrainStopState state, String name, DLColor color);

    /** Raw Minecraft entity name of the train (e.g. "やまびこ123号").
     *  Set server-side from Train.name.getString(). Empty string if unavailable. */
    String  crndisplaynext$getTrainEntityName();
    void    crndisplaynext$setTrainEntityName(String name);
}
