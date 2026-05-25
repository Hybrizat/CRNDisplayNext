package com.hybrizat.crndisplaynext.util;

import com.hybrizat.crndisplaynext.CRNDisplayNextMod;
import de.mrjulsen.crn.data.train.portable.StationDisplayData;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Filters "technical stops" (dwell time == 0) from station display lists.
 *
 * For a turnaround schedule like 001-002-003(0s)-||-003(5s)-002-001:
 *   - 003(0s): isNextSectionExcluded=true, dwell=0  → FILTER (turnaround point)
 *   - 003(5s): isPrevSectionExcluded=true, dwell=100 → KEEP   (real stop)
 *
 * Important: do NOT guard on isLastStop() or isNextSectionExcluded().
 * Turnaround stops ARE isNextSectionExcluded=true, so guarding on that
 * would protect exactly the entries we need to remove.
 *
 * A true terminus (end of line, no return) would normally have dwell > 0
 * (the driver needs time to switch ends). If someone sets dwell=0 at a
 * true terminus, this filter would also hide it — acceptable since the
 * feature is opt-in via checkbox.
 */
public final class StopFilter {

    /**
     * Stops with scheduled dwell <= this value (ticks) are considered technical.
     * 0 = only hide exact 0-tick stops.
     */
    public static final long TECHNICAL_STOP_THRESHOLD_TICKS = 0L;

    private StopFilter() {}

    public static List<StationDisplayData> filterDisplayData(List<StationDisplayData> data, boolean enabled) {
        if (!enabled || data == null || data.isEmpty()) {
            return data;
        }

        CRNDisplayNextMod.LOGGER.debug("[CRNExt] filterDisplayData: input size={}", data.size());

        List<StationDisplayData> result = data.stream()
                .filter(entry -> {
                    boolean technical = isTechnicalStop(entry);
                    if (technical) {
                        CRNDisplayNextMod.LOGGER.debug(
                                "[CRNExt] REMOVED: arrival={}, departure={}, isLastStop={}, isNextExcluded={}, isPrevExcluded={}",
                                entry.getStationData().getScheduledArrivalTime(),
                                entry.getStationData().getScheduledDepartureTime(),
                                entry.isLastStop(),
                                entry.isNextSectionExcluded(),
                                entry.isPrevSectionExcluded()
                        );
                    } else {
                        CRNDisplayNextMod.LOGGER.debug(
                                "[CRNExt] KEPT:    arrival={}, departure={}, isLastStop={}, isNextExcluded={}, isPrevExcluded={}",
                                entry.getStationData().getScheduledArrivalTime(),
                                entry.getStationData().getScheduledDepartureTime(),
                                entry.isLastStop(),
                                entry.isNextSectionExcluded(),
                                entry.isPrevSectionExcluded()
                        );
                    }
                    return !technical;
                })
                .collect(Collectors.toList());

        CRNDisplayNextMod.LOGGER.debug("[CRNExt] filterDisplayData: output size={}", result.size());
        return result;
    }

    public static boolean isTechnicalStop(StationDisplayData entry) {
        try {
            long arrival   = entry.getStationData().getScheduledArrivalTime();
            long departure = entry.getStationData().getScheduledDepartureTime();
            long dwell     = departure - arrival;

            // Filter ALL dwell=0 entries — no exceptions.
            //
            // Do NOT guard on isLastStop() or isNextSectionExcluded():
            // turnaround stops (the ones we want to remove) are exactly
            // the entries where isNextSectionExcluded=true AND dwell=0.
            // Guarding on isNextSectionExcluded would protect them.
            return dwell <= TECHNICAL_STOP_THRESHOLD_TICKS;

        } catch (Exception e) {
            CRNDisplayNextMod.LOGGER.warn("[CRNExt] Could not determine dwell time, keeping entry: {}", e.getMessage());
            return false;
        }
    }
}