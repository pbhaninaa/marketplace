import React, { useEffect } from 'react';
import { NavigationContainer } from '@react-navigation/native';
import { createStackNavigator } from '@react-navigation/stack';
import { ActivityIndicator, View } from 'react-native';
import type { RootStackParamList } from './types';
import { useAppDispatch, useAppSelector } from '../store/hooks';
import { restoreSession } from '../store/slices/authSlice';
import { loadCart } from '../store/slices/cartSlice';
import { loadTheme } from '../store/slices/themeSlice';

import AuthStack from './AuthStack';
import MainTabs from './MainTabs';

const Stack = createStackNavigator<RootStackParamList>();

const RootNavigator = () => {
  const dispatch = useAppDispatch();
  const { isAuthenticated, loading } = useAppSelector(state => state.auth);
  const [isReady, setIsReady] = React.useState(false);

  useEffect(() => {
    const initializeApp = async () => {
      try {
        // Restore session, cart, and theme in parallel
        await Promise.all([
          dispatch(restoreSession()).unwrap().catch(() => {}),
          dispatch(loadCart()).unwrap().catch(() => {}),
          dispatch(loadTheme()).unwrap().catch(() => {}),
        ]);
      } catch (error) {
        console.error('Failed to initialize app:', error);
      } finally {
        setIsReady(true);
      }
    };

    initializeApp();
  }, [dispatch]);

  if (!isReady || loading) {
    return (
      <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center', backgroundColor: '#FFFFFF' }}>
        <ActivityIndicator size="large" color="#3D7A66" />
      </View>
    );
  }

  return (
    <NavigationContainer>
      <Stack.Navigator screenOptions={{ headerShown: false }}>
        {!isAuthenticated ? (
          <Stack.Screen name="Auth" component={AuthStack} />
        ) : (
          <Stack.Screen name="Main" component={MainTabs} />
        )}
      </Stack.Navigator>
    </NavigationContainer>
  );
};

export default RootNavigator;
