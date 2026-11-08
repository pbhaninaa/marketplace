<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { adminDashboardApi } from '../services/marketplaceApi';
import { useAuthStore } from '../stores/auth';
import { formatCount, formatDateRange, formatMoney } from '../utils/display';
import { useLiveRefresh } from '../composables/useLiveRefresh';
import LiveStatus from '../components/LiveStatus.vue';
import DateRangeFilter from '../components/ui/DateRangeFilter.vue';
import TimeChart from '../components/charts/TimeChart.vue';
import RankedBars from '../components/charts/RankedBars.vue';
import SegmentedBar from '../components/charts/SegmentedBar.vue';
import { dateRangeQuery } from '../utils/dateRangeQuery';
import {
  labelsOf,
  orderStatusParts,
  paymentMethodItems,
  perBucket,
  providerStateParts,
  revenueSeries,
  signupSeries,
  topItems,
  volumeSeries,
} from '../components/charts/dashboardSeries';

const router = useRouter();
const auth = useAuthStore();

const loading = ref(false);
const stats = ref(null);
const trends = ref(null);
const error = ref('');
/** One filter row scopes every number and chart below it. */
const range = ref({ preset: 'LAST_30', from: '', to: '' });
const chartLabels = computed(() => labelsOf(trends.value));
const per = computed(() => perBucket(trends.value));

/** KPI text: real number once loaded, otherwise say what is happening instead of a dash. */
const kpi = (value) => {
  if (stats.value) return formatCount(value);
  return loading.value ? 'Loading…' : 'Unavailable';
};

onMounted(() => {
  auth.restoreFromStorage();
  if (!auth.isPlatformAdmin) {
    router.replace({ path: '/login', query: { redirect: '/admin' } });
    return;
  }
  load();
});

async function load({ quiet = false } = {}) {
  if (!quiet) {
    loading.value = true;
    error.value = '';
  }
  try {
    const q = dateRangeQuery(range.value) || undefined;
    const [statsRes, trendsRes] = await Promise.all([
      adminDashboardApi.stats(q),
      // Charts are a bonus: if they fail, keep the KPI numbers.
      adminDashboardApi.trends(q).catch(() => null),
    ]);
    stats.value = statsRes.data;
    if (trendsRes) trends.value = trendsRes.data;
  } catch (e) {
    // Background refresh: keep the numbers on screen.
    if (quiet) throw e;
    stats.value = null;
    error.value = e.response?.data?.message || 'Could not load platform stats. Try Refresh.';
  } finally {
    if (!quiet) loading.value = false;
  }
}

const rangeLabel = computed(() =>
  stats.value ? formatDateRange(stats.value.from, stats.value.to) : 'Last 30 days',
);

watch(
  () => ({ ...range.value }),
  () => {
    if (!auth.isPlatformAdmin) return;
    if (range.value.preset === 'CUSTOM' && (!range.value.from || !range.value.to)) return;
    load();
  },
);

// Live: reload on server pushes (orders, payments, subscriptions), tab re-focus, and every 60s.
const { refreshNow, lastUpdated, refreshing } = useLiveRefresh(() => load({ quiet: true }));

const activityScore = computed(() => {
  // Field names match AdminDashboardStatsResponse (note the capital "O" in OrdersCount).
  const orderCount = Number(stats.value?.OrdersCount ?? 0);
  const rentalCount = Number(stats.value?.rentalBookingsCount ?? 0);
  const n = orderCount + rentalCount;
  return Math.max(0, Math.min(100, Math.round((Math.log10(n + 1) / Math.log10(200 + 1)) * 100)));
});
</script>

<template>
  <div class="page-document page-document--wide admin-page">
    <header class="page-hero">
      <p class="page-hero__eyebrow">Platform</p>
      <h1 class="page-hero__title">Administration</h1>
      <p class="page-hero__lead">Progress overview and quick links to admin tools.</p>
    </header>

    <section class="surface-panel admin-panel admin-panel--wide">
      <div class="panel-head">
        <h2>Platform progress</h2>
        <LiveStatus :updated-at="lastUpdated" :refreshing="refreshing || loading" @refresh="refreshNow" />
      </div>

      <div class="range-row">
        <DateRangeFilter v-model="range" />
        <p class="muted small range-note">{{ rangeLabel }}</p>
      </div>
      <p v-if="error" class="err-toast" role="alert">{{ error }}</p>

      <div class="kpi-grid">
        <div class="kpi">
          <span class="kpi__label">Users created</span>
          <strong class="kpi__value">{{ kpi(stats?.usersCreated) }}</strong>
          <span class="kpi__hint">In date range</span>
        </div>
        <div class="kpi">
          <span class="kpi__label">Providers created</span>
          <strong class="kpi__value">{{ kpi(stats?.providersCreated) }}</strong>
          <span class="kpi__hint">Onboarding</span>
        </div>
        <div class="kpi">
          <span class="kpi__label">Listings created</span>
          <strong class="kpi__value">{{ kpi(stats?.listingsCreated) }}</strong>
          <span class="kpi__hint">{{ stats ? `${formatCount(stats.activeListings)} active now` : 'Supply growth' }}</span>
        </div>
        <div class="kpi">
          <span class="kpi__label">Orders</span>
          <strong class="kpi__value">{{ kpi(stats?.OrdersCount) }}</strong>
          <span class="kpi__hint">{{ stats ? formatMoney(stats.OrdersTotal) : 'Purchases' }}</span>
        </div>
        <div class="kpi">
          <span class="kpi__label">Rental bookings</span>
          <strong class="kpi__value">{{ kpi(stats?.rentalBookingsCount) }}</strong>
          <span class="kpi__hint">{{ stats ? formatMoney(stats.rentalBookingsTotal) : 'Rentals' }}</span>
        </div>
        <div class="kpi">
          <span class="kpi__label">Activity health</span>
          <strong class="kpi__value">{{ stats ? `${activityScore}%` : kpi() }}</strong>
          <span class="kpi__hint">Signal from order volume</span>
        </div>
      </div>

      <div v-if="trends" class="dash-charts" aria-label="Platform charts">
        <TimeChart
          class="dash-charts__wide"
          title="Revenue"
          :subtitle="`All providers, sales orders and rentals ${per}, cancelled excluded`"
          :labels="chartLabels"
          :granularity="trends.granularity"
          :series="revenueSeries(trends)"
          money
          :busy="loading"
          empty-text="No revenue in this period yet."
        />
        <TimeChart
          title="Orders and bookings"
          :subtitle="`Number placed ${per}`"
          kind="columns"
          :labels="chartLabels"
          :granularity="trends.granularity"
          :series="volumeSeries(trends)"
          :busy="loading"
          empty-text="No orders or bookings in this period yet."
        />
        <TimeChart
          title="Sign-ups"
          subtitle="Running total of new users and providers this period"
          :labels="chartLabels"
          :granularity="trends.granularity"
          :series="signupSeries(trends)"
          :busy="loading"
          empty-text="No sign-ups in this period yet."
        />
        <SegmentedBar
          title="Providers who can take orders"
          subtitle="Every provider right now, by subscription state"
          :parts="providerStateParts(trends)"
          unit="providers"
          :busy="loading"
          empty-text="No providers yet."
        />
        <SegmentedBar
          title="Order status"
          subtitle="Where this period's orders and bookings are now"
          :parts="orderStatusParts(trends)"
          unit="orders"
          :busy="loading"
          empty-text="No orders or bookings in this period yet."
        />
        <RankedBars
          title="Top providers"
          subtitle="Revenue in this period, top 5"
          :items="topItems(trends, { money: true })"
          money
          :busy="loading"
          empty-text="No provider revenue in this period yet."
        />
        <RankedBars
          title="Payment methods"
          subtitle="Amount by how buyers paid"
          :items="paymentMethodItems(trends)"
          money
          :busy="loading"
          empty-text="No payments in this period yet."
        />
      </div>
    </section>

    <section class="surface-panel admin-panel admin-panel--wide">
      <h2>Admin tools</h2>
      <div class="tools-grid">
        <router-link to="/admin/history" class="tool-card">
          <strong>Orders &amp; payments</strong>
          <span class="muted small">Filter every order and payment, export CSV / Excel</span>
        </router-link>
        <router-link to="/admin/settings" class="tool-card">
          <strong>Settings</strong>
          <span class="muted small">Name, pricing, and usage fees</span>
        </router-link>
        <router-link to="/admin/providers" class="tool-card">
          <strong>Providers</strong>
          <span class="muted small">Approve & support providers</span>
        </router-link>
        <router-link to="/admin/listings" class="tool-card">
          <strong>Listings</strong>
          <span class="muted small">Publish/unpublish & cleanup</span>
        </router-link>
        <router-link to="/admin/users" class="tool-card">
          <strong>Users</strong>
          <span class="muted small">Search and disable accounts</span>
        </router-link>
        <router-link to="/admin/support-users" class="tool-card">
          <strong>Support users</strong>
          <span class="muted small">Create and manage support accounts</span>
        </router-link>
        <router-link to="/admin/password" class="tool-card">
          <strong>Password</strong>
          <span class="muted small">Change your admin password</span>
        </router-link>
      </div>
    </section>
  </div>
</template>

<style scoped>
.admin-page {
  padding: 0.5rem 0 2rem;
}

.admin-panel {
  margin: 0 auto 1.35rem;
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.admin-panel--wide {
  max-width: none;
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 0.5rem;
}

.admin-panel .muted {
  margin: 0 0 0.5rem;
}

.admin-panel h2 {
  font-family: var(--font-display);
}

.range-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem 1rem;
  margin-bottom: 0.75rem;
}

.range-row .range-note {
  margin: 0;
}

.dash-charts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1rem;
  margin-top: 1.25rem;
}

.dash-charts__wide {
  grid-column: 1 / -1;
}

@media (max-width: 820px) {
  .dash-charts {
    grid-template-columns: minmax(0, 1fr);
  }
}

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0.85rem;
  margin-top: 0.75rem;
}

.kpi {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 0.85rem 0.95rem;
  background: var(--color-surface-elevated);
  box-shadow: var(--shadow-sm);
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.kpi__label {
  font-size: 0.78rem;
  letter-spacing: 0.02em;
  color: var(--color-muted);
}

.kpi__value {
  font-family: var(--font-display);
  font-size: 1.2rem;
}

.kpi__hint {
  font-size: 0.82rem;
  color: var(--color-muted);
}

.tools-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-4);
  margin-top: var(--space-3);
}

.tool-card {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: var(--space-5);
  background: var(--color-surface-elevated);
  box-shadow: var(--shadow-sm);
  text-decoration: none;
  color: var(--color-canopy);
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.tool-card:hover {
  border-color: rgba(61, 122, 102, 0.28);
  box-shadow: var(--shadow-md);
}

.tool-card--danger {
  border-color: rgba(180, 40, 40, 0.22);
  background: rgba(252, 239, 234, 0.6);
}

@media (max-width: 980px) {
  .kpi-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .tools-grid {
    grid-template-columns: 1fr;
  }
}
</style>
