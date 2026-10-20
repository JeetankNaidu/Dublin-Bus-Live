package ie.dublinbus.live;

import java.time.Instant;
import java.util.List;

/**
 * What /api/vehicles returns: the latest positions plus enough status for the UI to explain gaps.
 *
 * @param fetchedAt     when the backend last fetched the feed successfully (null if never)
 * @param feedTimestamp the feed header's own timestamp, in epoch seconds
 * @param filtered      true when only Dublin Bus routes are included
 * @param error         the most recent fetch error, or null if the last fetch succeeded
 */
public record VehicleSnapshot(
        Instant fetchedAt,
        Long feedTimestamp,
        boolean filtered,
        int count,
        List<VehiclePosition> vehicles,
        String error) {

    public static VehicleSnapshot empty(String error) {
        return new VehicleSnapshot(null, null, false, 0, List.of(), error);
    }
}
