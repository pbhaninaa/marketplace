import React from 'react';
import { NavigationContainer } from '@react-navigation/native';
import { createStackNavigator } from '@react-navigation/stack';
import WebAppScreen from '../screens/WebAppScreen';

const Stack = createStackNavigator();

/**
 * Mobile shell loads the Vue web app in a WebView with native bottom navigation.
 * The hamburger / sidebar is hidden in the shell; tabs mirror main web routes per role.
 */
const AppNavigator = () => {
  return (
    <NavigationContainer>
      <Stack.Navigator screenOptions={{ headerShown: false }}>
        <Stack.Screen name="WebApp" component={WebAppScreen} />
      </Stack.Navigator>
    </NavigationContainer>
  );
};

export default AppNavigator;
