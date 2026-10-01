package com.hybrizat.crndisplaynext.compat;

import de.mrjulsen.crn.api.core.ref.StationRef;
import de.mrjulsen.crn.api.core.CallDirection;
import de.mrjulsen.crn.api.core.snapshot.BoardEntry;
import de.mrjulsen.crn.core.timing.StopTimes;
import de.mrjulsen.crn.api.core.StopVisitState;

/** Compatibility-only view over CRN 0.10.0 BoardEntry. */
public final class StationDisplayData {
    private final BoardEntry entry;
    private final boolean lastStop;
    private final boolean nextSectionExcluded;
    private final boolean prevSectionExcluded;
    private final boolean technicalStop;
    /**
     * Visit state to report. Normally the board entry's own, but a view built from a
     * journey stop has to carry the state the journey computed for it — that is the
     * only thing that can tell a passed stop from an upcoming one.
     */
    private final StopVisitState visitState;

    public StationDisplayData(BoardEntry entry, boolean lastStop, boolean nextSectionExcluded, boolean prevSectionExcluded, boolean technicalStop) {
        this(entry, lastStop, nextSectionExcluded, prevSectionExcluded, technicalStop,
             entry == null ? StopVisitState.UPCOMING : entry.visitState());
    }

    public StationDisplayData(BoardEntry entry, boolean lastStop, boolean nextSectionExcluded,
                              boolean prevSectionExcluded, boolean technicalStop, StopVisitState visitState) {
        this.entry = entry;
        this.lastStop = lastStop;
        this.nextSectionExcluded = nextSectionExcluded;
        this.prevSectionExcluded = prevSectionExcluded;
        this.technicalStop = technicalStop;
        this.visitState = visitState == null ? StopVisitState.UPCOMING : visitState;
    }

    public BoardEntry raw() { return entry; }
    public TrainStopDisplayData getStationData() { return new TrainStopDisplayData(entry); }
    public String getDestination() { return entry.destination().displayName(); }
    public StationRef getRealTimeStation() { return entry.station(); }
    public long getScheduledDepartureTime() { return entry.scheduled().departure(); }
    public long getRealTimeArrivalTime() { return entry.realtime().arrival(); }
    public String getTrainName() { return entry.trainName(); }
    
    /** Get the visit state: PASSED, CURRENT, or UPCOMING */
    public StopVisitState getVisitState() { return visitState; }

    public ListOfStopovers getStopoversView() { return new ListOfStopovers(entry.stopovers().stream().map(StationRef::displayName).toList()); }
    public boolean isLastStop() { return lastStop; }
    public boolean isNextSectionExcluded() { return nextSectionExcluded; }
    public boolean isPrevSectionExcluded() { return prevSectionExcluded; }
    public boolean isTechnicalStop() { return technicalStop; }

    public static final class ListOfStopovers {
        private final java.util.List<String> values;
        public ListOfStopovers(java.util.List<String> values) { this.values = values; }
        public java.util.List<String> values() { return values; }
    }
}
