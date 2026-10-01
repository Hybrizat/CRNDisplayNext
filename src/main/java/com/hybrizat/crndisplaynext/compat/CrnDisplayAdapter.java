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
import de.mrjulsen.crn.util.TrainUtils;
import de.mrjulsen.mcdragonlib.util.DLColor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Bridges CRN 0.10.0 snapshots into the legacy display model without adding features. */
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
        StationDisplayData current = waiting ? new StationDisplayData(entry, entry.terminus(), entry.sectionChange(), false, technical(entry)) : null;
        // "next" must be the stop FOLLOWING the current one. be.getNextStop() is the
        // first of getRemainingStops(), which already starts at the current stop, so
        // while the train stands at a station it would name the station it is at.
        StationDisplayData next = journey == null ? null
            : journey.nextStop().map(s -> wrapJourney(be, journey, s, false)).orElse(null);
        List<StationDisplayData> stops = new ArrayList<>();
        if (journey != null) {
            for (StopSnapshot stop : journey.upcomingStops()) {
                stops.add(wrapJourney(be, journey, stop, false));
            }
        }
        // Cancellation is a property of the CALL, not of the train snapshot. A
        // station/platform display has no trainSnapshot at all (CRN only fills it in
        // contraptionTick, and only for TRAIN_INFORMATION/NONE sources), so deriving
        // this from `train` marked every departure-board entry cancelled and the
        // renderer's cancelled filter then threw away everything outside the display
        // lead time.
        TrainDisplayData trainData = new TrainDisplayData(basic, stage, entry.isCancelled(), waiting, current, next, stops);
        return new DisplayEntry(trainData, new StationDisplayData(entry, entry.terminus(), entry.sectionChange(), false, technical(entry)), entry.stopovers().stream().map(x -> x.displayName()).toList());
    }

    public static TrainDisplayData train(AdvancedDisplayBlockEntity be) {
        TrainSnapshot train = be.getTrain().orElse(null);
        if (train == null) return null;
        JourneySnapshot journey = be.getJourney().orElse(null);
        BasicTrainDisplayData basic = basic(train, null);
        TrainDisplayData.Stage stage = stage(be);
        boolean waiting = be.isWaitingAtStation();
        StationDisplayData current = be.getCurrentStop().map(s -> wrapJourney(be, journey, s, true)).orElse(null);
        // While the train STANDS at a station, be.getNextStop() is that same station
        // (getRemainingStops() starts at the current stop), so the following stop is
        // journey.nextStop(). While it is running, the current stop already IS the
        // station being approached, so be.getNextStop() is the right one.
        StationDisplayData next = waiting
            ? (journey == null ? null : journey.nextStop().map(s -> wrapJourney(be, journey, s, false)).orElse(null))
            : be.getNextStop().map(s -> wrapJourney(be, journey, s, false)).orElse(null);
        // Route list = already-passed stops, then the stop the train occupies, then the
        // rest up to the schedule's terminus.
        //
        // - passedStops() are prepended so the renderer can grey them out.
        // - upcomingStops() begins at currentStopIndex, so it already contains the
        //   current stop. While the train stands at a station it therefore supplied the
        //   SAME stop that was prepended separately, which is what duplicated the
        //   current station in the list.
        // - On a non-cyclic schedule upcomingStops() stops at the terminus by itself, so
        //   the route ends there without projecting the next timetable period. Only a
        //   genuinely cyclic run wraps, and then only over one period.
        List<StationDisplayData> stops = new ArrayList<>();
        if (journey != null) {
            for (StopSnapshot passed : journey.passedStops()) {
                stops.add(wrapJourney(be, journey, passed, false));
            }
            List<StopSnapshot> upcoming = journey.upcomingStops();
            for (int i = 0; i < upcoming.size(); i++) {
                stops.add(wrapJourney(be, journey, upcoming.get(i), waiting && i == 0));
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
        // Pass the hard (non-optional) terminus forms only: CRN evaluates
        // shouldNotBoard(includeSoftTerminus) against the user's setting, so baking
        // `true` in here made the "do not board" text appear even when it was off.
        return new TrainDisplayData.Stage(be.getStage().isOutOfService(), be.getStage().shouldNotBoard(false));
    }

    /**
     * Wraps a journey stop as a legacy station view.
     *
     * <p>Train-information displays (front, side, VIS) never have a populated
     * {@code be.getStops()} — CRN only fills that via {@code AdvancedDisplayTarget.prepare},
     * which runs for the platform data source — so this synthetic branch is the
     * only path those displays use, and it has to derive everything from the
     * journey snapshot itself.</p>
     */
    private static StationDisplayData wrapJourney(AdvancedDisplayBlockEntity be, JourneySnapshot journey, StopSnapshot stop, boolean current) {
        BoardEntry entry = be.getStops().stream().filter(x -> x.entryIndex() == stop.entryIndex()).findFirst().orElse(null);
        boolean borrowed = entry != null;
        if (entry == null) {
            TrainSnapshot train = be.getTrain().orElse(null);
            // The destination is where the service is BOUND FOR, not the station being
            // called at. Passing stop.station() here made every train display show the
            // station the train is at or approaching as its 行先/destination.
            StationRef destination = journey == null ? null
                : journey.operatingSection(false).map(SectionSnapshot::destination).orElse(null);
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
        // Ask the journey rather than comparing entryIndex (a schedule-entry index)
        // with currentStopIndex (a position in the filtered stop list). Comparing two
        // different index spaces reported passed stops as upcoming and never produced
        // CURRENT, which also put the VIS "you are here" marker on the run's first stop.
        StopVisitState visitState = current ? StopVisitState.CURRENT
            : (journey == null ? StopVisitState.UPCOMING : journey.visitStateOf(stop));
        // Only a borrowed entry knows whether it is a terminus; a synthetic one is not.
        boolean isLastStop = borrowed && entry.terminus() && !current;
        return new StationDisplayData(entry, isLastStop, false, false, technical(entry), visitState);
    }

    private static boolean technical(BoardEntry entry) {
        return entry.scheduled().isKnown() && entry.scheduled().stayDuration() <= 0;
    }

    private static boolean matches(String value, String filter) {
        return TrainUtils.stationMatches(value, filter);
    }
}
