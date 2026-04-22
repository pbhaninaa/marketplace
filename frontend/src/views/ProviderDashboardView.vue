<script setup>
import { onMounted, computed, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { providerDashboardApi } from '../services/marketplaceApi';
import { useAuthStore } from '../stores/auth';
import DateRangeFilter from '../components/ui/DateRangeFilter.vue';

const router = useRouter();
const auth = useAuthStore();

const canManage = computed(() => auth.role === 'PROVIDER_OWNER' || auth.role === 'PROVIDER_ADMIN');

const loading = ref(false);
const error = ref('');
const stats = ref(null);
const range = ref({ preset: 'LAST_7', from: '', to: '' });

onMounted(() => {
  auth.restoreFromStorage();
  if (!auth.isProviderUser) {
    router.replace({ path: '/login', query: { redirect: '/provider' } });
    return;
  }
  loadStats();
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

async function loadStats() {
  loading.value = true;
  error.value = '';
  try {
    const q = computeQuery();
    const params = q ? { params: q } : undefined;
    const { data } = await providerDashboardApi.stats(params);
    stats.value = data;
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  } finally {
    loading.value = false;
  }
}

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
      <p class="page-hero__lead">Insights and quick access to your provider tools.</p>
    </header>

    <p v-if="error" class="err-toast">{{ error }}</p>

    <section class="dash-top">
      <DateRangeFilter v-model="range" />
      <div class="kpis">
        <div class="kpi">
          <span class="kpi__label">Purchase orders</span>
          <strong class="kpi__value">{{ stats?.purchaseOrdersCount ?? '—' }}</strong>
          <span class="kpi__hint">Total: R {{ stats?.purchaseOrdersTotal ?? '—' }}</span>
        </div>
        <div class="kpi">
          <span class="kpi__label">Rental bookings</span>
          <strong class="kpi__value">{{ stats?.rentalBookingsCount ?? '—' }}</strong>
          <span class="kpi__hint">Total: R {{ stats?.rentalBookingsTotal ?? '—' }}</span>
        </div>
        <div class="kpi">
          <span class="kpi__label">Range</span>
          <strong class="kpi__value">{{ stats?.from ?? '—' }} → {{ stats?.to ?? '—' }}</strong>
          <span class="kpi__hint">{{ loading ? 'Loading…' : 'Updated' }}</span>
        </div>
      </div>
    </section>

    <section class="surface-panel dash-panel">
      <div class="dash-grid">
        <router-link to="/provider/orders" class="dash-card">
          <strong>Orders</strong>
          <span class="muted small">Purchases & rentals</span>
        </router-link>
        <router-link to="/provider/listings" class="dash-card">
          <strong>Listings</strong>
          <span class="muted small">Create and manage items/services</span>
        </router-link>
        <router-link to="/provider/settings" class="dash-card">
          <strong>Profile & settings</strong>
          <span class="muted small">Location, banking, payment methods</span>
        </router-link>
        <router-link v-if="canManage" to="/provider/team" class="dash-card">
          <strong>Team & payroll</strong>
          <span class="muted small">Staff, permissions, payroll</span>
        </router-link>
        <router-link v-if="canManage" to="/provider/staff-payments" class="dash-card">
          <strong>Staff payments</strong>
          <span class="muted small">Expected payouts from rate × units</span>
        </router-link>
      </div>
    </section>
  </div>
</template>

<style scoped>
.provider-dash {
  padding-bottom: 2rem;
}
.dash-top {
  max-width: 840px;
  margin: 0 auto 1rem;
  display: grid;
  grid-template-columns: 1fr;
  gap: 0.85rem;
}
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
  gap: 0.25rem;
}
.kpi__label {
  font-size: 0.8rem;
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
.dash-panel {
  max-width: 840px;
  margin: 0 auto;
}
.dash-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1rem;
}
.dash-card {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 1rem 1.05rem;
  background: var(--color-surface-elevated);
  box-shadow: var(--shadow-sm);
  text-decoration: none;
  color: var(--color-canopy);
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}
.dash-card:hover {
  border-color: rgba(61, 122, 102, 0.28);
  box-shadow: var(--shadow-md);
}
@media (max-width: 900px) {
  .kpis {
    grid-template-columns: 1fr;
  }
  .dash-grid {
    grid-template-columns: 1fr;
  }
}
</style>

