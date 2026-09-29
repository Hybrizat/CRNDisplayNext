package com.hybrizat.crndisplaynext.compat;

import com.hybrizat.crndisplaynext.api.IBasicTrainDisplayDataExt;
import de.mrjulsen.mcdragonlib.util.DLColor;

/** Compatibility-only train/category/line name and colour view. */
public final class BasicTrainDisplayData implements IBasicTrainDisplayDataExt {
    private final String trainName;
    private final String categoryName;
    private final DLColor categoryColor;
    private final String lineName;
    private final DLColor lineColor;
    private final boolean cancelled;
    private String trainEntityName;

    public BasicTrainDisplayData(String trainName, String categoryName, DLColor categoryColor, String lineName, DLColor lineColor, boolean cancelled) {
        this.trainName = trainName == null ? "" : trainName;
        this.categoryName = categoryName == null ? "" : categoryName;
        this.categoryColor = categoryColor == null ? DLColor.TRANSPARENT : categoryColor;
        this.lineName = lineName == null ? "" : lineName;
        this.lineColor = lineColor == null ? DLColor.TRANSPARENT : lineColor;
        this.cancelled = cancelled;
        this.trainEntityName = this.trainName;
    }

    public String getName(ETrainStopState state) {
        return state == ETrainStopState.ARRIVAL && !categoryName.isBlank() ? categoryName : lineName;
    }
    public DLColor getColor(ETrainStopState state) {
        return state == ETrainStopState.ARRIVAL && !categoryName.isBlank() ? categoryColor : lineColor;
    }
    public boolean hasColor(ETrainStopState state) { return !getColor(state).isTransparent(); }
    public boolean isCancelled() { return cancelled; }
    public String getCategoryName() { return categoryName; }
    public DLColor getCategoryColor() { return categoryColor; }
    public String getLineName() { return lineName; }
    public DLColor getLineColor() { return lineColor; }

    @Override
    public DLColor crndisplaynext$getCategoryColor(ETrainStopState state) {
        return state == ETrainStopState.ARRIVAL ? categoryColor : lineColor;
    }

    @Override
    public String crndisplaynext$getCategoryName(ETrainStopState state) {
        return state == ETrainStopState.ARRIVAL ? categoryName : lineName;
    }

    @Override
    public boolean crndisplaynext$hasCategoryColor(ETrainStopState state) {
        return !crndisplaynext$getCategoryColor(state).isTransparent();
    }

    @Override
    public void crndisplaynext$setCategoryData(ETrainStopState state, String name, DLColor color) { /* compatibility view is immutable after construction */ }

    @Override
    public String crndisplaynext$getTrainEntityName() {
        return trainEntityName;
    }

    @Override
    public void crndisplaynext$setTrainEntityName(String name) {
        trainEntityName = name == null ? "" : name;
    }
}

