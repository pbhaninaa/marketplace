/** Proxies address search / reverse geocode through React Native (WebView fetch to Nominatim is unreliable). */
export const NATIVE_GEOCODE_BRIDGE = `
(function () {
  if (window.__wheelHubGeocodeBridgeInstalled) return;
  window.__wheelHubGeocodeBridgeInstalled = true;

  var pending = Object.create(null);

  window.__wheelHubGeocodeResolve = function (id, payload) {
    var entry = pending[id];
    if (!entry) return;
    delete pending[id];
    entry.resolve(payload);
  };

  window.__wheelHubGeocodeReject = function (id, message) {
    var entry = pending[id];
    if (!entry) return;
    delete pending[id];
    entry.reject(new Error(message || 'Geocode failed'));
  };

  function postToNative(payload) {
    if (window.ReactNativeWebView && window.ReactNativeWebView.postMessage) {
      window.ReactNativeWebView.postMessage(payload);
      return true;
    }
    return false;
  }

  function registerRequest(id, resolve, reject, timeoutMs) {
    pending[id] = { resolve: resolve, reject: reject };
    setTimeout(function () {
      if (!pending[id]) return;
      delete pending[id];
      reject(new Error('Geocode request timed out'));
    }, timeoutMs || 15000);
  }

  window.__wheelHubNativeGeocodeSearch = function (query, limit) {
    return new Promise(function (resolve, reject) {
      var id = 'gc_' + Date.now() + '_' + Math.random().toString(36).slice(2);
      registerRequest(id, resolve, reject, 15000);
      var payload = JSON.stringify({
        type: 'GEOCODE_SEARCH',
        id: id,
        query: String(query || ''),
        limit: limit || 6,
      });
      if (postToNative(payload)) return;

      var attempts = 0;
      var timer = setInterval(function () {
        attempts += 1;
        if (postToNative(payload)) {
          clearInterval(timer);
          return;
        }
        if (attempts >= 100) {
          clearInterval(timer);
          delete pending[id];
          reject(new Error('Geocode bridge unavailable'));
        }
      }, 50);
    });
  };

  window.__wheelHubNativeGeocodeReverse = function (latitude, longitude) {
    return new Promise(function (resolve, reject) {
      var id = 'gcr_' + Date.now() + '_' + Math.random().toString(36).slice(2);
      registerRequest(
        id,
        function (name) {
          resolve(typeof name === 'string' && name.trim() ? name.trim() : 'Current location');
        },
        reject,
        15000,
      );
      var payload = JSON.stringify({
        type: 'GEOCODE_REVERSE',
        id: id,
        latitude: latitude,
        longitude: longitude,
      });
      if (postToNative(payload)) return;

      var attempts = 0;
      var timer = setInterval(function () {
        attempts += 1;
        if (postToNative(payload)) {
          clearInterval(timer);
          return;
        }
        if (attempts >= 100) {
          clearInterval(timer);
          delete pending[id];
          resolve('Current location');
        }
      }, 50);
    });
  };
})();
true;
`;

export type GeocodeSearchMessage = {
  type: 'GEOCODE_SEARCH';
  id: string;
  query: string;
  limit?: number;
};

export type GeocodeReverseMessage = {
  type: 'GEOCODE_REVERSE';
  id: string;
  latitude: number;
  longitude: number;
};

export type GeocodeBridgeMessage = GeocodeSearchMessage | GeocodeReverseMessage;

export function isGeocodeBridgeMessage(raw: unknown): raw is GeocodeBridgeMessage {
  if (!raw || typeof raw !== 'object') return false;
  const msg = raw as GeocodeBridgeMessage;
  if (msg.type === 'GEOCODE_SEARCH') {
    return typeof msg.id === 'string' && typeof msg.query === 'string';
  }
  if (msg.type === 'GEOCODE_REVERSE') {
    return (
      typeof msg.id === 'string' &&
      typeof msg.latitude === 'number' &&
      typeof msg.longitude === 'number'
    );
  }
  return false;
}

export function geocodeResolveScript(id: string, payload: unknown): string {
  const safeId = JSON.stringify(id);
  const safePayload = JSON.stringify(payload);
  return `window.__wheelHubGeocodeResolve(${safeId}, ${safePayload}); true;`;
}

export function geocodeRejectScript(id: string, message: string): string {
  const safeId = JSON.stringify(id);
  const safeMessage = JSON.stringify(message);
  return `window.__wheelHubGeocodeReject(${safeId}, ${safeMessage}); true;`;
}
