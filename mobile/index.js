/**
 * @format
 */

import 'react-native-gesture-handler';
import { AppRegistry } from 'react-native';
import notifee, { EventType } from '@notifee/react-native';
import App from './App';
import { name as appName } from './app.json';
import { handleBackgroundNotificationEvent } from './src/services/pushNotificationService';

notifee.onBackgroundEvent(async ({ type, detail }) => {
  await handleBackgroundNotificationEvent(type, detail.notification?.data);
});

AppRegistry.registerComponent(appName, () => App);
