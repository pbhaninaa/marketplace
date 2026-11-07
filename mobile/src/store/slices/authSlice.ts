import { createSlice, createAsyncThunk, PayloadAction } from '@reduxjs/toolkit';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { apiClient } from '../../api/client';
import { CONFIG } from '../../constants/config';
import type { User, AuthState, LoginCredentials, RegisterData, ApiResponse } from '../../types';

const initialState: AuthState = {
  user: null,
  token: null,
  isAuthenticated: false,
  loading: false,
  error: null,
};

// Async thunks
export const login = createAsyncThunk(
  'auth/login',
  async (credentials: LoginCredentials, { rejectWithValue }) => {
    try {
      const response = await apiClient.post<ApiResponse<{ user: User; token: string }>>(
        '/api/auth/login',
        credentials
      );

      if (response.success && response.data) {
        // Save token to storage
        await AsyncStorage.setItem(CONFIG.STORAGE_KEYS.AUTH_TOKEN, response.data.token);
        await AsyncStorage.setItem(CONFIG.STORAGE_KEYS.USER_DATA, JSON.stringify(response.data.user));

        return response.data;
      }

      return rejectWithValue(response.message || 'Login failed');
    } catch (error: any) {
      return rejectWithValue(error.response?.data?.message || 'Login failed');
    }
  }
);

export const register = createAsyncThunk(
  'auth/register',
  async (data: RegisterData, { rejectWithValue }) => {
    try {
      const response = await apiClient.post<ApiResponse<{ user: User; token: string }>>(
        '/api/auth/register',
        data
      );

      if (response.success && response.data) {
        // Save token to storage
        await AsyncStorage.setItem(CONFIG.STORAGE_KEYS.AUTH_TOKEN, response.data.token);
        await AsyncStorage.setItem(CONFIG.STORAGE_KEYS.USER_DATA, JSON.stringify(response.data.user));

        return response.data;
      }

      return rejectWithValue(response.message || 'Registration failed');
    } catch (error: any) {
      return rejectWithValue(error.response?.data?.message || 'Registration failed');
    }
  }
);

export const logout = createAsyncThunk(
  'auth/logout',
  async () => {
    try {
      // Call logout API
      await apiClient.post('/api/auth/logout');

      // Clear storage
      await AsyncStorage.removeItem(CONFIG.STORAGE_KEYS.AUTH_TOKEN);
      await AsyncStorage.removeItem(CONFIG.STORAGE_KEYS.USER_DATA);
      await AsyncStorage.removeItem(CONFIG.STORAGE_KEYS.CART_DATA);

      return null;
    } catch (error: any) {
      // Even if API call fails, clear local data
      await AsyncStorage.removeItem(CONFIG.STORAGE_KEYS.AUTH_TOKEN);
      await AsyncStorage.removeItem(CONFIG.STORAGE_KEYS.USER_DATA);
      await AsyncStorage.removeItem(CONFIG.STORAGE_KEYS.CART_DATA);

      return null;
    }
  }
);

export const restoreSession = createAsyncThunk(
  'auth/restoreSession',
  async (_, { rejectWithValue }) => {
    try {
      const token = await AsyncStorage.getItem(CONFIG.STORAGE_KEYS.AUTH_TOKEN);
      const userData = await AsyncStorage.getItem(CONFIG.STORAGE_KEYS.USER_DATA);

      if (token && userData) {

        // Verify token is still valid
        try {
          const response = await apiClient.get<ApiResponse<User>>('/api/auth/me');
          if (response.success && response.data) {
            return { user: response.data, token };
          }
        } catch (error) {
          // Token is invalid, clear storage
          await AsyncStorage.removeItem(CONFIG.STORAGE_KEYS.AUTH_TOKEN);
          await AsyncStorage.removeItem(CONFIG.STORAGE_KEYS.USER_DATA);
          return rejectWithValue('Session expired');
        }
      }

      return rejectWithValue('No session found');
    } catch (error: any) {
      return rejectWithValue('Failed to restore session');
    }
  }
);

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    clearError: (state) => {
      state.error = null;
    },
    updateUser: (state, action: PayloadAction<Partial<User>>) => {
      if (state.user) {
        state.user = { ...state.user, ...action.payload };
      }
    },
  },
  extraReducers: (builder) => {
    // Login
    builder.addCase(login.pending, (state) => {
      state.loading = true;
      state.error = null;
    });
    builder.addCase(login.fulfilled, (state, action) => {
      state.loading = false;
      state.user = action.payload.user;
      state.token = action.payload.token;
      state.isAuthenticated = true;
      state.error = null;
    });
    builder.addCase(login.rejected, (state, action) => {
      state.loading = false;
      state.error = action.payload as string;
    });

    // Register
    builder.addCase(register.pending, (state) => {
      state.loading = true;
      state.error = null;
    });
    builder.addCase(register.fulfilled, (state, action) => {
      state.loading = false;
      state.user = action.payload.user;
      state.token = action.payload.token;
      state.isAuthenticated = true;
      state.error = null;
    });
    builder.addCase(register.rejected, (state, action) => {
      state.loading = false;
      state.error = action.payload as string;
    });

    // Logout
    builder.addCase(logout.fulfilled, (state) => {
      state.user = null;
      state.token = null;
      state.isAuthenticated = false;
      state.loading = false;
      state.error = null;
    });

    // Restore session
    builder.addCase(restoreSession.pending, (state) => {
      state.loading = true;
    });
    builder.addCase(restoreSession.fulfilled, (state, action) => {
      state.loading = false;
      state.user = action.payload.user;
      state.token = action.payload.token;
      state.isAuthenticated = true;
    });
    builder.addCase(restoreSession.rejected, (state) => {
      state.loading = false;
      state.isAuthenticated = false;
    });
  },
});

export const { clearError, updateUser } = authSlice.actions;
export default authSlice.reducer;
