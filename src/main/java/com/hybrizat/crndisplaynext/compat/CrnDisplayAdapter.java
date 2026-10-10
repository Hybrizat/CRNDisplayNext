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
        StationDisplayData current = waiting ? new StationDisplayData(entry, entry.terminus(), entry.sectionChange(), false) : null;
        // "next" must be the stop FOLLOWING the current one. be.getNextStop() is the
        // first of getRemainingStops(), which already starts at the current stop, so
        // while the train stands at a station it would name the station it is at.
        StationDisplayData next = journey == null ? null
            : journey.nextStop().map(s -> wrapJourney(be, journey, s, StopVisitState.UPCOMING)).orElse(null);
        List<StationDisplayData> stops = new ArrayList<>();
        if (journey != null) {
            for (StopSnapshot stop : journey.upcomingStops()) {
                stops.add(wrapJourney(be, journey, stop, StopVisitState.UPCOMING));
            }
        }
        // Cancellation is a property of the CALL, not of the train snapshot. A
        // station/platform display has no trainSnapshot at all (CRN only fills it in
        // contraptionTick, and only for TRAIN_INFORMATION/NONE sources), so deriving
        // this from `train` marked every departure-board entry cancelled and the
        // renderer's cancelled filter then threw away everything outside the display
        // lead time.
        TrainDisplayData trainData = new TrainDisplayData(basic, stage, entry.isCancelled(), waiting, current, next, stops);
        return new DisplayEntry(trainData, new StationDisplayData(entry, entry.terminus(), entry.sectionChange(), false), entry.stopovers().stream().map(x -> x.displayName()).toList());
    }

    public static TrainDisplayData train(AdvancedDisplayBlockEntity be) {
        TrainSnapshot train = be.getTrain().orElse(null);
        if (train == null) return null;
        JourneySnapshot journey = be.getJourney().orElse(null);
        BasicTrainDisplayData basic = basic(train, null);
        TrainDisplayData.Stage stage = stage(be);
        boolean waiting = be.isWaitingAtStation();
        StationDisplayData current = be.getCurrentStop().map(s -> wrapJourney(be, journey, s,
            waiting ? StopVisitState.CURRENT : StopVisitState.UPCOMING)).orElse(null);
        // While the train STANDS at a station, be.getNextStop() is that same station
        // (getRemainingStops() starts at the current stop), so the following stop is
        // journey.nextStop(). While it is running, the current stop already IS the
        // station being approached, so be.getNextStop() is the right one.
        StationDisplayData next = waiting
            ? (journey == null ? null : journey.nextStop().map(s -> wrapJourney(be, journey, s, StopVisitState.UPCOMING)).orElse(null))
            : be.getNextStop().map(s -> wrapJourney(be, journey, s, StopVisitState.UPCOMING)).orElse(null);
        // Route list = already-passed stops (greyed), then the forward stops up to the
        // end of THIS timetable pass.
        //
        // - passedStops() are prepended so the renderer can grey them out.
        // - upcomingStops() begins at currentStopIndex, so it already contains the
        //   station the train is at (dwelling) or approaching (running). Adding
        //   currentStop() separately duplicated that station, so it is not added twice.
        // - upcomingStops() returns a WHOLE CYCLE on a cyclic run ("wraps around and
        //   covers one whole cycle"), which appended the next period's stations past
        //   the terminus. Cut it to the remainder of the current pass.
        List<StationDisplayData> stops = new ArrayList<>();
        if (journey != null) {
            for (StopSnapshot passed : journey.passedStops()) {
                stops.add(wrapJourney(be, journey, passed, StopVisitState.PASSED));
            }
            List<StopSnapshot> upcoming = journey.upcomingStops();
            int remaining = Math.max(0, journey.stops().size() - journey.currentStopIndex());
            int limit = Math.min(upcoming.size(), remaining);
            for (int i = 0; i < limit; i++) {
                // CURRENT only while the train actually STANDS at the stop. Between
                // stations the first stop is the one being approached, and it must
                // read as UPCOMING — otherwise the two states render identically.
                StopVisitState vs = (waiting && i == 0)
                    ? StopVisitState.CURRENT
                    : StopVisitState.UPCOMING;
                stops.add(wrapJourney(be, journey, upcoming.get(i), vs));
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
     * <p>The visit state is supplied by the caller rather than derived here:
     * {@code JourneySnapshot.visitStateOf} reports CURRENT for the station the
     * train is <em>approaching</em> as well as the one it stands at, so deriving
     * it locally made "dwelling" and "travelling" render identically.</p>
     */
    private static StationDisplayData wrapJourney(AdvancedDisplayBlockEntity be, JourneySnapshot journey, StopSnapshot stop, StopVisitState visitState) {
        BoardEntry entry = be.getStops().stream().filter(x -> x.entryIndex() == stop.entryIndex()).findFirst().orElse(null);
        boolean borrowed = entry != null;
        if (entry == null) {
            TrainSnapshot train = be.getTrain().orElse(null);
            // Destination shown for a train is the endpoint of the SECTION the train is
            // operating — not the last stop of the whole timetable. A-B-C-D rendered as
            // one section names D correctly, while a multi-section journey must name the
            // current section's endpoint.
            StationRef destination = displayTerminus(journey, stop);
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
        // Only a borrowed entry knows whether it is a terminus; a synthetic one is not.
        boolean isLastStop = borrowed && entry.terminus() && state != StopVisitState.CURRENT;
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
        return new StationDisplayData(entry, isLastStop, false, false, state, sectionColor);
    }

    /**
     * Destination shown by train displays (front, side, VIS). Three cases, in order:
     *
     * <ol>
     *   <li><b>直通区段</b>: when the operating section belongs to a through-service
     *       group, the group's final endpoint.</li>
     *   <li><b>Sectioned timetable</b>: the endpoint of the section being operated —
     *       NOT the whole timetable's last stop.</li>
     *   <li><b>Section-less timetable</b> (the default A-B-C-D): the last stop of the
     *       run.</li>
     * </ol>
     *
     * <p>Section resolution uses {@code operatingSection(true)}, which keeps the
     * section containing where the train is; {@code operatingSection(false)} hands back
     * the previous section whenever the current stop is a section start, which at the
     * journey's first stop resolves to the previous run's section and made A-B-C-D
     * display its ORIGIN as the destination.</p>
     */
    private static StationRef displayTerminus(JourneySnapshot journey, StopSnapshot stop) {
        if (journey == null) return null;
        // 直通区段 (through-service group) override: when the operating section belongs
        // to a through-service group, the displayed destination becomes that group's
        // final endpoint instead of the plain section endpoint. The grouping model is
        // not finalised yet, so this hook currently yields null.
        StationRef throughGroupEnd = throughGroupTerminus(journey, stop);
        if (throughGroupEnd != null) return throughGroupEnd;

        // Section endpoint: the section containing where the train is.
        // operatingSection(true) keeps that section and only falls back to the previous
        // one when it is unusable; (false) hands back the previous section whenever the
        // current stop is a section start, which at the journey's first stop resolves to
        // the previous run's section and named the ORIGIN as the destination.
        StationRef bySection = journey.operatingSection(true)
            .map(SectionSnapshot::destination).orElse(null);
        if (bySection == null) {
            bySection = journey.operatingSection(false).map(SectionSnapshot::destination).orElse(null);
        }
        if (bySection != null) return bySection;

        // Section-less timetable (the default A-B-C-D case): no section resolves at all,
        // so name the LAST stop of the run. Falling back to the stop being wrapped named
        // the stop itself, which made A-B-C-D display A.
        List<StopSnapshot> all = journey.stops();
        return all.isEmpty() ? null : all.get(all.size() - 1).station();
    }

    /**
     * Reserved for the 直通区段 (through-service) feature.
     *
     * <p>A through-service group is a logical grouping of consecutive CRN sections that
     * the same train runs through without ending its service. When the operating
     * section is inside such a group, displays must name the group's FINAL endpoint —
     * i.e. the {@code destination} of the group's last section, skipping blacklisted
     * stations and falling back to the last visible one — rather than the endpoint of
     * the individual section.</p>
     *
     * <p>Return {@code null} here to show the plain section endpoint. Implemented
     * once the grouping model (section index ranges, blacklisting rules, interaction
     * with {@code includesNextSection_start}) is settled.</p>
     */
    private static StationRef throughGroupTerminus(JourneySnapshot journey, StopSnapshot stop) {
        return null;
    }

    private static boolean matches(String value, String filter) {
        return TrainUtils.stationMatches(value, filter);
    }
}
