package com.hybrizat.crndisplaynext.api;

/**
 * Interface for the "Hide Technical Stops" feature.
 * A "technical stop" is defined as a stop with 0 scheduled dwell time —
 * typically used for turnaround maneuvers (e.g. A-B-C-||-C-B-A schedules).
 *
 * This interface mirrors CRN's pattern for display settings (e.g. IShowTrainMultipleTimes).
 * It should be implemented by block entity settings that support this feature.
 */
public interface IHideTechnicalStops {

    /**
     * @return true if stops with 0 dwell time should be hidden from the display.
     */
    boolean hideTechnicalStops();
}
