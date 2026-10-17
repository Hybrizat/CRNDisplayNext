package com.hybrizat.crndisplaynext.compat;

import de.mrjulsen.crn.api.core.RailwayBackendApi;
import de.mrjulsen.crn.api.core.snapshot.BoardEntry;
import de.mrjulsen.crn.api.core.snapshot.JourneySnapshot;
import de.mrjulsen.crn.api.core.snapshot.SectionSnapshot;
import de.mrjulsen.crn.api.core.snapshot.StopSnapshot;
import de.mrjulsen.crn.api.core.snapshot.TrainSnapshot;
import de.mrjulsen.crn.api.core.ref.LineRef;
import de.mrjulsen.crn.api.core.ref.StationRef;
import de.mrjulsen.crn.api.core.ref.TrainCategoryRef;
import de.mrjulsen.crn.api.core.StopVisitState;
import de.mrjulsen.crn.block.blockentity.AdvancedDisplayBlockEntity;
import com.hybrizat.crndisplaynext.train.RouteResolver;
import de.mrjulsen.mcdragonlib.util.DLColor;

import java.util.ArrayList;
import java.util.List;

/**
 * Bridges CRN 0.10.0 snapshots into the legacy display model without adding features.
 *
 * <p>Data source split, which decides which entry point a display has to use:
 * {@code EDisplayType.PLATFORM} displays (station / platform) only ever get
 * {@code be.getStops()} — a departure board of {@link BoardEntry} — because CRN fills
 * the train and journey snapshots in {@code contraptionTick} only for the
 * TRAIN_INFORMATION and NONE sources. Train-information displays (front / side / VIS)
 * therefore have to derive everything from {@code be.getTrain()} +
 * {@code be.getJourney()}.</p>
 */
public final class CrnDisplayAdapter {
    private CrnDisplayAdapter() {}

    public static List<DisplayEntry> board(AdvancedDisplayBlockEntity be) {
        List<BoardEntry> source = be.getStops();
        List<DisplayEntry> result = new ArrayList<>(source.size());
        for (BoardEntry entry : source) {
            result.add(toEntry(be, entry));
        }
        return result;
    }

    public static DisplayEntry toEntry(AdvancedDisplayBlockEntity be, BoardEntry entry) {
        TrainSnapshot train = be.getTrain().orElse(null);
        JourneySnapshot journey = be.getJourney().orElse(null);
        BasicTrainDisplayData basic = basic(train, entry);
        TrainDisplayData.Stage stage = stage(be);
        boolean waiting = be.isWaitingAtStation();
        StationDisplayData current = waiting ? new StationDisplayData(entry, entry.terminus(), StopVisitState.CURRENT) : null;
        // A departure-board display never renders these (AbstractJRERenderer only
        // reads the entry itself), but they still have to be built. The board entry
        // already carries CRN's own resolved destination for this call, so pass that
        // rather than re-deriving one from a journey snapshot the display does not have.
        StationRef destination = entry.destination();
        // "next" must be the stop FOLLOWING the current one. be.getNextStop() is the
        // first of getRemainingStops(), which already starts at the current stop, so
        // while the train stands at a station it would name the station it is at.
        StationDisplayData next = journey == null ? null
            : journey.nextStop().map(s -> wrapJourney(be, journey, s, StopVisitState.UPCOMING, destination)).orElse(null);
        List<StationDisplayData> stops = new ArrayList<>();
        if (journey != null) {
            for (StopSnapshot stop : journey.upcomingStops()) {
                stops.add(wrapJourney(be, journey, stop, StopVisitState.UPCOMING, destination));
            }
        }
        // Cancellation is a property of the CALL, not of the train snapshot. A
        // station/platform display has no trainSnapshot at all (CRN only fills it in
        // contraptionTick, and only for TRAIN_INFORMATION/NONE sources), so deriving
        // this from `train` marked every departure-board entry cancelled and the
        // renderer's cancelled filter then threw away everything outside the display
        // lead time.
        TrainDisplayData trainData = new TrainDisplayData(basic, stage, entry.isCancelled(), waiting, current, next, stops);
        return new DisplayEntry(trainData, new StationDisplayData(entry, entry.terminus()), entry.stopovers());
    }

    public static TrainDisplayData train(AdvancedDisplayBlockEntity be) {
        TrainSnapshot train = be.getTrain().orElse(null);
        if (train == null) return null;
        JourneySnapshot journey = be.getJourney().orElse(null);
        BasicTrainDisplayData basic = basic(train, null);
        TrainDisplayData.Stage stage = stage(be);
        boolean waiting = be.isWaitingAtStation();

        // Resolved once per update, then handed to every stop.
        RouteResolver.Route route = RouteResolver.resolve(journey, waiting);
        StationRef destination = RouteResolver.terminus(journey, route);

        StationDisplayData current = be.getCurrentStop().map(s -> wrapJourney(be, journey, s,
            waiting ? StopVisitState.CURRENT : StopVisitState.UPCOMING, destination)).orElse(null);
        // While the train STANDS at a station, be.getNextStop() is that same station
        // (getRemainingStops() starts at the current stop), so the following stop is
        // journey.nextStop(). While it is running, the current stop already IS the
        // station being approached, so be.getNextStop() is the right one.
        StationDisplayData next = waiting
            ? (journey == null ? null : journey.nextStop().map(s -> wrapJourney(be, journey, s, StopVisitState.UPCOMING, destination)).orElse(null))
            : be.getNextStop().map(s -> wrapJourney(be, journey, s, StopVisitState.UPCOMING, destination)).orElse(null);

        // Route list = the stops of THIS service, in order: the ones already left behind
        // first (so the renderer can grey them), then the ones still ahead. Sliced
        // straight out of journey.stops() between the route's bounds rather than
        // assembled from passedStops() + upcomingStops(), because the latter spans the
        // WHOLE run — a train in section B would still list section A as "passed" — and
        // wraps into the next period on a cyclic run.
        List<StationDisplayData> stops = new ArrayList<>();
        if (journey != null && !route.isEmpty()) {
            List<StopSnapshot> all = journey.stops();
            int currentIndex = journey.currentStopIndex();
            int end = Math.min(route.endIndex(), all.size() - 1);
            for (int i = Math.max(0, route.startIndex()); i <= end; i++) {
                StopVisitState vs;
                if (currentIndex < 0 || i > currentIndex) {
                    vs = StopVisitState.UPCOMING;
                } else if (i < currentIndex) {
                    vs = StopVisitState.PASSED;
                } else {
                    // CURRENT only while the train actually STANDS at the stop. Between
                    // stations the current stop is the one being approached, and it must
                    // read as UPCOMING — otherwise the two states render identically.
                    vs = waiting ? StopVisitState.CURRENT : StopVisitState.UPCOMING;
                }
                stops.add(wrapJourney(be, journey, all.get(i), vs, destination));
            }
        }
        return new TrainDisplayData(basic, stage, train.isCancelled(), waiting, current, next, stops);
    }

    private static BasicTrainDisplayData basic(TrainSnapshot train, BoardEntry entry) {
        // A station/platform display has no TrainSnapshot — CRN only fills that in
        // contraptionTick for TRAIN_INFORMATION/NONE display sources — so the line and
        // category shown in the train column have to come from the BoardEntry itself.
        // Falling back to the (always null) snapshot left the column empty and forced
        // the renderer to print "—" in place of every line name.
        if (entry == null) {
            return train == null
                ? new BasicTrainDisplayData("", "", DLColor.TRANSPARENT, "", DLColor.TRANSPARENT, true)
                : new BasicTrainDisplayData(train.trainName(), train.category().name(), train.category().color(),
                                            train.line().name(), train.line().color(), train.isCancelled());
        }
        LineRef line = entry.line() == null ? LineRef.NONE : entry.line();
        TrainCategoryRef category = entry.category() == null ? TrainCategoryRef.NONE : entry.category();
        String trainName = entry.trainName();
        if ((trainName == null || trainName.isBlank()) && train != null) {
            trainName = train.trainName();
        }
        return new BasicTrainDisplayData(trainName, category.name(), category.color(),
                                         line.name(), line.color(), entry.isCancelled());
    }

    private static TrainDisplayData.Stage stage(AdvancedDisplayBlockEntity be) {
        // Held as CRN's own enum rather than a pair of booleans, so each renderer's
        // own showDoNotBoardText setting still decides whether a soft terminus counts.
        return new TrainDisplayData.Stage(be.getStage());
    }

    /**
     * Wraps a journey stop as a legacy station view.
     *
     * <p>Train-information displays (front, side, VIS) never have a populated
     * {@code be.getStops()} — CRN only fills that via {@code AdvancedDisplayTarget.prepare},
     * which runs for the platform data source — so this synthetic branch is the
     * only path those displays use, and it has to derive everything from the
     * journey snapshot itself.</p>
     * <p>The visit state is supplied by the caller rather than derived here:
     * {@code JourneySnapshot.visitStateOf} reports CURRENT for the station the
     * train is <em>approaching</em> as well as the one it stands at, so deriving
     * it locally made "dwelling" and "travelling" render identically.</p>
     */
    private static StationDisplayData wrapJourney(AdvancedDisplayBlockEntity be, JourneySnapshot journey, StopSnapshot stop, StopVisitState visitState, StationRef destination) {
        BoardEntry entry = be.getStops().stream().filter(x -> x.entryIndex() == stop.entryIndex()).findFirst().orElse(null);
        if (entry == null) {
            TrainSnapshot train = be.getTrain().orElse(null);
            entry = new BoardEntry(
                train == null ? java.util.UUID.randomUUID() : train.trainId(),
                java.util.UUID.randomUUID(),
                train == null ? "" : train.trainName(),
                0,
                train == null ? LineRef.NONE : train.line(),
                train == null ? TrainCategoryRef.NONE : train.category(),
                LineRef.NONE,
                TrainCategoryRef.NONE,
                stop.station(),
                stop.scheduledStation(),
                stop.station(),
                stop.title(),
                destination == null ? stop.station() : destination,
                stop.scheduled(),
                stop.realtime(),
                de.mrjulsen.crn.core.train.ServiceState.IDLE,
                StopVisitState.UPCOMING,
                stop.entryIndex(),
                stop.sectionIndex(),
                false, false, false, List.of(), List.of());
        }
        StopVisitState state = visitState == null ? StopVisitState.UPCOMING : visitState;
        // CRN's own definition of "passengers must leave here", which already accounts
        // for a through-service section handing over to the next one without ending
        // the run. The board entry carries the same answer, but asking the journey
        // keeps this identical for borrowed and synthetic entries alike.
        boolean isLastStop = journey != null && journey.isTerminus(stop);
        // Per-section route colour: a journey that spans several sections (through
        // service) carries a different line colour per section, and the ribbon is
        // coloured accordingly instead of using one colour for the whole run.
        java.awt.Color sectionColor = null;
        if (journey != null) {
            sectionColor = journey.sectionOf(stop).map(SectionSnapshot::displayColor)
                .filter(c -> c != null && !c.isTransparent())
                .map(c -> new java.awt.Color(c.getRed(), c.getGreen(), c.getBlue()))
                .orElse(null);
        }
        return new StationDisplayData(entry, isLastStop, state, sectionColor);
    }
}
