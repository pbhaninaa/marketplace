<script setup>
import { onMounted, ref } from 'vue';
import FormField from '../components/ui/FormField.vue';
import { adminReferralsApi } from '../services/marketplaceApi';
import {
  buildAdminReferralParams,
  formatPercent,
  formatZar,
  referralStatusLabel,
} from '../utils/referralInvite';

const PAGE_SIZE = 20;

const filters = ref({ referrer: '', referee: '', status: '', from: '', to: '' });
const rows = ref([]);
const page = ref(0);
const totalPages = ref(0);
const totalElements = ref(0);
const stats = ref(null);
const loading = ref(false);
const error = ref('');
const message = ref('');
const busyId = ref(null);

function formatDate(value) {
  if (!value) return '—';
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? '—' : d.toLocaleString();
}

async function load(toPage = 0) {
  loading.value = true;
  error.value = '';
  try {
    const [list, st] = await Promise.all([
      adminReferralsApi.list(buildAdminReferralParams(filters.value, toPage, PAGE_SIZE)),
      adminReferralsApi.stats(),
    ]);
    rows.value = list.data?.items ?? [];
    page.value = list.data?.page ?? 0;
    totalPages.value = list.data?.totalPages ?? 0;
    totalElements.value = list.data?.totalElements ?? 0;
    stats.value = st.data;
  } catch (e) {
    error.value = e.response?.data?.message || e.message || 'Failed to load referrals.';
  } finally {
    loading.value = false;
  }
}

function resetFilters() {
  filters.value = { referrer: '', referee: '', status: '', from: '', to: '' };
  load(0);
}

async function markPaid(row) {
  const note = window.prompt(
    `Record payout of ${formatZar(row.commissionAmount)} to ${row.referrerEmail}?\nOptional note (e.g. EFT reference):`,
    '',
  );
  if (note === null) return;
  busyId.value = row.id;
  message.value = '';
  error.value = '';
  try {
    await adminReferralsApi.markPaid(row.id, note);
    message.value = 'Commission marked as paid.';
    await load(page.value);
  } catch (e) {
    error.value = e.response?.data?.message || e.message || 'Could not mark as paid.';
  } finally {
    busyId.value = null;
  }
}

onMounted(() => load(0));
</script>

<template>
  <div class="page-document page-document--wide">
    <header class="page-hero">
      <p class="page-hero__eyebrow">Admin</p>
      <h1 class="page-hero__title">Referrals</h1>
      <p class="page-hero__lead">
        Businesses referred by provider users, commission earned on first subscription payments, and manual payouts.
        Commission rate and claim window are set in <router-link to="/admin/settings#referrals">Settings</router-link>.
      </p>
    </header>

    <section v-if="stats" class="stats">
      <div class="stat-card"><p class="stat-label">Referrals</p><p class="stat-value">{{ stats.totalReferrals }}</p></div>
      <div class="stat-card"><p class="stat-label">Waiting for first payment</p><p class="stat-value">{{ stats.pendingCount }}</p></div>
      <div class="stat-card"><p class="stat-label">Earned (unpaid)</p><p class="stat-value">{{ stats.earnedCount }}</p></div>
      <div class="stat-card"><p class="stat-label">Awaiting payout</p><p class="stat-value">{{ formatZar(stats.awaitingPayoutAmount) }}</p></div>
      <div class="stat-card"><p class="stat-label">Paid out</p><p class="stat-value">{{ formatZar(stats.paidOutAmount) }}</p></div>
    </section>

    <section class="surface-panel">
      <h2>Filters</h2>
      <form class="filters" @submit.prevent="load(0)">
        <FormField label="Referrer (email, name, business or code)">
          <input v-model="filters.referrer" type="text" />
        </FormField>
        <FormField label="Referred business (name or email)">
          <input v-model="filters.referee" type="text" />
        </FormField>
        <FormField label="Status">
          <select v-model="filters.status">
            <option value="">Any</option>
            <option value="PENDING">Waiting for first payment</option>
            <option value="EARNED">Earned (unpaid)</option>
            <option value="PAID">Paid out</option>
          </select>
        </FormField>
        <FormField label="Referred from">
          <input v-model="filters.from" type="date" />
        </FormField>
        <FormField label="Referred to">
          <input v-model="filters.to" type="date" />
        </FormField>
        <div class="filter-actions">
          <button type="submit" class="btn btn-primary" :disabled="loading">Apply</button>
          <button type="button" class="btn btn-ghost" :disabled="loading" @click="resetFilters">Reset</button>
        </div>
      </form>
    </section>

    <p v-if="error" class="err-toast">{{ error }}</p>
    <p v-if="message" class="ok-msg">{{ message }}</p>

    <section class="surface-panel">
      <h2>Referrals ({{ totalElements }})</h2>
      <p v-if="loading" class="muted">Loading…</p>
      <p v-else-if="!rows.length" class="muted">No referrals match these filters.</p>
      <div v-else class="table-wrap">
        <table class="ref-table">
          <thead>
            <tr>
              <th>Referred on</th>
              <th>Referrer</th>
              <th>Referred business</th>
              <th>Via</th>
              <th>Status</th>
              <th>Payment</th>
              <th>Commission</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in rows" :key="r.id">
              <td>{{ formatDate(r.createdAt) }}</td>
              <td>
                <div>{{ r.referrerBusiness || r.referrerName || '—' }}</div>
                <div class="muted small">{{ r.referrerEmail }} · {{ r.referralCode }}</div>
              </td>
              <td>
                <div>{{ r.refereeBusiness }}</div>
                <div class="muted small">{{ r.refereeEmail || '—' }}</div>
              </td>
              <td>{{ r.source === 'CLAIM' ? 'Added later' : 'Sign-up' }}</td>
              <td>
                <div>{{ referralStatusLabel(r.status) }}</div>
                <div v-if="r.earnedAt" class="muted small">Earned {{ formatDate(r.earnedAt) }}</div>
                <div v-if="r.paidOutAt" class="muted small">
                  Paid {{ formatDate(r.paidOutAt) }} by {{ r.paidOutBy }}<span v-if="r.payoutNote"> — {{ r.payoutNote }}</span>
                </div>
              </td>
              <td>
                <div>{{ r.paymentAmount != null ? formatZar(r.paymentAmount) : '—' }}</div>
                <div v-if="r.qualifyingPaymentRef" class="muted small">{{ r.qualifyingPaymentRef }}</div>
              </td>
              <td>
                {{ r.commissionAmount != null ? formatZar(r.commissionAmount) : '—' }}
                <span v-if="r.commissionPercent != null" class="muted small">({{ formatPercent(r.commissionPercent) }})</span>
              </td>
              <td>
                <button
                  v-if="r.status === 'EARNED'"
                  type="button"
                  class="btn btn-ghost"
                  :disabled="busyId === r.id"
                  @click="markPaid(r)"
                >
                  Mark paid
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="totalPages > 1" class="pager">
        <button type="button" class="btn btn-ghost" :disabled="page <= 0 || loading" @click="load(page - 1)">Previous</button>
        <span class="muted">Page {{ page + 1 }} of {{ totalPages }}</span>
        <button type="button" class="btn btn-ghost" :disabled="page + 1 >= totalPages || loading" @click="load(page + 1)">
          Next
        </button>
      </div>
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
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 0.85rem;
  align-items: end;
}
.filter-actions {
  display: flex;
  gap: 0.5rem;
}
.table-wrap {
  overflow-x: auto;
}
.ref-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.9rem;
}
.ref-table th,
.ref-table td {
  text-align: left;
  vertical-align: top;
  padding: 0.5rem 0.6rem;
  border-bottom: 1px solid var(--color-border);
}
.ref-table th {
  font-size: 0.75rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--color-text-secondary);
}
.small {
  font-size: 0.8rem;
}
.pager {
  display: flex;
  gap: 0.75rem;
  align-items: center;
  justify-content: flex-end;
  margin-top: 0.85rem;
}
</style>
