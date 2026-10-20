package ie.dublinbus.live;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;

/**
 * Polls the NTA vehicles feed on a schedule and keeps the latest snapshot in memory,
 * so browser requests never hit the rate-limited NTA API directly.
 */
@Service
public class VehiclePositionService {

    private static final Logger log = LoggerFactory.getLogger(VehiclePositionService.class);

    private final NtaProperties properties;
    private final HttpClient httpClient;
    private final RouteCatalog routeCatalog;

    private volatile VehicleSnapshot snapshot = VehicleSnapshot.empty("Waiting for first fetch");

    public VehiclePositionService(NtaProperties properties, HttpClient httpClient, RouteCatalog routeCatalog) {
        this.properties = properties;
        this.httpClient = httpClient;
        this.routeCatalog = routeCatalog;
    }

    public VehicleSnapshot current() {
        return snapshot;
    }

    @Scheduled(initialDelay = 1000, fixedDelayString = "${nta.refresh-ms}")
    public void refresh() {
        if (!properties.hasApiKey()) {
            snapshot = VehicleSnapshot.empty("NTA_API_KEY is not set on the backend");
            return;
        }
        try {
            FeedMessage feed = fetchFeed();
            snapshot = toSnapshot(feed, Instant.now());
            log.debug("Fetched {} vehicles", snapshot.count());
        } catch (IOException | InterruptedException | RuntimeException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Vehicle feed fetch failed: {}", e.toString());
            // Keep showing the last good positions, but tell the UI they are stale.
            VehicleSnapshot last = snapshot;
            snapshot = new VehicleSnapshot(last.fetchedAt(), last.feedTimestamp(), last.filtered(),
                    last.count(), last.vehicles(), e.getMessage());
        }
    }

    private FeedMessage fetchFeed() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.vehiclesUrl()))
                .timeout(Duration.ofSeconds(20))
                .header("x-api-key", properties.apiKey())
                .header("Cache-Control", "no-cache")
                .GET()
                .build();
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            if (response.statusCode() != 200) {
                throw new IOException("NTA API returned HTTP " + response.statusCode());
            }
            return FeedMessage.parseFrom(body);
        }
    }

    VehicleSnapshot toSnapshot(FeedMessage feed, Instant fetchedAt) {
        boolean filter = routeCatalog.isLoaded();
        List<VehiclePosition> vehicles = new ArrayList<>();
        for (FeedEntity entity : feed.getEntityList()) {
            if (!entity.hasVehicle() || !entity.getVehicle().hasPosition()) {
                continue;
            }
            var v = entity.getVehicle();
            String routeId = v.getTrip().hasRouteId() ? v.getTrip().getRouteId() : null;
            if (filter && (routeId == null || !routeCatalog.isDublinBusRoute(routeId))) {
                continue;
            }
            vehicles.add(new VehiclePosition(
                    entity.getId(),
                    v.getVehicle().hasId() ? v.getVehicle().getId() : null,
                    routeId,
                    routeId == null ? null : routeCatalog.shortName(routeId).orElse(null),
                    v.getTrip().hasTripId() ? v.getTrip().getTripId() : null,
                    v.getTrip().hasDirectionId() ? v.getTrip().getDirectionId() : null,
                    v.getPosition().getLatitude(),
                    v.getPosition().getLongitude(),
                    v.getPosition().hasBearing() ? v.getPosition().getBearing() : null,
                    v.hasTimestamp() ? v.getTimestamp() : null));
        }
        Long feedTimestamp = feed.getHeader().hasTimestamp() ? feed.getHeader().getTimestamp() : null;
        return new VehicleSnapshot(fetchedAt, feedTimestamp, filter, vehicles.size(), List.copyOf(vehicles), null);
    }
}
