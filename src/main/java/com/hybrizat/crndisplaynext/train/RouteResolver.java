package com.hybrizat.crndisplaynext.train;

import de.mrjulsen.crn.api.core.ref.StationRef;
import de.mrjulsen.crn.api.core.snapshot.JourneySnapshot;
import de.mrjulsen.crn.api.core.snapshot.SectionSnapshot;
import de.mrjulsen.crn.api.core.snapshot.StopSnapshot;

import java.util.List;

/**
 * Works out which stretch of a train's run a display should show, honouring CRN's
 * 直通区段 (through-service) model.
 *
 * <p>CRN splits a run into sections, and a section whose
 * {@link SectionSnapshot#includesNextSectionStart()} is set carries its passengers into
 * the next section, which works under a different line or category. A display for the
 * train therefore has to cover the whole <b>through group</b>: from the first stop of
 * the section being operated to the last stop of the group.</p>
 *
 * <p>Deliberately derived from the stop list rather than from
 * {@link SectionSnapshot#destination()}. That field cannot be trusted here:</p>
 * <ul>
 *   <li>A run with no sections of its own still gets one synthetic
 *       {@code JourneySection.def()} whose {@code includesNextSectionStart} is
 *       <b>true</b>, so on a cyclic timetable {@code nextSectionOf} hands back that same
 *       section and CRN's {@code terminusStop} resolves to the section's FIRST stop.
 *       Reading it would name A on a plain A-B-C-D.</li>
 *   <li>It names the handover station (the next section's first stop) for a section that
 *       does carry onward, which is not where the train ends up either.</li>
 * </ul>
 * <p>Taking the terminus as the last stop of the ending section's own stop list gives
 * the same name CRN would resolve (both go through
 * {@code StopSnapshot.station}, which is the resolved realtime station) while being
 * immune to both cases.</p>
 */
public final class RouteResolver {

    private RouteResolver() {}

    /**
     * The half-open index range of {@code JourneySnapshot.stops()} this service covers.
     *
     * @param startIndex the first stop of the section being operated, or {@code 0} when
     *                   the run resolves no section
     * @param endIndex   the last stop of the through group, or the last stop of the run
     */
    public record Route(int startIndex, int endIndex) {
        public boolean isEmpty() { return endIndex < startIndex; }
        public int size() { return isEmpty() ? 0 : endIndex - startIndex + 1; }
    }

    /**
     * Resolves the displayed route.
     *
     * @param journey the run, or {@code null}
     * @param arrived whether the train has come to a stand at its current stop
     */
    public static Route resolve(JourneySnapshot journey, boolean arrived) {
        List<StopSnapshot> all = journey == null ? List.of() : journey.stops();
        if (all.isEmpty()) return new Route(0, -1);
        int last = all.size() - 1;

        SectionSnapshot operating = operatingSection(journey, arrived);
        SectionSnapshot ending = throughGroupEnd(journey, operating);
        if (operating == null || ending == null) return new Route(0, last);

        int start = indexOf(all, firstStopOf(operating));
        int end = indexOf(all, lastStopOf(ending));
        // An unusable or unresolvable section must not blank the display; fall back to
        // the whole run, which is what a section-less timetable resolves to anyway.
        if (start < 0 || end < 0 || end < start) return new Route(0, last);
        return new Route(start, end);
    }

    /**
     * The 行先 for this service: the station at the end of the route, falling back to
     * the last stop of the whole run when the route cannot be resolved.
     */
    public static StationRef terminus(JourneySnapshot journey, Route route) {
        List<StopSnapshot> all = journey == null ? List.of() : journey.stops();
        if (all.isEmpty()) return null;
        int end = route != null && !route.isEmpty() ? Math.min(route.endIndex(), all.size() - 1) : all.size() - 1;
        return all.get(Math.max(0, end)).station();
    }

    /**
     * The section the train should be described as working right now.
     *
     * <p>At a stop where one section hands over to the next, CRN keeps counting the
     * earlier section until the train has arrived, so a service is not renamed while
     * passengers are still boarding. That is exactly the behaviour wanted here.</p>
     */
    private static SectionSnapshot operatingSection(JourneySnapshot journey, boolean arrived) {
        if (journey == null) return null;
        return journey.operatingSection(arrived)
            .or(() -> journey.operatingSection(!arrived))
            .orElse(null);
    }

    /**
     * Walks a through-service chain from the operated section to the section that ends
     * the run: the first one that does not carry passengers onward, is not usable, or
     * has no following section.
     *
     * <p>The loop is bounded by the section count so a cyclic run whose only section
     * carries passengers back into its own first stop cannot spin.</p>
     */
    private static SectionSnapshot throughGroupEnd(JourneySnapshot journey, SectionSnapshot start) {
        if (journey == null || start == null) return start;
        SectionSnapshot current = start;
        for (int guard = 0; guard < journey.sections().size(); guard++) {
            if (!current.usable() || !journey.carriesPassengersOnward(current)) break;
            SectionSnapshot next = journey.nextSection(current).orElse(null);
            // nextSection wraps on a cyclic run, so the same section can come back.
            if (next == null || !next.usable() || next.index() == current.index()) break;
            current = next;
        }
        return current;
    }

    private static StopSnapshot firstStopOf(SectionSnapshot section) {
        List<StopSnapshot> stops = section.stops();
        return stops.isEmpty() ? null : stops.get(0);
    }

    private static StopSnapshot lastStopOf(SectionSnapshot section) {
        List<StopSnapshot> stops = section.stops();
        return stops.isEmpty() ? null : stops.get(stops.size() - 1);
    }

    /** Position of the stop with the given schedule entry, or {@code -1}. */
    private static int indexOf(List<StopSnapshot> all, StopSnapshot stop) {
        if (stop == null) return -1;
        int entry = stop.entryIndex();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).entryIndex() == entry) return i;
        }
        return -1;
    }
}