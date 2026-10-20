package ie.dublinbus.live;

import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * One-shot mode for static hosting: fetch the feed once, write the snapshot to a JSON file, and exit.
 * The GitHub Pages workflow runs this on a schedule. Enabled by setting app.export-file.
 * Exits non-zero if the fetch fails, so a bad run never replaces the last good published data.
 */
@Component
@ConditionalOnProperty("app.export-file")
public class SnapshotExportRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SnapshotExportRunner.class);

    private final RouteCatalog routeCatalog;
    private final VehiclePositionService service;
    private final ObjectMapper objectMapper;
    private final Path exportFile;
    private final ConfigurableApplicationContext context;

    public SnapshotExportRunner(RouteCatalog routeCatalog, VehiclePositionService service, ObjectMapper objectMapper,
            @Value("${app.export-file}") String exportFile, ConfigurableApplicationContext context) {
        this.routeCatalog = routeCatalog;
        this.service = service;
        this.objectMapper = objectMapper;
        this.exportFile = Path.of(exportFile);
        this.context = context;
    }

    @Override
    public void run(String... args) throws Exception {
        // Runners fire before ApplicationReadyEvent, so load the route catalog ourselves.
        routeCatalog.load();
        service.refresh();
        VehicleSnapshot snapshot = service.current();
        if (snapshot.error() != null) {
            throw new IllegalStateException("Vehicle feed fetch failed: " + snapshot.error());
        }
        if (exportFile.getParent() != null) {
            Files.createDirectories(exportFile.getParent());
        }
        objectMapper.writeValue(exportFile.toFile(), snapshot);
        log.info("Wrote {} vehicles to {}", snapshot.count(), exportFile);
        // The scheduler thread would otherwise keep the JVM alive.
        System.exit(SpringApplication.exit(context));
    }
}
