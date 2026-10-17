package com.hybrizat.crndisplaynext.compat;

import java.util.List;
import de.mrjulsen.crn.data.TrainJourneyStage;
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

    /**
     * The journey stage, held as CRN's own enum so that the caller's
     * {@code includeSoftTerminus} choice actually reaches CRN's evaluation.
     *
     * <p>This used to be a record of two booleans snapshotted at
     * {@code shouldNotBoard(false)}, which silently dropped the argument: every
     * renderer asks with its own {@code showDoNotBoardText} setting, and the
     * {@code SOFT_TERMINUS_ANNOUNCED} stage — the one stage that setting exists
     * to control — could therefore never be reported. Keeping the enum means
     * {@code isIrregular(flag)} / {@code shouldNotBoard(flag)} are CRN's own
     * predicates, identical to what CRN's built-in displays evaluate.</p>
     */
    public static final class Stage {
        private final TrainJourneyStage stage;

        public Stage(TrainJourneyStage stage) {
            this.stage = stage == null ? TrainJourneyStage.OUT_OF_SERVICE : stage;
        }

        public boolean isOutOfService() { return stage.isOutOfService(); }

        public boolean isIrregular(boolean includeSoftTerminus) { return stage.isIrregular(includeSoftTerminus); }

        public boolean shouldNotBoard(boolean includeSoftTerminus) { return stage.shouldNotBoard(includeSoftTerminus); }

        @Override public String toString() { return stage.name(); }
    }
}
