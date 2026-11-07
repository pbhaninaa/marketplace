import React from 'react';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import Icon from 'react-native-vector-icons/MaterialCommunityIcons';
import type { MainTabParamList } from './types';
import { useAppSelector } from '../store/hooks';
import { BRAND_COLORS } from '../theme/colors';

// Import placeholder screens (will create proper ones later)
import MarketplaceScreen from '../screens/marketplace/MarketplaceScreen';
import CartScreen from '../screens/cart/CartScreen';
import OrdersScreen from '../screens/orders/OrdersScreen';
import SettingsScreen from '../screens/settings/SettingsScreen';

const Tab = createBottomTabNavigator<MainTabParamList>();

type TabIconProps = { color: string; size: number };

// Defined at module scope so React sees a stable component type across renders.
const makeTabIcon = (name: string) => ({ color, size }: TabIconProps) => (
  <Icon name={name} size={size} color={color} />
);

const ShopTabIcon = makeTabIcon('storefront-outline');
const CartTabIcon = makeTabIcon('cart-outline');
const OrdersTabIcon = makeTabIcon('receipt-text-outline');
const SettingsTabIcon = makeTabIcon('cog-outline');

const MainTabs = () => {
  const cartItems = useAppSelector(state => state.cart.totalItems);
  const isDark = useAppSelector(state => state.theme.mode === 'dark');

  return (
    <Tab.Navigator
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: BRAND_COLORS.primary,
        tabBarInactiveTintColor: isDark ? '#999' : '#666',
        tabBarStyle: {
          backgroundColor: isDark ? '#1E1E1E' : '#FFFFFF',
          borderTopColor: isDark ? '#2C2C2C' : '#E0E0E0',
          height: 60,
          paddingBottom: 8,
          paddingTop: 8,
        },
        tabBarLabelStyle: {
          fontSize: 12,
          fontWeight: '600',
        },
      }}
    >
      <Tab.Screen
        name="ShopTab"
        component={MarketplaceScreen}
        options={{
          tabBarLabel: 'Shop',
          tabBarIcon: ShopTabIcon,
        }}
      />

      {cartItems > 0 && (
        <Tab.Screen
          name="CartTab"
          component={CartScreen}
          options={{
            tabBarLabel: 'Cart',
            tabBarBadge: cartItems > 99 ? '99+' : cartItems,
            tabBarIcon: CartTabIcon,
          }}
        />
      )}

      <Tab.Screen
        name="OrdersTab"
        component={OrdersScreen}
        options={{
          tabBarLabel: 'Orders',
          tabBarIcon: OrdersTabIcon,
        }}
      />

      <Tab.Screen
        name="SettingsTab"
        component={SettingsScreen}
        options={{
          tabBarLabel: 'Settings',
          tabBarIcon: SettingsTabIcon,
        }}
      />
    </Tab.Navigator>
  );
};

export default MainTabs;
