<script setup>
import { computed } from 'vue';
import { useAuthStore } from '../stores/auth';
import { useThemeStore } from '../stores/theme';

const auth = useAuthStore();
const theme = useThemeStore();

auth.restoreFromStorage();

const isLoggedIn = computed(() => auth.isAuthenticated);
const userLabel = computed(() => auth.displayLabel || 'Signed in');

const themeOptions = [
  { value: 'light', label: 'Light', icon: 'light_mode' },
  { value: 'dark', label: 'Dark', icon: 'dark_mode' },
];
</script>

<template>
  <div class="page-document settings-page">
    <header class="page-hero">
      <h1 class="page-hero__title">Settings</h1>
      <p class="page-hero__lead">Manage your account, preferences, and get help.</p>
    </header>

    <section class="surface-panel settings-section" aria-labelledby="settings-account">
      <h2 id="settings-account">Account</h2>
      <div class="settings-list">
        <router-link v-if="isLoggedIn" to="/profile" class="setting-item setting-item--action">
          <span class="material-icons setting-icon" aria-hidden="true">account_circle</span>
          <span class="setting-info">
            <strong>Profile</strong>
            <span class="muted small">{{ userLabel }}</span>
          </span>
          <span class="material-icons setting-arrow" aria-hidden="true">chevron_right</span>
        </router-link>

        <router-link
          v-else
          :to="{ path: '/login', query: { redirect: '/profile' } }"
          class="setting-item setting-item--action"
        >
          <span class="material-icons setting-icon" aria-hidden="true">login</span>
          <span class="setting-info">
            <strong>Sign in</strong>
            <span class="muted small">Access your profile, orders and saved information</span>
          </span>
          <span class="material-icons setting-arrow" aria-hidden="true">chevron_right</span>
        </router-link>

        <button v-if="isLoggedIn" type="button" class="setting-item setting-item--action" @click="auth.logout()">
          <span class="material-icons setting-icon setting-icon--warn" aria-hidden="true">logout</span>
          <span class="setting-info">
            <strong>Sign out</strong>
            <span class="muted small">Sign out on this device</span>
          </span>
        </button>
      </div>
    </section>

    <section class="surface-panel settings-section" aria-labelledby="settings-appearance">
      <h2 id="settings-appearance">Appearance</h2>
      <div class="setting-item setting-item--static">
        <span class="material-icons setting-icon" aria-hidden="true">palette</span>
        <span class="setting-info">
          <strong>Theme</strong>
          <span class="muted small">Choose how Agri Marketplace looks on this device</span>
        </span>
      </div>
      <div class="theme-choice" role="radiogroup" aria-label="Theme">
        <button
          v-for="opt in themeOptions"
          :key="opt.value"
          type="button"
          role="radio"
          class="theme-choice__btn"
          :class="{ 'theme-choice__btn--active': theme.resolvedMode === opt.value }"
          :aria-checked="theme.resolvedMode === opt.value"
          @click="theme.setMode(opt.value)"
        >
          <span class="material-icons" aria-hidden="true">{{ opt.icon }}</span>
          {{ opt.label }}
        </button>
      </div>
    </section>

    <section class="surface-panel settings-section" aria-labelledby="settings-help">
      <h2 id="settings-help">Help &amp; support</h2>
      <div class="settings-list">
        <router-link to="/help/client" class="setting-item setting-item--action">
          <span class="material-icons setting-icon" aria-hidden="true">help_outline</span>
          <span class="setting-info">
            <strong>Help center</strong>
            <span class="muted small">Guides for browsing, checkout, and invoices</span>
          </span>
          <span class="material-icons setting-arrow" aria-hidden="true">chevron_right</span>
        </router-link>
        <router-link to="/order-invoice" class="setting-item setting-item--action">
          <span class="material-icons setting-icon" aria-hidden="true">receipt_long</span>
          <span class="setting-info">
            <strong>Order invoice</strong>
            <span class="muted small">Find an invoice with your order number</span>
          </span>
          <span class="material-icons setting-arrow" aria-hidden="true">chevron_right</span>
        </router-link>
      </div>
    </section>

    <section class="surface-panel settings-section" aria-labelledby="settings-about">
      <h2 id="settings-about">About</h2>
      <div class="settings-list">
        <div class="setting-item setting-item--static">
          <span class="material-icons setting-icon" aria-hidden="true">agriculture</span>
          <span class="setting-info">
            <strong>Agri Marketplace</strong>
            <span class="muted small">Equipment &amp; stock: rental &amp; resale · Version 1.0.3</span>
          </span>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.settings-page {
  max-width: 760px;
  margin: 0 auto;
  padding-bottom: 2rem;
}

.settings-section {
  margin-bottom: var(--space-5);
}

.settings-section h2 {
  font-size: 1.15rem;
  margin: 0 0 var(--space-3);
}

.settings-list {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.setting-item {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  width: 100%;
  min-height: 3.5rem;
  padding: var(--space-3) var(--space-4);
  background: var(--color-surface);
  color: var(--color-text);
  border: none;
  font: inherit;
  text-align: left;
  text-decoration: none;
  box-sizing: border-box;
}

.settings-list .setting-item + .setting-item {
  border-top: 1px solid var(--color-border);
}

.setting-item--static {
  padding-left: 0;
  padding-right: 0;
  background: transparent;
}

.setting-item--action {
  cursor: pointer;
  transition: background 0.15s ease;
}

.setting-item--action:hover {
  background: var(--color-sage-soft);
  text-decoration: none;
}

.setting-item--action:active {
  background: var(--color-sage-soft);
  filter: brightness(0.97);
}

.setting-icon {
  flex-shrink: 0;
  font-size: 1.35rem;
  color: var(--color-sage);
}

.setting-icon--warn {
  color: var(--color-earth);
}

.setting-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.setting-info strong {
  font-size: 1rem;
  color: var(--color-text);
}

.setting-info .muted {
  overflow-wrap: anywhere;
}

.setting-arrow {
  flex-shrink: 0;
  color: var(--color-muted);
}

.theme-choice {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-2);
  padding: 0.3rem;
  border-radius: var(--radius-md);
  background: var(--color-sage-soft);
}

.theme-choice__btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.4rem;
  min-height: 2.75rem;
  border: 1px solid transparent;
  border-radius: calc(var(--radius-md) - 4px);
  background: transparent;
  color: var(--color-canopy);
  font: inherit;
  font-weight: 600;
  cursor: pointer;
}

.theme-choice__btn .material-icons {
  font-size: 1.15rem;
}

.theme-choice__btn--active {
  background: var(--color-surface-elevated);
  border-color: var(--color-border);
  box-shadow: var(--shadow-sm);
}

@media (max-width: 640px) {
  .settings-section {
    margin-bottom: var(--space-4);
  }

  .setting-item {
    padding: var(--space-3);
  }

  .setting-item--static {
    padding-left: 0;
    padding-right: 0;
  }
}
</style>
