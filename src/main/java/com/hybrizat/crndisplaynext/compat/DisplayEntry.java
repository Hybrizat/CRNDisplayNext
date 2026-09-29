package com.hybrizat.crndisplaynext.compat;

import de.mrjulsen.crn.api.core.ref.LineRef;
import de.mrjulsen.crn.api.core.ref.StationRef;
import de.mrjulsen.crn.api.core.ref.TrainCategoryRef;
import de.mrjulsen.mcdragonlib.util.DLColor;

import java.util.List;

/** Compatibility-only station-departure model used by the existing JRE renderers. */
public final class DisplayEntry {
    private final TrainDisplayData trainData;
    private final StationDisplayData stationData;
    private final List<String> stopovers;

    public DisplayEntry(TrainDisplayData trainData, StationDisplayData stationData, List<String> stopovers) {
        this.trainData = trainData;
        this.stationData = stationData;
        this.stopovers = List.copyOf(stopovers);
    }

    public TrainDisplayData getTrainData() { return trainData; }
    public StationDisplayData getStationData() { return stationData; }
    public List<String> getStopovers() { return stopovers; }
    public boolean isCancelled() { return trainData.isCancelled(); }
    public boolean isLastStop() { return stationData.isLastStop(); }
    public boolean isNextSectionExcluded() { return stationData.isNextSectionExcluded(); }
    public boolean isPrevSectionExcluded() { return stationData.isPrevSectionExcluded(); }
}
