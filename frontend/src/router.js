import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from './stores/auth';
import { useSetupStore } from './stores/setup';
import MarketplaceView from './views/MarketplaceView.vue';
import CheckoutView from './views/CheckoutView.vue';
import SetupView from './views/SetupView.vue';
import LoginView from './views/LoginView.vue';
import RegisterView from './views/RegisterView.vue';
import ForgotPasswordView from './views/ForgotPasswordView.vue';
import ResetPasswordView from './views/ResetPasswordView.vue';
import ClientEntryView from './views/ClientEntryView.vue';
import ClientVerifyOtpView from './views/ClientVerifyOtpView.vue';
import AdminDashboardView from './views/AdminDashboardView.vue';
import AdminProvidersView from './views/AdminProvidersView.vue';
import AdminListingsView from './views/AdminListingsView.vue';
import AdminUsersView from './views/AdminUsersView.vue';
import AdminProviderSupportView from './views/AdminProviderSupportView.vue';
import AdminSupportUsersView from './views/AdminSupportUsersView.vue';
import AdminPasswordView from './views/AdminPasswordView.vue';
import SupportDashboardView from './views/SupportDashboardView.vue';
import ProviderTeamView from './views/ProviderTeamView.vue';
import ProviderSettingsView from './views/ProviderSettingsView.vue';
import ProviderListingsView from './views/ProviderListingsView.vue';
import ProviderOrdersView from './views/ProviderOrdersView.vue';
import ProviderDashboardView from './views/ProviderDashboardView.vue';
import ProviderStaffPaymentsView from './views/ProviderStaffPaymentsView.vue';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'market', component: MarketplaceView },
    { path: '/checkout', name: 'checkout', component: CheckoutView },
    { path: '/setup', name: 'setup', component: SetupView, meta: { title: 'Setup' } },
    { path: '/login', name: 'login', component: LoginView, meta: { title: 'Login' } },
    { path: '/register', name: 'register', component: RegisterView, meta: { title: 'Sign up' } },
    { path: '/forgot-password', name: 'forgot-password', component: ForgotPasswordView, meta: { title: 'Forgot password' } },
    { path: '/reset-password', name: 'reset-password', component: ResetPasswordView, meta: { title: 'Reset password' } },
    { path: '/client', name: 'client', component: ClientEntryView, meta: { title: 'Client access' } },
    { path: '/client/verify', name: 'client-verify', component: ClientVerifyOtpView, meta: { title: 'Verify code' } },
    {      path: '/admin',      name: 'admin',      component: AdminDashboardView,      meta: { requiresAdmin: true },
    },
    { path: '/admin/providers', name: 'admin-providers', component: AdminProvidersView, meta: { requiresAdmin: true } },
    { path: '/admin/listings', name: 'admin-listings', component: AdminListingsView, meta: { requiresAdmin: true } },
    { path: '/admin/users', name: 'admin-users', component: AdminUsersView, meta: { requiresAdmin: true } },
    { path: '/admin/providers/:id', name: 'admin-provider-support', component: AdminProviderSupportView, meta: { requiresAdmin: true } },
    { path: '/admin/support-users', name: 'admin-support-users', component: AdminSupportUsersView, meta: { requiresAdmin: true } },
    { path: '/admin/password', name: 'admin-password', component: AdminPasswordView, meta: { requiresAdmin: true } },
    { path: '/support', name: 'support', component: SupportDashboardView, meta: { requiresSupport: true } },
    { path: '/provider', name: 'provider-home', component: ProviderDashboardView, meta: { requiresAuth: true } },
    {      path: '/provider/team',      name: 'provider-team',      component: ProviderTeamView,      meta: { requiresAuth: true },    },
    { path: '/provider/staff-payments', name: 'provider-staff-payments', component: ProviderStaffPaymentsView, meta: { requiresAuth: true } },
    { path: '/provider/settings', name: 'provider-settings', component: ProviderSettingsView, meta: { requiresAuth: true } },
    { path: '/provider/listings', name: 'provider-listings', component: ProviderListingsView, meta: { requiresAuth: true } },
    { path: '/provider/orders', name: 'provider-orders', component: ProviderOrdersView, meta: { requiresAuth: true } },
  ],
});

router.beforeEach(async (to) => {
  const auth = useAuthStore();
  const setup = useSetupStore();
  auth.restoreFromStorage();

  if (to.meta.requiresAdmin && !auth.isPlatformAdmin) {
    return { path: '/login', query: { redirect: to.fullPath } };
  }
  if (to.meta.requiresSupport && !(auth.isPlatformAdmin || auth.isSupport)) {
    return { path: '/login', query: { redirect: to.fullPath } };
  }
  if (to.meta.requiresAuth && !auth.isAuthenticated) {
    return { path: '/login', query: { redirect: to.fullPath } };
  }
  if (to.name === 'market' && auth.isAuthenticated) {
    if (auth.isPlatformAdmin) return { path: '/admin' };
    if (auth.isSupport && !auth.isPlatformAdmin) return { path: '/support' };
    if (auth.isProviderUser) return { path: '/provider' };
  }
  if (to.name === 'provider-team' && auth.isAuthenticated && !auth.canManageStaff) {
    return { path: '/provider' };
  }
  if (to.name === 'provider-staff-payments' && auth.isAuthenticated && !auth.canManageStaff) {
    return { path: '/provider' };
  }
  if (to.name === 'setup') {
    await setup.fetchStatus();
    if (!setup.needsFirstAdmin) {
      return { path: '/' };
    }
  }
  if (to.name === 'register') {
    await setup.fetchStatus();
    if (setup.needsFirstAdmin) {
      return { path: '/setup' };
    }
  }
});

export default router;
