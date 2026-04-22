import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { setAuthToken } from '../api';

const TOKEN_KEY = 'agri_token';
const ROLE_KEY = 'agri_role';
const EMAIL_KEY = 'agri_email';
const DISPLAY_NAME_KEY = 'agri_display_name';
const PROVIDER_KEY = 'agri_provider_id';

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem(TOKEN_KEY) || '');
  const role = ref(localStorage.getItem(ROLE_KEY) || '');
  const email = ref(localStorage.getItem(EMAIL_KEY) || '');
  const displayName = ref(localStorage.getItem(DISPLAY_NAME_KEY) || '');
  const providerId = ref(localStorage.getItem(PROVIDER_KEY) || '');

  const displayLabel = computed(() => displayName.value?.trim() || email.value || '');

  const isAuthenticated = computed(() => !!token.value);
  const isPlatformAdmin = computed(() => role.value === 'PLATFORM_ADMIN');
  const isSupport = computed(() => role.value === 'SUPPORT');
  const isProviderUser = computed(() =>
    ['PROVIDER_OWNER', 'PROVIDER_ADMIN', 'PROVIDER_STAFF', 'PROVIDER_VIEWER'].includes(role.value),
  );
  const canManageStaff = computed(() => role.value === 'PROVIDER_OWNER' || role.value === 'PROVIDER_ADMIN');
  const isClientUser = computed(() => role.value === 'CLIENT');

  function applyToken(t) {
    token.value = t;
    if (t) {
      localStorage.setItem(TOKEN_KEY, t);
      setAuthToken(t);
    } else {
      localStorage.removeItem(TOKEN_KEY);
      setAuthToken(null);
    }
  }

  function setSession(login) {
    applyToken(login.token);
    role.value = login.role;
    email.value = login.email || '';
    displayName.value = login.displayName?.trim() || '';
    providerId.value = login.providerId != null ? String(login.providerId) : '';
    localStorage.setItem(ROLE_KEY, role.value);
    localStorage.setItem(EMAIL_KEY, email.value);
    if (displayName.value) {
      localStorage.setItem(DISPLAY_NAME_KEY, displayName.value);
    } else {
      localStorage.removeItem(DISPLAY_NAME_KEY);
    }
    if (login.providerId != null) {
      localStorage.setItem(PROVIDER_KEY, String(login.providerId));
    } else {
      localStorage.removeItem(PROVIDER_KEY);
    }
  }

  function logout() {
    applyToken('');
    role.value = '';
    email.value = '';
    displayName.value = '';
    providerId.value = '';
    localStorage.removeItem(ROLE_KEY);
    localStorage.removeItem(EMAIL_KEY);
    localStorage.removeItem(DISPLAY_NAME_KEY);
    localStorage.removeItem(PROVIDER_KEY);
  }

  function restoreFromStorage() {
    if (token.value) {
      setAuthToken(token.value);
    }
  }

  return {
    token,
    role,
    email,
    displayName,
    displayLabel,
    providerId,
    isAuthenticated,
    isPlatformAdmin,
    isSupport,
    isProviderUser,
    canManageStaff,
    isClientUser,
    setSession,
    logout,
    restoreFromStorage,
  };
});
