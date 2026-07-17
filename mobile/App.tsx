/**
 * Agri Marketplace mobile — WebView shell for the Vue web app.
 */

import React, { useEffect } from 'react';
import { Platform, StatusBar, View } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import AppNavigator from './src/navigation/AppNavigator';
import { MARKETPLACE_PRIMARY } from './src/config/branding';
import { initializePushNotifications } from './src/services/pushNotificationService';

function App() {
  useEffect(() => {
    initializePushNotifications();
  }, []);

  return (
    <SafeAreaProvider>
      <StatusBar
        barStyle="light-content"
        backgroundColor={MARKETPLACE_PRIMARY}
        translucent={Platform.OS === 'android'}
      />
      <View style={{ flex: 1, backgroundColor: MARKETPLACE_PRIMARY }}>
        <AppNavigator />
      </View>
    </SafeAreaProvider>
  );
}

export default App;
