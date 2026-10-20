package ie.dublinbus.live;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedHeader;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.Position;
import com.google.transit.realtime.GtfsRealtime.TripDescriptor;
import com.google.transit.realtime.GtfsRealtime.VehicleDescriptor;
import com.sun.net.httpserver.HttpServer;

class VehiclePositionServiceTest {

    private HttpServer server;
    private final AtomicReference<String> receivedKey = new AtomicReference<>();
    private volatile int status = 200;

    @BeforeEach
    void startFakeNta() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/gtfsr/v2/Vehicles", exchange -> {
            receivedKey.set(exchange.getRequestHeaders().getFirst("x-api-key"));
            byte[] body = status == 200 ? sampleFeed().toByteArray() : new byte[0];
            exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
    }

    @AfterEach
    void stopFakeNta() {
        server.stop(0);
    }

    private String vehiclesUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/gtfsr/v2/Vehicles";
    }

    private static FeedMessage sampleFeed() {
        return FeedMessage.newBuilder()
                .setHeader(FeedHeader.newBuilder().setGtfsRealtimeVersion("2.0").setTimestamp(1_760_000_000L))
                .addEntity(vehicle("V1", "DB_46A", 53.35f, -6.26f))
                .addEntity(vehicle("V2", "LUAS_GREEN", 53.30f, -6.21f))
                .addEntity(FeedEntity.newBuilder().setId("no-position"))
                .build();
    }

    private static FeedEntity vehicle(String id, String routeId, float lat, float lon) {
        return FeedEntity.newBuilder()
                .setId(id)
                .setVehicle(com.google.transit.realtime.GtfsRealtime.VehiclePosition.newBuilder()
                        .setTrip(TripDescriptor.newBuilder().setRouteId(routeId).setTripId("trip-" + id).setDirectionId(1))
                        .setVehicle(VehicleDescriptor.newBuilder().setId("bus-" + id))
                        .setPosition(Position.newBuilder().setLatitude(lat).setLongitude(lon).setBearing(90f))
                        .setTimestamp(1_760_000_000L))
                .build();
    }

    private static RouteCatalog catalogWith(NtaProperties props, String routesCsv) throws IOException {
        RouteCatalog catalog = new RouteCatalog(props, HttpClient.newHttpClient());
        if (routesCsv != null) {
            catalog.loadRoutesCsv(new ByteArrayInputStream(routesCsv.getBytes(StandardCharsets.UTF_8)));
        }
        return catalog;
    }

    @Test
    void fetchesWithApiKeyAndFiltersToDublinBusRoutes() throws IOException {
        NtaProperties props = new NtaProperties("test-key", vehiclesUrl(), 30000, "");
        RouteCatalog catalog = catalogWith(props,
                "﻿route_id,agency_id,route_short_name,route_long_name\nDB_46A,978,\"46A\",\"Phoenix Park - Dun Laoghaire\"\n");
        VehiclePositionService service = new VehiclePositionService(props, HttpClient.newHttpClient(), catalog);

        service.refresh();
        VehicleSnapshot snapshot = service.current();

        assertThat(receivedKey.get()).isEqualTo("test-key");
        assertThat(snapshot.error()).isNull();
        assertThat(snapshot.filtered()).isTrue();
        assertThat(snapshot.feedTimestamp()).isEqualTo(1_760_000_000L);
        assertThat(snapshot.vehicles()).singleElement().satisfies(v -> {
            assertThat(v.routeShortName()).isEqualTo("46A");
            assertThat(v.vehicleId()).isEqualTo("bus-V1");
            assertThat(v.directionId()).isEqualTo(1);
            assertThat(v.latitude()).isCloseTo(53.35, org.assertj.core.data.Offset.offset(1e-4));
        });
    }

    @Test
    void showsAllVehiclesWhenRouteCatalogIsUnavailable() throws IOException {
        NtaProperties props = new NtaProperties("test-key", vehiclesUrl(), 30000, "");
        VehiclePositionService service = new VehiclePositionService(props, HttpClient.newHttpClient(), catalogWith(props, null));

        service.refresh();

        assertThat(service.current().filtered()).isFalse();
        assertThat(service.current().count()).isEqualTo(2);
    }

    @Test
    void keepsLastPositionsAndReportsErrorWhenFetchFails() throws IOException {
        NtaProperties props = new NtaProperties("test-key", vehiclesUrl(), 30000, "");
        VehiclePositionService service = new VehiclePositionService(props, HttpClient.newHttpClient(), catalogWith(props, null));
        service.refresh();

        status = 429;
        service.refresh();

        assertThat(service.current().count()).isEqualTo(2);
        assertThat(service.current().error()).contains("429");
    }

    @Test
    void reportsMissingApiKey() throws IOException {
        NtaProperties props = new NtaProperties("", vehiclesUrl(), 30000, "");
        VehiclePositionService service = new VehiclePositionService(props, HttpClient.newHttpClient(), catalogWith(props, null));

        service.refresh();

        assertThat(receivedKey.get()).isNull();
        assertThat(service.current().error()).contains("NTA_API_KEY");
    }

    @Test
    void parsesQuotedCsvFields() {
        assertThat(RouteCatalog.parseCsvLine("a,\"b, c\",\"d \"\"e\"\"\""))
                .containsExactly("a", "b, c", "d \"e\"");
    }
}
