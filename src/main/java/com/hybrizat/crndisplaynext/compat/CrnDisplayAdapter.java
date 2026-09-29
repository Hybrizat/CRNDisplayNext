package com.hybrizat.crndisplaynext.compat;

import de.mrjulsen.crn.api.core.RailwayBackendApi;
import de.mrjulsen.crn.api.core.snapshot.BoardEntry;
import de.mrjulsen.crn.api.core.snapshot.JourneySnapshot;
import de.mrjulsen.crn.api.core.snapshot.SectionSnapshot;
import de.mrjulsen.crn.api.core.snapshot.StopSnapshot;
import de.mrjulsen.crn.api.core.snapshot.TrainSnapshot;
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
        StationDisplayData next = be.getNextStop().map(s -> wrapJourney(be, journey, s, s == journey.currentStop().orElse(null))).orElse(null);
        List<StationDisplayData> stops = new ArrayList<>();
        if (journey != null) {
            for (StopSnapshot stop : journey.upcomingStops()) {
                stops.add(wrapJourney(be, journey, stop, false));
            }
        }
        TrainDisplayData trainData = new TrainDisplayData(basic, stage, train == null || train.isCancelled(), waiting, current, next, stops);
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
        StationDisplayData next = be.getNextStop().map(s -> wrapJourney(be, journey, s, false)).orElse(null);
        List<StationDisplayData> stops = new ArrayList<>();
        if (journey != null) {
            // Include passed stops (already visited), current stop, and upcoming stops
            // This provides the full route for passenger VIS displays
            stops.addAll(journey.passedStops().stream().map(s -> wrapJourney(be, journey, s, false)).toList());
            // Add current stop if train is at a station
            journey.currentStop().ifPresent(s -> stops.add(wrapJourney(be, journey, s, true)));
            // Add upcoming stops
            stops.addAll(journey.upcomingStops().stream().map(s -> wrapJourney(be, journey, s, false)).toList());
        }
        return new TrainDisplayData(basic, stage, train.isCancelled(), waiting, current, next, stops);
    }

    private static BasicTrainDisplayData basic(TrainSnapshot train, BoardEntry entry) {
        if (train == null) return new BasicTrainDisplayData("", "", DLColor.TRANSPARENT, "", DLColor.TRANSPARENT, true);
        String line = train.line().name();
        String cat = train.category().name();
        DLColor lc = train.line().color();
        DLColor cc = train.category().color();
        return new BasicTrainDisplayData(train.trainName(), cat, cc, line, lc, train.isCancelled());
    }

    private static TrainDisplayData.Stage stage(AdvancedDisplayBlockEntity be) {
        return new TrainDisplayData.Stage(be.getStage().isOutOfService(), be.getStage().shouldNotBoard(true));
    }

    private static StationDisplayData wrapJourney(AdvancedDisplayBlockEntity be, JourneySnapshot journey, StopSnapshot stop, boolean current) {
        BoardEntry entry = be.getStops().stream().filter(x -> x.entryIndex() == stop.entryIndex()).findFirst().orElse(null);
        if (entry == null) {
            TrainSnapshot train = be.getTrain().orElse(null);
            // Determine visit state based on stop index relative to current stop
            boolean isPassed = journey != null && stop.entryIndex() < journey.currentStopIndex();
            StopVisitState visitState = isPassed ? StopVisitState.PASSED : StopVisitState.UPCOMING;
            entry = new BoardEntry(
                train == null ? java.util.UUID.randomUUID() : train.trainId(),
                java.util.UUID.randomUUID(),
                train == null ? "" : train.trainName(),
                0,
                train == null ? de.mrjulsen.crn.api.core.ref.LineRef.NONE : train.line(),
                train == null ? de.mrjulsen.crn.api.core.ref.TrainCategoryRef.NONE : train.category(),
                de.mrjulsen.crn.api.core.ref.LineRef.NONE,
                de.mrjulsen.crn.api.core.ref.TrainCategoryRef.NONE,
                stop.station(),
                stop.scheduledStation(),
                stop.station(),
                stop.title(),
                stop.station(),
                stop.scheduled(),
                stop.realtime(),
                de.mrjulsen.crn.core.train.ServiceState.IDLE,
                visitState,
                stop.entryIndex(),
                stop.sectionIndex(),
                false, false, false, List.of(), List.of());
        }
        // For passed stops, mark as lastStop=false, for current/upcoming use the entry's terminus flag
        boolean isLastStop = entry.terminus() && !current && (journey != null && stop.entryIndex() >= journey.currentStopIndex());
        return new StationDisplayData(entry, isLastStop, false, false, technical(entry));
    }

    private static boolean technical(BoardEntry entry) {
        return entry.scheduled().isKnown() && entry.scheduled().stayDuration() <= 0;
    }

    private static boolean matches(String value, String filter) {
        return TrainUtils.stationMatches(value, filter);
    }
}
