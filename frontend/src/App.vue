<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { useSessionStore } from './stores/session';
import { useSetupStore } from './stores/setup';
import { useAuthStore } from './stores/auth';
import { useCartStore } from './stores/cart';
import { useThemeStore } from './stores/theme';
import { useDialog } from './composables/useDialog';
import DialogModal from './components/ui/DialogModal.vue';
import NotificationBell from './components/NotificationBell.vue';

const route = useRoute();
const session = useSessionStore();
const setup = useSetupStore();
const auth = useAuthStore();
const cart = useCartStore();
const theme = useThemeStore();
const { dialogState } = useDialog();

const menuOpen = ref(false);

const closeMenu = () => {
  menuOpen.value = false;
};

const toggleMenu = () => {
  menuOpen.value = !menuOpen.value;
};

const brandHome = computed(() => {
  if (!auth.isAuthenticated) return '/';
  if (auth.isPlatformAdmin) return '/admin';
  if (auth.isSupport) return '/support';
  if (auth.isProviderUser) return '/provider';
  return '/';
});

const workspaceLabel = computed(() => {
  if (auth.isPlatformAdmin) return 'Platform admin';
  if (auth.isSupport) return 'Support';
  if (auth.isProviderUser) return 'Provider workspace';
  if (auth.isClientUser) return 'Customer';
  return 'Menu';
});

watch(
  () => route.fullPath,
  () => {
    menuOpen.value = false;
  },
);

onMounted(async () => {
  theme.init();
  await session.ensureSession();
  try {
    await setup.fetchStatus();
  } catch {
    /* leave needsFirstAdmin unchanged if API is unreachable */
  }
  auth.restoreFromStorage();
  auth.listenForForceLogout();
  try {
    await cart.refresh();
  } catch {
    /* cart optional if API unreachable */
  }
});
</script>

<template>
  <div class="app-shell">
    <div v-if="setup.needsFirstAdmin === true" class="setup-banner" role="status">
      <span class="setup-banner__text">No platform administrator exists yet.</span>
      <router-link to="/setup" class="setup-banner__cta">Create first administrator →</router-link>
    </div>

    <header class="top-bar">
      <button
        type="button"
        class="hamburger"
        :class="{ 'hamburger--open': menuOpen }"
        :aria-label="menuOpen ? 'Close menu' : 'Open menu'"
        :aria-expanded="menuOpen"
        aria-controls="side-nav"
        @click="toggleMenu"
      >
        <span class="hamburger__line"></span>
        <span class="hamburger__line"></span>
        <span class="hamburger__line"></span>
      </button>

      <router-link :to="brandHome" class="brand" @click="closeMenu">
        <span class="brand__mark" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path
              d="M12 3C8 7 6 11 6 15c0 3 2.5 5.5 6 6 3.5-.5 6-3 6-6 0-4-2-8-6-12z"
              fill="currentColor"
              opacity="0.92"
            />
            <path d="M12 8v9M9 12h6" stroke="rgba(255,255,255,0.35)" stroke-width="1.2" stroke-linecap="round" />
          </svg>
        </span>
        <span class="brand__word">Agri Marketplace</span>
      </router-link>

      <div class="top-bar__actions">
        <NotificationBell />
        <router-link
          v-if="!auth.isAuthenticated"
          to="/checkout"
          class="top-icon-link"
          aria-label="Cart"
        >
          Cart
          <span v-if="cart.totalQuantity > 0" class="cart-badge">{{ cart.totalQuantity }}</span>
        </router-link>
        <span v-if="auth.isAuthenticated" class="top-user" :title="auth.email">{{ auth.displayLabel }}</span>
        <button
          type="button"
          class="theme-toggle"
          :aria-label="theme.resolvedMode === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'"
          :title="theme.resolvedMode === 'dark' ? 'Light mode' : 'Dark mode'"
          @click="theme.toggle()"
        >
          <svg
            v-if="theme.resolvedMode === 'dark'"
            class="theme-toggle__icon"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            aria-hidden="true"
          >
            <circle cx="12" cy="12" r="4" />
            <path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41" />
          </svg>
          <svg
            v-else
            class="theme-toggle__icon"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
            aria-hidden="true"
          >
            <path d="M21 14.5A8.5 8.5 0 1 1 9.5 3a7 7 0 0 0 11.5 11.5z" />
          </svg>
        </button>
      </div>
    </header>

    <div
      v-if="menuOpen"
      class="side-nav-backdrop"
      aria-hidden="true"
      @click="closeMenu"
    />

    <aside
      id="side-nav"
      class="side-nav"
      :class="{ 'side-nav--open': menuOpen }"
      aria-label="Main navigation"
      :aria-hidden="!menuOpen"
    >
      <div class="side-nav__head">
        <p class="side-nav__label">{{ workspaceLabel }}</p>
        <button type="button" class="side-nav__close" aria-label="Close menu" @click="closeMenu">
          ✕
        </button>
      </div>

      <nav class="side-nav__links" @click="closeMenu">
        <template v-if="!auth.isAuthenticated">
          <router-link to="/">Browse</router-link>
          <router-link to="/help/client">Help</router-link>
          <router-link to="/order-invoice">Order invoice</router-link>
          <router-link to="/checkout">
            Cart
            <span v-if="cart.totalQuantity > 0" class="side-badge">{{ cart.totalQuantity }}</span>
          </router-link>
          <router-link v-if="setup.needsFirstAdmin === true" to="/setup">Setup</router-link>
          <router-link to="/login" class="side-link--cta">Sign in</router-link>
          <router-link to="/register">Sign up</router-link>
        </template>

        <template v-else-if="auth.isProviderUser">
          <p class="side-nav__section">Provider</p>
          <router-link to="/provider">Dashboard</router-link>
          <router-link to="/provider/listings">Listings</router-link>
          <router-link to="/provider/orders">Orders</router-link>
          <router-link
            v-if="auth.canManageStaff && auth.isPremiumPlan"
            to="/provider/team"
          >Team management</router-link>
          <router-link
            v-if="auth.isPremiumPlan"
            to="/provider/staff-payments"
          >{{ auth.canManageStaff ? 'Staff payments' : 'My income' }}</router-link>
          <router-link to="/provider/subscription">Subscription</router-link>
          <router-link to="/provider/settings">Settings</router-link>
          <router-link to="/provider/help">Help</router-link>
        </template>

        <template v-else-if="auth.isClientUser">
          <p class="side-nav__section">Customer</p>
          <router-link to="/">Browse</router-link>
          <router-link to="/checkout">
            Cart
            <span v-if="cart.totalQuantity > 0" class="side-badge">{{ cart.totalQuantity }}</span>
          </router-link>
          <router-link to="/order-invoice">Order invoice</router-link>
          <router-link to="/help/client">Help</router-link>
        </template>

        <template v-else-if="auth.isSupport && !auth.isPlatformAdmin">
          <p class="side-nav__section">Support</p>
          <router-link to="/support">Dashboard</router-link>
          <router-link to="/support/users">Users</router-link>
          <router-link to="/support/tickets">Tickets</router-link>
          <router-link to="/support/otp">Client OTP</router-link>
          <router-link to="/support/order-invoice">Order invoice</router-link>
        </template>

        <template v-else-if="auth.isPlatformAdmin">
          <p class="side-nav__section">Admin</p>
          <router-link to="/admin">Dashboard</router-link>
          <router-link to="/admin/settings">Settings</router-link>
          <router-link to="/admin/manual-verifications">Manual verifications</router-link>
          <router-link to="/admin/providers">Providers</router-link>
          <router-link to="/admin/listings">Listings</router-link>
          <router-link to="/admin/users">Users</router-link>
          <router-link to="/support">Support</router-link>
          <router-link to="/admin/support-users">Support users</router-link>
          <router-link to="/admin/password">Password</router-link>
        </template>
      </nav>

      <div v-if="auth.isAuthenticated" class="side-nav__footer">
        <p class="side-nav__user" :title="auth.email">{{ auth.displayLabel }}</p>
        <button type="button" class="side-nav__signout" @click="auth.logout()">
          Sign out
        </button>
      </div>
    </aside>

    <main class="main-area" :class="{ 'no-pad': route.name === 'market' }">
      <div class="main-area__inner" :class="{ 'main-area__inner--flush': route.name === 'market' }">
        <router-view />
      </div>
    </main>

    <DialogModal
      v-if="dialogState.isOpen"
      :title="dialogState.title"
      :message="dialogState.message"
      :type="dialogState.type"
      :confirm-text="dialogState.confirmText"
      :cancel-text="dialogState.cancelText"
      :show-cancel="dialogState.showCancel"
      @confirm="dialogState.onConfirm"
      @cancel="dialogState.onCancel"
      @close="dialogState.onCancel"
    />
  </div>
</template>

<style scoped>
.setup-banner {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 0.65rem 1.25rem;
  padding: 0.55rem 1.25rem;
  background: var(--color-wheat-soft);
  color: var(--color-earth);
  font-size: 0.88rem;
  border-bottom: 1px solid rgba(201, 162, 39, 0.35);
}

.setup-banner__cta {
  font-weight: 700;
  color: var(--color-canopy);
  padding: 0.3rem 0.7rem;
  border-radius: var(--radius-pill);
  background: var(--color-surface-elevated);
  border: 1px solid var(--color-border);
}

.setup-banner__cta:hover {
  text-decoration: none;
  background: #fff;
}

.top-bar__actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-left: auto;
}

.top-user {
  font-size: 0.82rem;
  color: var(--color-muted);
  max-width: 10rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.top-icon-link {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  padding: 0.4rem 0.7rem;
  border-radius: var(--radius-pill);
  font-size: 0.88rem;
  font-weight: 600;
  color: var(--color-text-secondary);
}

.top-icon-link:hover {
  background: var(--color-sage-soft);
  color: var(--color-canopy);
  text-decoration: none;
}

.hamburger {
  display: inline-flex;
  flex-direction: column;
  justify-content: center;
  gap: 5px;
  width: 2.25rem;
  height: 2.25rem;
  padding: 0.45rem;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-surface-elevated);
  cursor: pointer;
  flex-shrink: 0;
}

.hamburger__line {
  display: block;
  width: 100%;
  height: 2px;
  background: var(--color-canopy);
  border-radius: 2px;
  transition: transform 0.2s var(--ease-out), opacity 0.2s var(--ease-out);
}

.hamburger--open .hamburger__line:nth-child(1) {
  transform: translateY(7px) rotate(45deg);
}

.hamburger--open .hamburger__line:nth-child(2) {
  opacity: 0;
}

.hamburger--open .hamburger__line:nth-child(3) {
  transform: translateY(-7px) rotate(-45deg);
}

.side-nav-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(28, 36, 24, 0.4);
  z-index: 54;
  backdrop-filter: blur(2px);
}

.side-nav {
  position: fixed;
  top: 0;
  left: 0;
  width: min(300px, 88vw);
  height: 100dvh;
  display: flex;
  flex-direction: column;
  background: var(--color-surface-elevated);
  border-right: 1px solid var(--color-border);
  box-shadow: 8px 0 32px rgba(28, 36, 24, 0.12);
  z-index: 55;
  transform: translateX(-105%);
  transition: transform 0.22s var(--ease-out);
}

.side-nav--open {
  transform: translateX(0);
}

.side-nav__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 1rem 1rem 0.75rem;
  border-bottom: 1px solid var(--color-border);
}

.side-nav__label {
  margin: 0;
  font-size: 0.72rem;
  font-weight: 800;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--color-sage);
}

.side-nav__close {
  width: 2rem;
  height: 2rem;
  border: none;
  border-radius: 8px;
  background: var(--color-sage-soft);
  color: var(--color-canopy);
  cursor: pointer;
  font-size: 1rem;
  line-height: 1;
}

.side-nav__links {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  padding: 0.75rem;
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.side-nav__section {
  margin: 0.65rem 0.5rem 0.35rem;
  font-size: 0.68rem;
  font-weight: 800;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--color-muted);
}

.side-nav__links a {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  padding: 0.65rem 0.75rem;
  border-radius: 12px;
  color: var(--color-canopy);
  font-weight: 600;
  text-decoration: none;
}

.side-nav__links a:hover {
  background: var(--color-sage-soft);
  text-decoration: none;
}

.side-nav__links a.router-link-active {
  background: var(--color-sage-soft);
  box-shadow: inset 3px 0 0 var(--color-sage);
}

.side-link--cta {
  background: var(--color-canopy) !important;
  color: #fafdfb !important;
  margin-top: 0.35rem;
}

.side-link--cta:hover {
  background: var(--color-canopy-mid) !important;
  color: #fff !important;
}

.side-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 1.2rem;
  height: 1.2rem;
  padding: 0 0.35rem;
  border-radius: var(--radius-pill);
  font-size: 0.68rem;
  font-weight: 800;
  background: var(--color-earth);
  color: #fff;
}

.side-nav__footer {
  padding: 0.85rem 1rem 1.1rem;
  border-top: 1px solid var(--color-border);
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.side-nav__user {
  margin: 0;
  font-size: 0.85rem;
  color: var(--color-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.side-nav__signout {
  border: 1px solid var(--color-border);
  background: transparent;
  color: var(--color-earth);
  font: inherit;
  font-weight: 600;
  font-size: 0.9rem;
  padding: 0.55rem 0.75rem;
  border-radius: 10px;
  cursor: pointer;
  text-align: left;
}

.side-nav__signout:hover {
  background: var(--color-wheat-soft);
}

.main-area {
  flex: 1;
}

.main-area__inner {
  width: 100%;
  max-width: none;
  margin: 0;
  padding: var(--space-6) clamp(var(--space-4), 2vw, var(--space-6)) var(--space-8);
  box-sizing: border-box;
}

.main-area__inner--flush,
.main-area.no-pad .main-area__inner {
  padding: 0;
}
</style>
