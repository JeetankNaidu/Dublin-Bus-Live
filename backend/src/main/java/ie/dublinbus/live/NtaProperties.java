package ie.dublinbus.live;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the NTA GTFS-Realtime API and the static GTFS used to identify Dublin Bus routes.
 *
 * @param apiKey          NTA developer portal key, sent as the x-api-key header. Supply via NTA_API_KEY.
 * @param vehiclesUrl     GTFS-Realtime vehicle positions feed (protobuf).
 * @param refreshMs       how often to poll the feed. The NTA rate-limits keys, so keep this at 30s or more.
 * @param staticGtfsUrl   static GTFS zip whose routes.txt lists the Dublin Bus routes. Blank disables filtering.
 */
@ConfigurationProperties(prefix = "nta")
public record NtaProperties(String apiKey, String vehiclesUrl, long refreshMs, String staticGtfsUrl) {

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
