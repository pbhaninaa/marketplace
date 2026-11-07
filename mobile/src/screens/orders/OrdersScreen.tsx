import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { spacing, typography } from '../../theme';
import { BRAND_COLORS } from '../../theme/colors';

const OrdersScreen = () => {
  return (
    <View style={styles.container}>
      <Text style={styles.title}>My Orders</Text>
      <Text style={styles.subtitle}>Order history coming soon</Text>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#F5F5F5',
    padding: spacing.lg,
  },
  title: {
    ...typography.h1,
    color: BRAND_COLORS.primary,
    marginBottom: spacing.sm,
  },
  subtitle: {
    ...typography.body,
    color: '#5A6C67',
    textAlign: 'center',
  },
});

export default OrdersScreen;
