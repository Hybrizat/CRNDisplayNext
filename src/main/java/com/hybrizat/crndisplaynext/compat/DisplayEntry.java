package com.hybrizat.crndisplaynext.compat;

import de.mrjulsen.crn.api.core.ref.LineRef;
import de.mrjulsen.crn.api.core.ref.StationRef;
import de.mrjulsen.crn.api.core.ref.TrainCategoryRef;

import java.util.List;

/** Compatibility-only station-departure model used by the existing JRE renderers. */
public final class DisplayEntry {
    private final TrainDisplayData trainData;
    private final StationDisplayData stationData;
    private final List<StationRef> stopovers;

    public DisplayEntry(TrainDisplayData trainData, StationDisplayData stationData, List<StationRef> stopovers) {
        this.trainData = trainData;
        this.stationData = stationData;
        this.stopovers = stopovers == null ? List.of() : List.copyOf(stopovers);
    }

    public TrainDisplayData getTrainData() { return trainData; }
    public StationDisplayData getStationData() { return stationData; }
    /** The stations this departure goes on to serve, in order. */
    public List<StationRef> getStopovers() { return stopovers; }
    public boolean isCancelled() { return trainData.isCancelled(); }
    public boolean isLastStop() { return stationData.isLastStop(); }
}
