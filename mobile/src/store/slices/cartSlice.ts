import { createSlice, createAsyncThunk, PayloadAction } from '@reduxjs/toolkit';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { CONFIG } from '../../constants/config';
import type { Cart, CartItem, Listing } from '../../types';

interface CartState extends Cart {
  loading: boolean;
  error: string | null;
}

const initialState: CartState = {
  items: [],
  totalItems: 0,
  totalPrice: 0,
  loading: false,
  error: null,
};

export const loadCart = createAsyncThunk(
  'cart/load',
  async () => {
    const savedCart = await AsyncStorage.getItem(CONFIG.STORAGE_KEYS.CART_DATA);
    if (savedCart) {
      return JSON.parse(savedCart) as Cart;
    }
    return initialState;
  }
);

export const saveCartToStorage = createAsyncThunk(
  'cart/save',
  async (cart: Cart) => {
    await AsyncStorage.setItem(CONFIG.STORAGE_KEYS.CART_DATA, JSON.stringify(cart));
    return cart;
  }
);

const cartSlice = createSlice({
  name: 'cart',
  initialState,
  reducers: {
    addToCart: (state, action: PayloadAction<{ listing: Listing; quantity: number; isRental: boolean; rentalDays?: number }>) => {
      const { listing, quantity, isRental, rentalDays } = action.payload;

      // Check if item already exists
      const existingItem = state.items.find(
        item => item.listingId === listing.id && item.isRental === isRental
      );

      const price = isRental && listing.rentPrice ? listing.rentPrice : listing.price;
      const total = price * quantity * (isRental && rentalDays ? rentalDays : 1);

      if (existingItem) {
        existingItem.quantity += quantity;
        existingItem.total = price * existingItem.quantity * (isRental && rentalDays ? rentalDays : 1);
      } else {
        const newItem: CartItem = {
          id: `${listing.id}-${isRental ? 'rent' : 'buy'}-${Date.now()}`,
          listingId: listing.id,
          listing,
          quantity,
          isRental,
          rentalDays,
          price,
          total,
        };
        state.items.push(newItem);
      }

      // Recalculate totals
      state.totalItems = state.items.reduce((sum, item) => sum + item.quantity, 0);
      state.totalPrice = state.items.reduce((sum, item) => sum + item.total, 0);

      // Set provider ID (single provider per cart)
      if (!state.providerId) {
        state.providerId = listing.providerId;
      }
    },

    removeFromCart: (state, action: PayloadAction<string>) => {
      state.items = state.items.filter(item => item.id !== action.payload);

      // Recalculate totals
      state.totalItems = state.items.reduce((sum, item) => sum + item.quantity, 0);
      state.totalPrice = state.items.reduce((sum, item) => sum + item.total, 0);

      // Clear provider if cart is empty
      if (state.items.length === 0) {
        state.providerId = undefined;
      }
    },

    updateQuantity: (state, action: PayloadAction<{ id: string; quantity: number }>) => {
      const item = state.items.find(i => i.id === action.payload.id);
      if (item) {
        item.quantity = action.payload.quantity;
        item.total = item.price * item.quantity * (item.isRental && item.rentalDays ? item.rentalDays : 1);

        // Recalculate totals
        state.totalItems = state.items.reduce((sum, i) => sum + i.quantity, 0);
        state.totalPrice = state.items.reduce((sum, i) => sum + i.total, 0);
      }
    },

    clearCart: (state) => {
      state.items = [];
      state.totalItems = 0;
      state.totalPrice = 0;
      state.providerId = undefined;
      state.error = null;
    },

    setError: (state, action: PayloadAction<string>) => {
      state.error = action.payload;
    },

    clearError: (state) => {
      state.error = null;
    },
  },
  extraReducers: (builder) => {
    builder.addCase(loadCart.fulfilled, (state, action) => {
      state.items = action.payload.items;
      state.totalItems = action.payload.totalItems;
      state.totalPrice = action.payload.totalPrice;
      state.providerId = action.payload.providerId;
      state.loading = false;
    });
    builder.addCase(saveCartToStorage.fulfilled, (state) => {
      state.loading = false;
    });
  },
});

export const {
  addToCart,
  removeFromCart,
  updateQuantity,
  clearCart,
  setError,
  clearError,
} = cartSlice.actions;
export default cartSlice.reducer;
