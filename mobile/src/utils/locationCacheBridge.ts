export type ServiceHubCachedPosition = {
  latitude: number;
  longitude: number;
  accuracy: number;
  locationName: string;
};

/** Push a native GPS fix into the WebView for instant booking form autofill. */
export function buildLocationCacheScript(cache: ServiceHubCachedPosition): string {
  const payload = JSON.stringify({
    coords: {
      latitude: cache.latitude,
      longitude: cache.longitude,
      accuracy: cache.accuracy,
    },
    locationName: cache.locationName,
    timestamp: Date.now(),
  });

  return `
(function () {
  var entry = ${payload};
  window.__wheelHubCachedPosition = entry;
  window.__wheelHubCachedLocation = {
    latitude: entry.coords.latitude,
    longitude: entry.coords.longitude,
    locationName: entry.locationName,
  };
  window.dispatchEvent(new CustomEvent('servicehub-location-ready', { detail: entry }));
  if (typeof window.__wheelHubDrainGeoWaiters === 'function') {
    window.__wheelHubDrainGeoWaiters();
  }
})();
true;
`;
}
