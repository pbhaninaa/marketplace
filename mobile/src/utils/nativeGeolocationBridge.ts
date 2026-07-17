/** Patches navigator.geolocation to use native GPS + OS permission dialog. */
export const NATIVE_GEOLOCATION_BRIDGE = `
(function () {
  if (window.__wheelHubGeoBridgeInstalled) {
    window.dispatchEvent(new Event('servicehub-native-ready'));
    return;
  }
  window.__wheelHubNativeShell = true;
  window.__wheelHubGeoBridgeInstalled = true;

  var pending = Object.create(null);
  var watchTimers = Object.create(null);
  var watchSeq = 1;
  var geoInFlight = false;
  var geoWaiters = [];

  window.__wheelHubNativeGeoResolve = function (id, coords) {
    var entry = pending[id];
    if (!entry) return;
    delete pending[id];
    var timestamp = Date.now();
    var resolvedName =
      typeof coords.locationName === 'string' && coords.locationName.trim()
        ? coords.locationName.trim()
        : 'Current location';
    var cacheEntry = {
      coords: {
        latitude: coords.latitude,
        longitude: coords.longitude,
        accuracy: coords.accuracy || 0,
      },
      locationName: resolvedName,
      timestamp: timestamp,
    };
    window.__wheelHubCachedPosition = cacheEntry;
    window.__wheelHubCachedLocation = {
      latitude: coords.latitude,
      longitude: coords.longitude,
      locationName: resolvedName,
    };
    window.dispatchEvent(new CustomEvent('servicehub-location-ready', { detail: cacheEntry }));
    entry.success({
      coords: {
        latitude: coords.latitude,
        longitude: coords.longitude,
        accuracy: coords.accuracy || 0,
        altitude: null,
        altitudeAccuracy: null,
        heading: null,
        speed: null,
      },
      timestamp: timestamp,
    });
  };

  window.__wheelHubNativeGeoReject = function (id, code, message) {
    var entry = pending[id];
    if (!entry) return;
    delete pending[id];
    var err = new Error(message || 'Location error');
    err.code = code || 1;
    entry.error(err);
  };

  function postToNative(payload) {
    if (window.ReactNativeWebView && window.ReactNativeWebView.postMessage) {
      window.ReactNativeWebView.postMessage(payload);
      return true;
    }
    return false;
  }

  function requestPosition(success, error) {
    if (geoInFlight) {
      geoWaiters.push({ success: success, error: error || function () {} });
      return;
    }
    geoInFlight = true;
    var id = 'geo_' + Date.now() + '_' + Math.random().toString(36).slice(2);
    pending[id] = {
      success: function (pos) {
        geoInFlight = false;
        success(pos);
        drainGeoWaiters();
      },
      error: function (err) {
        geoInFlight = false;
        (error || function () {})(err);
        drainGeoWaiters();
      },
    };
    var payload = JSON.stringify({ type: 'GEO_REQUEST', id: id });
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
        geoInFlight = false;
        var bridgeErr = new Error('Lendhand location bridge unavailable');
        bridgeErr.code = 2;
        (error || function () {})(bridgeErr);
        drainGeoWaiters();
      }
    }, 50);
  }

  function readCachedPosition(maxAgeMs) {
    var entry = window.__wheelHubCachedPosition;
    if (!entry || !entry.coords) return null;
    var age = Date.now() - (entry.timestamp || 0);
    if (age > (maxAgeMs || 300000)) return null;
    return {
      coords: {
        latitude: entry.coords.latitude,
        longitude: entry.coords.longitude,
        accuracy: entry.coords.accuracy || 0,
        altitude: null,
        altitudeAccuracy: null,
        heading: null,
        speed: null,
      },
      timestamp: entry.timestamp || Date.now(),
    };
  }

  function deliverCachedPosition(success, maxAgeMs) {
    var cached = readCachedPosition(maxAgeMs);
    if (!cached) return false;
    success(cached);
    return true;
  }

  function drainGeoWaiters() {
    if (geoInFlight || !geoWaiters.length) return;
    var next = geoWaiters.shift();
    requestPosition(next.success, next.error);
  }

  window.__wheelHubDrainGeoWaiters = drainGeoWaiters;

  var nativeGeolocation = {
    getCurrentPosition: function (success, error, options) {
      var maxAge = options && typeof options.maximumAge === 'number' ? options.maximumAge : 300000;
      if (deliverCachedPosition(success, maxAge)) return;
      requestPosition(success, error || function () {});
    },
    watchPosition: function (success, error, options) {
      var watchId = watchSeq++;
      var maxAge = options && typeof options.maximumAge === 'number' ? options.maximumAge : 300000;
      if (!deliverCachedPosition(success, maxAge)) {
        requestPosition(success, error || function () {});
      }
      watchTimers[watchId] = setInterval(function () {
        if (!geoInFlight) requestPosition(success, error || function () {});
      }, 30000);
      return watchId;
    },
    clearWatch: function (watchId) {
      if (watchTimers[watchId]) {
        clearInterval(watchTimers[watchId]);
        delete watchTimers[watchId];
      }
    },
  };

  try {
    Object.defineProperty(navigator, 'geolocation', {
      value: nativeGeolocation,
      configurable: true,
    });
  } catch (e) {
    navigator.geolocation = nativeGeolocation;
  }

  window.dispatchEvent(new Event('servicehub-native-ready'));
})();
true;
`;

export type GeoBridgeMessage = {
  type: 'GEO_REQUEST';
  id: string;
};

export function isGeoBridgeMessage(raw: unknown): raw is GeoBridgeMessage {
  if (!raw || typeof raw !== 'object') return false;
  const msg = raw as GeoBridgeMessage;
  return msg.type === 'GEO_REQUEST' && typeof msg.id === 'string' && /^geo_/.test(msg.id);
}

export function geoResolveScript(
  id: string,
  latitude: number,
  longitude: number,
  accuracy: number,
  locationName = 'Current location',
): string {
  const safeId = JSON.stringify(id);
  const safeName = JSON.stringify(locationName);
  return `window.__wheelHubNativeGeoResolve(${safeId}, { latitude: ${latitude}, longitude: ${longitude}, accuracy: ${accuracy}, locationName: ${safeName} }); true;`;
}

export function geoRejectScript(id: string, code: number, message: string): string {
  const safeId = JSON.stringify(id);
  const safeMessage = JSON.stringify(message);
  return `window.__wheelHubNativeGeoReject(${safeId}, ${code}, ${safeMessage}); true;`;
}
