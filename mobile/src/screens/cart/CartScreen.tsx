import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { useAppSelector } from '../../store/hooks';
import { spacing, typography } from '../../theme';
import { BRAND_COLORS } from '../../theme/colors';

const CartScreen = () => {
  const { totalItems, totalPrice } = useAppSelector(state => state.cart);

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Shopping Cart</Text>
      <Text style={styles.subtitle}>
        {totalItems > 0
          ? `${totalItems} item${totalItems > 1 ? 's' : ''} - R${totalPrice.toFixed(2)}`
          : 'Your cart is empty'}
      </Text>
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

export default CartScreen;
