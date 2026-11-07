// Navigation types for TypeScript

export type RootStackParamList = {
  Auth: undefined;
  Main: undefined;
};

export type AuthStackParamList = {
  Login: undefined;
  Register: undefined;
  ForgotPassword: undefined;
  ResetPassword: { token: string };
};

export type MainTabParamList = {
  ShopTab: undefined;
  CartTab: undefined;
  OrdersTab: undefined;
  SettingsTab: undefined;
};

export type ShopStackParamList = {
  Marketplace: undefined;
  ListingDetail: { listingId: string };
};

export type CartStackParamList = {
  Cart: undefined;
  Checkout: undefined;
  CheckoutDelivery: undefined;
  CheckoutPayment: undefined;
  OrderConfirmation: { orderId: string };
};

export type OrdersStackParamList = {
  OrderLookup: undefined;
  OrderDetail: { orderId: string };
};

export type SettingsStackParamList = {
  Settings: undefined;
  Help: undefined;
  Profile: undefined;
};
