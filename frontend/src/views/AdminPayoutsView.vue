<script setup>
import { computed, onMounted, ref } from 'vue';
import FormField from '../components/ui/FormField.vue';
import DataTableShell from '../components/ui/DataTableShell.vue';
import { adminPayoutsApi } from '../services/marketplaceApi';
import {
  formatRand,
  payoutReferenceError,
  settlementLabel,
  settlementTone,
  sumSelected,
} from '../utils/payouts';

const STATUS_OPTIONS = [
  { value: 'HELD', label: 'Awaiting payout' },
  { value: 'PAID_OUT', label: 'Paid out' },
  { value: 'REFUND_DUE', label: 'Refund due to buyer' },
  { value: 'REFUNDED', label: 'Refunded to buyer' },
];

const summary = ref(null);
const rows = ref([]);
const loading = ref(false);
const busy = ref(false);
const error = ref('');
const message = ref('');
const filters = ref({ status: 'HELD', providerId: '', from: '', to: '' });
const selected = ref([]);
const reference = ref('');
const referenceTouched = ref(false);

const sellers = computed(() => summary.value?.sellers ?? []);
const sellerOptions = computed(() => {
  const map = new Map();
  for (const s of sellers.value) map.set(String(s.providerId), s.providerName);
  for (const r of rows.value) if (!map.has(String(r.providerId))) map.set(String(r.providerId), r.providerName);
  return [...map.entries()].map(([value, label]) => ({ value, label })).sort((a, b) => a.label.localeCompare(b.label));
});

const canSelect = computed(() => filters.value.status === 'HELD' || filters.value.status === 'REFUND_DUE');
const selectableRows = computed(() => (canSelect.value ? rows.value : []));
const selectedRows = computed(() => rows.value.filter((r) => selected.value.includes(r.paymentId)));
const selectedSellerIds = computed(() => [...new Set(selectedRows.value.map((r) => r.providerId))]);
const selectedTotal = computed(() =>
  sumSelected(rows.value, selected.value, filters.value.status === 'REFUND_DUE' ? 'amount' : 'sellerPayoutAmount'),
);
const referenceError = computed(() => (referenceTouched.value ? payoutReferenceError(reference.value) : ''));
const allSelected = computed(
  () => selectableRows.value.length > 0 && selectableRows.value.every((r) => selected.value.includes(r.paymentId)),
);

function formatWhen(value) {
  if (!value) return 'Not set';
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? 'Not set' : d.toLocaleString();
}

function bankLine(s) {
  const parts = [s.bankName, s.bankAccountName, s.bankAccountNumber, s.bankBranchCode && `branch ${s.bankBranchCode}`];
  const text = parts.filter(Boolean).join(', ');
  return text || 'No bank details on file';
}

async function loadSummary() {
  const { data } = await adminPayoutsApi.summary();
  summary.value = data;
}

async function loadRows() {
  const f = filters.value;
  const { data } = await adminPayoutsApi.payments({
    status: f.status,
    providerId: f.providerId || undefined,
    from: f.from || undefined,
    to: f.to || undefined,
  });
  rows.value = Array.isArray(data) ? data : [];
  selected.value = [];
}

async function load() {
  loading.value = true;
  error.value = '';
  try {
    await Promise.all([loadSummary(), loadRows()]);
  } catch (e) {
    error.value = e.response?.data?.message || e.message || 'Failed to load payouts.';
  } finally {
    loading.value = false;
  }
}

async function applyFilters() {
  loading.value = true;
  error.value = '';
  try {
    await loadRows();
  } catch (e) {
    error.value = e.response?.data?.message || e.message || 'Failed to load payments.';
  } finally {
    loading.value = false;
  }
}

function resetFilters() {
  filters.value = { status: 'HELD', providerId: '', from: '', to: '' };
  applyFilters();
}

function showSeller(providerId) {
  filters.value = { ...filters.value, status: 'HELD', providerId: String(providerId) };
  applyFilters();
}

function toggleAll(checked) {
  selected.value = checked ? selectableRows.value.map((r) => r.paymentId) : [];
}

async function markPaid({ providerId, paymentIds, total, sellerName }) {
  referenceTouched.value = true;
  const refError = payoutReferenceError(reference.value);
  if (refError) {
    error.value = refError;
    return;
  }
  const what = paymentIds?.length ? `${paymentIds.length} payment(s)` : 'every payment awaiting payout';
  if (!window.confirm(`Record a payout of ${formatRand(total)} to ${sellerName} for ${what}?`)) return;
  busy.value = true;
  error.value = '';
  message.value = '';
  try {
    const { data } = await adminPayoutsApi.markPaid({ providerId, paymentIds, reference: reference.value.trim() });
    message.value = `Recorded payout of ${formatRand(data?.total)} to ${sellerName} (${data?.count} payment(s)).`;
    reference.value = '';
    referenceTouched.value = false;
    await load();
  } catch (e) {
    error.value = e.response?.data?.message || e.message || 'Could not record the payout.';
  } finally {
    busy.value = false;
  }
}

function markSellerPaid(s) {
  return markPaid({ providerId: s.providerId, paymentIds: [], total: s.amountOwed, sellerName: s.providerName });
}

function markSelectedPaid() {
  if (selectedSellerIds.value.length !== 1) {
    error.value = 'Select payments from one seller at a time.';
    return;
  }
  const first = selectedRows.value[0];
  return markPaid({
    providerId: first.providerId,
    paymentIds: [...selected.value],
    total: selectedTotal.value,
    sellerName: first.providerName,
  });
}

async function markSelectedRefunded() {
  referenceTouched.value = true;
  const refError = payoutReferenceError(reference.value);
  if (refError) {
    error.value = refError;
    return;
  }
  if (!window.confirm(`Record that ${selected.value.length} buyer payment(s), ${formatRand(selectedTotal.value)} in total, were refunded?`)) {
    return;
  }
  busy.value = true;
  error.value = '';
  message.value = '';
  try {
    const { data } = await adminPayoutsApi.markRefunded({ paymentIds: [...selected.value], reference: reference.value.trim() });
    message.value = `Recorded ${data?.count} refund(s), ${formatRand(data?.total)} in total.`;
    reference.value = '';
    referenceTouched.value = false;
    await load();
  } catch (e) {
    error.value = e.response?.data?.message || e.message || 'Could not record the refund.';
  } finally {
    busy.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div class="page-document page-document--wide">
    <header class="page-hero">
      <p class="page-hero__eyebrow">Admin</p>
      <h1 class="page-hero__title">Seller payouts</h1>
      <p class="page-hero__lead">
        Orders and rentals paid online (Card / Instant EFT) are collected by the platform. Pay each seller their share
        by bank transfer, then record it here with the transfer reference. The platform fee is set in
        <router-link to="/admin/settings#online-payments">Settings</router-link>.
      </p>
    </header>

    <section v-if="summary" class="stats">
      <div class="stat-card"><p class="stat-label">Owed to sellers</p><p class="stat-value">{{ formatRand(summary.totalOwed) }}</p></div>
      <div class="stat-card"><p class="stat-label">Sellers waiting</p><p class="stat-value">{{ sellers.length }}</p></div>
      <div class="stat-card"><p class="stat-label">Refunds due</p><p class="stat-value">{{ summary.refundDueCount }}</p></div>
      <div class="stat-card"><p class="stat-label">Refunds due (total)</p><p class="stat-value">{{ formatRand(summary.refundDueTotal) }}</p></div>
      <div class="stat-card"><p class="stat-label">Platform fee now</p><p class="stat-value">{{ Number(summary.onlineOrderFeePercent || 0).toFixed(2) }}%</p></div>
    </section>

    <p v-if="error" class="err-toast">{{ error }}</p>
    <p v-if="message" class="ok-msg">{{ message }}</p>

    <section class="surface-panel">
      <h2>Payout reference</h2>
      <p class="muted small">
        Enter the bank transfer reference first, then use a Mark button below. It is saved on every payment you mark.
      </p>
      <div class="ref-row">
        <FormField label="Bank transfer reference" :error="referenceError">
          <input v-model="reference" type="text" maxlength="120" placeholder="For example FNB 2026-11-08 Green Acres"
            @blur="referenceTouched = true" />
        </FormField>
      </div>
    </section>

    <section class="surface-panel">
      <h2>Owed per seller</h2>
      <p v-if="loading && !summary" class="muted">Loading…</p>
      <p v-else-if="!sellers.length" class="muted">Nothing is owed to sellers right now.</p>
      <DataTableShell v-else caption="Amount owed per seller" :max-chars="40">
        <thead>
          <tr>
            <th>Seller</th>
            <th>Bank details</th>
            <th class="num">Payments</th>
            <th class="num">Paid by buyers</th>
            <th class="num">Platform fee</th>
            <th class="num">Owed</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="s in sellers" :key="s.providerId">
            <td>{{ s.providerName }}</td>
            <td>{{ bankLine(s) }}</td>
            <td class="num">{{ s.paymentCount }}</td>
            <td class="num">{{ formatRand(s.grossAmount) }}</td>
            <td class="num">{{ formatRand(s.platformFee) }}</td>
            <td class="num"><strong>{{ formatRand(s.amountOwed) }}</strong></td>
            <td class="cell-actions">
              <div class="row-actions">
                <button type="button" class="btn btn-ghost" :disabled="busy" @click="showSeller(s.providerId)">Payments</button>
                <button type="button" class="btn btn-primary" :disabled="busy" @click="markSellerPaid(s)">Mark all paid out</button>
              </div>
            </td>
          </tr>
        </tbody>
      </DataTableShell>
    </section>

    <section class="surface-panel">
      <h2>Payments</h2>
      <form class="filters" @submit.prevent="applyFilters">
        <FormField label="Status">
          <AppSelect v-model="filters.status">
            <option v-for="o in STATUS_OPTIONS" :key="o.value" :value="o.value">{{ o.label }}</option>
          </AppSelect>
        </FormField>
        <FormField label="Seller">
          <AppSelect v-model="filters.providerId">
            <option value="">All sellers</option>
            <option v-for="o in sellerOptions" :key="o.value" :value="o.value">{{ o.label }}</option>
          </AppSelect>
        </FormField>
        <FormField label="Paid from">
          <input v-model="filters.from" type="date" />
        </FormField>
        <FormField label="Paid to">
          <input v-model="filters.to" type="date" />
        </FormField>
        <div class="filter-actions">
          <button type="submit" class="btn btn-primary" :disabled="loading">Apply</button>
          <button type="button" class="btn btn-ghost" :disabled="loading" @click="resetFilters">Reset</button>
        </div>
      </form>

      <div v-if="canSelect && selected.length" class="selection-bar" role="status">
        <span>{{ selected.length }} selected, {{ formatRand(selectedTotal) }}</span>
        <button v-if="filters.status === 'HELD'" type="button" class="btn btn-primary" :disabled="busy"
          @click="markSelectedPaid">Mark selected paid out</button>
        <button v-if="filters.status === 'REFUND_DUE'" type="button" class="btn btn-primary" :disabled="busy"
          @click="markSelectedRefunded">Mark selected refunded</button>
      </div>
      <p v-if="filters.status === 'REFUND_DUE'" class="muted small">
        These buyers paid online for an order or booking that had already been cancelled (or the seller cancelled a paid
        booking). Refund the full amount to the buyer from the payment gateway dashboard, then mark it refunded here.
      </p>

      <p v-if="loading" class="muted">Loading…</p>
      <p v-else-if="!rows.length" class="muted">No payments match these filters.</p>
      <DataTableShell v-else caption="Online payments" :max-chars="32">
        <thead>
          <tr>
            <th v-if="canSelect">
              <input type="checkbox" :checked="allSelected" aria-label="Select all" @change="toggleAll($event.target.checked)" />
            </th>
            <th>Paid</th>
            <th>Seller</th>
            <th>Item</th>
            <th>Buyer</th>
            <th class="num">Amount</th>
            <th class="num">Fee</th>
            <th class="num">Seller gets</th>
            <th>Status</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in rows" :key="r.paymentId">
            <td v-if="canSelect" class="cell-actions">
              <input v-model="selected" type="checkbox" :value="r.paymentId" :aria-label="`Select payment ${r.paymentId}`" />
            </td>
            <td>{{ formatWhen(r.paidAt) }}</td>
            <td>{{ r.providerName }}</td>
            <td>
              <div>{{ r.kind === 'RENTAL' ? 'Rental' : 'Order' }} #{{ r.referenceId }}</div>
              <div class="muted small">{{ r.verificationCode }} ({{ r.gateway === 'PAYFAST' ? 'PayFast' : 'Peach' }})</div>
            </td>
            <td>{{ r.buyerName || 'Not set' }}</td>
            <td class="num">{{ formatRand(r.amount) }}</td>
            <td class="num">{{ formatRand(r.platformFee) }}</td>
            <td class="num">{{ formatRand(r.sellerPayoutAmount) }}</td>
            <td>
              <span class="badge" :class="`badge--${settlementTone(r.settlementStatus)}`">{{ settlementLabel(r.settlementStatus) }}</span>
              <div v-if="r.paidOutAt" class="muted small">
                {{ formatWhen(r.paidOutAt) }} by {{ r.paidOutBy || 'admin' }}, ref {{ r.payoutReference }}
              </div>
            </td>
          </tr>
        </tbody>
      </DataTableShell>
    </section>
  </div>
</template>

<style scoped>
.stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 0.75rem;
  margin-bottom: 1.25rem;
}
.stat-label {
  margin: 0;
  font-size: 0.8rem;
  color: var(--color-muted);
}
.stat-value {
  margin: 0.25rem 0 0;
  font-size: 1.3rem;
  font-weight: 700;
}
.filters {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 0.85rem;
  align-items: end;
  margin-bottom: 1rem;
}
.filter-actions,
.row-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}
.ref-row {
  max-width: 420px;
}
.selection-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem;
  padding: 0.6rem 0.8rem;
  margin-bottom: 0.75rem;
  border-radius: 10px;
  background: var(--color-sage-soft, #eef5f1);
}
.num {
  text-align: right;
  white-space: nowrap;
}
.badge {
  display: inline-block;
  padding: 0.1rem 0.5rem;
  border-radius: 999px;
  font-size: 0.8rem;
  font-weight: 600;
}
.badge--warn {
  background: #fef3c7;
  color: #92400e;
}
.badge--ok {
  background: #dcfce7;
  color: #15803d;
}
.badge--danger {
  background: #fee2e2;
  color: #b91c1c;
}
.badge--muted {
  background: #f1f5f9;
  color: #475569;
}
</style>
