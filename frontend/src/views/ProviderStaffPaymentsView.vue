<script setup>
import { ref, onMounted, computed, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { providerTeamApi } from '../services/marketplaceApi';
import { useAuthStore } from '../stores/auth';
import FormField from '../components/ui/FormField.vue';
import DataTableShell from '../components/ui/DataTableShell.vue';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();

const loading = ref(true);
const error = ref('');
const message = ref('');
const calculations = ref([]);
const selectedStaffId = ref('');
const income = ref(null);
const incomeLoading = ref(false);
const acting = ref(false);

const periodMode = ref('month'); // month | all
const startDate = ref('');
const endDate = ref('');

function monthBounds() {
  const now = new Date();
  const y = now.getFullYear();
  const m = String(now.getMonth() + 1).padStart(2, '0');
  const last = new Date(y, now.getMonth() + 1, 0).getDate();
  startDate.value = `${y}-${m}-01`;
  endDate.value = `${y}-${m}-${String(last).padStart(2, '0')}`;
}

function periodParams() {
  if (periodMode.value === 'all') return {};
  return { startDate: startDate.value || undefined, endDate: endDate.value || undefined };
}

onMounted(async () => {
  auth.restoreFromStorage();
  if (!auth.isProviderUser) {
    router.replace({ path: '/login', query: { redirect: '/provider/staff-payments' } });
    return;
  }
  monthBounds();
  if (route.query.staff) {
    selectedStaffId.value = String(route.query.staff);
  }
  await loadCalculations();
  if (selectedStaffId.value) {
    await loadIncome();
  }
});

watch(periodMode, async () => {
  if (periodMode.value === 'month') monthBounds();
  await loadCalculations();
  if (selectedStaffId.value) await loadIncome();
});

async function loadCalculations() {
  loading.value = true;
  error.value = '';
  try {
    const { data } = await providerTeamApi.paymentCalculations(periodParams());
    calculations.value = data?.calculations || [];
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  } finally {
    loading.value = false;
  }
}

async function loadIncome() {
  if (!selectedStaffId.value) {
    income.value = null;
    return;
  }
  incomeLoading.value = true;
  error.value = '';
  try {
    const { data } = await providerTeamApi.staffIncome(selectedStaffId.value, periodParams());
    income.value = data;
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
    income.value = null;
  } finally {
    incomeLoading.value = false;
  }
}

async function selectStaff(id) {
  selectedStaffId.value = String(id);
  router.replace({ query: { ...route.query, staff: id } });
  await loadIncome();
}

async function markPaid(orderId) {
  acting.value = true;
  error.value = '';
  try {
    await providerTeamApi.markPayrollPaid(selectedStaffId.value, orderId);
    message.value = 'Marked as paid.';
    await Promise.all([loadCalculations(), loadIncome()]);
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  } finally {
    acting.value = false;
  }
}

async function unmarkPaid(orderId) {
  acting.value = true;
  error.value = '';
  try {
    await providerTeamApi.unmarkPayrollPaid(selectedStaffId.value, orderId);
    message.value = 'Payment mark cleared.';
    await Promise.all([loadCalculations(), loadIncome()]);
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  } finally {
    acting.value = false;
  }
}

async function payAll() {
  if (!selectedStaffId.value) return;
  acting.value = true;
  error.value = '';
  try {
    const { data } = await providerTeamApi.payAllUnpaid(selectedStaffId.value, periodParams());
    message.value = `Marked ${data?.markedCount ?? 0} order(s) as paid.`;
    await Promise.all([loadCalculations(), loadIncome()]);
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  } finally {
    acting.value = false;
  }
}

function money(v) {
  if (v == null || v === '') return '—';
  return new Intl.NumberFormat('en-ZA', { style: 'currency', currency: 'ZAR' }).format(Number(v));
}

function payLabel(v) {
  const map = {
    PER_SERVICE: 'Per service',
    HOURLY: 'Per hour',
    DAILY: 'Per day',
    WEEKLY: 'Per week',
    MONTHLY: 'Monthly',
  };
  return map[v] || v || '—';
}

function formatWhen(iso) {
  if (!iso) return '—';
  return String(iso).replace('T', ' ').slice(0, 16);
}

const totals = computed(() => {
  const rows = calculations.value || [];
  return {
    expected: rows.reduce((s, r) => s + Number(r.expectedPayment || 0), 0),
    paid: rows.reduce((s, r) => s + Number(r.paidPayment || 0), 0),
    unpaid: rows.reduce((s, r) => s + Number(r.unpaidPayment || 0), 0),
  };
});
</script>

<template>
  <div class="page-document page-document--wide pay-page">
    <header class="page-hero">
      <p class="page-hero__eyebrow">Provider</p>
      <h1 class="page-hero__title">Staff payments</h1>
      <p class="page-hero__lead">
        Pay staff from completed orders they collected. Amounts follow each employee’s pay method
        (per service, hourly, daily, weekly, or monthly), plus any bonus %.
        <router-link to="/provider/team">Team management</router-link>
      </p>
    </header>

    <p v-if="error" class="alert alert--error">{{ error }}</p>
    <p v-if="message" class="alert alert--ok">{{ message }}</p>

    <div class="period-bar surface-panel">
      <div class="period-bar__modes">
        <button
          type="button"
          class="seg"
          :class="{ active: periodMode === 'month' }"
          @click="periodMode = 'month'"
        >
          This month
        </button>
        <button
          type="button"
          class="seg"
          :class="{ active: periodMode === 'all' }"
          @click="periodMode = 'all'"
        >
          All time
        </button>
      </div>
      <div v-if="periodMode === 'month'" class="period-bar__dates">
        <FormField label="From">
          <input v-model="startDate" type="date" @change="loadCalculations().then(() => selectedStaffId && loadIncome())" />
        </FormField>
        <FormField label="To">
          <input v-model="endDate" type="date" @change="loadCalculations().then(() => selectedStaffId && loadIncome())" />
        </FormField>
      </div>
      <button type="button" class="btn btn-ghost" @click="loadCalculations().then(() => selectedStaffId && loadIncome())">
        Refresh
      </button>
    </div>

    <div class="summary-row">
      <article class="summary-card">
        <span class="muted small">Expected</span>
        <strong>{{ money(totals.expected) }}</strong>
      </article>
      <article class="summary-card summary-card--ok">
        <span class="muted small">Paid</span>
        <strong>{{ money(totals.paid) }}</strong>
      </article>
      <article class="summary-card summary-card--warn">
        <span class="muted small">Unpaid</span>
        <strong>{{ money(totals.unpaid) }}</strong>
      </article>
    </div>

    <section class="surface-panel">
      <h2>Payment calculations</h2>
      <p class="muted small lead">
        Based on orders marked <strong>COLLECTED</strong> by each staff member. Select a row to mark individual
        orders paid or pay all unpaid.
      </p>
      <p v-if="loading" class="muted">Loading…</p>
      <DataTableShell v-else caption="Payment calculations">
        <thead>
          <tr>
            <th>Employee</th>
            <th>Pay method</th>
            <th>Rate</th>
            <th>Orders</th>
            <th>Units</th>
            <th>Expected</th>
            <th>Paid</th>
            <th>Unpaid</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="row in calculations"
            :key="row.staffUserId"
            :class="{ selected: String(row.staffUserId) === String(selectedStaffId) }"
            @click="selectStaff(row.staffUserId)"
          >
            <td>
              <strong>{{ row.displayName || row.email }}</strong>
              <div class="muted tiny">{{ row.email }}</div>
            </td>
            <td>{{ payLabel(row.payMethod) }}</td>
            <td>{{ money(row.payRate) }}</td>
            <td>{{ row.jobCount }}</td>
            <td>{{ row.units }}</td>
            <td>{{ money(row.expectedPayment) }}</td>
            <td>{{ money(row.paidPayment) }}</td>
            <td><strong>{{ money(row.unpaidPayment) }}</strong></td>
            <td>
              <button type="button" class="btn btn-ghost" @click.stop="selectStaff(row.staffUserId)">
                Details
              </button>
            </td>
          </tr>
          <tr v-if="!calculations.length">
            <td colspan="9" class="muted">
              No staff payroll yet. Enrol staff on
              <router-link to="/provider/team">Team management</router-link>, then have them collect orders.
            </td>
          </tr>
        </tbody>
      </DataTableShell>
    </section>

    <section v-if="selectedStaffId" class="surface-panel income-panel">
      <div class="income-head">
        <div>
          <h2>Order payouts</h2>
          <p class="muted small" v-if="income">
            {{ income.email }} · {{ payLabel(income.payMethod) }} · {{ money(income.payRate) }}
          </p>
        </div>
        <button
          type="button"
          class="btn btn-primary"
          :disabled="acting || !income?.lines?.some((l) => !l.payrollPaid)"
          @click="payAll"
        >
          {{ acting ? 'Working…' : 'Pay all unpaid' }}
        </button>
      </div>

      <p v-if="incomeLoading" class="muted">Loading lines…</p>
      <DataTableShell v-else-if="income" caption="Staff income lines">
        <thead>
          <tr>
            <th>Order</th>
            <th>Guest</th>
            <th>Completed</th>
            <th>Order total</th>
            <th>Units</th>
            <th>Line amount</th>
            <th>Status</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="line in income.lines" :key="line.orderId">
            <td>#{{ line.orderId }}</td>
            <td>{{ line.guestName }}</td>
            <td>{{ formatWhen(line.completedAt) }}</td>
            <td>{{ money(line.orderTotal) }}</td>
            <td>{{ line.units }}</td>
            <td>{{ money(line.lineAmount) }}</td>
            <td>
              <span class="chip" :class="line.payrollPaid ? 'chip--ok' : 'chip--warn'">
                {{ line.payrollPaid ? 'Paid' : 'Unpaid' }}
              </span>
            </td>
            <td>
              <button
                v-if="!line.payrollPaid"
                type="button"
                class="btn btn-ghost"
                :disabled="acting"
                @click="markPaid(line.orderId)"
              >
                Mark paid
              </button>
              <button
                v-else
                type="button"
                class="btn btn-ghost"
                :disabled="acting"
                @click="unmarkPaid(line.orderId)"
              >
                Undo
              </button>
            </td>
          </tr>
          <tr v-if="!income.lines?.length">
            <td colspan="8" class="muted">
              No collected orders attributed to this staff member in the selected period.
            </td>
          </tr>
        </tbody>
      </DataTableShell>

      <div v-if="income" class="income-totals">
        <span>Expected {{ money(income.expectedTotal) }}</span>
        <span>Paid {{ money(income.paidTotal) }}</span>
        <span>Unpaid <strong>{{ money(income.unpaidTotal) }}</strong></span>
      </div>
    </section>
  </div>
</template>

<style scoped>
.pay-page {
  padding-bottom: 2.5rem;
}

.period-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 0.85rem;
  margin-bottom: 1rem;
}

.period-bar__modes {
  display: inline-flex;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-pill);
  overflow: hidden;
}

.seg {
  border: none;
  background: transparent;
  padding: 0.45rem 0.9rem;
  cursor: pointer;
  font-weight: 600;
  color: var(--color-text-secondary);
}

.seg.active {
  background: var(--color-sage-soft);
  color: var(--color-canopy);
}

.period-bar__dates {
  display: flex;
  gap: 0.75rem;
  flex-wrap: wrap;
}

.summary-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0.75rem;
  margin-bottom: 1rem;
}

@media (max-width: 720px) {
  .summary-row {
    grid-template-columns: 1fr;
  }
}

.summary-card {
  background: var(--color-surface-elevated);
  border: 1px solid var(--color-border);
  border-radius: 18px;
  padding: 0.9rem 1rem;
  border-left: 3px solid var(--color-sage);
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.summary-card strong {
  font-size: 1.25rem;
  color: var(--color-canopy);
}

.summary-card--ok {
  border-left-color: var(--color-success-text);
}

.summary-card--warn {
  border-left-color: var(--color-wheat);
}

.lead {
  margin-top: -0.35rem;
  margin-bottom: 0.85rem;
}

tbody tr {
  cursor: pointer;
}

tbody tr.selected {
  background: var(--color-sage-soft);
}

.income-head {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 0.75rem;
  align-items: flex-start;
  margin-bottom: 0.75rem;
}

.income-head h2 {
  margin: 0;
  border: none;
  padding: 0;
}

.income-totals {
  display: flex;
  flex-wrap: wrap;
  gap: 1.25rem;
  margin-top: 0.85rem;
  padding-top: 0.75rem;
  border-top: 1px solid var(--color-border);
  color: var(--color-text-secondary);
}

.chip {
  display: inline-flex;
  padding: 0.12rem 0.5rem;
  border-radius: var(--radius-pill);
  font-size: 0.75rem;
  font-weight: 700;
}

.chip--ok {
  background: var(--color-success-bg);
  color: var(--color-success-text);
}

.chip--warn {
  background: var(--color-wheat-soft);
  color: var(--color-earth);
}

.alert {
  padding: 0.75rem 1rem;
  border-radius: var(--radius-md);
  margin-bottom: 0.85rem;
}

.alert--error {
  background: var(--color-danger-bg);
  color: var(--color-danger-text);
}

.alert--ok {
  background: var(--color-success-bg);
  color: var(--color-success-text);
}

.tiny {
  font-size: 0.78rem;
}
</style>
