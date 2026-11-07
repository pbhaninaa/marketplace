import { createSlice, createAsyncThunk, PayloadAction } from '@reduxjs/toolkit';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { CONFIG } from '../../constants/config';
import type { ThemeMode } from '../../types';

interface ThemeState {
  mode: ThemeMode;
}

const initialState: ThemeState = {
  mode: 'light',
};

export const loadTheme = createAsyncThunk(
  'theme/load',
  async () => {
    const savedTheme = await AsyncStorage.getItem(CONFIG.STORAGE_KEYS.THEME_MODE);
    return (savedTheme as ThemeMode) || 'light';
  }
);

export const saveTheme = createAsyncThunk(
  'theme/save',
  async (mode: ThemeMode) => {
    await AsyncStorage.setItem(CONFIG.STORAGE_KEYS.THEME_MODE, mode);
    return mode;
  }
);

const themeSlice = createSlice({
  name: 'theme',
  initialState,
  reducers: {
    setTheme: (state, action: PayloadAction<ThemeMode>) => {
      state.mode = action.payload;
    },
    toggleTheme: (state) => {
      state.mode = state.mode === 'light' ? 'dark' : 'light';
    },
  },
  extraReducers: (builder) => {
    builder.addCase(loadTheme.fulfilled, (state, action) => {
      state.mode = action.payload;
    });
    builder.addCase(saveTheme.fulfilled, (state, action) => {
      state.mode = action.payload;
    });
  },
});

export const { setTheme, toggleTheme } = themeSlice.actions;
export default themeSlice.reducer;
