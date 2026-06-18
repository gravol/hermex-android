/**
 * Notification service for Hermes Chat.
 *
 * Uses expo-notifications on native platforms and gracefully no-ops on web.
 * UnifiedPush is implemented as a bridge for now: the app gives Hermes a stable
 * device id and ntfy base URL, and Hermes performs server-side ntfy delivery.
 */
import { Platform } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import Constants from 'expo-constants';
import * as Device from 'expo-device';
import * as Notifications from 'expo-notifications';
import { NTFY_SERVER_URL } from '../config';
import type { HermesAPI } from './api';

const NOTIFICATIONS_ENABLED_KEY = 'hermes.notifications.enabled';
const DEVICE_ID_KEY = 'hermes_device_id';
const UNIFIED_PUSH_STATUS_KEY = 'hermes.unified_push.status';
const UNIFIED_PUSH_ENDPOINT_KEY = 'hermes.unified_push.endpoint';
const ANDROID_CHANNEL_ID = 'hermes-chat';

export type UnifiedPushStatus = 'registered' | 'not_registered' | 'unavailable';

let expoPushToken: string | null = null;
let unifiedPushEndpoint: string | null = null;
let unifiedPushStatus: UnifiedPushStatus = isUnifiedPushBridgeAvailable() ? 'not_registered' : 'unavailable';
let notificationReceivedSubscription: Notifications.EventSubscription | null = null;
let notificationResponseSubscription: Notifications.EventSubscription | null = null;

const isWeb = Platform.OS === 'web';

if (!isWeb) {
  Notifications.setNotificationHandler({
    handleNotification: async () => ({
      shouldPlaySound: false,
      shouldSetBadge: false,
      shouldShowBanner: true,
      shouldShowList: true,
    }),
  });
}

export function getExpoPushToken(): string | null {
  return expoPushToken;
}

export function getUnifiedPushStatus(): UnifiedPushStatus {
  return unifiedPushStatus;
}

export function getUnifiedPushEndpoint(): string | null {
  return unifiedPushEndpoint;
}

function isUnifiedPushBridgeAvailable(): boolean {
  // Expo does not expose Android's UnifiedPush distributor APIs without a native
  // module. The bridge is usable on native Android (GrapheneOS + ntfy app on the
  // phone for real UnifiedPush distribution), and gracefully unavailable elsewhere.
  return Platform.OS === 'android';
}

function generateDeviceId(): string {
  const randomPart = Math.random().toString(36).slice(2, 12);
  return `device-${randomPart}-${Date.now()}`;
}

export async function getDeviceId(): Promise<string> {
  const existing = await AsyncStorage.getItem(DEVICE_ID_KEY);
  if (existing) return existing;

  const created = generateDeviceId();
  await AsyncStorage.setItem(DEVICE_ID_KEY, created);
  return created;
}

export async function registerWithUnifiedPush(): Promise<string | null> {
  if (!isUnifiedPushBridgeAvailable()) {
    unifiedPushStatus = 'unavailable';
    await AsyncStorage.setItem(UNIFIED_PUSH_STATUS_KEY, unifiedPushStatus);
    return null;
  }

  const enabled = await areNotificationsEnabled();
  if (!enabled) {
    unifiedPushStatus = 'not_registered';
    await AsyncStorage.setItem(UNIFIED_PUSH_STATUS_KEY, unifiedPushStatus);
    return null;
  }

  const deviceId = await getDeviceId();
  const endpoint = `${NTFY_SERVER_URL.replace(/\/$/, '')}/${encodeURIComponent(deviceId)}`;
  unifiedPushEndpoint = endpoint;
  unifiedPushStatus = 'not_registered';
  await AsyncStorage.multiSet([
    [UNIFIED_PUSH_ENDPOINT_KEY, endpoint],
    [UNIFIED_PUSH_STATUS_KEY, unifiedPushStatus],
  ]);
  return endpoint;
}

export async function sendPushEndpointToHermes(api: HermesAPI): Promise<string | null> {
  const endpoint = await registerWithUnifiedPush();
  if (!endpoint) return null;

  const deviceId = await getDeviceId();
  await api.sendHiddenMessage(`/register-device ${deviceId} ntfy ${NTFY_SERVER_URL}`);
  unifiedPushStatus = 'registered';
  await AsyncStorage.setItem(UNIFIED_PUSH_STATUS_KEY, unifiedPushStatus);
  return endpoint;
}

async function restoreUnifiedPushState(): Promise<void> {
  const [[, storedStatus], [, storedEndpoint]] = await AsyncStorage.multiGet([
    UNIFIED_PUSH_STATUS_KEY,
    UNIFIED_PUSH_ENDPOINT_KEY,
  ]);

  unifiedPushEndpoint = storedEndpoint;
  if (!isUnifiedPushBridgeAvailable()) {
    unifiedPushStatus = 'unavailable';
  } else if (storedStatus === 'registered' || storedStatus === 'not_registered') {
    unifiedPushStatus = storedStatus;
  } else {
    unifiedPushStatus = 'not_registered';
  }
}

export async function areNotificationsEnabled(): Promise<boolean> {
  if (isWeb) return false;

  const stored = await AsyncStorage.getItem(NOTIFICATIONS_ENABLED_KEY);
  // Default to enabled on native; user can opt out in Settings.
  return stored === null ? true : stored === 'true';
}

export async function setNotificationsEnabled(enabled: boolean): Promise<void> {
  await AsyncStorage.setItem(NOTIFICATIONS_ENABLED_KEY, enabled ? 'true' : 'false');

  if (enabled) {
    await setupNotifications();
  } else {
    unifiedPushStatus = isUnifiedPushBridgeAvailable() ? 'not_registered' : 'unavailable';
    await AsyncStorage.setItem(UNIFIED_PUSH_STATUS_KEY, unifiedPushStatus);
    removeNotificationListeners();
  }
}

function removeNotificationListeners(): void {
  notificationReceivedSubscription?.remove();
  notificationResponseSubscription?.remove();
  notificationReceivedSubscription = null;
  notificationResponseSubscription = null;
}

async function ensureAndroidChannel(): Promise<void> {
  if (Platform.OS !== 'android') return;

  await Notifications.setNotificationChannelAsync(ANDROID_CHANNEL_ID, {
    name: 'Hermes Chat',
    description: 'Hermes replies and chat errors',
    importance: Notifications.AndroidImportance.MAX,
    vibrationPattern: [0, 250, 250, 250],
    lightColor: '#B8860B',
  });
}

async function registerForPushNotificationsAsync(): Promise<string | null> {
  if (isWeb) return null;

  await ensureAndroidChannel();

  if (!Device.isDevice && Platform.OS !== 'android') {
    console.warn('Push notifications generally require a physical device.');
    return null;
  }

  const existingPermissions = await Notifications.getPermissionsAsync();
  let finalStatus = existingPermissions.status;

  if (existingPermissions.status !== 'granted') {
    const requestedPermissions = await Notifications.requestPermissionsAsync();
    finalStatus = requestedPermissions.status;
  }

  if (finalStatus !== 'granted') {
    console.warn('Notification permission was not granted.');
    return null;
  }

  const projectId = Constants.expoConfig?.extra?.eas?.projectId ?? Constants.easConfig?.projectId;
  if (!projectId) {
    console.warn('EAS project ID not found; skipping Expo push token registration. Local notifications still work.');
    return null;
  }

  const token = (await Notifications.getExpoPushTokenAsync({ projectId })).data;
  expoPushToken = token;
  return token;
}

export async function setupNotifications(): Promise<() => void> {
  if (isWeb) {
    return () => undefined;
  }

  const enabled = await areNotificationsEnabled();
  if (!enabled) {
    return () => undefined;
  }

  await restoreUnifiedPushState();

  try {
    await registerForPushNotificationsAsync();
  } catch (error) {
    console.warn('Failed to register for push notifications:', error);
  }

  try {
    await registerWithUnifiedPush();
  } catch (error) {
    console.warn('Failed to initialize UnifiedPush bridge:', error);
  }

  notificationReceivedSubscription?.remove();
  notificationResponseSubscription?.remove();

  notificationReceivedSubscription = Notifications.addNotificationReceivedListener(notification => {
    console.log('Notification received:', notification.request.content.title);
  });

  notificationResponseSubscription = Notifications.addNotificationResponseReceivedListener(response => {
    console.log('Notification response received:', response.notification.request.content.title);
  });

  return removeNotificationListeners;
}

export async function showLocalNotification(title: string, body: string): Promise<void> {
  if (isWeb) return;

  const enabled = await areNotificationsEnabled();
  if (!enabled) return;

  try {
    await ensureAndroidChannel();
    await Notifications.scheduleNotificationAsync({
      content: {
        title,
        body,
      },
      trigger: null,
    });
  } catch (error) {
    console.warn('Failed to show local notification:', error);
  }
}
