import React from 'react';
import {
  TouchableOpacity,
  Text,
  ActivityIndicator,
  ViewStyle,
  TextStyle,
} from 'react-native';
import { BRAND_COLORS } from '../../theme/colors';
import { spacing, borderRadius, typography } from '../../theme';

interface ButtonProps {
  title: string;
  onPress: () => void;
  variant?: 'primary' | 'secondary' | 'outline' | 'ghost';
  size?: 'small' | 'medium' | 'large';
  loading?: boolean;
  disabled?: boolean;
  fullWidth?: boolean;
  style?: ViewStyle;
}

const Button: React.FC<ButtonProps> = ({
  title,
  onPress,
  variant = 'primary',
  size = 'medium',
  loading = false,
  disabled = false,
  fullWidth = false,
  style,
}) => {
  const isDisabled = disabled || loading;

  const getButtonStyle = (): ViewStyle => {
    const baseStyle: ViewStyle = {
      borderRadius: borderRadius.md,
      alignItems: 'center',
      justifyContent: 'center',
      flexDirection: 'row',
    };

    // Size
    const sizeStyles: Record<string, ViewStyle> = {
      small: { paddingVertical: spacing.sm, paddingHorizontal: spacing.md, minHeight: 36 },
      medium: { paddingVertical: spacing.md, paddingHorizontal: spacing.lg, minHeight: 44 },
      large: { paddingVertical: spacing.lg, paddingHorizontal: spacing.xl, minHeight: 52 },
    };

    // Variant
    const variantStyles: Record<string, ViewStyle> = {
      primary: { backgroundColor: BRAND_COLORS.primary },
      secondary: { backgroundColor: BRAND_COLORS.secondary },
      outline: {
        backgroundColor: 'transparent',
        borderWidth: 1,
        borderColor: BRAND_COLORS.primary,
      },
      ghost: { backgroundColor: 'transparent' },
    };

    return {
      ...baseStyle,
      ...sizeStyles[size],
      ...variantStyles[variant],
      ...(fullWidth && { width: '100%' }),
      ...(isDisabled && { opacity: 0.5 }),
    };
  };

  const getTextStyle = (): TextStyle => {
    const baseStyle: TextStyle = {
      ...typography.button,
      fontWeight: '600',
    };

    const variantTextStyles: Record<string, TextStyle> = {
      primary: { color: '#FFFFFF' },
      secondary: { color: '#1A3C34' },
      outline: { color: BRAND_COLORS.primary },
      ghost: { color: BRAND_COLORS.primary },
    };

    const sizeTextStyles: Record<string, TextStyle> = {
      small: { fontSize: 14 },
      medium: { fontSize: 16 },
      large: { fontSize: 18 },
    };

    return {
      ...baseStyle,
      ...variantTextStyles[variant],
      ...sizeTextStyles[size],
    };
  };

  return (
    <TouchableOpacity
      style={[getButtonStyle(), style]}
      onPress={onPress}
      disabled={isDisabled}
      activeOpacity={0.7}
    >
      {loading ? (
        <ActivityIndicator
          size="small"
          color={variant === 'primary' ? '#FFFFFF' : BRAND_COLORS.primary}
        />
      ) : (
        <Text style={getTextStyle()}>{title}</Text>
      )}
    </TouchableOpacity>
  );
};

export default Button;
