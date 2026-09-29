package com.hybrizat.crndisplaynext.compat;

import java.util.List;
import de.mrjulsen.crn.api.core.ref.TrainCategoryRef;
import de.mrjulsen.crn.core.train.LiveTrainState;
import de.mrjulsen.crn.core.train.ServiceState;
import de.mrjulsen.mcdragonlib.util.DLColor;

/** Compatibility-only train view used by existing JRE renderers. */
public final class TrainDisplayData {
    private final BasicTrainDisplayData basic;
    private final Stage stage;
    private final boolean cancelled;
    private final boolean waiting;
    private final StationDisplayData currentStop;
    private final StationDisplayData nextStop;
    private final java.util.List<StationDisplayData> stopsFromCurrentStation;

    public TrainDisplayData(BasicTrainDisplayData basic, Stage stage, boolean cancelled, boolean waiting, StationDisplayData currentStop, StationDisplayData nextStop, java.util.List<StationDisplayData> stopsFromCurrentStation) {
        this.basic = basic;
        this.stage = stage;
        this.cancelled = cancelled;
        this.waiting = waiting;
        this.currentStop = currentStop;
        this.nextStop = nextStop;
        this.stopsFromCurrentStation = java.util.List.copyOf(stopsFromCurrentStation);
    }

    public BasicTrainDisplayData getTrainData() { return basic; }
    public Stage getState() { return stage; }
    public boolean isCancelled() { return cancelled; }
    public boolean isWaitingAtStation() { return waiting; }
    public java.util.Optional<StationDisplayData> getCurrentStop() { return java.util.Optional.ofNullable(currentStop); }
    public java.util.Optional<StationDisplayData> getNextStop() { return java.util.Optional.ofNullable(nextStop); }
    public java.util.List<StationDisplayData> getStopsFromCurrentStation() { return stopsFromCurrentStation; }

    public record Stage(boolean outOfService, boolean shouldNotBoard) {
        public boolean isIrregular(boolean includeSoftTerminus) { return outOfService; }
        public boolean shouldNotBoard(boolean includeSoftTerminus) { return shouldNotBoard; }
    }
}
