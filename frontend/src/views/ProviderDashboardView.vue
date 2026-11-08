<script setup>
import { onMounted, computed, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { providerDashboardApi, providerSubscriptionApi } from '../services/marketplaceApi';
import { useAuthStore } from '../stores/auth';
import DateRangeFilter from '../components/ui/DateRangeFilter.vue';
import { formatCount, formatDate, formatEnum, formatMoney } from '../utils/display';
import LiveStatus from '../components/LiveStatus.vue';
import { useLiveRefresh } from '../composables/useLiveRefresh';
import TimeChart from '../components/charts/TimeChart.vue';
import SegmentedBar from '../components/charts/SegmentedBar.vue';
import {
  labelsOf,
  orderStatusParts,
  perBucket,
  revenueSeries,
  revenueTotalOf,
} from '../components/charts/dashboardSeries';

const router = useRouter();
const auth = useAuthStore();

const loading = ref(false);
const error = ref('');
const stats = ref(null);
const range = ref({ preset: 'LAST_7', from: '', to: '' });
const subLoading = ref(false);
const subscription = ref(null);

onMounted(() => {
  auth.restoreFromStorage();
  if (!auth.isProviderUser) {
    router.replace({ path: '/login', query: { redirect: '/provider' } });
    return;
  }
  loadStats();
  loadSubscription();
});

function toDateInputValue(d) {
  const yyyy = d.getFullYear();
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  return `${yyyy}-${mm}-${dd}`;
}

function computeQuery() {
  const now = new Date();
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  if (range.value.preset === 'TODAY') {
    const v = toDateInputValue(today);
    return { from: v, to: v };
  }
  if (range.value.preset === 'LAST_7') {
    const from = new Date(today);
    from.setDate(from.getDate() - 6);
    return { from: toDateInputValue(from), to: toDateInputValue(today) };
  }
  if (range.value.preset === 'LAST_30') {
    const from = new Date(today);
    from.setDate(from.getDate() - 29);
    return { from: toDateInputValue(from), to: toDateInputValue(today) };
  }
  // CUSTOM
  if (!range.value.from || !range.value.to) return null;
  return { from: range.value.from, to: range.value.to };
}

async function loadStats({ quiet = false } = {}) {
  if (!quiet) {
    loading.value = true;
    error.value = '';
  }
  try {
    const q = computeQuery();
    const params = q ? { params: q } : undefined;
    const [statsRes, trendsRes] = await Promise.all([
      providerDashboardApi.stats(params),
      // Charts are a bonus: if they fail, keep the KPI numbers.
      providerDashboardApi.trends(params).catch(() => null),
    ]);
    stats.value = statsRes.data;
    if (trendsRes) trends.value = trendsRes.data;
    error.value = '';
  } catch (e) {
    if (quiet) throw e; // background refresh: keep the numbers on screen
    error.value = e.response?.data?.message || e.message;
  } finally {
    if (!quiet) loading.value = false;
  }
}

async function loadSubscription({ quiet = false } = {}) {
  if (!quiet) subLoading.value = true;
  try {
    const { data } = await providerSubscriptionApi.status();
    subscription.value = data;
  } catch {
    if (!quiet) subscription.value = null;
  } finally {
    if (!quiet) subLoading.value = false;
  }
}

/** Chart data for the same date range as the KPI tiles. */
const trends = ref(null);
const chartLabels = computed(() => labelsOf(trends.value));
const per = computed(() => perBucket(trends.value));

/** KPI text: real value once loaded, otherwise what is happening (never a dash). */
const kpi = (formatted) => {
  if (stats.value) return formatted;
  return loading.value ? 'Loading…' : 'Unavailable';
};

// Live: reload on this provider's order / payment / subscription pushes, tab re-focus, and every 60s.
const { refreshNow, lastUpdated, refreshing } = useLiveRefresh(() =>
  Promise.all([loadStats({ quiet: true }), loadSubscription({ quiet: true })]),
);

/** Same figure the Revenue chart plots (cancelled excluded), so the tile and chart always agree. */
const revenueTotal = computed(() => revenueTotalOf(trends.value));

/** One-line subscription status: what plan, and how long it lasts. */
const subscriptionHeadline = computed(() => {
  const s = subscription.value;
  if (!s) return '';
  if (s.onTrial) return 'Free trial';
  if (s.valid) return `${formatEnum(s.plan, 'Paid')} plan active`;
  return 'Not taking orders: no active plan';
});

const subscriptionDetail = computed(() => {
  const s = subscription.value;
  if (!s) return '';
  if (s.onTrial) {
    const d = Number(s.trialDaysRemaining) || 0;
    return d > 0 ? `${d} day${d === 1 ? '' : 's'} left` : 'ends today';
  }
  if (s.valid && s.expiresAt) return `renews or ends ${formatDate(s.expiresAt)}`;
  if (!s.valid) return 'your listings are hidden until you choose a plan';
  return '';
});

watch(
  () => ({ ...range.value }),
  () => {
    if (!auth.isProviderUser) return;
    if (range.value.preset === 'CUSTOM' && (!range.value.from || !range.value.to)) return;
    loadStats();
  },
  { deep: true },
);
</script>

<template>
  <div class="page-document provider-dash">
    <header class="page-hero">
      <p class="page-hero__eyebrow">Provider</p>
      <h1 class="page-hero__title">Dashboard</h1>
      <p class="page-hero__lead">How your business is doing at a glance. Details live in the menu.</p>
    </header>

    <div class="dash-live">
      <LiveStatus :updated-at="lastUpdated" :refreshing="refreshing || loading" @refresh="refreshNow" />
    </div>

    <p v-if="error" class="err-toast">{{ error }}</p>

    <section class="dash-top">
      <DateRangeFilter v-model="range" />
      <div class="kpis">
        <div class="kpi">
          <span class="kpi__label">Revenue</span>
          <strong class="kpi__value">{{ trends ? formatMoney(revenueTotal) : kpi() }}</strong>
          <span class="kpi__hint">Sales and rentals, cancelled excluded</span>
        </div>
        <div class="kpi">
          <span class="kpi__label">Purchase orders</span>
          <!-- Field names match ProviderDashboardStatsResponse (capital "O" in OrdersCount). -->
          <strong class="kpi__value">{{ kpi(formatCount(stats?.OrdersCount)) }}</strong>
          <span class="kpi__hint">In this period</span>
        </div>
        <div class="kpi">
          <span class="kpi__label">Rental bookings</span>
          <strong class="kpi__value">{{ kpi(formatCount(stats?.rentalBookingsCount)) }}</strong>
          <span class="kpi__hint">In this period</span>
        </div>
      </div>
    </section>

    <div v-if="subscription" class="sub-strip" :class="{ 'sub-strip--warn': !subscription.valid }" role="status">
      <span class="material-icons" aria-hidden="true">{{ subscription.valid ? 'verified' : 'error_outline' }}</span>
      <span class="sub-strip__text">
        <strong>{{ subscriptionHeadline }}</strong>
        <span v-if="subscriptionDetail" class="muted"> · {{ subscriptionDetail }}</span>
      </span>
      <router-link to="/provider/subscription" class="btn btn-ghost btn-xs">
        {{ subscription.valid ? 'Manage' : 'Choose a plan' }}
      </router-link>
    </div>

    <section v-if="trends" class="dash-charts" aria-label="Sales overview charts">
      <TimeChart
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
        title="Order status"
        subtitle="Orders and bookings from this period"
        :parts="orderStatusParts(trends)"
        unit="orders"
        :busy="loading"
        empty-text="No orders or bookings in this period yet."
      />
    </section>

    <p class="dash-more">
      <router-link to="/provider/orders">Open orders</router-link>
      <span aria-hidden="true">·</span>
      <router-link to="/provider/history">See every order and payment</router-link>
      <span aria-hidden="true">·</span>
      <router-link to="/provider/listings">Manage listings</router-link>
    </p>
  </div>
</template>

<style scoped>
.dash-live {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 0.5rem;
  margin-bottom: 0.85rem;
}

.dash-top {
  width: 100%;
  margin: 0 0 1rem;
  display: grid;
  grid-template-columns: 1fr;
  gap: 0.85rem;
}

/* KPI tiles: same look as the admin dashboard. */
.kpis {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0.85rem;
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

/* One-line subscription status instead of a whole section. */
.sub-strip {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding: 0.6rem 0.85rem;
  margin-bottom: 1rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-sage-soft);
  color: var(--color-text);
  font-size: 0.9rem;
}

.sub-strip .material-icons {
  font-size: 1.2rem;
  color: var(--color-sage);
}

.sub-strip__text {
  flex: 1;
  min-width: 0;
}

.sub-strip .btn {
  margin-left: auto;
}

.sub-strip--warn {
  background: var(--color-danger-bg);
}

.sub-strip--warn .material-icons {
  color: var(--color-danger-text);
}

/* Overview: the revenue trend leads, order status sits beside it. */
.dash-charts {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(0, 1fr);
  gap: 1rem;
  align-items: start;
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

@media (max-width: 900px) {
  .kpis {
    grid-template-columns: 1fr;
  }
  .dash-charts {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>

