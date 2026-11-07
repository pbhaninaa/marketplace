<script setup>
/**
 * One page for orders and payments: shared filter bar, server-side paging over every record,
 * filtered totals, CSV/Excel export, and live refresh.
 */
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import DataTableShell from '../ui/DataTableShell.vue';
import PaginationBar from '../ui/PaginationBar.vue';
import LiveStatus from '../LiveStatus.vue';
import HistoryFilterBar from './HistoryFilterBar.vue';
import { useLiveRefresh } from '../../composables/useLiveRefresh';
import {
  ORDER_STATUS_OPTIONS,
  PAYMENT_METHOD_OPTIONS,
  buildHistoryParams,
  countActiveFilters,
  emptyHistoryFilters,
  formatMoney,
  labelFor,
} from '../../utils/historyFilters';

const props = defineProps({
  /** { orders(params), payments(params), exportReport(kind, params, format) } */
  api: { type: Object, required: true },
  audience: { type: String, default: 'provider', validator: (v) => ['admin', 'provider', 'client'].includes(v) },
  /** [{ id, name }] for the admin provider filter. */
  providers: { type: Array, default: null },
  canExport: { type: Boolean, default: false },
  /** Called with a client order row to download its invoice. */
  onInvoice: { type: Function, default: null },
});

const route = useRoute();
const router = useRouter();

const tab = ref(route.query.tab === 'payments' ? 'payments' : 'orders');
const filters = ref(emptyHistoryFilters());
const page = ref(0);
const size = ref(25);
const result = ref(null);
const loading = ref(false);
const error = ref('');
const exporting = ref('');

const isAdmin = computed(() => props.audience === 'admin');
const isClient = computed(() => props.audience === 'client');
const showProvider = computed(() => props.audience !== 'provider');
const paramOptions = computed(() => ({ tab: tab.value, allowProvider: isAdmin.value }));
const activeCount = computed(() => countActiveFilters(filters.value, paramOptions.value));
const rows = computed(() => result.value?.content || []);
const summary = computed(() => result.value?.summary || null);
const totalPages = computed(() => Math.max(1, Number(result.value?.totalPages || 1)));

let requestSeq = 0;

async function load({ quiet = false } = {}) {
  const seq = ++requestSeq;
  if (!quiet) loading.value = true;
  try {
    const params = { ...buildHistoryParams(filters.value, paramOptions.value), page: page.value, size: size.value };
    const call = tab.value === 'payments' ? props.api.payments : props.api.orders;
    const { data } = await call(params);
    if (seq !== requestSeq) return;
    result.value = data;
    error.value = '';
    // A live reload can shrink the result set below the current page.
    if (data && page.value > 0 && page.value >= data.totalPages) {
      page.value = Math.max(0, data.totalPages - 1);
    }
  } catch (e) {
    if (seq !== requestSeq) return;
    error.value = e.response?.data?.message || e.message || 'Could not load history.';
    if (quiet) throw e;
  } finally {
    if (seq === requestSeq) loading.value = false;
  }
}

const { refreshNow, lastUpdated, refreshing, markFresh } = useLiveRefresh(() => load({ quiet: true }));

async function reload() {
  await load();
  markFresh();
}

onMounted(reload);

let searchTimer = null;
watch(
  filters,
  (next, prev) => {
    page.value = 0;
    clearTimeout(searchTimer);
    // Debounce typing in the search box; apply select/date changes immediately.
    const delay = prev && next.q !== prev.q ? 350 : 0;
    searchTimer = setTimeout(reload, delay);
  },
  { deep: true },
);

watch(tab, (t) => {
  page.value = 0;
  result.value = null;
  router.replace({ query: { ...route.query, tab: t === 'payments' ? 'payments' : undefined } }).catch(() => {});
  reload();
});

watch(size, () => {
  page.value = 0;
  reload();
});

function goTo(p) {
  page.value = Math.min(Math.max(0, p), totalPages.value - 1);
  reload();
}

function resetFilters() {
  filters.value = emptyHistoryFilters();
}

async function exportAs(format) {
  exporting.value = format;
  try {
    await props.api.exportReport(tab.value, buildHistoryParams(filters.value, paramOptions.value), format);
  } catch (e) {
    error.value = e.response?.data?.message || e.message || 'Export failed.';
  } finally {
    exporting.value = '';
  }
}

function when(value) {
  if (!value) return '—';
  try {
    return new Date(value).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' });
  } catch {
    return String(value);
  }
}

function statusClass(value) {
  const v = String(value || '').toUpperCase();
  if (v === 'PAID' || v === 'COLLECTED') return 'hist-pill--ok';
  if (v === 'CANCELLED') return 'hist-pill--bad';
  return 'hist-pill--wait';
}

const methodLabel = (m) => labelFor(PAYMENT_METHOD_OPTIONS, m);
const statusLabel = (s) => labelFor(ORDER_STATUS_OPTIONS, s);
const paymentLabel = (s) => (String(s || '').toUpperCase() === 'PAID' ? 'Paid' : 'Pending');
</script>

<template>
  <div class="hist">
    <div class="hist__top">
      <div class="tabs" role="tablist">
        <button type="button" role="tab" class="tab" :class="{ active: tab === 'orders' }" :aria-selected="tab === 'orders'" @click="tab = 'orders'">
          Orders
        </button>
        <button type="button" role="tab" class="tab" :class="{ active: tab === 'payments' }" :aria-selected="tab === 'payments'" @click="tab = 'payments'">
          Payments
        </button>
      </div>
      <div class="hist__tools">
        <LiveStatus :updated-at="lastUpdated" :refreshing="refreshing || loading" @refresh="refreshNow" />
        <template v-if="canExport">
          <button type="button" class="btn btn-ghost btn-xs" :disabled="!!exporting || !result?.totalElements" @click="exportAs('csv')">
            {{ exporting === 'csv' ? 'Exporting…' : 'Export CSV' }}
          </button>
          <button type="button" class="btn btn-ghost btn-xs" :disabled="!!exporting || !result?.totalElements" @click="exportAs('xlsx')">
            {{ exporting === 'xlsx' ? 'Exporting…' : 'Export Excel' }}
          </button>
        </template>
      </div>
    </div>

    <HistoryFilterBar
      v-model="filters"
      :tab="tab"
      :providers="isAdmin ? providers || [] : null"
      :active-count="activeCount"
      @reset="resetFilters"
    />

    <p v-if="error" class="err-toast">{{ error }}</p>

    <div v-if="summary" class="hist__summary" aria-label="Totals for the current filters">
      <div class="hist__stat">
        <span class="hist__stat-label">{{ tab === 'orders' ? 'Orders' : 'Payments' }}</span>
        <strong>{{ summary.count }}</strong>
      </div>
      <div class="hist__stat">
        <span class="hist__stat-label">Total value</span>
        <strong>{{ formatMoney(summary.totalAmount) }}</strong>
      </div>
      <div class="hist__stat">
        <span class="hist__stat-label">Paid ({{ summary.paidCount }})</span>
        <strong>{{ formatMoney(summary.paidAmount) }}</strong>
      </div>
      <div v-for="m in summary.byMethod || []" :key="m.method" class="hist__stat hist__stat--method">
        <span class="hist__stat-label">{{ methodLabel(m.method) }} ({{ m.count }})</span>
        <strong>{{ formatMoney(m.amount) }}</strong>
      </div>
    </div>

    <p v-if="loading && !result" class="muted">Loading…</p>
    <p v-else-if="result && rows.length === 0" class="muted hist__empty">
      {{ activeCount ? 'Nothing matches these filters.' : tab === 'orders' ? 'No orders yet.' : 'No payments yet.' }}
    </p>

    <DataTableShell v-else-if="rows.length && tab === 'orders'" caption="Orders" :max-chars="0">
      <thead>
        <tr>
          <th>Order</th>
          <th>Date</th>
          <th v-if="showProvider">Provider</th>
          <th v-if="!isClient">Customer</th>
          <th>Items</th>
          <th>Status</th>
          <th>Payment</th>
          <th class="num">Total</th>
          <th v-if="isClient && onInvoice"><span class="sr-only">Actions</span></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="o in rows" :key="o.id">
          <td>#{{ o.id }}<div v-if="o.reference" class="muted small">Ref {{ o.reference }}</div></td>
          <td>{{ when(o.createdAt) }}</td>
          <td v-if="showProvider">{{ o.providerName || '—' }}</td>
          <td v-if="!isClient">
            <div class="cell-stack">
              <span>{{ o.customerName || '—' }}</span>
              <span class="muted small">{{ o.customerEmail }}</span>
            </div>
          </td>
          <td><span class="hist__items" :title="o.items">{{ o.items || '—' }}</span></td>
          <td><span class="hist-pill" :class="statusClass(o.status)">{{ statusLabel(o.status) }}</span></td>
          <td>
            <div class="cell-stack">
              <span class="hist-pill" :class="statusClass(o.paymentStatus)">{{ paymentLabel(o.paymentStatus) }}</span>
              <span class="muted small">{{ methodLabel(o.paymentMethod) }}</span>
            </div>
          </td>
          <td class="num">{{ formatMoney(o.totalAmount) }}</td>
          <td v-if="isClient && onInvoice" class="cell-actions">
            <button type="button" class="btn btn-ghost btn-xs" @click="onInvoice(o)">Invoice</button>
          </td>
        </tr>
      </tbody>
    </DataTableShell>

    <DataTableShell v-else-if="rows.length" caption="Payments" :max-chars="0">
      <thead>
        <tr>
          <th>Payment</th>
          <th>Date</th>
          <th v-if="showProvider">Provider</th>
          <th>For</th>
          <th v-if="!isClient">Customer</th>
          <th>Method</th>
          <th>Status</th>
          <th class="num">Amount</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="p in rows" :key="p.id">
          <td>#{{ p.id }}</td>
          <td>{{ when(p.createdAt) }}</td>
          <td v-if="showProvider">{{ p.providerName || '—' }}</td>
          <td>{{ p.kind === 'RENTAL' ? `Rental #${p.rentalId}` : `Order #${p.orderId ?? '—'}` }}</td>
          <td v-if="!isClient">
            <div class="cell-stack">
              <span>{{ p.customerName || '—' }}</span>
              <span class="muted small">{{ p.customerEmail }}</span>
            </div>
          </td>
          <td>{{ methodLabel(p.method) }}</td>
          <td><span class="hist-pill" :class="statusClass(p.status)">{{ paymentLabel(p.status) }}</span></td>
          <td class="num">{{ formatMoney(p.amount) }}</td>
        </tr>
      </tbody>
    </DataTableShell>

    <div v-if="result && result.totalElements > 0" class="hist__paging">
      <PaginationBar v-if="totalPages > 1" :page="page" :total-pages="totalPages" @prev="goTo(page - 1)" @next="goTo(page + 1)" />
      <label class="hist__size">
        <span class="muted small">Rows per page</span>
        <select v-model.number="size">
          <option :value="25">25</option>
          <option :value="50">50</option>
          <option :value="100">100</option>
          <option :value="200">200</option>
        </select>
      </label>
      <span class="muted small">{{ result.totalElements }} record{{ result.totalElements === 1 ? '' : 's' }}</span>
    </div>
  </div>
</template>

<style scoped>
.hist__top {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  margin-bottom: 0.85rem;
}
.hist__tools {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
}
.hist__tools .btn + .btn {
  margin-left: 0;
}
.tabs {
  display: inline-flex;
  gap: 0.5rem;
  padding: 0.25rem;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-surface-elevated);
}
.tab {
  border: none;
  background: transparent;
  cursor: pointer;
  font-weight: 700;
  padding: 0.5rem 0.95rem;
  border-radius: 999px;
  color: var(--color-muted);
}
.tab.active {
  background: rgba(61, 122, 102, 0.14);
  color: var(--color-canopy);
}
.hist__summary {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 0.65rem;
  margin-bottom: 1rem;
}
.hist__stat {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  padding: 0.65rem 0.8rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface-elevated);
}
.hist__stat--method {
  background: var(--color-sage-soft);
}
.hist__stat-label {
  font-size: 0.75rem;
  color: var(--color-muted);
}
.hist__items {
  display: inline-block;
  max-width: 28ch;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: top;
}
.hist__empty {
  padding: 1.5rem 0;
  text-align: center;
}
.num {
  text-align: right;
  white-space: nowrap;
}
.hist-pill {
  display: inline-block;
  padding: 0.15rem 0.55rem;
  border-radius: 999px;
  font-size: 0.75rem;
  font-weight: 700;
  white-space: nowrap;
  text-transform: capitalize;
}
.hist-pill--ok {
  background: var(--color-success-bg);
  color: var(--color-success-text);
}
.hist-pill--bad {
  background: var(--color-danger-bg);
  color: var(--color-danger-text);
}
.hist-pill--wait {
  background: var(--color-wheat-soft);
  color: var(--color-earth);
}
.hist__paging {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 0.75rem 1.25rem;
  margin-top: 1rem;
}
.hist__paging :deep(.pagination-bar) {
  margin-top: 0;
}
.hist__size {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
}
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
}
</style>
