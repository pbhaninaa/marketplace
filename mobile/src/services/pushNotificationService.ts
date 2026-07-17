import { Platform, PermissionsAndroid } from 'react-native';
import notifee, {
  AndroidImportance,
  AuthorizationStatus,
  EventType,
} from '@notifee/react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { CONFIG } from '../config';
import { normalizeNotificationNavPath } from '../utils/pushNotificationBridge';

const PENDING_NAV_KEY = 'marketplace_pending_nav';
const shownNotificationIds = new Set<string>();
let channelReady = false;
let pressHandler: ((path: string) => void) | null = null;

function rememberNotificationId(id: string) {
  shownNotificationIds.add(id);
  if (shownNotificationIds.size > 200) {
    const oldest = shownNotificationIds.values().next().value;
    if (oldest) shownNotificationIds.delete(oldest);
  }
}

async function ensureChannel(): Promise<void> {
  if (channelReady) return;
  if (Platform.OS === 'android') {
    await notifee.createChannel({
      id: CONFIG.NOTIFICATIONS.CHANNEL_ID,
      name: CONFIG.NOTIFICATIONS.CHANNEL_NAME,
      description: CONFIG.NOTIFICATIONS.CHANNEL_DESCRIPTION,
      importance: AndroidImportance.HIGH,
      sound: 'default',
      vibration: true,
    });
  }
  channelReady = true;
}

export async function requestPushNotificationPermission(): Promise<boolean> {
  if (Platform.OS === 'android' && Platform.Version >= 33) {
    const granted = await PermissionsAndroid.request(
      PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS,
    );
    if (granted !== PermissionsAndroid.RESULTS.GRANTED) {
      return false;
    }
  }

  const settings = await notifee.requestPermission();
  return (
    settings.authorizationStatus === AuthorizationStatus.AUTHORIZED ||
    settings.authorizationStatus === AuthorizationStatus.PROVISIONAL
  );
}

export function setPushNotificationPressHandler(
  handler: ((path: string) => void) | null,
): void {
  pressHandler = handler;
}

async function handleNotificationPress(
  data?: Record<string, unknown>,
): Promise<void> {
  const navPath = normalizeNotificationNavPath(data?.navPath);
  await AsyncStorage.setItem(PENDING_NAV_KEY, navPath);
  pressHandler?.(navPath);
}

export async function consumePendingNotificationNavigation(): Promise<string | null> {
  const path = await AsyncStorage.getItem(PENDING_NAV_KEY);
  if (!path) return null;
  await AsyncStorage.removeItem(PENDING_NAV_KEY);
  return path;
}

export async function displayPushNotification(input: {
  id?: string;
  title: string;
  body?: string;
  navPath?: string;
}): Promise<void> {
  const title = input.title?.trim();
  if (!title) return;

  const id = input.id?.trim() || `wh_${Date.now()}`;
  if (shownNotificationIds.has(id)) return;

  const navPath = normalizeNotificationNavPath(input.navPath);

  await ensureChannel();
  const allowed = await requestPushNotificationPermission();
  if (!allowed) return;

  rememberNotificationId(id);

  await notifee.displayNotification({
    id,
    title,
    body: input.body?.trim() || undefined,
    data: { navPath },
    android: {
      channelId: CONFIG.NOTIFICATIONS.CHANNEL_ID,
      pressAction: { id: 'default' },
      smallIcon: 'ic_launcher',
      importance: AndroidImportance.HIGH,
    },
    ios: {
      sound: 'default',
      foregroundPresentationOptions: {
        alert: true,
        badge: true,
        sound: true,
      },
    },
  });
}

export function initializePushNotifications(): void {
  notifee.onForegroundEvent(({ type, detail }) => {
    if (type === EventType.PRESS || type === EventType.ACTION_PRESS) {
      void handleNotificationPress(detail.notification?.data);
    }
  });
}

export async function handleBackgroundNotificationEvent(
  type: number,
  data?: Record<string, unknown>,
): Promise<void> {
  if (type === EventType.PRESS || type === EventType.ACTION_PRESS) {
    await handleNotificationPress(data);
  }
}
