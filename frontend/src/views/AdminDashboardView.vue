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
import SegmentedBar from '../components/charts/SegmentedBar.vue';
import { dateRangeQuery } from '../utils/dateRangeQuery';
import {
  labelsOf,
  perBucket,
  providerStateParts,
  revenueSeries,
  revenueTotalOf,
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

/** Same figure the Revenue chart plots (cancelled excluded), so the tile and chart always agree. */
const revenueTotal = computed(() => revenueTotalOf(trends.value));
</script>

<template>
  <div class="page-document page-document--wide admin-page">
    <header class="page-hero">
      <p class="page-hero__eyebrow">Platform</p>
      <h1 class="page-hero__title">Administration</h1>
      <p class="page-hero__lead">How the platform is doing at a glance. Details live in the menu.</p>
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
          <span class="kpi__label">Revenue</span>
          <strong class="kpi__value">{{ trends ? formatMoney(revenueTotal) : kpi() }}</strong>
          <span class="kpi__hint">Sales and rentals, cancelled excluded</span>
        </div>
        <div class="kpi">
          <span class="kpi__label">Orders and bookings</span>
          <strong class="kpi__value">{{ stats ? formatCount(Number(stats.OrdersCount) + Number(stats.rentalBookingsCount)) : kpi() }}</strong>
          <span class="kpi__hint">{{ stats ? `${formatCount(stats.OrdersCount)} orders, ${formatCount(stats.rentalBookingsCount)} rentals` : 'In this period' }}</span>
        </div>
        <div class="kpi">
          <span class="kpi__label">New providers</span>
          <strong class="kpi__value">{{ kpi(stats?.providersCreated) }}</strong>
          <span class="kpi__hint">{{ stats ? `${formatCount(stats.usersCreated)} new users` : 'In this period' }}</span>
        </div>
        <div class="kpi">
          <span class="kpi__label">Live listings</span>
          <strong class="kpi__value">{{ kpi(stats?.activeListings) }}</strong>
          <span class="kpi__hint">{{ stats ? `${formatCount(stats.listingsCreated)} added this period` : 'Right now' }}</span>
        </div>
      </div>

      <div v-if="trends" class="dash-charts" aria-label="Platform overview charts">
        <TimeChart
          class="dash-charts__main"
          title="Revenue"
          :subtitle="`Sales orders and rentals ${per}`"
          :labels="chartLabels"
          :granularity="trends.granularity"
          :series="revenueSeries(trends)"
          money
          :busy="loading"
          empty-text="No revenue in this period yet."
        />
        <SegmentedBar
          title="Providers who can take orders"
          subtitle="All providers right now"
          :parts="providerStateParts(trends)"
          unit="providers"
          :busy="loading"
          empty-text="No providers yet."
        />
      </div>

      <p class="dash-more">
        <router-link to="/admin/history">See every order and payment</router-link>
        <span aria-hidden="true">·</span>
        <router-link to="/admin/providers">Manage providers</router-link>
      </p>
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
  /* Overview: the revenue trend leads, the provider mix sits beside it. */
  grid-template-columns: minmax(0, 2fr) minmax(0, 1fr);
  gap: 1rem;
  margin-top: 1.25rem;
  align-items: start;
}

@media (max-width: 900px) {
  .dash-charts {
    grid-template-columns: minmax(0, 1fr);
  }
}

.dash-more {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin: 1rem 0 0;
  font-size: 0.88rem;
  color: var(--color-muted);
}

.dash-more a {
  font-weight: 600;
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

@media (max-width: 980px) {
  .kpi-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
