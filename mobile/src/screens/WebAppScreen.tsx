import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  ActivityIndicator,
  AppState,
  Linking,
  Platform,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { WebView } from 'react-native-webview';
import type { ShouldStartLoadRequest } from 'react-native-webview/lib/WebViewTypes';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import AppBottomNav from '../components/AppBottomNav';
import { MARKETPLACE_PRIMARY } from '../config/branding';
import { CONFIG } from '../config';
import {
  activeTabId,
  tabMatchesPath,
  tabsForRole,
  type BottomNavTab,
} from '../navigation/bottomNavConfig';
import { shouldShowBottomNav } from '../navigation/navigationMode';
import locationService from '../services/locationService';
import {
  buildLocationCacheScript,
} from '../utils/locationCacheBridge';
import { buildApiBaseUrlBridgeScript } from '../utils/apiBaseUrlBridge';
import {
  geoRejectScript,
  geoResolveScript,
  isGeoBridgeMessage,
  NATIVE_GEOLOCATION_BRIDGE,
} from '../utils/nativeGeolocationBridge';
import {
  geocodeRejectScript,
  geocodeResolveScript,
  isGeocodeBridgeMessage,
  NATIVE_GEOCODE_BRIDGE,
} from '../utils/nativeGeocodeBridge';
import { MOBILE_VIEWPORT_BRIDGE, buildRoleLayoutScript, APPLY_ROLE_LAYOUT_FROM_STORAGE } from '../utils/mobileViewportBridge';
import { webViewLoadErrorMessage } from '../utils/networkMessages';
import {
  isPullRefreshMessage,
  PULL_TO_REFRESH_BRIDGE,
} from '../utils/pullToRefreshBridge';
import {
  buildWebNavigateScript,
  isWebAppStateMessage,
  WEB_APP_STATE_BRIDGE,
} from '../utils/webNavigationBridge';
import {
  isNavBadgesMessage,
  normalizeNavBadgeCounts,
  type NavBadgeCounts,
} from '../utils/navBadgesBridge';
import { isPushNotificationMessage } from '../utils/pushNotificationBridge';
import { isOpenPdfMessage } from '../utils/pdfViewerBridge';
import { hostOf, isAllowedWebViewUrl } from '../utils/allowedWebViewUrl';
import PdfViewerModal from '../components/PdfViewerModal';
import {
  consumePendingNotificationNavigation,
  displayPushNotification,
  requestPushNotificationPermission,
  setPushNotificationPressHandler,
} from '../services/pushNotificationService';

const LOADER_MAX_MS = 8000;
const LOCATION_PREFETCH_PATHS = new Set([
  '/login',
  '/register',
  '/checkout',
  '/client',
]);

function normalizeWebPath(path: string): string {
  const base = (path || '/').split('?')[0] || '/';
  return base.startsWith('/') ? base : `/${base}`;
}

function shouldPrefetchLocation(path: string): boolean {
  return LOCATION_PREFETCH_PATHS.has(normalizeWebPath(path));
}

const MOBILE_USER_AGENT = Platform.select({
  ios:
    'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1',
  android:
    'Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36',
  default: undefined,
});

const WEB_INJECTED_SCRIPTS = `${MOBILE_VIEWPORT_BRIDGE}\n${buildApiBaseUrlBridgeScript(CONFIG.API_BASE_URL)}\n${NATIVE_GEOLOCATION_BRIDGE}\n${NATIVE_GEOCODE_BRIDGE}\n${PULL_TO_REFRESH_BRIDGE}\n${WEB_APP_STATE_BRIDGE}`;

export default function WebAppScreen() {
  const insets = useSafeAreaInsets();
  const webRef = useRef<WebView>(null);
  const [loading, setLoading] = useState(true);
  const [webPath, setWebPath] = useState('/');
  const [userRole, setUserRole] = useState<string | null>(null);
  const [loggedIn, setLoggedIn] = useState(false);
  const [navBadgeCounts, setNavBadgeCounts] = useState<NavBadgeCounts>({});
  const [pdfViewer, setPdfViewer] = useState<{ title: string; base64: string } | null>(null);
  const tabNavBusyRef = useRef(false);
  const initialLoadRef = useRef(true);
  const geoBusyRef = useRef(false);
  const geoQueueRef = useRef<string[]>([]);
  const locationPrefetchBusyRef = useRef(false);
  const lastInjectedCoordsRef = useRef<{ lat: number; lng: number } | null>(null);
  const appHost = hostOf(CONFIG.WEB_APP_URL) ?? '';

  const tabs = useMemo(() => tabsForRole(userRole), [userRole]);
  const showBottomNav = shouldShowBottomNav(webPath, loggedIn, userRole);
  const currentTabId = activeTabId(tabs, webPath);

  const applyRoleLayout = useCallback((role: string | null, isLoggedIn: boolean) => {
    webRef.current?.injectJavaScript(buildRoleLayoutScript(role, isLoggedIn));
  }, []);

  const hideLoader = useCallback(() => {
    setLoading(false);
    initialLoadRef.current = false;
  }, []);

  const installWebBridges = useCallback(() => {
    webRef.current?.injectJavaScript(WEB_INJECTED_SCRIPTS);
  }, []);

  const refreshPage = useCallback(() => {
    setLoading(true);
    webRef.current?.reload();
  }, []);

  const renderWebViewError = useCallback(
    (_domain?: string, _code?: number, description?: string) => (
      <View style={styles.errorOverlay}>
        <Text style={styles.errorTitle}>Could not load app</Text>
        <Text style={styles.errorText}>{webViewLoadErrorMessage(description)}</Text>
        <TouchableOpacity style={styles.retryButton} onPress={refreshPage}>
          <Text style={styles.retryText}>Retry</Text>
        </TouchableOpacity>
      </View>
    ),
    [refreshPage],
  );

  const navigateWebTo = useCallback((path: string) => {
    if (tabNavBusyRef.current) return;
    tabNavBusyRef.current = true;
    setWebPath(path);
    webRef.current?.injectJavaScript(
      buildWebNavigateScript(path, CONFIG.WEB_APP_URL),
    );
    setTimeout(() => {
      tabNavBusyRef.current = false;
      webRef.current?.injectJavaScript(
        'if (typeof window.__wheelHubSendAppState === "function") window.__wheelHubSendAppState(); true;',
      );
    }, 400);
  }, []);

  const onTabPress = useCallback(
    (tab: BottomNavTab) => {
      if (tabMatchesPath(tab, webPath)) return;
      navigateWebTo(tab.path);
    },
    [navigateWebTo, webPath],
  );

  useEffect(() => {
    const timeout = setTimeout(hideLoader, LOADER_MAX_MS);
    return () => clearTimeout(timeout);
  }, [hideLoader]);

  useEffect(() => {
    setPushNotificationPressHandler(navigateWebTo);
    void consumePendingNotificationNavigation().then((path) => {
      if (path) navigateWebTo(path);
    });
    return () => setPushNotificationPressHandler(null);
  }, [navigateWebTo]);

  useEffect(() => {
    if (!loggedIn) return;
    void requestPushNotificationPermission();
  }, [loggedIn]);

  const replyToGeoRequest = useCallback((script: string) => {
    webRef.current?.injectJavaScript(script);
  }, []);

  const injectLocationCache = useCallback(
    async (latitude: number, longitude: number, accuracy: number, force = false) => {
      if (!force) {
        const prev = lastInjectedCoordsRef.current;
        if (
          prev &&
          Math.abs(prev.lat - latitude) < 0.00005 &&
          Math.abs(prev.lng - longitude) < 0.00005
        ) {
          return;
        }
      }
      lastInjectedCoordsRef.current = { lat: latitude, lng: longitude };

      let locationName = 'Current location';
      try {
        locationName = await Promise.race([
          locationService.getAddressFromCoordinates(latitude, longitude),
          new Promise<string>((resolve) => {
            setTimeout(() => resolve('Current location'), 5000);
          }),
        ]);
      } catch {
        // keep fallback label
      }
      replyToGeoRequest(
        buildLocationCacheScript({
          latitude,
          longitude,
          accuracy,
          locationName,
        }),
      );
    },
    [replyToGeoRequest],
  );

  const prefetchLocationCache = useCallback(
    async (path?: string, force = false) => {
      const normalized = path ? normalizeWebPath(path) : '';
      if (!force && normalized && !shouldPrefetchLocation(normalized)) return;
      if (locationPrefetchBusyRef.current && !force) return;
      locationPrefetchBusyRef.current = true;
      try {
        const granted = await locationService.ensureLocationPermission();
        if (!granted) return;
        const loc = await locationService.getCurrentLocation();
        await injectLocationCache(
          loc.latitude,
          loc.longitude,
          loc.accuracy ?? 0,
          force,
        );
      } catch {
        // booking form can retry via the geolocation bridge
      } finally {
        locationPrefetchBusyRef.current = false;
      }
    },
    [injectLocationCache],
  );

  useEffect(() => {
    const sub = AppState.addEventListener('change', (state) => {
      if (state === 'active' && loggedIn) {
        void prefetchLocationCache(webPath, true);
      }
    });
    return () => sub.remove();
  }, [loggedIn, prefetchLocationCache, webPath]);

  const processGeoQueue = useCallback(() => {
    if (geoBusyRef.current || geoQueueRef.current.length === 0) return;
    geoBusyRef.current = true;
    const id = geoQueueRef.current.shift()!;

    void (async () => {
      try {
        const granted = await locationService.ensureLocationPermission();
        if (!granted) {
          replyToGeoRequest(
            geoRejectScript(
              id,
              1,
              'Location access denied. Allow location for Lendhand in your phone settings, or enter your address manually.',
            ),
          );
          return;
        }

        const loc = await locationService.getCurrentLocation();
        let locationName = 'Current location';
        try {
          locationName = await Promise.race([
            locationService.getAddressFromCoordinates(loc.latitude, loc.longitude),
            new Promise<string>((resolve) => {
              setTimeout(() => resolve('Current location'), 8000);
            }),
          ]);
        } catch {
          // keep fallback label
        }
        replyToGeoRequest(
          geoResolveScript(
            id,
            loc.latitude,
            loc.longitude,
            loc.accuracy ?? 0,
            locationName,
          ),
        );
        void injectLocationCache(
          loc.latitude,
          loc.longitude,
          loc.accuracy ?? 0,
        );
      } catch (err: unknown) {
        const geoErr = err as { code?: number; message?: string };
        replyToGeoRequest(
          geoRejectScript(
            id,
            geoErr.code ?? 2,
            geoErr.message ?? 'Could not determine location.',
          ),
        );
      } finally {
        geoBusyRef.current = false;
        processGeoQueue();
      }
    })();
  }, [replyToGeoRequest, injectLocationCache]);

  const enqueueGeoRequest = useCallback(
    (id: string) => {
      geoQueueRef.current.push(id);
      processGeoQueue();
    },
    [processGeoQueue],
  );

  const handleGeocodeMessage = useCallback(
    (parsed: { type: string; id: string; query?: string; limit?: number; latitude?: number; longitude?: number }) => {
      void (async () => {
        try {
          if (parsed.type === 'GEOCODE_SEARCH') {
            const rows = await locationService.searchAddressSuggestions(
              parsed.query || '',
              parsed.limit ?? 6,
            );
            replyToGeoRequest(geocodeResolveScript(parsed.id, rows));
            return;
          }
          if (parsed.type === 'GEOCODE_REVERSE') {
            const name = await locationService.getAddressFromCoordinates(
              parsed.latitude ?? 0,
              parsed.longitude ?? 0,
            );
            replyToGeoRequest(geocodeResolveScript(parsed.id, name));
          }
        } catch (err: unknown) {
          const message = err instanceof Error ? err.message : 'Geocode failed';
          replyToGeoRequest(geocodeRejectScript(parsed.id, message));
        }
      })();
    },
    [replyToGeoRequest],
  );

  const onMessage = useCallback(
    (event: { nativeEvent: { data: string } }) => {
      let parsed: unknown;
      try {
        parsed = JSON.parse(event.nativeEvent.data);
      } catch {
        return;
      }

      if (isGeocodeBridgeMessage(parsed)) {
        handleGeocodeMessage(parsed);
        return;
      }

      if (isWebAppStateMessage(parsed)) {
        const nextLoggedIn = !!parsed.loggedIn;
        const nextPath = parsed.path || '/';
        setWebPath(nextPath);
        setUserRole(parsed.role || null);
        setLoggedIn(nextLoggedIn);
        if (!nextLoggedIn) {
          setNavBadgeCounts({});
        }
        applyRoleLayout(parsed.role || null, nextLoggedIn);
        installWebBridges();
        if (nextLoggedIn || shouldPrefetchLocation(normalizeWebPath(nextPath))) {
          void prefetchLocationCache(
            nextPath,
            shouldPrefetchLocation(normalizeWebPath(nextPath)) || nextLoggedIn,
          );
        }
        if (nextLoggedIn) {
          void requestPushNotificationPermission();
        }
        return;
      }

      if (isNavBadgesMessage(parsed)) {
        setNavBadgeCounts(normalizeNavBadgeCounts(parsed.navBadges));
        return;
      }

      if (isPushNotificationMessage(parsed)) {
        void displayPushNotification({
          id: parsed.id,
          title: parsed.title,
          body: parsed.body,
          navPath: parsed.navPath,
        });
        return;
      }

      if (isPullRefreshMessage(parsed)) {
        refreshPage();
        return;
      }

      if (isOpenPdfMessage(parsed)) {
        setPdfViewer({
          title: parsed.title?.trim() || 'Document',
          base64: parsed.base64,
        });
        return;
      }

      if (!isGeoBridgeMessage(parsed)) return;

      enqueueGeoRequest(parsed.id);
    },
    [refreshPage, enqueueGeoRequest, applyRoleLayout, installWebBridges, prefetchLocationCache, handleGeocodeMessage],
  );

  const onShouldStartLoadWithRequest = useCallback(
    (request: ShouldStartLoadRequest) => {
      const { url } = request;
            if (!url || url.startsWith('about:') || url.startsWith('data:') || url.startsWith('blob:')) {
        return true;
      }
      if (isAllowedWebViewUrl(url, CONFIG.WEB_APP_URL, CONFIG.API_BASE_URL)) {
        return true;
      }
      if (url.startsWith('tel:') || url.startsWith('mailto:') || url.startsWith('sms:')) {
        void Linking.openURL(url);
        return false;
      }
      if (url.startsWith('http://') || url.startsWith('https://')) {
        void Linking.openURL(url);
        return false;
      }
      return true;
    },
    [],
  );

  const onLoadEnd = useCallback(() => {
    installWebBridges();
    hideLoader();
    tabNavBusyRef.current = false;
    webRef.current?.injectJavaScript(APPLY_ROLE_LAYOUT_FROM_STORAGE);
    if (loggedIn || shouldPrefetchLocation(webPath)) {
      void prefetchLocationCache(webPath, true);
    }
  }, [hideLoader, installWebBridges, loggedIn, prefetchLocationCache, webPath]);

  const onWebViewError = useCallback(() => {
    hideLoader();
  }, [hideLoader]);

  return (
    <View style={styles.root}>
      {insets.top > 0 ? (
        <View style={[styles.statusBarFill, { height: insets.top }]} />
      ) : null}
      <View style={styles.webWrap}>
        <WebView
          ref={webRef}
          testID="webview"
          source={{ uri: CONFIG.WEB_APP_URL }}
          style={styles.webview}
          userAgent={MOBILE_USER_AGENT}
          javaScriptEnabled
          domStorageEnabled
          geolocationEnabled
          cacheEnabled
          nestedScrollEnabled
          startInLoadingState={false}
          injectedJavaScript={WEB_INJECTED_SCRIPTS}
          injectedJavaScriptBeforeContentLoaded={WEB_INJECTED_SCRIPTS}
          onMessage={onMessage}
          allowsInlineMediaPlayback
          mediaPlaybackRequiresUserAction={false}
          sharedCookiesEnabled
          thirdPartyCookiesEnabled
          setSupportMultipleWindows={false}
          allowsBackForwardNavigationGestures
          originWhitelist={['*']}
          onShouldStartLoadWithRequest={onShouldStartLoadWithRequest}
          showsVerticalScrollIndicator={false}
          showsHorizontalScrollIndicator={false}
          textZoom={100}
          setBuiltInZoomControls={false}
          setDisplayZoomControls={false}
          overScrollMode="never"
          onLoadStart={() => {
            if (initialLoadRef.current) {
              setLoading(true);
            }
          }}
          onLoadEnd={onLoadEnd}
          renderError={renderWebViewError}
          onError={() => {
            onWebViewError();
          }}
          onHttpError={() => {
            onWebViewError();
          }}
          onRenderProcessGone={
            Platform.OS === 'android'
              ? () => {
                  webRef.current?.reload();
                  return true;
                }
              : undefined
          }
        />
        {loading ? (
          <View style={styles.loader} pointerEvents="none">
            <ActivityIndicator size="large" color={CONFIG.COLORS.PRIMARY} />
          </View>
        ) : null}
      </View>
      {showBottomNav ? (
        <AppBottomNav
          tabs={tabs}
          activeTabId={currentTabId}
          onTabPress={onTabPress}
          navBadgeCounts={navBadgeCounts}
        />
      ) : null}
      <PdfViewerModal
        visible={!!pdfViewer}
        title={pdfViewer?.title || 'Document'}
        base64={pdfViewer?.base64 || ''}
        onClose={() => setPdfViewer(null)}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: CONFIG.COLORS.WHITE,
  },
  statusBarFill: {
    backgroundColor: MARKETPLACE_PRIMARY,
    width: '100%',
  },
  webWrap: {
    flex: 1,
  },
  webview: {
    flex: 1,
    backgroundColor: CONFIG.COLORS.WHITE,
  },
  loader: {
    ...StyleSheet.absoluteFillObject,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(255,255,255,0.85)',
  },
  errorOverlay: {
    ...StyleSheet.absoluteFillObject,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: CONFIG.COLORS.WHITE,
    paddingHorizontal: 24,
  },
  errorTitle: {
    fontSize: CONFIG.FONT_SIZES.XLARGE,
    fontWeight: '600',
    color: CONFIG.COLORS.DARK_GRAY,
    marginBottom: 12,
    textAlign: 'center',
  },
  errorText: {
    fontSize: CONFIG.FONT_SIZES.MEDIUM,
    color: CONFIG.COLORS.GRAY,
    textAlign: 'center',
    marginBottom: 20,
    lineHeight: 20,
  },
  retryButton: {
    backgroundColor: CONFIG.COLORS.PRIMARY,
    paddingHorizontal: 24,
    paddingVertical: 12,
    borderRadius: CONFIG.DIMENSIONS.BORDER_RADIUS,
  },
  retryText: {
    color: CONFIG.COLORS.WHITE,
    fontSize: CONFIG.FONT_SIZES.LARGE,
    fontWeight: '600',
  },
});
