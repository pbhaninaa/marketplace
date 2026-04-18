<script setup>
import { ref, onMounted, computed } from 'vue';
import { useRouter } from 'vue-router';
import { api } from '../api';
import { useAuthStore } from '../stores/auth';
import ResponsiveRecordShell from '../components/layout/ResponsiveRecordShell.vue';
import DataTableShell from '../components/ui/DataTableShell.vue';
import VerifyCodePanel from '../components/provider/VerifyCodePanel.vue';

const router = useRouter();
const auth = useAuthStore();

const tab = ref('purchases'); // purchases | rentals | verify
const loading = ref(true);
const error = ref('');

const purchases = ref({ content: [], totalElements: 0 });
const rentals = ref({ content: [], totalElements: 0 });

const rows = computed(() => (tab.value === 'purchases' ? purchases.value.content : rentals.value.content));

onMounted(async () => {
  auth.restoreFromStorage();
  if (!auth.isProviderUser) {
    router.replace({ path: '/login', query: { redirect: '/provider/orders' } });
    return;
  }
  await load();
});

async function load() {
  loading.value = true;
  error.value = '';
  try {
    const [p, r] = await Promise.all([
      api.get('/api/provider/me/orders/purchases', { params: { page: 0, size: 50 } }),
      api.get('/api/provider/me/orders/rentals', { params: { page: 0, size: 50 } }),
    ]);
    purchases.value = p.data;
    rentals.value = r.data;
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="page-document page-document--wide provider-orders-page">
    <header class="page-hero">
      <p class="page-hero__eyebrow">Provider</p>
      <h1 class="page-hero__title">Orders & bookings</h1>
      <p class="page-hero__lead">View guest purchases and rental bookings for your listings.</p>
    </header>

    <p v-if="error" class="err-toast">{{ error }}</p>
    <p v-if="loading" class="muted loading-line">Loading…</p>

    <template v-else>
      <div class="tabs">
        <button type="button" class="tab" :class="{ active: tab === 'purchases' }" @click="tab = 'purchases'">
          Purchases ({{ purchases.totalElements || 0 }})
        </button>
        <button type="button" class="tab" :class="{ active: tab === 'rentals' }" @click="tab = 'rentals'">
          Rentals ({{ rentals.totalElements || 0 }})
        </button>
        <button type="button" class="tab" :class="{ active: tab === 'verify' }" @click="tab = 'verify'">
          <span class="material-icons tab-icon">verified_user</span>
          Verify Code
        </button>
      </div>

      <VerifyCodePanel v-if="tab === 'verify'" />

      <ResponsiveRecordShell v-else :desktop-label="tab === 'purchases' ? 'Purchase orders' : 'Rental bookings'">
        <template #desktop>
          <DataTableShell :caption="tab === 'purchases' ? 'Purchase orders' : 'Rental bookings'">
            <thead>
              <tr>
                <th>ID</th>
                <th>Guest</th>
                <th>Status</th>
                <th class="col-num">Total</th>
                <th>Created</th>
                <th v-if="tab === 'rentals'">Window</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="o in rows" :key="o.id">
                <td>#{{ o.id }}</td>
                <td>{{ o.guestEmail || '—' }}</td>
                <td>{{ o.status }}</td>
                <td class="col-num">R {{ o.total }}</td>
                <td>{{ String(o.createdAt || '').slice(0, 19) }}</td>
                <td v-if="tab === 'rentals'">
                  {{ String(o.startAt || '').slice(0, 16) }} → {{ String(o.endAt || '').slice(0, 16) }}
                </td>
              </tr>
              <tr v-if="!rows || !rows.length">
                <td :colspan="tab === 'rentals' ? 6 : 5" class="muted small">No records yet.</td>
              </tr>
            </tbody>
          </DataTableShell>
        </template>

        <template #mobile>
          <div class="cards">
            <article v-for="o in rows" :key="o.id" class="order-card">
              <strong>#{{ o.id }} · {{ o.status }}</strong>
              <span class="meta">{{ o.guestEmail || '—' }}</span>
              <span class="meta">R {{ o.total }}</span>
              <span class="meta">{{ String(o.createdAt || '').slice(0, 16) }}</span>
              <span v-if="tab === 'rentals'" class="meta">
                {{ String(o.startAt || '').slice(0, 16) }} → {{ String(o.endAt || '').slice(0, 16) }}
              </span>
            </article>
            <p v-if="!rows || !rows.length" class="muted small">No records yet.</p>
          </div>
        </template>
      </ResponsiveRecordShell>
    </template>
  </div>
</template>

<style scoped>
.provider-orders-page {
  padding-bottom: 2rem;
}

.tabs {
  display: inline-flex;
  gap: 0.5rem;
  padding: 0.25rem;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-surface-elevated);
  box-shadow: var(--shadow-sm);
  margin-bottom: 1rem;
}

.tab {
  border: none;
  background: transparent;
  cursor: pointer;
  font: inherit;
  font-weight: 700;
  padding: 0.5rem 0.85rem;
  border-radius: 999px;
  color: var(--color-muted);
}

.tab.active {
  background: rgba(61, 122, 102, 0.14);
  color: var(--color-canopy);
}

.tab-icon {
  font-size: 18px;
  vertical-align: middle;
  margin-right: 4px;
}

.col-num {
  text-align: right;
}

.cards {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.order-card {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 0.95rem 1.05rem;
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.meta {
  font-size: 0.82rem;
  color: var(--color-muted);
}
</style>

