import Geolocation from '@react-native-community/geolocation';
import { PermissionsAndroid, Platform } from 'react-native';

export interface Location {
  latitude: number;
  longitude: number;
  address?: string;
  accuracy?: number;
  timestamp?: number;
}

export interface LocationError {
  code: number;
  message: string;
}

const LOCATION_PERMISSION_RATIONALE = {
  title: 'Location access',
  message:
    'Lendhand uses your location for KYC verification and borrower profile capture.',
  buttonNeutral: 'Ask Me Later',
  buttonNegative: 'Not Now',
  buttonPositive: 'Allow',
};

type PositionOptions = {
  enableHighAccuracy: boolean;
  timeout: number;
  maximumAge: number;
};

/** Fast network/cached fix, then GPS — physical devices often need this order. */
const LOCATION_ATTEMPTS: PositionOptions[] = [
  {
    enableHighAccuracy: false,
    timeout: 12_000,
    maximumAge: 300_000,
  },
  {
    enableHighAccuracy: true,
    timeout: 45_000,
    maximumAge: 120_000,
  },
  {
    enableHighAccuracy: false,
    timeout: 20_000,
    maximumAge: 600_000,
  },
];

class LocationService {
  private hasPermission = false;

  /** True when OS location permission is already granted. */
  async hasLocationPermission(): Promise<boolean> {
    if (Platform.OS === 'android') {
      const fine = await PermissionsAndroid.check(
        PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION,
      );
      if (fine) {
        this.hasPermission = true;
        return true;
      }
      const coarse = await PermissionsAndroid.check(
        PermissionsAndroid.PERMISSIONS.ACCESS_COARSE_LOCATION,
      );
      this.hasPermission = coarse;
      return coarse;
    }

    if (Platform.OS === 'ios') {
      return this.hasPermission;
    }

    return false;
  }

  /** Show the system location permission dialog when needed. */
  async requestLocationPermission(): Promise<boolean> {
    if (Platform.OS === 'android') {
      try {
        const fine = await PermissionsAndroid.request(
          PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION,
          LOCATION_PERMISSION_RATIONALE,
        );
        if (fine === PermissionsAndroid.RESULTS.GRANTED) {
          this.hasPermission = true;
          return true;
        }

        const coarse = await PermissionsAndroid.request(
          PermissionsAndroid.PERMISSIONS.ACCESS_COARSE_LOCATION,
          LOCATION_PERMISSION_RATIONALE,
        );
        this.hasPermission = coarse === PermissionsAndroid.RESULTS.GRANTED;
        return this.hasPermission;
      } catch (err) {
        console.error('Error requesting location permission:', err);
        this.hasPermission = false;
        return false;
      }
    }

    return new Promise((resolve) => {
      Geolocation.requestAuthorization(
        () => {
          this.hasPermission = true;
          resolve(true);
        },
        () => {
          this.hasPermission = false;
          resolve(false);
        },
      );
    });
  }

  /** Check first; prompt only when permission is missing. */
  async ensureLocationPermission(): Promise<boolean> {
    if (await this.hasLocationPermission()) {
      return true;
    }
    return this.requestLocationPermission();
  }

  private getPositionOnce(options: PositionOptions): Promise<Location> {
    return new Promise((resolve, reject) => {
      Geolocation.getCurrentPosition(
        (position) => {
          resolve({
            latitude: position.coords.latitude,
            longitude: position.coords.longitude,
            accuracy: position.coords.accuracy,
            timestamp: position.timestamp,
          });
        },
        (error) => {
          reject({
            code: error.code,
            message: error.message,
          } satisfies LocationError);
        },
        options,
      );
    });
  }

  private configureGeolocationAfterGrant(): void {
    if (Platform.OS === 'android' || Platform.OS === 'ios') {
      Geolocation.setRNConfiguration({
        skipPermissionRequests: true,
        authorizationLevel: 'whenInUse',
      });
    }
  }

  /** Physical devices often need a live GPS watch; one-shot getCurrentPosition can time out indoors. */
  private getPositionViaWatch(timeoutMs = 90_000): Promise<Location> {
    return new Promise((resolve, reject) => {
      let watchId: number | null = null;
      let settled = false;

      const finish = (action: () => void) => {
        if (settled) return;
        settled = true;
        if (watchId != null) {
          Geolocation.clearWatch(watchId);
        }
        clearTimeout(timer);
        action();
      };

      const timer = setTimeout(() => {
        finish(() => {
          reject({
            code: 3,
            message:
              'Location request timed out. Try again outdoors, or enter your address manually.',
          } satisfies LocationError);
        });
      }, timeoutMs);

      watchId = Geolocation.watchPosition(
        (position) => {
          finish(() => {
            resolve({
              latitude: position.coords.latitude,
              longitude: position.coords.longitude,
              accuracy: position.coords.accuracy,
              timestamp: position.timestamp,
            });
          });
        },
        (error) => {
          finish(() => {
            reject({
              code: error.code,
              message: error.message,
            } satisfies LocationError);
          });
        },
        {
          enableHighAccuracy: true,
          distanceFilter: 0,
          interval: 1000,
          fastestInterval: 500,
          maximumAge: 0,
        },
      );
    });
  }

  async getCurrentLocation(): Promise<Location> {
    const granted = await this.ensureLocationPermission();
    if (!granted) {
      throw {
        code: 1,
        message:
          'Location access denied. Allow location for Lendhand in your phone settings, or enter your address manually.',
      } satisfies LocationError;
    }

    this.configureGeolocationAfterGrant();

    let lastError: LocationError = {
      code: 3,
      message: 'Location request timed out. Try again or enter your address manually.',
    };

    for (const options of LOCATION_ATTEMPTS) {
      try {
        return await this.getPositionOnce(options);
      } catch (err) {
        const geoErr = err as LocationError;
        lastError = geoErr;
        if (geoErr.code === 1) {
          throw geoErr;
        }
      }
    }

    try {
      return await this.getPositionViaWatch();
    } catch (err) {
      const geoErr = err as LocationError;
      if (geoErr.code === 1) {
        throw geoErr;
      }
      throw lastError;
    }
  }

  watchLocation(
    onLocation: (location: Location) => void,
    onError: (error: LocationError) => void,
  ): number {
    return Geolocation.watchPosition(
      (position) => {
        onLocation({
          latitude: position.coords.latitude,
          longitude: position.coords.longitude,
          accuracy: position.coords.accuracy,
          timestamp: position.timestamp,
        });
      },
      (error) => {
        onError({
          code: error.code,
          message: error.message,
        });
      },
      {
        enableHighAccuracy: true,
        distanceFilter: 10,
        interval: 5000,
        fastestInterval: 2000,
      },
    );
  }

  stopWatchingLocation(watchId: number): void {
    Geolocation.clearWatch(watchId);
  }

  async getAddressFromCoordinates(
    latitude: number,
    longitude: number,
  ): Promise<string> {
    try {
      const response = await fetch(
        `https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=${latitude}&lon=${longitude}`,
        {
          headers: {
            'Accept-Language': 'en',
            'User-Agent': 'LendhandMobile/1.0',
          },
        },
      );
      const data = await response.json();
      if (data?.display_name) {
        return data.display_name;
      }
    } catch (error) {
      console.error('Nominatim reverse failed:', error);
    }

    try {
      const rev = await fetch(
        `https://photon.komoot.io/reverse?lon=${longitude}&lat=${latitude}`,
      );
      const geo = await rev.json();
      const feature = geo?.features?.[0];
      const props = feature?.properties;
      if (props) {
        const parts = [
          props.name,
          props.street,
          props.city,
          props.district,
          props.state,
          props.country,
        ].filter(Boolean);
        const label = [...new Set(parts.map((p) => String(p).trim()))]
          .filter(Boolean)
          .join(', ');
        if (label) return label;
      }
    } catch (error) {
      console.error('Photon reverse failed:', error);
    }

    return 'Current location';
  }

  /** Address suggestions while typing (Nominatim + Photon). */
  async searchAddressSuggestions(
    query: string,
    limit = 6,
  ): Promise<Array<{ label: string; latitude: number; longitude: number }>> {
    const q = String(query || '').trim();
    const max = Math.min(Math.max(limit, 1), 10);
    if (q.length < 2) return [];

    const suggestions: Array<{ label: string; latitude: number; longitude: number }> = [];
    const seen = new Set<string>();

    const add = (label: string, latitude: number, longitude: number) => {
      const text = String(label || '').trim();
      if (!text || !Number.isFinite(latitude) || !Number.isFinite(longitude)) {
        return;
      }
      const key = text.toLowerCase();
      if (seen.has(key)) return;
      seen.add(key);
      suggestions.push({ label: text, latitude, longitude });
    };

    try {
      const url = `https://nominatim.openstreetmap.org/search?q=${encodeURIComponent(q)}`
        + `&format=json&limit=${encodeURIComponent(String(max))}`;
      const response = await fetch(url, {
        headers: {
          Accept: 'application/json',
          'Accept-Language': 'en',
          'User-Agent': 'LendhandMobile/1.0',
        },
      });
      const data = await response.json();
      for (const row of data || []) {
        add(row.display_name, parseFloat(row.lat), parseFloat(row.lon));
        if (suggestions.length >= max) return suggestions;
      }
    } catch (error) {
      console.error('Nominatim search failed:', error);
    }

    if (suggestions.length >= max) return suggestions;

    try {
      const url = `https://photon.komoot.io/api/?q=${encodeURIComponent(q)}&limit=${max}`;
      const response = await fetch(url);
      const data = await response.json();
      for (const feat of data?.features || []) {
        const [lon, lat] = feat?.geometry?.coordinates || [];
        const props = feat?.properties || {};
        const parts = [
          props.name,
          props.housenumber && props.street
            ? `${props.housenumber} ${props.street}`
            : props.street,
          props.city || props.town || props.village,
          props.state,
          props.country,
        ]
          .map((p) => (p != null ? String(p).trim() : ''))
          .filter(Boolean);
        add([...new Set(parts)].join(', '), lat, lon);
        if (suggestions.length >= max) break;
      }
    } catch (error) {
      console.error('Photon search failed:', error);
    }

    return suggestions;
  }

  calculateDistance(
    lat1: number,
    lon1: number,
    lat2: number,
    lon2: number,
  ): number {
    const R = 6371e3;
    const φ1 = (lat1 * Math.PI) / 180;
    const φ2 = (lat2 * Math.PI) / 180;
    const Δφ = ((lat2 - lat1) * Math.PI) / 180;
    const Δλ = ((lon2 - lon1) * Math.PI) / 180;

    const a =
      Math.sin(Δφ / 2) * Math.sin(Δφ / 2) +
      Math.cos(φ1) * Math.cos(φ2) * Math.sin(Δλ / 2) * Math.sin(Δλ / 2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

    return R * c;
  }

  formatDistance(meters: number): string {
    if (meters < 1000) {
      return `${Math.round(meters)}m`;
    }
    return `${(meters / 1000).toFixed(1)}km`;
  }

  isWithinRadius(
    centerLat: number,
    centerLon: number,
    targetLat: number,
    targetLon: number,
    radiusMeters: number,
  ): boolean {
    const distance = this.calculateDistance(
      centerLat,
      centerLon,
      targetLat,
      targetLon,
    );
    return distance <= radiusMeters;
  }
}

export default new LocationService();
