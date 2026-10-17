package com.hybrizat.crndisplaynext.compat;

import de.mrjulsen.crn.api.core.ref.StationRef;
import de.mrjulsen.crn.api.core.snapshot.BoardEntry;
import de.mrjulsen.crn.api.core.StopVisitState;

/** Compatibility-only view over CRN 0.10.0 BoardEntry. */
public final class StationDisplayData {
    private final BoardEntry entry;
    private final boolean lastStop;
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

    public StationDisplayData(BoardEntry entry, boolean lastStop) {
        this(entry, lastStop, entry == null ? StopVisitState.UPCOMING : entry.visitState(), null);
    }

    public StationDisplayData(BoardEntry entry, boolean lastStop, StopVisitState visitState) {
        this(entry, lastStop, visitState, null);
    }

    public StationDisplayData(BoardEntry entry, boolean lastStop,
                              StopVisitState visitState, java.awt.Color routeColor) {
        this.entry = entry;
        this.lastStop = lastStop;
        this.visitState = visitState == null ? StopVisitState.UPCOMING : visitState;
        this.routeColor = routeColor;
    }

    public BoardEntry raw() { return entry; }
    public TrainStopDisplayData getStationData() { return new TrainStopDisplayData(entry); }

    /**
     * The 行先 to show.
     *
     * <p>Delegates to CRN's {@link BoardEntry#destinationText()} so that a schedule
     * title set with a "change title" instruction wins over the raw station name —
     * that is exactly what the title is for, and it is what CRN's own displays show.
     * Reading {@code destination().displayName()} directly silently dropped it.</p>
     */
    public String getDestination() { return entry.destinationText(); }

    public StationRef getRealTimeStation() { return entry.station(); }
    public long getScheduledDepartureTime() { return entry.scheduled().departure(); }
    public long getRealTimeArrivalTime() { return entry.realtime().arrival(); }
    public String getTrainName() { return entry.trainName(); }

    /** The stations served onward from here, in order. Empty when this call ends the run. */
    public java.util.List<StationRef> getStopovers() { return entry.stopovers(); }

    /** Get the visit state: PASSED, CURRENT, or UPCOMING */
    public StopVisitState getVisitState() { return visitState; }

    /** Section route colour, or null to fall back to the display-wide route colour. */
    public java.awt.Color getRouteColor() { return routeColor; }

    /**
     * Whether passengers must leave the train here. CRN already answers this per call
     * as {@link BoardEntry#terminus()}, and it understands a through-service section
     * that hands over to the next one instead of ending the run.
     */
    public boolean isLastStop() { return lastStop; }
}