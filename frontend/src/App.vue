<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { useSessionStore } from './stores/session';
import { useSetupStore } from './stores/setup';
import { useAuthStore } from './stores/auth';
import { useCartStore } from './stores/cart';
import { useThemeStore } from './stores/theme';
import { useDialog } from './composables/useDialog';
import DialogModal from './components/ui/DialogModal.vue';
import NotificationBell from './components/NotificationBell.vue';
import DisplayStyleToggle from './components/ui/DisplayStyleToggle.vue';
import { refreshProviderSubscription } from './utils/providerSubscriptionGate';
import { createLiveStream } from './services/liveStream';

const route = useRoute();
const session = useSessionStore();
const setup = useSetupStore();
const auth = useAuthStore();
const cart = useCartStore();
const theme = useThemeStore();
const { dialogState } = useDialog();

/** Shoppers (guests and signed-in customers) always see the cart in the top bar. */
const showCart = computed(() => !auth.isAuthenticated || auth.isClientUser);

const menuOpen = ref(false);

// One live-updates stream per tab for staff accounts; pages listen via utils/liveUpdates.
const liveStream = createLiveStream({ getToken: () => auth.token });
const wantsLiveUpdates = computed(
  () => auth.isAuthenticated && (auth.isPlatformAdmin || auth.isSupport || auth.isProviderUser),
);
watch(
  [wantsLiveUpdates, () => auth.token],
  ([want]) => {
    liveStream.stop();
    if (want) liveStream.start();
  },
  { immediate: true },
);

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
  if (auth.isProviderUser) {
    return auth.providerSubValid ? '/provider' : '/provider/subscription';
  }
  return '/';
});

const workspaceLabel = computed(() => {
  if (auth.isPlatformAdmin) return 'Platform admin';
  if (auth.isSupport) return 'Support';
  if (auth.isProviderUser) return 'Provider workspace';
  if (auth.isClientUser) return 'Customer';
  return 'Menu';
});

const userInitials = computed(() => {
  const label = auth.displayLabel || '';
  const source = label.includes('@') ? label.split('@')[0] : label;
  const parts = source.split(/[\s._-]+/).filter(Boolean);
  return ((parts[0]?.[0] || '') + (parts[1]?.[0] || '')).toUpperCase() || '?';
});

watch(
  () => route.fullPath,
  () => {
    menuOpen.value = false;
  },
);

const onKeydown = (event) => {
  if (event.key === 'Escape' && menuOpen.value) closeMenu();
};

onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown));

onMounted(async () => {
  window.addEventListener('keydown', onKeydown);
  theme.init();
  await session.ensureSession();
  try {
    await setup.fetchStatus();
  } catch {
    /* leave needsFirstAdmin unchanged if API is unreachable */
  }
  auth.restoreFromStorage();
  auth.listenForForceLogout();
  if (auth.isProviderUser) {
    try {
      await refreshProviderSubscription(auth);
    } catch {
      /* router gate will refresh on navigation */
    }
  }
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
        <!-- Cart lives in the top bar (always visible to shoppers), not hidden in the side menu. -->
        <router-link
          v-if="showCart"
          to="/checkout"
          class="cart-btn"
          :aria-label="cart.totalQuantity > 0 ? `Cart, ${cart.totalQuantity} item${cart.totalQuantity === 1 ? '' : 's'}` : 'Cart, empty'"
          :title="cart.totalQuantity > 0 ? 'View cart' : 'Your cart is empty'"
          @click="closeMenu"
        >
          <span class="material-icons" aria-hidden="true">shopping_cart</span>
          <span v-if="cart.totalQuantity > 0" class="cart-btn__badge" aria-hidden="true">
            {{ cart.totalQuantity > 99 ? '99+' : cart.totalQuantity }}
          </span>
        </router-link>
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
      :inert="!menuOpen"
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
          <router-link to="/my-orders">My orders</router-link>
          <router-link to="/order-invoice">Order invoice</router-link>
          <router-link to="/settings">Settings</router-link>
          <router-link v-if="setup.needsFirstAdmin === true" to="/setup">Setup</router-link>
          <router-link to="/help/client" class="side-nav__link--help">Help</router-link>
        </template>

        <template v-else-if="auth.isProviderUser">
          <p class="side-nav__section">Provider</p>
          <template v-if="auth.providerSubValid">
            <router-link to="/provider">Dashboard</router-link>
            <router-link to="/provider/listings">Listings</router-link>
            <router-link to="/provider/orders">Orders</router-link>
            <router-link to="/provider/history">Order &amp; payment history</router-link>
            <router-link
              v-if="auth.canManageStaff && auth.isPremiumPlan"
              to="/provider/team"
            >Team management</router-link>
            <router-link
              v-if="auth.isPremiumPlan"
              to="/provider/staff-payments"
            >{{ auth.canManageStaff ? 'Staff payments' : 'My income' }}</router-link>
            <router-link to="/provider/subscription">Subscription</router-link>
            <router-link to="/provider/referrals">My referrals</router-link>
            <router-link to="/provider/settings">Settings</router-link>
          </template>
          <template v-else>
            <router-link to="/provider/subscription">Subscription</router-link>
            <p class="side-nav__hint muted small">
              Choose and activate a plan to unlock your provider workspace.
            </p>
          </template>
          <router-link to="/profile">Profile</router-link>
          <router-link to="/provider/help" class="side-nav__link--help">Help</router-link>
        </template>

        <template v-else-if="auth.isClientUser">
          <p class="side-nav__section">Customer</p>
          <router-link to="/">Browse</router-link>
          <router-link to="/my-orders">My orders &amp; payments</router-link>
          <router-link to="/order-invoice">Order invoice</router-link>
          <router-link to="/profile">Profile</router-link>
          <router-link to="/settings">Settings</router-link>
          <router-link to="/help/client" class="side-nav__link--help">Help</router-link>
        </template>

        <template v-else-if="auth.isSupport && !auth.isPlatformAdmin">
          <p class="side-nav__section">Support</p>
          <router-link to="/support">Dashboard</router-link>
          <router-link to="/support/users">Users</router-link>
          <router-link to="/support/tickets">Tickets</router-link>
          <router-link to="/support/otp">Client OTP</router-link>
          <router-link to="/support/order-invoice">Order invoice</router-link>
          <router-link to="/profile">Profile</router-link>
        </template>

        <template v-else-if="auth.isPlatformAdmin">
          <p class="side-nav__section">Admin</p>
          <router-link to="/admin">Dashboard</router-link>
          <router-link to="/admin/history">Orders &amp; payments</router-link>
          <router-link to="/admin/providers">Providers</router-link>
          <router-link to="/admin/listings">Listings</router-link>
          <router-link to="/admin/users">Users</router-link>
          <router-link to="/admin/support-users">Support users</router-link>
          <router-link to="/admin/referrals">Referrals</router-link>
          <router-link to="/support">Support</router-link>
          <router-link to="/admin/settings">Settings</router-link>
          <router-link to="/profile">Profile</router-link>
        </template>
      </nav>

      <div class="side-nav__footer">
        <button
          type="button"
          class="side-nav__action"
          role="switch"
          :aria-checked="theme.resolvedMode === 'dark'"
          @click="theme.toggle()"
        >
          <span class="material-icons" aria-hidden="true">
            {{ theme.resolvedMode === 'dark' ? 'dark_mode' : 'light_mode' }}
          </span>
          <span class="side-nav__action-label">
            {{ theme.resolvedMode === 'dark' ? 'Dark mode' : 'Light mode' }}
          </span>
          <span class="side-nav__switch" :class="{ 'side-nav__switch--on': theme.resolvedMode === 'dark' }" aria-hidden="true"></span>
        </button>

        <router-link
          v-if="!auth.isAuthenticated"
          to="/login"
          class="side-nav__action side-nav__action--primary"
          @click="closeMenu"
        >
          <span class="material-icons" aria-hidden="true">login</span>
          <span class="side-nav__action-label">Sign in</span>
        </router-link>

        <template v-else>
          <router-link to="/profile" class="side-nav__user" :title="auth.email" @click="closeMenu">
            <span class="side-nav__avatar" aria-hidden="true">{{ userInitials }}</span>
            <span class="side-nav__user-text">
              <span class="side-nav__user-name">{{ auth.displayLabel }}</span>
              <span v-if="auth.displayName && auth.email" class="side-nav__user-email">{{ auth.email }}</span>
            </span>
          </router-link>
          <DisplayStyleToggle variant="compact" />
          <button type="button" class="side-nav__signout" @click="auth.logout()">
            <span class="material-icons" aria-hidden="true">logout</span>
            Sign out
          </button>
        </template>
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
  gap: 0.35rem;
  margin-left: auto;
}

.cart-btn {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2.25rem;
  height: 2.25rem;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-surface-elevated);
  color: var(--color-canopy);
  text-decoration: none;
}

.cart-btn:hover {
  text-decoration: none;
  border-color: var(--color-canopy);
}

.cart-btn:focus-visible {
  outline: 2px solid var(--color-canopy);
  outline-offset: 2px;
}

.cart-btn .material-icons {
  font-size: 1.2rem;
}

@media (max-width: 640px) {
  .cart-btn {
    min-width: 44px;
    min-height: 44px;
  }
}

.cart-btn__badge {
  position: absolute;
  top: -0.35rem;
  right: -0.35rem;
  min-width: 1.1rem;
  height: 1.1rem;
  padding: 0 0.3rem;
  border-radius: var(--radius-pill);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 0.65rem;
  font-weight: 800;
  line-height: 1;
  background: var(--color-earth);
  color: #fff;
  box-shadow: 0 0 0 2px var(--color-surface-elevated);
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
  position: relative;
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
  padding: 0.85rem 0.75rem 1rem;
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
}

.side-nav__section {
  margin: 0.15rem 0.5rem 0.15rem;
  font-size: 0.68rem;
  font-weight: 800;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--color-muted);
}

.side-nav__hint {
  margin: 0 0.5rem;
  line-height: 1.4;
}

.side-nav__links a {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  min-height: 2.75rem;
  padding: 0.7rem 0.85rem;
  border-radius: 12px;
  color: var(--color-canopy);
  font-weight: 600;
  text-decoration: none;
  box-sizing: border-box;
}

.side-nav__links a:hover {
  background: var(--color-sage-soft);
  text-decoration: none;
}

.side-nav__links a.router-link-active {
  background: var(--color-sage-soft);
  box-shadow: inset 3px 0 0 var(--color-sage);
}

.side-nav__link--help {
  margin-top: auto;
}


.side-nav__footer {
  padding: 0.85rem 1rem 1.1rem;
  border-top: 1px solid var(--color-border);
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

/* Shared row style for footer actions (theme switch, sign in). */
.side-nav__action {
  display: flex;
  align-items: center;
  gap: 0.65rem;
  width: 100%;
  min-height: 2.75rem;
  padding: 0.6rem 0.85rem;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  background: transparent;
  color: var(--color-canopy);
  font: inherit;
  font-weight: 600;
  font-size: 0.92rem;
  text-align: left;
  text-decoration: none;
  cursor: pointer;
  box-sizing: border-box;
}

.side-nav__action:hover {
  background: var(--color-sage-soft);
  text-decoration: none;
}

.side-nav__action .material-icons {
  font-size: 1.2rem;
  flex-shrink: 0;
}

.side-nav__action-label {
  flex: 1;
  min-width: 0;
}

.side-nav__action--primary {
  justify-content: center;
  border-color: var(--color-canopy);
  background: var(--color-canopy);
  color: #fff;
}

.side-nav__action--primary .side-nav__action-label {
  flex: 0 1 auto;
}

.side-nav__action--primary:hover {
  background: var(--color-canopy-mid);
  color: #fff;
}

.side-nav__switch {
  position: relative;
  flex-shrink: 0;
  width: 2.3rem;
  height: 1.3rem;
  border-radius: var(--radius-pill);
  background: var(--color-border-strong);
  transition: background 0.18s var(--ease-out);
}

.side-nav__switch::after {
  content: '';
  position: absolute;
  top: 0.15rem;
  left: 0.15rem;
  width: 1rem;
  height: 1rem;
  border-radius: 50%;
  background: #fff;
  box-shadow: var(--shadow-sm);
  transition: transform 0.18s var(--ease-out);
}

.side-nav__switch--on {
  background: var(--color-sage);
}

.side-nav__switch--on::after {
  transform: translateX(1rem);
}

.side-nav__user {
  display: flex;
  align-items: center;
  gap: 0.65rem;
  padding: 0.5rem 0.6rem;
  border-radius: 12px;
  color: var(--color-canopy);
  text-decoration: none;
  min-width: 0;
}

.side-nav__user:hover,
.side-nav__user.router-link-active {
  background: var(--color-sage-soft);
  text-decoration: none;
}

.side-nav__avatar {
  flex-shrink: 0;
  width: 2.25rem;
  height: 2.25rem;
  border-radius: 50%;
  display: inline-grid;
  place-items: center;
  font-size: 0.78rem;
  font-weight: 800;
  letter-spacing: 0.02em;
  color: #fff;
  background: linear-gradient(135deg, var(--color-sage), var(--color-canopy));
}

.side-nav__user-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.side-nav__user-name,
.side-nav__user-email {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.side-nav__user-name {
  font-size: 0.92rem;
  font-weight: 700;
}

.side-nav__user-email {
  font-size: 0.78rem;
  color: var(--color-muted);
}

.side-nav__signout {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  min-height: 2.75rem;
  border: 1px solid var(--color-border);
  background: transparent;
  color: var(--color-earth);
  font: inherit;
  font-weight: 600;
  font-size: 0.9rem;
  padding: 0.55rem 0.85rem;
  border-radius: 12px;
  cursor: pointer;
  text-align: left;
}

.side-nav__signout .material-icons {
  font-size: 1.15rem;
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
