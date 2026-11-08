<script setup>
/**
 * Pick one point on a map: tap/click the map or drag the pin, or use the device's location.
 * v-model is { lat, lng } or null. Used for a seller's pickup point and a buyer's delivery point.
 */
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';

const props = defineProps({
  modelValue: { type: Object, default: null },
  height: { type: String, default: '280px' },
  /** Shown above the map. */
  hint: { type: String, default: 'Tap the map or drag the pin to the exact spot.' },
  /** Try the device location automatically when there is no point yet. */
  autoLocate: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
});
const emit = defineEmits(['update:modelValue']);

// South Africa, whole country, when there is no point yet.
const DEFAULT_CENTER = [-29.0, 24.5];
const DEFAULT_ZOOM = 5;
const PIN_ZOOM = 15;

const LAYERS = {
  streets: {
    url: 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
    attribution: '&copy; OpenStreetMap contributors',
  },
  satellite: {
    url: 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
    attribution: 'Tiles &copy; Esri',
  },
};

const mapEl = ref(null);
const layerKind = ref('streets');
const locating = ref(false);
const locateError = ref('');
let map = null;
let marker = null;
let tiles = null;

const pinIcon = L.divIcon({
  className: 'map-pin-icon',
  html: '<span class="map-pin-icon__dot"></span>',
  iconSize: [28, 28],
  iconAnchor: [14, 28],
});

function round(n) {
  return Math.round(n * 1e6) / 1e6;
}

function setPoint(lat, lng, { pan = true } = {}) {
  const point = { lat: round(lat), lng: round(lng) };
  emit('update:modelValue', point);
  placeMarker(point, pan);
}

function placeMarker(point, pan) {
  if (!map || !point) return;
  const latlng = [point.lat, point.lng];
  if (!marker) {
    marker = L.marker(latlng, { icon: pinIcon, draggable: !props.disabled, keyboard: true, title: 'Selected location' }).addTo(map);
    marker.on('dragend', () => {
      const p = marker.getLatLng();
      setPoint(p.lat, p.lng, { pan: false });
    });
  } else {
    marker.setLatLng(latlng);
  }
  if (pan) map.setView(latlng, Math.max(map.getZoom(), PIN_ZOOM));
}

function setLayer(kind) {
  layerKind.value = kind;
  if (!map) return;
  if (tiles) map.removeLayer(tiles);
  const cfg = LAYERS[kind];
  tiles = L.tileLayer(cfg.url, { attribution: cfg.attribution, maxZoom: 19 }).addTo(map);
}

function useMyLocation() {
  locateError.value = '';
  if (!navigator.geolocation) {
    locateError.value = 'Your device can’t share its location. Tap the map instead.';
    return;
  }
  locating.value = true;
  navigator.geolocation.getCurrentPosition(
    (pos) => {
      locating.value = false;
      setPoint(pos.coords.latitude, pos.coords.longitude);
    },
    () => {
      locating.value = false;
      locateError.value = 'Couldn’t get your location. Allow location access, or tap the map instead.';
    },
    { enableHighAccuracy: true, timeout: 10000, maximumAge: 60000 },
  );
}

let resizeObserver = null;
onMounted(() => {
  if (!mapEl.value) return;
  try {
    const start = props.modelValue;
    map = L.map(mapEl.value, { scrollWheelZoom: false }).setView(
      start ? [start.lat, start.lng] : DEFAULT_CENTER,
      start ? PIN_ZOOM : DEFAULT_ZOOM,
    );
    setLayer(layerKind.value);
    if (start) placeMarker(start, false);
    map.on('click', (e) => {
      if (!props.disabled) setPoint(e.latlng.lat, e.latlng.lng, { pan: false });
    });
    // Maps inside panels that change size need to re-measure.
    if (typeof ResizeObserver !== 'undefined') {
      resizeObserver = new ResizeObserver(() => map?.invalidateSize());
      resizeObserver.observe(mapEl.value);
    }
    if (!start && props.autoLocate) useMyLocation();
  } catch {
    // No map (e.g. tests or blocked tiles): the location button still works.
  }
});

onBeforeUnmount(() => {
  resizeObserver?.disconnect();
  map?.remove();
  map = null;
  marker = null;
});

watch(
  () => props.modelValue,
  (p) => {
    if (!p) {
      if (marker && map) map.removeLayer(marker);
      marker = null;
      return;
    }
    const cur = marker?.getLatLng();
    if (!cur || round(cur.lat) !== p.lat || round(cur.lng) !== p.lng) placeMarker(p, true);
  },
);
</script>

<template>
  <div class="map-pin-picker">
    <div class="map-pin-picker__bar">
      <p class="map-pin-picker__hint">{{ hint }}</p>
      <div class="map-pin-picker__actions">
        <div class="map-pin-picker__layers" role="group" aria-label="Map style">
          <button
            type="button"
            :class="{ active: layerKind === 'streets' }"
            :aria-pressed="layerKind === 'streets'"
            @click="setLayer('streets')"
          >
            Map
          </button>
          <button
            type="button"
            :class="{ active: layerKind === 'satellite' }"
            :aria-pressed="layerKind === 'satellite'"
            @click="setLayer('satellite')"
          >
            Satellite
          </button>
        </div>
        <button type="button" class="btn btn-ghost btn-xs" :disabled="locating || disabled" @click="useMyLocation">
          <span class="material-icons" aria-hidden="true">my_location</span>
          {{ locating ? 'Finding you…' : 'Use my current location' }}
        </button>
      </div>
    </div>
    <div
      ref="mapEl"
      class="map-pin-picker__map"
      :style="{ height }"
      role="application"
      aria-label="Map. Tap to place the pin."
    ></div>
    <p v-if="locateError" class="map-pin-picker__error" role="alert">{{ locateError }}</p>
    <p v-else-if="modelValue" class="map-pin-picker__coords">
      <span class="material-icons" aria-hidden="true">place</span>
      Pin set at {{ modelValue.lat.toFixed(5) }}, {{ modelValue.lng.toFixed(5) }}
    </p>
    <p v-else class="map-pin-picker__coords muted">No location chosen yet.</p>
  </div>
</template>

<style>
/* Not scoped: Leaflet creates the pin element itself. */
.map-pin-icon {
  background: none;
  border: none;
}

.map-pin-icon__dot {
  display: block;
  width: 22px;
  height: 22px;
  margin: 0 auto;
  border-radius: 50% 50% 50% 0;
  background: var(--color-sage, #3d7a66);
  border: 3px solid #fff;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.35);
  transform: rotate(-45deg);
}
</style>

<style scoped>
.map-pin-picker {
  display: grid;
  gap: 0.5rem;
}

.map-pin-picker__bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}

.map-pin-picker__hint {
  margin: 0;
  font-size: 0.85rem;
  color: var(--color-text-secondary);
}

.map-pin-picker__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
}

.map-pin-picker__actions .btn {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
}

.map-pin-picker__actions .material-icons {
  font-size: 1rem;
}

.map-pin-picker__layers {
  display: inline-flex;
  border: 1px solid var(--color-border-strong);
  border-radius: 999px;
  overflow: hidden;
}

.map-pin-picker__layers button {
  border: none;
  background: var(--color-surface-elevated);
  color: var(--color-text-secondary);
  padding: 0.3rem 0.75rem;
  font: inherit;
  font-size: 0.8rem;
  cursor: pointer;
}

.map-pin-picker__layers button.active {
  background: var(--color-sage-soft);
  color: var(--color-canopy);
  font-weight: 600;
}

.map-pin-picker__map {
  width: 100%;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-md, 12px);
  overflow: hidden;
  z-index: 0;
}

.map-pin-picker__coords,
.map-pin-picker__error {
  display: flex;
  align-items: center;
  gap: 0.3rem;
  margin: 0;
  font-size: 0.82rem;
  color: var(--color-text-secondary);
}

.map-pin-picker__coords .material-icons {
  font-size: 1rem;
  color: var(--color-sage);
}

.map-pin-picker__error {
  color: var(--color-danger-text);
}
</style>
