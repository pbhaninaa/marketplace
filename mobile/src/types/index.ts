// Core Types for Agri Marketplace Native App

// ===== USER & AUTH =====
export interface User {
  id: string;
  email: string;
  name: string;
  phone?: string;
  role: 'client' | 'provider' | 'admin' | 'support';
  providerId?: string;
}

export interface AuthState {
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  loading: boolean;
  error: string | null;
}

export interface LoginCredentials {
  email: string;
  password: string;
}

export interface RegisterData {
  email: string;
  password: string;
  name: string;
  phone?: string;
}

// ===== LISTINGS =====
export interface Listing {
  id: string;
  title: string;
  description: string;
  price: number;
  rentPrice?: number;
  forSale: boolean;
  forRent: boolean;
  categoryId: string;
  categoryName: string;
  providerId: string;
  providerName: string;
  location: string;
  latitude?: number;
  longitude?: number;
  images: string[];
  stock: number;
  condition?: string;
  specifications?: Record<string, any>;
  createdAt: string;
  updatedAt: string;
}

export interface Category {
  id: string;
  name: string;
  description?: string;
  icon?: string;
}

export interface Provider {
  id: string;
  name: string;
  email: string;
  phone?: string;
  location?: string;
  rating?: number;
  verified: boolean;
}

export interface ListingFilters {
  categoryId?: string;
  providerId?: string;
  minPrice?: number;
  maxPrice?: number;
  location?: string;
  search?: string;
  forRent?: boolean;
  forSale?: boolean;
}

// ===== CART =====
export interface CartItem {
  id: string;
  listingId: string;
  listing: Listing;
  quantity: number;
  isRental: boolean;
  rentalDays?: number;
  rentalStartDate?: string;
  rentalEndDate?: string;
  price: number;
  total: number;
}

export interface Cart {
  items: CartItem[];
  totalItems: number;
  totalPrice: number;
  providerId?: string; // Single provider per cart
}

// ===== ORDERS =====
export type OrderStatus = 'pending' | 'confirmed' | 'processing' | 'completed' | 'cancelled';
export type PaymentStatus = 'pending' | 'paid' | 'failed' | 'refunded';
export type DeliveryMethod = 'delivery' | 'pickup';

export interface Order {
  id: string;
  orderNumber: string;
  status: OrderStatus;
  paymentStatus: PaymentStatus;
  items: CartItem[];
  subtotal: number;
  deliveryFee: number;
  total: number;
  deliveryMethod: DeliveryMethod;
  deliveryAddress?: DeliveryAddress;
  pickupLocation?: string;
  notes?: string;
  createdAt: string;
  updatedAt: string;
  estimatedDeliveryDate?: string;
  invoiceUrl?: string;
}

export interface DeliveryAddress {
  street: string;
  city: string;
  province: string;
  postalCode: string;
  country: string;
  latitude?: number;
  longitude?: number;
}

// ===== CHECKOUT =====
export interface CheckoutData {
  items: CartItem[];
  deliveryMethod: DeliveryMethod;
  deliveryAddress?: DeliveryAddress;
  pickupLocation?: string;
  notes?: string;
  paymentMethod: string;
}

// ===== THEME =====
export type ThemeMode = 'light' | 'dark';

export interface ThemeColors {
  primary: string;
  secondary: string;
  background: string;
  surface: string;
  text: string;
  textSecondary: string;
  border: string;
  error: string;
  success: string;
  warning: string;
  info: string;
}

// ===== API RESPONSES =====
export interface ApiResponse<T> {
  success: boolean;
  data?: T;
  message?: string;
  error?: string;
}

export interface PaginatedResponse<T> {
  data: T[];
  page: number;
  limit: number;
  total: number;
  totalPages: number;
}

// ===== UI STATE =====
export interface LoadingState {
  [key: string]: boolean;
}

export interface ErrorState {
  [key: string]: string | null;
}
