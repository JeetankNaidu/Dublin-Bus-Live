package ie.dublinbus.live;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Maps GTFS route_ids to their public route numbers (e.g. "46A") using the static Dublin Bus GTFS.
 * The realtime feed covers every operator in Ireland, so this is also what limits the map to Dublin Bus.
 * If the static feed can't be loaded, the catalog stays empty and all vehicles are shown unfiltered.
 */
@Component
public class RouteCatalog {

    private static final Logger log = LoggerFactory.getLogger(RouteCatalog.class);

    private final NtaProperties properties;
    private final HttpClient httpClient;
    private final Map<String, String> shortNamesByRouteId = new ConcurrentHashMap<>();

    public RouteCatalog(NtaProperties properties, HttpClient httpClient) {
        this.properties = properties;
        this.httpClient = httpClient;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void load() {
        String url = properties.staticGtfsUrl();
        if (url == null || url.isBlank()) {
            log.info("No static GTFS URL configured; showing all operators");
            return;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60)).GET().build();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                response.body().close();
                throw new IOException("HTTP " + response.statusCode());
            }
            try (InputStream body = response.body()) {
                loadFromZip(body);
            }
            log.info("Loaded {} Dublin Bus routes from static GTFS", shortNamesByRouteId.size());
        } catch (IOException | InterruptedException | RuntimeException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Could not load static GTFS from {} ({}); showing all operators", url, e.toString());
        }
    }

    void loadFromZip(InputStream zip) throws IOException {
        try (ZipInputStream in = new ZipInputStream(zip)) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (entry.getName().endsWith("routes.txt")) {
                    loadRoutesCsv(in);
                    return;
                }
            }
        }
        throw new IOException("routes.txt not found in static GTFS zip");
    }

    void loadRoutesCsv(InputStream csv) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(csv, StandardCharsets.UTF_8));
        String header = reader.readLine();
        if (header == null) {
            throw new IOException("routes.txt is empty");
        }
        List<String> columns = parseCsvLine(header.replace("﻿", ""));
        int idCol = columns.indexOf("route_id");
        int nameCol = columns.indexOf("route_short_name");
        if (idCol < 0 || nameCol < 0) {
            throw new IOException("routes.txt is missing route_id or route_short_name");
        }
        String line;
        while ((line = reader.readLine()) != null) {
            List<String> fields = parseCsvLine(line);
            if (fields.size() > Math.max(idCol, nameCol)) {
                shortNamesByRouteId.put(fields.get(idCol), fields.get(nameCol));
            }
        }
    }

    /** Minimal CSV split that handles quoted fields, which is all GTFS routes.txt needs. */
    static List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields;
    }

    public boolean isLoaded() {
        return !shortNamesByRouteId.isEmpty();
    }

    public boolean isDublinBusRoute(String routeId) {
        return shortNamesByRouteId.containsKey(routeId);
    }

    public Optional<String> shortName(String routeId) {
        return Optional.ofNullable(shortNamesByRouteId.get(routeId));
    }
}
