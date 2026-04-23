<script setup>
import { computed, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import { useSessionStore } from './stores/session';
import { useSetupStore } from './stores/setup';
import { useAuthStore } from './stores/auth';
import { useCartStore } from './stores/cart';
import { useDialog } from './composables/useDialog';
import DialogModal from './components/ui/DialogModal.vue';

const route = useRoute();
const session = useSessionStore();
const setup = useSetupStore();
const auth = useAuthStore();
const cart = useCartStore();
const { dialogState } = useDialog();

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
      </nav>

      <!-- Logged in: compact user strip in top bar -->
      <div v-else class="top-user">
        <span class="nav-user" :title="auth.email">{{ auth.displayLabel }}</span>
        <button type="button" class="nav-signout" @click="auth.logout()">Sign out</button>
      </div>

      <!-- <span v-if="envBadge" class="env-pill">{{ envBadge }}</span> -->
    </header>

    <!-- Logged in: side navigation -->
    <div v-if="auth.isAuthenticated" class="authed-layout">
      <aside class="side-nav" aria-label="Workspace navigation">
        <div v-if="auth.isProviderUser" class="side-nav__group">
          <p class="side-nav__title">Provider</p>
          <router-link to="/provider" class="side-link">Dashboard</router-link>
          <router-link to="/provider/settings" class="side-link">Settings</router-link>
          <router-link to="/provider/orders" class="side-link">Orders</router-link>
          <!-- <router-link v-if="auth.canManageStaff" to="/provider/team" class="side-link">Team & payroll</router-link> -->
          <!-- <router-link v-if="auth.canManageStaff" to="/provider/staff-payments" class="side-link">Staff payments</router-link> -->
          <!-- Listings UI will live at /provider/listings -->
          <router-link to="/provider/listings" class="side-link">Listings</router-link>
        </div>

        <div v-if="auth.isSupport && !auth.isPlatformAdmin" class="side-nav__group">
          <p class="side-nav__title">Support</p>
          <router-link to="/support" class="side-link">Support dashboard</router-link>
        </div>

        <div v-if="auth.isPlatformAdmin" class="side-nav__group">
          <p class="side-nav__title">Admin</p>
          <router-link to="/admin" class="side-link">Dashboard</router-link>
          <router-link to="/admin/providers" class="side-link">Providers</router-link>
          <router-link to="/admin/listings" class="side-link">Listings</router-link>
          <router-link to="/admin/users" class="side-link">Users</router-link>
          <router-link to="/support" class="side-link">Support</router-link>
          <router-link to="/admin/support-users" class="side-link">Support users</router-link>
          <router-link to="/admin/password" class="side-link">Password</router-link>
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

@media (max-width: 980px) {
  .authed-layout {
    grid-template-columns: 1fr;
  }
  .side-nav {
    position: static;
    height: auto;
    border-right: none;
    border-bottom: 1px solid var(--color-border);
    padding: 0.75rem 0.75rem;

    display: flex;
    gap: 0.75rem;
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
  }
  .side-nav__group {
    flex: 0 0 auto;
    min-width: 170px;
  }
  .side-nav__group + .side-nav__group {
    margin-top: 0;
    padding-top: 0;
    border-top: none;
  }
  .side-link {
    padding: 0.45rem 0.6rem;
  }
}
</style>
