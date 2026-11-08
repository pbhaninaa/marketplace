import { beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';

const trends = {
  from: '2026-11-01',
  to: '2026-11-07',
  granularity: 'day',
  buckets: [
    { start: '2026-11-06', orders: 2, ordersRevenue: 150, rentals: 1, rentalsRevenue: 50, newUsers: 1, newProviders: 0 },
    { start: '2026-11-07', orders: 1, ordersRevenue: 100, rentals: 0, rentalsRevenue: 0, newUsers: 0, newProviders: 1 },
  ],
  orderStatus: [{ key: 'PAID', label: 'Paid', count: 3 }],
  paymentMethods: [],
  topItems: [],
  providerStates: [{ key: 'TRIAL', label: 'On free trial', count: 2 }],
};

vi.mock('../src/services/marketplaceApi', () => ({
  adminDashboardApi: {
    stats: vi.fn(async () => ({
      data: { usersCreated: 4, providersCreated: 2, listingsCreated: 5, activeListings: 9, OrdersCount: 3, OrdersTotal: 999, rentalBookingsCount: 1, rentalBookingsTotal: 50 },
    })),
    trends: vi.fn(async () => ({ data: trends })),
  },
  providerDashboardApi: {
    stats: vi.fn(async () => ({ data: { OrdersCount: 3, OrdersTotal: 999, rentalBookingsCount: 1, rentalBookingsTotal: 50 } })),
    trends: vi.fn(async () => ({ data: trends })),
  },
  providerSubscriptionApi: {
    status: vi.fn(async () => ({ data: { valid: true, onTrial: true, trialDaysRemaining: 12 } })),
  },
}));

vi.mock('../src/composables/useLiveRefresh', () => ({
  useLiveRefresh: () => ({ refreshNow: vi.fn(), lastUpdated: null, refreshing: false }),
}));

vi.mock('vue-router', () => ({
  useRouter: () => ({ replace: vi.fn(), push: vi.fn() }),
  useRoute: () => ({ query: {} }),
}));

const stubs = {
  'router-link': { template: '<a><slot /></a>' },
  LiveStatus: true,
  DateRangeFilter: true,
};

async function render(view, role) {
  const { useAuthStore } = await import('../src/stores/auth');
  const auth = useAuthStore();
  auth.restoreFromStorage = () => {};
  auth.setSession?.({ token: 't', role, email: 'a@b.test', displayName: '', providerId: role === 'PLATFORM_ADMIN' ? null : 1 });
  const wrapper = mount(view, { global: { stubs } });
  await flushPromises();
  return wrapper;
}

describe('dashboards are an overview', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it('admin shows 4 headline numbers and 2 charts, with revenue matching the chart (cancelled excluded)', async () => {
    const { default: AdminDashboardView } = await import('../src/views/AdminDashboardView.vue');
    const w = await render(AdminDashboardView, 'PLATFORM_ADMIN');
    expect(w.findAll('.kpi')).toHaveLength(4);
    expect(w.findAll('.viz-card')).toHaveLength(2);
    expect(w.text()).toContain('Providers who can take orders');
    expect(w.text()).not.toContain('Admin tools');
    expect(w.text()).not.toContain('Activity health');
    // 150 + 50 + 100 from the chart data, not the stats total of 999 + 50 (which includes cancelled).
    expect(w.find('.kpi__value').text()).toMatch(/300/);
  });

  it('provider shows 3 headline numbers, a one-line plan status and 2 charts', async () => {
    const { default: ProviderDashboardView } = await import('../src/views/ProviderDashboardView.vue');
    const w = await render(ProviderDashboardView, 'PROVIDER_OWNER');
    expect(w.findAll('.kpi')).toHaveLength(3);
    expect(w.findAll('.viz-card')).toHaveLength(2);
    expect(w.find('.sub-strip').text()).toContain('Free trial');
    expect(w.find('.sub-strip').text()).toContain('12 days left');
    expect(w.text()).not.toContain('Next best action');
  });
});
