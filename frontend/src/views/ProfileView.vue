<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { authApi, profileApi } from '../services/marketplaceApi';
import { useAuthStore } from '../stores/auth';
import FormField from '../components/ui/FormField.vue';

const auth = useAuthStore();

const ROLE_LABELS = {
  PLATFORM_ADMIN: 'Platform admin',
  SUPPORT: 'Support',
  PROVIDER_OWNER: 'Provider owner',
  PROVIDER_ADMIN: 'Provider admin',
  PROVIDER_STAFF: 'Provider staff',
  PROVIDER_VIEWER: 'Provider viewer',
  CLIENT: 'Customer',
};

/** OTP customers have no stored account, so there is nothing to load or edit server-side. */
const hasAccount = computed(() => auth.isAuthenticated && !auth.isClientUser);
const readOnly = computed(() => auth.isShadowing);

const loading = ref(false);
const loadError = ref('');
const profile = ref(null);

const details = reactive({ firstName: '', lastName: '', displayName: '', phoneNumber: '' });
const savedDetails = ref({ ...details });
const detailsSaving = ref(false);
const detailsError = ref('');
const detailsMessage = ref('');

const pw = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' });
const showPasswords = ref(false);
const pwSaving = ref(false);
const pwError = ref('');
const pwMessage = ref('');

const roleLabel = computed(() => ROLE_LABELS[profile.value?.role || auth.role] || 'Signed in');

const fullName = computed(() => {
  const p = profile.value;
  const name = [p?.firstName, p?.lastName].filter(Boolean).join(' ').trim();
  return p?.displayName || name || auth.displayLabel || 'Your profile';
});

const initials = computed(() => {
  const source = fullName.value.includes('@') ? fullName.value.split('@')[0] : fullName.value;
  const parts = source.split(/[\s._-]+/).filter(Boolean);
  return ((parts[0]?.[0] || '') + (parts[1]?.[0] || '')).toUpperCase() || '?';
});

const memberSince = computed(() => {
  if (!profile.value?.createdAt) return '';
  try {
    return new Date(profile.value.createdAt).toLocaleDateString(undefined, {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
    });
  } catch {
    return '';
  }
});

const detailsDirty = computed(() =>
  Object.keys(details).some((k) => (details[k] || '').trim() !== (savedDetails.value[k] || '').trim()),
);

const phoneError = computed(() =>
  details.phoneNumber && !/^[+0-9 ()-]*$/.test(details.phoneNumber)
    ? 'Use digits, spaces, plus signs, dashes or brackets only'
    : '',
);

const pwProblem = computed(() => {
  if (!pw.newPassword) return '';
  if (pw.newPassword.length < 8) return 'New password must be at least 8 characters';
  if (pw.confirmPassword && pw.confirmPassword !== pw.newPassword) return 'Passwords do not match';
  if (pw.currentPassword && pw.currentPassword === pw.newPassword) return 'Choose a different password than your current one';
  return '';
});

const pwReady = computed(
  () => pw.currentPassword && pw.newPassword && pw.confirmPassword && !pwProblem.value,
);

function errorMessage(e, fallback) {
  const data = e?.response?.data;
  if (data?.fieldErrors && typeof data.fieldErrors === 'object') {
    const first = Object.entries(data.fieldErrors)[0];
    if (first) return `${first[0]}: ${first[1]}`;
  }
  return data?.message || e?.message || fallback;
}

function applyProfile(p) {
  profile.value = p;
  details.firstName = p?.firstName || '';
  details.lastName = p?.lastName || '';
  details.displayName = p?.displayName || '';
  details.phoneNumber = p?.phoneNumber || '';
  savedDetails.value = { ...details };
}

async function load() {
  if (!hasAccount.value) return;
  loading.value = true;
  loadError.value = '';
  try {
    const { data } = await profileApi.get();
    applyProfile(data);
  } catch (e) {
    loadError.value = errorMessage(e, 'Could not load your profile.');
  } finally {
    loading.value = false;
  }
}

async function saveDetails() {
  if (!detailsDirty.value || phoneError.value || readOnly.value) return;
  detailsSaving.value = true;
  detailsError.value = '';
  detailsMessage.value = '';
  try {
    const { data } = await profileApi.update({ ...details });
    applyProfile(data);
    const shownName =
      data.displayName || [data.firstName, data.lastName].filter(Boolean).join(' ').trim();
    auth.setDisplayName(shownName);
    detailsMessage.value = 'Your details were saved.';
  } catch (e) {
    detailsError.value = errorMessage(e, 'Could not save your details.');
  } finally {
    detailsSaving.value = false;
  }
}

function resetDetails() {
  Object.assign(details, savedDetails.value);
  detailsError.value = '';
  detailsMessage.value = '';
}

async function changePassword() {
  if (!pwReady.value || readOnly.value) return;
  pwSaving.value = true;
  pwError.value = '';
  pwMessage.value = '';
  try {
    await authApi.changePassword({
      currentPassword: pw.currentPassword,
      newPassword: pw.newPassword,
    });
    pw.currentPassword = '';
    pw.newPassword = '';
    pw.confirmPassword = '';
    pwMessage.value = 'Password updated. Use it next time you sign in.';
  } catch (e) {
    pwError.value = errorMessage(e, 'Could not change your password.');
  } finally {
    pwSaving.value = false;
  }
}

onMounted(() => {
  auth.restoreFromStorage();
  load();
});
</script>

<template>
  <div class="page-document profile-page">
    <header class="profile-hero surface-panel">
      <div class="profile-hero__avatar" aria-hidden="true">{{ initials }}</div>
      <div class="profile-hero__text">
        <h1 class="profile-hero__name">{{ fullName }}</h1>
        <p class="profile-hero__email">{{ profile?.email || auth.email }}</p>
        <div class="profile-hero__chips">
          <span class="profile-chip">{{ roleLabel }}</span>
          <span v-if="profile?.providerName" class="profile-chip profile-chip--soft">
            {{ profile.providerName }}
          </span>
        </div>
        <p v-if="memberSince" class="muted small profile-hero__since">Member since {{ memberSince }}</p>
      </div>
    </header>

    <p v-if="readOnly" class="profile-note" role="status">
      You're viewing this workspace as support. Profile changes are turned off while shadowing.
    </p>

    <!-- OTP customers: nothing stored server-side -->
    <section v-if="!hasAccount" class="surface-panel profile-section">
      <h2>Your access</h2>
      <p class="muted">
        You signed in with a code sent to <strong>{{ auth.email }}</strong>.
        There's no password to manage. Request a new code whenever you need to sign in again.
      </p>
      <div class="profile-links">
        <router-link to="/order-invoice" class="profile-link">
          <span class="material-icons" aria-hidden="true">receipt_long</span>
          <span>Look up an order invoice</span>
        </router-link>
        <router-link to="/settings" class="profile-link">
          <span class="material-icons" aria-hidden="true">settings</span>
          <span>App settings</span>
        </router-link>
        <router-link to="/help/client" class="profile-link">
          <span class="material-icons" aria-hidden="true">help_outline</span>
          <span>Help center</span>
        </router-link>
      </div>
    </section>

    <template v-else>
      <div v-if="loading && !profile" class="surface-panel profile-section profile-skeleton" aria-busy="true">
        <span class="sr-only">Loading your profile…</span>
        <div class="skeleton-line"></div>
        <div class="skeleton-line skeleton-line--short"></div>
        <div class="skeleton-line"></div>
      </div>

      <div v-else-if="loadError" class="surface-panel profile-section">
        <p class="err-toast" role="alert">{{ loadError }}</p>
        <button type="button" class="btn btn-ghost" @click="load">Try again</button>
      </div>

      <form v-else class="surface-panel profile-section" novalidate @submit.prevent="saveDetails">
        <h2>Personal details</h2>
        <p class="muted small profile-section__lead">
          Your display name is what others see on orders, notifications and team lists.
        </p>

        <div class="profile-grid">
          <FormField label="First name" for-id="profile-first">
            <input
              id="profile-first"
              v-model="details.firstName"
              type="text"
              maxlength="100"
              autocomplete="given-name"
              :disabled="readOnly"
            />
          </FormField>
          <FormField label="Last name" for-id="profile-last">
            <input
              id="profile-last"
              v-model="details.lastName"
              type="text"
              maxlength="100"
              autocomplete="family-name"
              :disabled="readOnly"
            />
          </FormField>
          <FormField label="Display name" for-id="profile-display">
            <input
              id="profile-display"
              v-model="details.displayName"
              type="text"
              maxlength="200"
              autocomplete="nickname"
              :placeholder="[details.firstName, details.lastName].filter(Boolean).join(' ') || 'How your name appears'"
              :disabled="readOnly"
            />
          </FormField>
          <FormField label="Phone number" for-id="profile-phone" :error="phoneError">
            <input
              id="profile-phone"
              v-model="details.phoneNumber"
              type="tel"
              inputmode="tel"
              maxlength="40"
              autocomplete="tel"
              placeholder="+27 82 123 4567"
              :disabled="readOnly"
            />
          </FormField>
          <FormField label="Email" for-id="profile-email" class="profile-grid__full">
            <input id="profile-email" :value="profile?.email || auth.email" type="email" readonly disabled />
          </FormField>
        </div>
        <p class="muted small">You sign in with this email. Contact support to change it.</p>

        <p v-if="detailsError" class="err-toast" role="alert">{{ detailsError }}</p>
        <p v-if="detailsMessage" class="ok-msg" role="status">{{ detailsMessage }}</p>

        <div class="profile-actions">
          <button
            type="submit"
            class="btn btn-primary"
            :disabled="readOnly || detailsSaving || !detailsDirty || !!phoneError"
          >
            {{ detailsSaving ? 'Saving…' : 'Save changes' }}
          </button>
          <button
            v-if="detailsDirty && !detailsSaving"
            type="button"
            class="btn btn-ghost"
            @click="resetDetails"
          >
            Discard
          </button>
        </div>
      </form>

      <form class="surface-panel profile-section" novalidate @submit.prevent="changePassword">
        <div class="profile-section__head">
          <h2>Password</h2>
          <button
            type="button"
            class="btn btn-ghost btn-xs"
            :aria-pressed="showPasswords"
            @click="showPasswords = !showPasswords"
          >
            {{ showPasswords ? 'Hide' : 'Show' }} passwords
          </button>
        </div>

        <!-- Hidden username helps password managers pair the new password with this account -->
        <input
          type="text"
          name="username"
          autocomplete="username"
          :value="profile?.email || auth.email"
          class="sr-only"
          tabindex="-1"
          aria-hidden="true"
          readonly
        />

        <div class="profile-grid">
          <FormField label="Current password" for-id="pw-current" class="profile-grid__full">
            <input
              id="pw-current"
              v-model="pw.currentPassword"
              :type="showPasswords ? 'text' : 'password'"
              autocomplete="current-password"
              maxlength="100"
              :disabled="readOnly"
            />
          </FormField>
          <FormField label="New password" for-id="pw-new">
            <input
              id="pw-new"
              v-model="pw.newPassword"
              :type="showPasswords ? 'text' : 'password'"
              autocomplete="new-password"
              minlength="8"
              maxlength="100"
              :disabled="readOnly"
            />
          </FormField>
          <FormField label="Confirm new password" for-id="pw-confirm">
            <input
              id="pw-confirm"
              v-model="pw.confirmPassword"
              :type="showPasswords ? 'text' : 'password'"
              autocomplete="new-password"
              maxlength="100"
              :disabled="readOnly"
            />
          </FormField>
        </div>
        <p class="muted small" :class="{ 'profile-hint--warn': pwProblem }">
          {{ pwProblem || 'At least 8 characters.' }}
        </p>

        <p v-if="pwError" class="err-toast" role="alert">{{ pwError }}</p>
        <p v-if="pwMessage" class="ok-msg" role="status">{{ pwMessage }}</p>

        <div class="profile-actions">
          <button type="submit" class="btn btn-primary" :disabled="readOnly || pwSaving || !pwReady">
            {{ pwSaving ? 'Updating…' : 'Update password' }}
          </button>
        </div>
      </form>
    </template>

    <section class="surface-panel profile-section profile-section--signout">
      <div>
        <h2>Sign out</h2>
        <p class="muted small">Sign out of Agri Marketplace on this device.</p>
      </div>
      <button type="button" class="btn btn-ghost profile-signout" @click="auth.logout()">
        <span class="material-icons" aria-hidden="true">logout</span>
        Sign out
      </button>
    </section>
  </div>
</template>

<style scoped>
.profile-page {
  max-width: 760px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.profile-hero {
  display: flex;
  align-items: center;
  gap: var(--space-5);
}

.profile-hero__avatar {
  flex-shrink: 0;
  width: 4.5rem;
  height: 4.5rem;
  border-radius: 50%;
  display: grid;
  place-items: center;
  font-family: var(--font-display);
  font-size: 1.6rem;
  font-weight: 800;
  letter-spacing: 0.02em;
  color: #fff;
  background: linear-gradient(135deg, var(--color-sage), var(--color-canopy));
  box-shadow: var(--shadow-sm);
}

.profile-hero__text {
  min-width: 0;
}

.profile-hero__name {
  margin: 0;
  font-size: 1.6rem;
  line-height: 1.2;
  overflow-wrap: anywhere;
}

.profile-hero__email {
  margin: 0.2rem 0 0;
  color: var(--color-muted);
  overflow-wrap: anywhere;
}

.profile-hero__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
  margin-top: 0.6rem;
}

.profile-chip {
  display: inline-flex;
  align-items: center;
  padding: 0.2rem 0.65rem;
  border-radius: var(--radius-pill);
  font-size: 0.78rem;
  font-weight: 700;
  background: var(--color-canopy);
  color: #fff;
}

.profile-chip--soft {
  background: var(--color-sage-soft);
  color: var(--color-canopy);
}

.profile-hero__since {
  margin: 0.5rem 0 0;
}

.profile-note {
  margin: 0;
  padding: 0.75rem 1rem;
  border-radius: var(--radius-md);
  background: var(--color-info-bg);
  color: var(--color-info-text);
}

.profile-section {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.profile-section h2 {
  margin: 0;
}

.profile-section__lead {
  margin: -0.25rem 0 0;
}

.profile-section__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  flex-wrap: wrap;
}

.profile-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 var(--space-4);
}

.profile-grid__full {
  grid-column: 1 / -1;
}

.profile-grid input:disabled,
.profile-grid input[readonly] {
  opacity: 0.75;
  cursor: not-allowed;
}

.profile-hint--warn {
  color: var(--color-danger-text);
}

.profile-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.profile-actions .btn + .btn {
  margin-left: 0;
}

.profile-links {
  display: flex;
  flex-direction: column;
  gap: 1px;
  border-radius: var(--radius-md);
  overflow: hidden;
  border: 1px solid var(--color-border);
}

.profile-link {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  min-height: 3rem;
  padding: 0.7rem 1rem;
  color: var(--color-canopy);
  font-weight: 600;
  background: var(--color-surface);
  text-decoration: none;
}

.profile-link + .profile-link {
  border-top: 1px solid var(--color-border);
}

.profile-link:hover {
  background: var(--color-sage-soft);
  text-decoration: none;
}

.profile-section--signout {
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
}

.profile-section--signout p {
  margin: 0.25rem 0 0;
}

.profile-signout {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  color: var(--color-earth);
}

.profile-signout .material-icons {
  font-size: 1.15rem;
}

.profile-skeleton {
  gap: var(--space-3);
}

.skeleton-line {
  height: 1rem;
  border-radius: 6px;
  background: linear-gradient(90deg, var(--color-sage-soft), var(--color-border), var(--color-sage-soft));
  background-size: 200% 100%;
  animation: profile-shimmer 1.2s ease-in-out infinite;
}

.skeleton-line--short {
  width: 55%;
}

@keyframes profile-shimmer {
  from {
    background-position: 100% 0;
  }
  to {
    background-position: -100% 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .skeleton-line {
    animation: none;
  }
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

@media (max-width: 640px) {
  .profile-page {
    gap: var(--space-4);
  }

  .profile-hero {
    flex-direction: column;
    text-align: center;
    gap: var(--space-3);
  }

  .profile-hero__chips {
    justify-content: center;
  }

  .profile-hero__name {
    font-size: 1.35rem;
  }

  .profile-grid {
    grid-template-columns: 1fr;
  }

  .profile-actions .btn,
  .profile-section--signout .profile-signout {
    flex: 1 1 100%;
    justify-content: center;
  }
}
</style>
