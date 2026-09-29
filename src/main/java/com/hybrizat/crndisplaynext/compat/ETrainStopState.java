package com.hybrizat.crndisplaynext.compat;

/** Compatibility-only view of the legacy display state states. */
public enum ETrainStopState {
    ARRIVAL,
    DEPARTURE;

    public static ETrainStopState beforeArrival(boolean beforeArrival) {
        return beforeArrival ? DEPARTURE : ARRIVAL;
    }
}
