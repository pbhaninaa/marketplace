jest.mock('@notifee/react-native', () => ({
  __esModule: true,
  default: {
    createChannel: jest.fn().mockResolvedValue(undefined),
    requestPermission: jest.fn().mockResolvedValue({ authorizationStatus: 1 }),
    displayNotification: jest.fn().mockResolvedValue(undefined),
    onForegroundEvent: jest.fn(),
    onBackgroundEvent: jest.fn(),
  },
  AndroidImportance: { HIGH: 4 },
  AuthorizationStatus: { AUTHORIZED: 1, PROVISIONAL: 2 },
  EventType: { PRESS: 1, ACTION_PRESS: 2 },
}));

jest.mock('@react-native-async-storage/async-storage', () =>
  require('@react-native-async-storage/async-storage/jest/async-storage-mock'),
);

jest.mock('react-native-webview', () => {
  const React = require('react');
  const { View } = require('react-native');
  return {
    WebView: React.forwardRef((props, ref) => (
      <View ref={ref} testID="webview" {...props} />
    )),
  };
});

jest.mock('react-native-vector-icons/MaterialCommunityIcons', () => 'Icon');

jest.mock('react-native-safe-area-context', () => {
  const React = require('react');
  return {
    SafeAreaProvider: ({ children }) => children,
    useSafeAreaInsets: () => ({ top: 24, bottom: 16, left: 0, right: 0 }),
  };
});

jest.mock('./src/services/locationService', () => ({
  __esModule: true,
  default: {
    ensureLocationPermission: jest.fn().mockResolvedValue(true),
    getCurrentLocation: jest.fn().mockResolvedValue({
      latitude: -26.2,
      longitude: 28.04,
      accuracy: 10,
    }),
  },
}));
