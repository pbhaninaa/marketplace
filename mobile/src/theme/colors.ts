import type { ThemeColors } from '../types';

// Brand colors (matching web app)
export const BRAND_COLORS = {
  primary: '#3D7A66', // Canopy/teal
  primaryLight: '#4A9178',
  primaryDark: '#2C5A4D',
  secondary: '#C8A25B', // Gold
  secondaryLight: '#D4B373',
  secondaryDark: '#B08F4A',
};

// Light theme colors
export const lightColors: ThemeColors = {
  primary: BRAND_COLORS.primary,
  secondary: BRAND_COLORS.secondary,
  background: '#F5F5F5',
  surface: '#FFFFFF',
  text: '#1A3C34',
  textSecondary: '#5A6C67',
  border: '#E0E0E0',
  error: '#D32F2F',
  success: '#388E3C',
  warning: '#F57C00',
  info: '#1976D2',
};

// Dark theme colors
export const darkColors: ThemeColors = {
  primary: BRAND_COLORS.primaryLight,
  secondary: BRAND_COLORS.secondaryLight,
  background: '#121212',
  surface: '#1E1E1E',
  text: '#FFFFFF',
  textSecondary: '#B0B0B0',
  border: '#2C2C2C',
  error: '#EF5350',
  success: '#66BB6A',
  warning: '#FFA726',
  info: '#42A5F5',
};

export const getThemeColors = (isDark: boolean): ThemeColors => {
  return isDark ? darkColors : lightColors;
};
