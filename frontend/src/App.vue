<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { useSessionStore } from './stores/session';
import { useSetupStore } from './stores/setup';
import { useAuthStore } from './stores/auth';
import { useCartStore } from './stores/cart';
import { useThemeStore } from './stores/theme';
import { useDialog } from './composables/useDialog';
import DialogModal from './components/ui/DialogModal.vue';

const route = useRoute();
const session = useSessionStore();
const setup = useSetupStore();
const auth = useAuthStore();
const cart = useCartStore();
const theme = useThemeStore();
const { dialogState } = useDialog();

const mobileMenuOpen = ref(false);

const toggleMobileMenu = () => {
  mobileMenuOpen.value = !mobileMenuOpen.value;
};

const closeMobileMenu = () => {
  mobileMenuOpen.value = false;
};

const envBadge = computed(() => import.meta.env.VITE_APP_ENV || '');

/** Home for the logo: workspace dashboard per role — avoids sending admins to Browse. */
const brandHome = computed(() => {
  if (!auth.isAuthenticated) return '/';
  if (auth.isPlatformAdmin) return '/admin';
  if (auth.isSupport) return '/support';
  if (auth.isProviderUser) return '/provider';
  return '/';
});

onMounted(async () => {
  theme.init();
  await session.ensureSession();
  try {
    await setup.fetchStatus();
  } catch {
    /* leave needsFirstAdmin unchanged if API is unreachable */
  }
  auth.restoreFromStorage();
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
      <router-link :to="brandHome" class="brand">
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

      <!-- Logged out: top navigation only -->
      <nav v-if="!auth.isAuthenticated" class="nav-links" aria-label="Main">
        <router-link to="/">Browse</router-link>
        <router-link to="/order-invoice">Order invoice</router-link>
        <router-link to="/checkout" class="nav-cart-link">
          Cart
          <span
            v-if="cart.totalQuantity > 0"
            class="cart-badge"
            :aria-label="`${cart.totalQuantity} items in cart`"
          >{{ cart.totalQuantity }}</span>
        </router-link>
        <router-link v-if="setup.needsFirstAdmin === true" to="/setup" class="nav-highlight">Setup</router-link>
        <router-link to="/login">Login</router-link>
        <button
          type="button"
          class="theme-toggle"
          :aria-label="theme.resolvedMode === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'"
          @click="theme.toggle()"
        >
          <span aria-hidden="true">{{ theme.resolvedMode === 'dark' ? 'Light' : 'Dark' }}</span>
        </button>
      </nav>

      <!-- Logged in: compact user strip in top bar -->
      <div v-else class="top-user">
        <button 
          v-if="auth.isAuthenticated"
          type="button" 
          class="hamburger" 
          :aria-label="mobileMenuOpen ? 'Close menu' : 'Open menu'"
          @click="toggleMobileMenu"
        >
          <span class="hamburger__line"></span>
          <span class="hamburger__line"></span>
          <span class="hamburger__line"></span>
        </button>
        <span class="nav-user" :title="auth.email">{{ auth.displayLabel }}</span>
        <button
          type="button"
          class="theme-toggle"
          :aria-label="theme.resolvedMode === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'"
          @click="theme.toggle()"
        >
          <span aria-hidden="true">{{ theme.resolvedMode === 'dark' ? 'Light' : 'Dark' }}</span>
        </button>
        <button type="button" class="nav-signout" @click="auth.logout()">Sign out</button>
      </div>

      <!-- <span v-if="envBadge" class="env-pill">{{ envBadge }}</span> -->
    </header>

    <!-- Logged in: side navigation -->
    <div v-if="auth.isAuthenticated" class="authed-layout">
      <aside class="side-nav" :class="{ 'side-nav--mobile-open': mobileMenuOpen }" aria-label="Workspace navigation">
        <div v-if="auth.isProviderUser" class="side-nav__group">
          <p class="side-nav__title">Provider</p>
          <router-link to="/provider" class="side-link" @click="closeMobileMenu">Dashboard</router-link>
          <router-link to="/provider/settings" class="side-link" @click="closeMobileMenu">Settings</router-link>
          <router-link to="/provider/subscription" class="side-link" @click="closeMobileMenu">Subscription</router-link>
          <router-link to="/provider/orders" class="side-link" @click="closeMobileMenu">Orders</router-link>
          <router-link
            v-if="auth.canManageStaff && auth.isPremiumPlan"
            to="/provider/team"
            class="side-link"
            @click="closeMobileMenu"
          >Team & payroll</router-link>
          <router-link
            v-if="auth.canManageStaff && auth.isPremiumPlan"
            to="/provider/staff-payments"
            class="side-link"
            @click="closeMobileMenu"
          >Staff payments</router-link>
          <!-- Listings UI will live at /provider/listings -->
          <router-link to="/provider/listings" class="side-link" @click="closeMobileMenu">Listings</router-link>
        </div>

        <div v-if="auth.isSupport && !auth.isPlatformAdmin" class="side-nav__group">
          <p class="side-nav__title">Support</p>
          <router-link to="/support" class="side-link" @click="closeMobileMenu">Support dashboard</router-link>
          <router-link to="/support/users" class="side-link" @click="closeMobileMenu">Users</router-link>
          <router-link to="/support/tickets" class="side-link" @click="closeMobileMenu">Tickets</router-link>
          <router-link to="/support/otp" class="side-link" @click="closeMobileMenu">Client OTP</router-link>
          <router-link to="/support/order-invoice" class="side-link" @click="closeMobileMenu">Order invoice</router-link>
        </div>

        <div v-if="auth.isPlatformAdmin" class="side-nav__group">
          <p class="side-nav__title">Admin</p>
          <router-link to="/admin" class="side-link" @click="closeMobileMenu">Dashboard</router-link>
          <router-link to="/admin/settings" class="side-link" @click="closeMobileMenu">Settings</router-link>
          <router-link to="/admin/manual-verifications" class="side-link" @click="closeMobileMenu">Manual verifications</router-link>
          <router-link to="/admin/providers" class="side-link" @click="closeMobileMenu">Providers</router-link>
          <router-link to="/admin/listings" class="side-link" @click="closeMobileMenu">Listings</router-link>
          <router-link to="/admin/users" class="side-link" @click="closeMobileMenu">Users</router-link>
          <router-link to="/support" class="side-link" @click="closeMobileMenu">Support</router-link>
          <router-link to="/admin/support-users" class="side-link" @click="closeMobileMenu">Support users</router-link>
          <router-link to="/admin/password" class="side-link" @click="closeMobileMenu">Password</router-link>
        </div>
      </aside>

      <main class="main-area" :class="{ 'no-pad': route.name === 'market' }">
        <router-view />
      </main>
    </div>

    <!-- Logged out: no sidebar -->
    <main v-else class="main-area" :class="{ 'no-pad': route.name === 'market' }">
      <router-view />
    </main>

    <!-- Global Dialog Modal -->
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
  padding: 0.65rem 1.25rem;
  background: linear-gradient(90deg, var(--color-info-bg), #f0f7ff);
  color: var(--color-info-text);
  font-size: 0.88rem;
  border-bottom: 1px solid rgba(21, 74, 122, 0.15);
}

.setup-banner__text {
  font-weight: 500;
}

.setup-banner__cta {
  font-weight: 700;
  color: var(--color-canopy);
  padding: 0.35rem 0.75rem;
  border-radius: var(--radius-pill);
  background: rgba(255, 255, 255, 0.75);
  border: 1px solid rgba(26, 60, 52, 0.12);
  transition: background 0.15s ease;
}

.setup-banner__cta:hover {
  background: #fff;
  text-decoration: none;
}

.nav-user {
  font-size: 0.8rem;
  color: var(--color-muted);
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding: 0.35rem 0.5rem;
}

.nav-highlight {
  font-weight: 700 !important;
}

.nav-signout {
  border: none;
  background: transparent;
  font: inherit;
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--color-earth);
  cursor: pointer;
  padding: 0.45rem 0.75rem;
  border-radius: var(--radius-pill);
  transition: background 0.15s ease;
}

.nav-signout:hover {
  background: rgba(140, 98, 57, 0.1);
}

.top-user {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  margin-left: auto;
}

.authed-layout {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  min-height: calc(100vh - 64px);
}

.side-nav {
  position: sticky;
  top: 0;
  align-self: start;
  height: calc(100vh - 64px);
  padding: 1rem 0.9rem;
  border-right: 1px solid var(--color-border);
  background: linear-gradient(180deg, rgba(26, 60, 52, 0.03), rgba(255, 255, 255, 0));
}

.side-nav__group + .side-nav__group {
  margin-top: 1.1rem;
  padding-top: 1.1rem;
  border-top: 1px solid rgba(26, 60, 52, 0.08);
}

.side-nav__title {
  margin: 0 0 0.5rem;
  font-size: 0.72rem;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-muted);
}

.side-link {
  display: block;
  padding: 0.5rem 0.65rem;
  border-radius: 12px;
  color: var(--color-canopy);
  font-weight: 650;
  text-decoration: none;
  transition:
    background 0.15s ease,
    color 0.15s ease;
}

.side-link:hover {
  background: rgba(61, 122, 102, 0.10);
  text-decoration: none;
}

.side-link.router-link-active {
  background: rgba(61, 122, 102, 0.16);
  color: var(--color-canopy);
}

.hamburger {
  display: none;
  flex-direction: column;
  gap: 0.35rem;
  border: none;
  background: none;
  cursor: pointer;
  padding: 0.5rem;
  margin-right: auto;
}

.hamburger__line {
  width: 24px;
  height: 2.5px;
  background: var(--color-canopy, #1a3c34);
  border-radius: 2px;
  transition: all 0.3s ease;
}

@media (max-width: 980px) {
  .hamburger {
    display: flex;
  }

 .top-user {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 0.35rem;
}


  .authed-layout {
    grid-template-columns: 1fr;
  }
  .side-nav {
    position: fixed;
    top: 64px;
    left: 0;
    width: 100%;
    height: calc(100vh - 64px);
    border-right: none;
    border-bottom: none;
    padding: 1rem;
    background: white;
    backdrop-filter: blur(4px);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
    
    display: none;
    flex-direction: column;
    gap: 1rem;
    overflow-y: auto;
    z-index: 999;
  }

  .side-nav--mobile-open {
    display: flex;
  }

  .side-nav__group {
    flex: none;
    min-width: auto;
  }
  .side-nav__group + .side-nav__group {
    margin-top: 1rem;
    padding-top: 1rem;
    border-top: 1px solid rgba(26, 60, 52, 0.08);
  }
  .side-link {
    padding: 0.65rem 0.85rem;
    font-size: 0.95rem;
  }
}
</style>
