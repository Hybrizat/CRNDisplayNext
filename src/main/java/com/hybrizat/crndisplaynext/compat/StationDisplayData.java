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
    /**
     * Visit state to report. Normally the board entry's own, but a view built from a
     * journey stop has to carry the state the journey computed for it — that is the
     * only thing that can tell a passed stop from an upcoming one.
     */
    private final StopVisitState visitState;
    /**
     * Route colour of the section this stop belongs to, or null when the whole display
     * should use one colour. A through-service journey spans several sections with
     * different lines (e.g. Tokyu Den-en-toshi -> Tokyo Metro Hanzomon -> Tobu Skytree),
     * and the ribbon is coloured per section rather than uniformly.
     */
    private final java.awt.Color routeColor;

    public StationDisplayData(BoardEntry entry, boolean lastStop, boolean nextSectionExcluded, boolean prevSectionExcluded) {
        this(entry, lastStop, nextSectionExcluded, prevSectionExcluded,
             entry == null ? StopVisitState.UPCOMING : entry.visitState(), null);
    }

    public StationDisplayData(BoardEntry entry, boolean lastStop, boolean nextSectionExcluded,
                              boolean prevSectionExcluded, StopVisitState visitState) {
        this(entry, lastStop, nextSectionExcluded, prevSectionExcluded, visitState, null);
    }

    public StationDisplayData(BoardEntry entry, boolean lastStop, boolean nextSectionExcluded,
                              boolean prevSectionExcluded, StopVisitState visitState, java.awt.Color routeColor) {
        this.entry = entry;
        this.lastStop = lastStop;
        this.nextSectionExcluded = nextSectionExcluded;
        this.prevSectionExcluded = prevSectionExcluded;
        this.visitState = visitState == null ? StopVisitState.UPCOMING : visitState;
        this.routeColor = routeColor;
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

    /** Section route colour, or null to fall back to the display-wide route colour. */
    public java.awt.Color getRouteColor() { return routeColor; }

    public ListOfStopovers getStopoversView() { return new ListOfStopovers(entry.stopovers().stream().map(StationRef::displayName).toList()); }
    public boolean isLastStop() { return lastStop; }
    public boolean isNextSectionExcluded() { return nextSectionExcluded; }
    public boolean isPrevSectionExcluded() { return prevSectionExcluded; }

    public static final class ListOfStopovers {
        private final java.util.List<String> values;
        public ListOfStopovers(java.util.List<String> values) { this.values = values; }
        public java.util.List<String> values() { return values; }
    }
}
