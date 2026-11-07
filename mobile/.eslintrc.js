module.exports = {
  root: true,
  extends: '@react-native',
  // Leftover Expo Router template (not used by App.tsx / index.js entry)
  ignorePatterns: ['app/**', 'components/**', 'constants/**', '.expo/**'],
  rules: {
    'react-native/no-inline-styles': 'off',
    // Allow `void promise()` to mark intentional fire-and-forget calls
    'no-void': ['warn', { allowAsStatement: true }],
  },
};
