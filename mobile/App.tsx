/**
 * Agri Marketplace mobile — Native React Native app.
 */

import React from 'react';
import { Platform, StatusBar } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { NavigationContainer } from '@react-navigation/native';
import { Provider } from 'react-redux';
import { PersistGate } from 'redux-persist/integration/react';
import { store, persistor } from './src/store';
import RootNavigator from './src/navigation/RootNavigator';
import { BRAND_COLORS } from './src/theme/colors';

function App() {
  return (
    <Provider store={store}>
      <PersistGate loading={null} persistor={persistor}>
        <SafeAreaProvider>
          <NavigationContainer>
            <StatusBar
              barStyle="light-content"
              backgroundColor={BRAND_COLORS.primary}
              translucent={Platform.OS === 'android'}
            />
            <RootNavigator />
          </NavigationContainer>
        </SafeAreaProvider>
      </PersistGate>
    </Provider>
  );
}

export default App;
