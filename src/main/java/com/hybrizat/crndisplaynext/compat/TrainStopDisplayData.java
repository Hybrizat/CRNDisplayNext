package com.hybrizat.crndisplaynext.compat;

import de.mrjulsen.crn.api.core.CallDirection;
import de.mrjulsen.crn.api.core.snapshot.BoardEntry;
import de.mrjulsen.crn.core.timing.StopTimes;

/** Compatibility-only station-time accessors used by existing layout code. */
public final class TrainStopDisplayData {
    private final BoardEntry entry;
    public TrainStopDisplayData(BoardEntry entry) { this.entry = entry; }

    public StationInfo getScheduledStation() {
        return new StationInfo(entry.scheduledStation().displayName(), entry.scheduledStation().platform());
    }
    public StationInfo getRealTimeStation() {
        return new StationInfo(entry.station().displayName(), entry.station().platform());
    }
    public long getScheduledArrivalTime() { return entry.scheduled().arrival(); }
    public long getScheduledDepartureTime() { return entry.scheduled().departure(); }
    public long getRealtimeDepartureTime() { return entry.realtime().departure(); }
    public String getDestination() { return entry.destination().displayName(); }
    public String getTrainName() { return entry.trainName(); }

    public record StationInfo(String tagName, String platform) {
        public InfoView info() { return new InfoView(platform); }
        public String tagName() { return tagName; }
    }
    public record InfoView(String platform) {}
}

