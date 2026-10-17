package com.hybrizat.crndisplaynext.train;

import de.mrjulsen.crn.api.core.StopVisitState;
import de.mrjulsen.crn.api.core.ref.StationRef;
import de.mrjulsen.crn.api.core.snapshot.JourneySnapshot;
import de.mrjulsen.crn.api.core.snapshot.SectionSnapshot;
import de.mrjulsen.crn.api.core.snapshot.StopSnapshot;
import de.mrjulsen.crn.api.core.snapshot.TrainSnapshot;
import de.mrjulsen.crn.block.blockentity.AdvancedDisplayBlockEntity;
import de.mrjulsen.crn.data.TrainJourneyStage;
import de.mrjulsen.mcdragonlib.util.DLColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Everything a train-information display (front / side / VIS) needs, read straight off
 * CRN's snapshots.
 *
 * <p>This replaces the {@code compat} package's CRN-0.9-shaped simulation
 * ({@code TrainDisplayData} / {@code StationDisplayData} / {@code BoardEntry}
 * synthesis) for displays that can use it. It exists because that simulation had to
 * fabricate a {@code BoardEntry} per stop, which meant re-deriving — and getting
 * subtly wrong — everything CRN had already computed: the through-service terminus,
 * whether a stop is a terminus, the call direction, the per-section colour.</p>
 *
 * <p>Data source: only displays registered under {@code TRAIN_INFORMATION} or
 * {@code NONE} ever get a populated {@code be.getTrain()} / {@code be.getJourney()}
 * (CRN fills those in {@code contraptionTick} and skips the platform source), so
 * {@link #of} returns {@code null} for a station/platform board.</p>
 */
public record TrainView(
    TrainJourneyStage stage,
    boolean waiting,
    boolean cancelled,
    String trainName,
    /** 種別 — the category worked under, or empty when none is configured. */
    String categoryName,
    DLColor categoryColor,
    /** 路線名 — the line name, or the train's own name when it has no line. */
    String lineName,
    DLColor lineColor,
    /** 行先, already resolved through the through-service chain. */
    String destination,
    /** The stop the train stands at right now, or null while it is running. */
    StopView currentStop,
    /** The stop after the current one, or null at the end of the run. */
    StopView nextStop,
    /**
     * Passed stops first (greyed out), then the forward stops up to where this service
     * ends. Never projects into the next cycle.
     */
    List<StopView> route
) {

    /** One stop on the route. */
    public record StopView(
        StationRef station,
        StopVisitState visitState,
        long scheduledArrival,
        long realtimeArrival,
        long scheduledDeparture,
        long realtimeDeparture,
        /** Line/category colour of the section this stop belongs to, or null. */
        DLColor routeColor,
        /** Whether passengers must leave the train here (CRN's {@code isTerminus}). */
        boolean terminus,
        /**
         * Whether the train changes section here while carrying passengers through
         * (CRN's {@code isHandover}) — the 直通 handover station.
         */
        boolean handover
    ) {
        public String name() { return station == null ? "" : station.displayName(); }
        public boolean isPassed() { return visitState == StopVisitState.PASSED; }
        public boolean isCurrent() { return visitState == StopVisitState.CURRENT; }
    }

    public TrainView {
        route = route == null ? List.of() : List.copyOf(route);
        trainName = trainName == null ? "" : trainName;
        categoryName = categoryName == null ? "" : categoryName;
        lineName = lineName == null ? "" : lineName;
        destination = destination == null ? "" : destination;
        categoryColor = categoryColor == null ? DLColor.TRANSPARENT : categoryColor;
        lineColor = lineColor == null ? DLColor.TRANSPARENT : lineColor;
        stage = stage == null ? TrainJourneyStage.OUT_OF_SERVICE : stage;
    }

    public boolean isIrregular(boolean includeSoftTerminus) { return stage.isIrregular(includeSoftTerminus); }
    public boolean shouldNotBoard(boolean includeSoftTerminus) { return stage.shouldNotBoard(includeSoftTerminus); }

    /** The 種別 badge text: the category where one is configured, else the line name. */
    public String badge() { return categoryName.isBlank() ? lineName : categoryName; }

    /** The badge colour, matching {@link #badge()}. */
    public DLColor badgeColor() { return categoryName.isBlank() ? lineColor : categoryColor; }

    public boolean hasRoute() { return !route.isEmpty(); }

    // ═══════════════════════════════════════════════════════════════════════
    //  Construction
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Builds the view for a train-information display, or {@code null} when the
     * display has no train behind it (no snapshot, or a station/platform board).
     */
    public static TrainView of(AdvancedDisplayBlockEntity be) {
        TrainSnapshot train = be.getTrain().orElse(null);
        if (train == null) return null;
        JourneySnapshot journey = be.getJourney().orElse(null);
        boolean waiting = be.isWaitingAtStation();

        // Resolved once per update rather than per stop.
        RouteResolver.Route route = RouteResolver.resolve(journey, waiting);
        StationRef terminus = RouteResolver.terminus(journey, route);

        // CRN's own display name / colour: the operating section's line where it has
        // one, otherwise the train's own name. This already accounts for the handover
        // station not renaming the service before the train has arrived.
        String lineName = be.getTrainDisplayName();
        DLColor lineColor = be.getTrainDisplayColor();
        if (lineName.isBlank()) lineName = train.trainName();
        if (lineName.isBlank()) lineName = train.displayName();

        return new TrainView(
            be.getStage(),
            waiting,
            train.isCancelled(),
            train.trainName(),
            train.category().name(),
            train.category().color(),
            lineName,
            lineColor,
            // Title wins where the timetable sets one (ChangeTitleInstruction) — that is
            // what it is for, and it is what CRN's own displays show.
            preferredDestination(be, terminus),
            be.getCurrentStop().map(s -> stop(journey, s, waiting ? StopVisitState.CURRENT : StopVisitState.UPCOMING)).orElse(null),
            // While the train STANDS at a station, be.getNextStop() is that same station
            // (getRemainingStops() starts at the current stop), so the following stop is
            // journey.nextStop(). While it runs, the current stop already IS the station
            // being approached, so be.getNextStop() is the right one.
            Optional.ofNullable(waiting
                ? (journey == null ? null : journey.nextStop().orElse(null))
                : be.getNextStop().orElse(null))
                .map(s -> stop(journey, s, StopVisitState.UPCOMING)).orElse(null),
            route(journey, route, waiting)
        );
    }

    /**
     * The 行先: the schedule title where the timetable sets one, else the terminus
     * resolved by {@link RouteResolver}.
     *
     * <p>Deliberately NOT {@code be.getDestinationText()}: its middle fallback is
     * {@code getOperatingSection().destination()}, the very field that resolves to the
     * section's first stop on a section-less cyclic run. Only its first fallback (the
     * schedule title) is trustworthy, so that one is read directly and the terminus is
     * taken from the route instead.</p>
     */
    private static String preferredDestination(AdvancedDisplayBlockEntity be, StationRef terminus) {
        String title = be.getCurrentStop().map(StopSnapshot::title).filter(t -> !t.isBlank()).orElse("");
        if (!title.isBlank()) return title;
        if (terminus != null && terminus.isKnown()) return terminus.displayName();
        return be.getFinalStop().map(s -> s.station().displayName()).orElse("");
    }

    private static StopView stop(JourneySnapshot journey, StopSnapshot stop, StopVisitState forcedState) {
        return new StopView(
            stop.station(),
            forcedState,
            stop.scheduled().arrival(),
            stop.realtime().arrival(),
            stop.scheduled().departure(),
            stop.realtime().departure(),
            journey == null ? null : journey.sectionOf(stop).map(SectionSnapshot::displayColor).orElse(null),
            journey != null && journey.isTerminus(stop),
            journey != null && journey.isHandover(stop)
        );
    }

    /**
     * The stops of this service, in order: the ones already left behind first (so the
     * renderer can grey them), then the ones still ahead.
     *
     * <p>Walked straight off {@code journey.stops()} between the route's start and end
     * indices rather than assembled from {@code passedStops()} +
     * {@code upcomingStops()}. The latter spans the WHOLE run, so a train in section B
     * still listed section A's stops as "passed", and on a cyclic run
     * {@code upcomingStops()} wraps into the next period. Slicing the one list handles
     * both, and the section boundary falls out of {@link RouteResolver} for free.</p>
     */
    private static List<StopView> route(JourneySnapshot journey, RouteResolver.Route route, boolean waiting) {
        List<StopView> out = new ArrayList<>();
        if (journey == null || route == null || route.isEmpty()) return out;
        List<StopSnapshot> all = journey.stops();
        int currentIndex = journey.currentStopIndex();
        int end = Math.min(route.endIndex(), all.size() - 1);
        for (int i = Math.max(0, route.startIndex()); i <= end; i++) {
            StopSnapshot s = all.get(i);
            StopVisitState state;
            if (currentIndex < 0 || i > currentIndex) {
                state = StopVisitState.UPCOMING;
            } else if (i < currentIndex) {
                state = StopVisitState.PASSED;
            } else {
                // CURRENT only while the train actually STANDS at the stop. Between
                // stations the first stop is the one being approached and must read as
                // UPCOMING, otherwise the two states render identically.
                state = waiting ? StopVisitState.CURRENT : StopVisitState.UPCOMING;
            }
            out.add(stop(journey, s, state));
        }
        return out;
    }
}