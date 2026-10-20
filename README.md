# Dublin Bus Live

A map of live Dublin Bus vehicle positions from the National Transport Authority's GTFS-Realtime API.

- **backend/**: Java 21 + Spring Boot. Polls the NTA `Vehicles` feed every 30 seconds with your API key, keeps only Dublin Bus routes, and serves the result at `GET /api/vehicles`. The browser never sees the key and never calls the rate-limited NTA API directly.
- **frontend/**: Node.js + Vite + Leaflet. Shows each bus as a marker labelled with its route number, refreshes every 15 seconds, and lets you filter by route.

## Run it

You need Java 21, Maven 3.9+, Node.js 20+, and an NTA API key from <https://developer.nationaltransport.ie>.

```bash
# terminal 1: backend on http://localhost:8080
cd backend
export NTA_API_KEY=your-key-here     # never commit this
mvn spring-boot:run

# terminal 2: frontend on http://localhost:5173
cd frontend
npm install
npm run dev
```

Open <http://localhost:5173>. The Vite dev server proxies `/api` to the backend.

## How Dublin Bus is identified

The realtime feed covers every operator in Ireland. On startup the backend downloads the static Dublin Bus GTFS
(`nta.static-gtfs-url`) and reads `routes.txt` to get the Dublin Bus route IDs and their public numbers (46A, 39A, ...).
If that download fails, it logs a warning and shows every operator's vehicles instead, with the status bar saying so.

## Configuration

`backend/src/main/resources/application.properties`, each overridable with an env var or `--key=value`:

| Property | Default | Notes |
| --- | --- | --- |
| `nta.api-key` | `$NTA_API_KEY` | Sent as the `x-api-key` header |
| `nta.vehicles-url` | `https://api.nationaltransport.ie/gtfsr/v2/Vehicles` | Protobuf feed |
| `nta.refresh-ms` | `30000` | Keep at 30s or more to stay within NTA rate limits |
| `nta.static-gtfs-url` | TFI Dublin Bus GTFS zip | Blank disables Dublin Bus filtering |
| `app.allowed-origins` | `http://localhost:5173` | CORS for the frontend |

## Tests

```bash
cd backend && mvn test
```

The tests run the service against a fake NTA server, checking the API key header, Dublin Bus filtering,
the unfiltered fallback, and that the last good positions are kept when a fetch fails.

## Next ideas

- Service alerts from `https://api.nationaltransport.ie/gtfsr/v2/alerts`
- Route shapes and stops from the static GTFS
- Arrival predictions from the `TripUpdates` feed
