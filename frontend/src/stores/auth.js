import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { setAuthToken } from '../api';

const TOKEN_KEY = 'agri_token';
const ROLE_KEY = 'agri_role';
const EMAIL_KEY = 'agri_email';
const DISPLAY_NAME_KEY = 'agri_display_name';
const PROVIDER_KEY = 'agri_provider_id';
const SHADOW_BACKUP_KEY = 'agri_shadow_backup';
const PROVIDER_PLAN_KEY = 'agri_provider_plan';
const PROVIDER_SUB_VALID_KEY = 'agri_provider_sub_valid';

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem(TOKEN_KEY) || '');
  const role = ref(localStorage.getItem(ROLE_KEY) || '');
  const email = ref(localStorage.getItem(EMAIL_KEY) || '');
  const displayName = ref(localStorage.getItem(DISPLAY_NAME_KEY) || '');
  const providerId = ref(localStorage.getItem(PROVIDER_KEY) || '');
  const providerPlan = ref(localStorage.getItem(PROVIDER_PLAN_KEY) || '');
  const providerSubValid = ref(localStorage.getItem(PROVIDER_SUB_VALID_KEY) === 'true');

  const displayLabel = computed(() => displayName.value?.trim() || email.value || '');

  const isAuthenticated = computed(() => !!token.value);
  const isPlatformAdmin = computed(() => role.value === 'PLATFORM_ADMIN');
  const isSupport = computed(() => role.value === 'SUPPORT');
  const isShadowing = computed(() => !!localStorage.getItem(SHADOW_BACKUP_KEY));
  const isProviderUser = computed(() =>
    ['PROVIDER_OWNER', 'PROVIDER_ADMIN', 'PROVIDER_STAFF', 'PROVIDER_VIEWER'].includes(role.value),
  );
  const canManageStaff = computed(() => role.value === 'PROVIDER_OWNER' || role.value === 'PROVIDER_ADMIN');
  const isClientUser = computed(() => role.value === 'CLIENT');
  const isPremiumPlan = computed(() => String(providerPlan.value || '').toUpperCase() === 'PREMIUM');

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

  function beginShadow(login) {
    // Backup the current session once, so the support/admin user can return.
    if (!localStorage.getItem(SHADOW_BACKUP_KEY)) {
      localStorage.setItem(
        SHADOW_BACKUP_KEY,
        JSON.stringify({
          token: token.value,
          role: role.value,
          email: email.value,
          displayName: displayName.value,
          providerId: providerId.value ? Number(providerId.value) : null,
        }),
      );
    }
    setSession(login);
  }

  function endShadow() {
    const raw = localStorage.getItem(SHADOW_BACKUP_KEY);
    if (!raw) return;
    try {
      const prev = JSON.parse(raw);
      setSession(prev);
    } finally {
      localStorage.removeItem(SHADOW_BACKUP_KEY);
    }
  }

  function logout() {
    applyToken('');
    role.value = '';
    email.value = '';
    displayName.value = '';
    providerId.value = '';
    providerPlan.value = '';
    providerSubValid.value = false;
    localStorage.removeItem(ROLE_KEY);
    localStorage.removeItem(EMAIL_KEY);
    localStorage.removeItem(DISPLAY_NAME_KEY);
    localStorage.removeItem(PROVIDER_KEY);
    localStorage.removeItem(SHADOW_BACKUP_KEY);
    localStorage.removeItem(PROVIDER_PLAN_KEY);
    localStorage.removeItem(PROVIDER_SUB_VALID_KEY);
  }

  function restoreFromStorage() {
    if (token.value) {
      setAuthToken(token.value);
    }
  }

  function setProviderSubscriptionStatus(status) {
    // status is from /api/provider/me/subscription/status
    providerPlan.value = status?.plan ? String(status.plan) : '';
    providerSubValid.value = !!status?.valid;
    if (providerPlan.value) {
      localStorage.setItem(PROVIDER_PLAN_KEY, providerPlan.value);
    } else {
      localStorage.removeItem(PROVIDER_PLAN_KEY);
    }
    localStorage.setItem(PROVIDER_SUB_VALID_KEY, providerSubValid.value ? 'true' : 'false');
  }

  return {
    token,
    role,
    email,
    displayName,
    displayLabel,
    providerId,
    providerPlan,
    providerSubValid,
    isAuthenticated,
    isPlatformAdmin,
    isSupport,
    isShadowing,
    isProviderUser,
    canManageStaff,
    isClientUser,
    isPremiumPlan,
    setSession,
    beginShadow,
    endShadow,
    logout,
    restoreFromStorage,
    setProviderSubscriptionStatus,
  };
});
