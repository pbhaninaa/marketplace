import { getAppEnvironment } from './appEnv';
import { resolveEnvironmentEndpoints } from './environments';

const endpoints = resolveEnvironmentEndpoints(getAppEnvironment());

export const CONFIG = {
  APP_ENV: endpoints.env,
  WEB_APP_URL: endpoints.webAppUrl,
  API_BASE_URL: endpoints.apiBaseUrl,
  API_TIMEOUT: 15000,

  /** E.164 prefix when profile does not set one (e.g. +1, +44). Empty = omit and always collect on profile. */
  DEFAULT_PHONE_COUNTRY_CODE: '',

  // App Configuration
  APP_NAME: 'Agri Marketplace',
  APP_VERSION: '1.0.3',
  
  // Storage Keys
  STORAGE_KEYS: {
    AUTH_TOKEN: 'authToken',
    USER_DATA: 'user',
    SETTINGS: 'settings',
  },
  
  // Job Categories
  JOB_CATEGORIES: [
    'Battery Service',
    'Tire Service',
    'Engine Repair',
    'Electrical',
    'Diagnostics',
    'Oil Change',
    'Brake Service',
    'AC/Heating',
    'Towing',
    'Emergency',
    'Other',
  ],

  // Default price per category (Rands) - used when creating jobs
  JOB_CATEGORY_PRICES: {
    'Battery Service': 550,
    'Tire Service': 480,
    'Engine Repair': 850,
    'Electrical': 650,
    'Diagnostics': 350,
    'Oil Change': 350,
    'Brake Service': 650,
    'AC/Heating': 720,
    'Towing': 400,
    'Emergency': 500,
    'Other': 500,
  },
  
  // Job Priorities
  JOB_PRIORITIES: [
    { value: 'low', label: 'Low', color: '#4CAF50' },
    { value: 'medium', label: 'Medium', color: '#FF9800' },
    { value: 'high', label: 'High', color: '#F44336' },
    { value: 'emergency', label: 'Emergency', color: '#9C27B0' },
  ],
  
  // Job Statuses (canonical — matches backend JobRequestStatus)
  JOB_STATUSES: [
    { value: 'PENDING', label: 'Pending', color: '#FF9800' },
    { value: 'WAITING_PAYMENT', label: 'Awaiting payment', color: '#FFC107' },
    { value: 'PAID', label: 'Paid', color: '#00BCD4' },
    { value: 'IN_PROGRESS', label: 'In Progress', color: '#9C27B0' },
    { value: 'COMPLETED', label: 'Completed', color: '#4CAF50' },
    { value: 'DECLINED', label: 'Declined', color: '#F44336' },
    { value: 'CANCELLED', label: 'Cancelled', color: '#757575' },
  ],
  
  // Colors
  COLORS: {
    PRIMARY: '#0d9488',
    SECONDARY: '#5856D6',
    SUCCESS: '#34C759',
    WARNING: '#FF9500',
    ERROR: '#FF3B30',
    INFO: '#5AC8FA',
    LIGHT_GRAY: '#F2F2F7',
    GRAY: '#8E8E93',
    DARK_GRAY: '#1C1C1E',
    WHITE: '#FFFFFF',
    BLACK: '#000000',
  },
  
  // Dimensions
  DIMENSIONS: {
    PADDING: 16,
    MARGIN: 16,
    BORDER_RADIUS: 8,
    BUTTON_HEIGHT: 48,
    INPUT_HEIGHT: 48,
  },
  
  // Font Sizes
  FONT_SIZES: {
    SMALL: 12,
    MEDIUM: 14,
    LARGE: 16,
    XLARGE: 18,
    XXLARGE: 20,
    TITLE: 24,
    HEADER: 28,
  },
  
  // Map Configuration
  MAP: {
    DEFAULT_LATITUDE: 37.7749,
    DEFAULT_LONGITUDE: -122.4194,
    DEFAULT_ZOOM: 13,
    SEARCH_RADIUS: 5000, // meters
  },
  
  // Notification Configuration
  NOTIFICATIONS: {
    CHANNEL_ID: 'marketplace_app',
    CHANNEL_NAME: 'Lendhand Notifications',
    CHANNEL_DESCRIPTION: 'Notifications for Lendhand',
  },
  
  // File Upload
  UPLOAD: {
    MAX_FILE_SIZE: 10 * 1024 * 1024, // 10MB
    ALLOWED_TYPES: ['image/jpeg', 'image/png', 'image/jpg'],
    MAX_FILES: 5,
  },
  
  // Validation
  VALIDATION: {
    PASSWORD_MIN_LENGTH: 6,
    PHONE_REGEX: /^\+?[\d\s\-()]+$/,
    EMAIL_REGEX: /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
  },
};

export default CONFIG; 