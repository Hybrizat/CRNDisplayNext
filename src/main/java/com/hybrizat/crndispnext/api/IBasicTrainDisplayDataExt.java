package com.hybrizat.crndisplaynext.api;

import de.mrjulsen.crn.data.train.ETrainStopState;
import de.mrjulsen.mcdragonlib.util.DLColor;

public interface IBasicTrainDisplayDataExt {
    DLColor crndisplaynext$getCategoryColor(ETrainStopState state);
    String  crndisplaynext$getCategoryName(ETrainStopState state);
    boolean crndisplaynext$hasCategoryColor(ETrainStopState state);
    void    crndisplaynext$setCategoryData(ETrainStopState state, String name, DLColor color);
}
