package ie.dublinbus.live;

/** One bus on the map, flattened from a GTFS-Realtime VehiclePosition entity. */
public record VehiclePosition(
        String id,
        String vehicleId,
        String routeId,
        String routeShortName,
        String tripId,
        Integer directionId,
        double latitude,
        double longitude,
        Float bearing,
        Long timestamp) {
}
