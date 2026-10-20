import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import './style.css';

// With the Spring Boot backend, /api/vehicles refreshes every 30s. The GitHub Pages build instead
// bakes in a vehicles.json snapshot that a scheduled workflow republishes every few minutes.
const DATA_URL = import.meta.env.VITE_DATA_URL ?? '/api/vehicles';
const POLL_MS = 15000;
const DUBLIN = [53.3498, -6.2603];

const map = L.map('map').setView(DUBLIN, 12);
L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
  maxZoom: 19,
  attribution: '&copy; OpenStreetMap contributors · Bus data: National Transport Authority',
}).addTo(map);

const statusEl = document.getElementById('status');
const filterEl = document.getElementById('route-filter');
const markers = new Map(); // entity id -> Leaflet marker
let lastSnapshot = null;

function routeLabel(v) {
  return v.routeShortName ?? v.routeId ?? '?';
}

function iconFor(v) {
  return L.divIcon({
    className: '',
    html: `<div class="bus-icon">${escapeHtml(routeLabel(v))}</div>`,
    iconSize: null,
    iconAnchor: [13, 10],
  });
}

function popupFor(v) {
  const seen = v.timestamp ? new Date(v.timestamp * 1000).toLocaleTimeString() : 'unknown';
  return `
    <strong>Route ${escapeHtml(routeLabel(v))}</strong><br>
    Vehicle: ${escapeHtml(v.vehicleId ?? v.id)}<br>
    Direction: ${v.directionId ?? 'n/a'}<br>
    Last reported: ${seen}`;
}

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
}

function matchesFilter(v) {
  const q = filterEl.value.trim().toLowerCase();
  return !q || routeLabel(v).toLowerCase() === q;
}

function render(snapshot) {
  const visible = snapshot.vehicles.filter(matchesFilter);
  const seen = new Set();
  for (const v of visible) {
    seen.add(v.id);
    const existing = markers.get(v.id);
    if (existing) {
      existing.setLatLng([v.latitude, v.longitude]);
      existing.setIcon(iconFor(v));
      existing.setPopupContent(popupFor(v));
    } else {
      markers.set(v.id, L.marker([v.latitude, v.longitude], { icon: iconFor(v) }).bindPopup(popupFor(v)).addTo(map));
    }
  }
  for (const [id, marker] of markers) {
    if (!seen.has(id)) {
      marker.remove();
      markers.delete(id);
    }
  }

  const when = snapshot.fetchedAt ? new Date(snapshot.fetchedAt).toLocaleTimeString() : 'never';
  const scope = snapshot.filtered ? 'Dublin Bus' : 'all operators';
  let text = `${visible.length} of ${snapshot.count} buses (${scope}) · updated ${when}`;
  if (snapshot.error) text = `${snapshot.error}${snapshot.fetchedAt ? ` · showing data from ${when}` : ''}`;
  statusEl.textContent = text;
  statusEl.classList.toggle('error', Boolean(snapshot.error));
}

async function poll() {
  try {
    const res = await fetch(DATA_URL, { cache: 'no-store' });
    if (!res.ok) throw new Error(`Backend returned HTTP ${res.status}`);
    lastSnapshot = await res.json();
    render(lastSnapshot);
  } catch (err) {
    statusEl.textContent = `Can't reach the backend: ${err.message}`;
    statusEl.classList.add('error');
  }
}

filterEl.addEventListener('input', () => lastSnapshot && render(lastSnapshot));
poll();
setInterval(poll, POLL_MS);
