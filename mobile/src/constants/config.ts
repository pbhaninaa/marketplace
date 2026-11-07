// App Configuration

export const CONFIG = {
  // API Configuration
  API_BASE_URL: 'https://marketplace-production-129b.up.railway.app',
  API_TIMEOUT: 30000,

  // App Information
  APP_NAME: 'Agri Marketplace',
  APP_VERSION: '1.0.3',
  APP_DESCRIPTION: 'Equipment & Stock: Rental & Resale',

  // Storage Keys
  STORAGE_KEYS: {
    AUTH_TOKEN: '@auth_token',
    USER_DATA: '@user_data',
    CART_DATA: '@cart_data',
    THEME_MODE: '@theme_mode',
  },

  // Pagination
  DEFAULT_PAGE_SIZE: 20,
  MAX_PAGE_SIZE: 100,

  // Cache
  CACHE_DURATION: 5 * 60 * 1000, // 5 minutes

  // UI
  DEBOUNCE_DELAY: 300,
  ANIMATION_DURATION: 300,

  // Validation
  MIN_PASSWORD_LENGTH: 8,
  MAX_CART_ITEMS: 50,

  // Map
  DEFAULT_MAP_REGION: {
    latitude: -25.7479,
    longitude: 28.2293,
    latitudeDelta: 0.0922,
    longitudeDelta: 0.0421,
  },
};

// Environment-specific overrides (can be extended)
export const getApiBaseUrl = () => {
  // In the future, you can check different environments here
  return CONFIG.API_BASE_URL;
};
