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
const pendingOnly = ref(false);
const includeBonusOnPay = ref(true);
const staffMode = ref(false);

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

const isEmployer = computed(() => auth.isProviderOwner || auth.canManageStaff);

onMounted(async () => {
  auth.restoreFromStorage();
  if (!auth.isProviderUser) {
    router.replace({ path: '/login', query: { redirect: '/provider/staff-payments' } });
    return;
  }
  staffMode.value = !isEmployer.value || route.query.mode === 'mine';
  monthBounds();
  if (route.query.staff) {
    selectedStaffId.value = String(route.query.staff);
  }
  await reload();
});

watch(periodMode, async () => {
  if (periodMode.value === 'month') monthBounds();
  await reload();
});

async function reload() {
  if (staffMode.value) {
    await loadMyIncome();
  } else {
    await loadCalculations();
    if (selectedStaffId.value) await loadIncome();
  }
}

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

async function loadMyIncome() {
  loading.value = true;
  incomeLoading.value = true;
  error.value = '';
  try {
    const { data } = await providerTeamApi.myExpectedIncome(periodParams());
    income.value = data;
    selectedStaffId.value = String(data?.staffUserId || auth.email);
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
    income.value = null;
  } finally {
    loading.value = false;
    incomeLoading.value = false;
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

function displayAmount(base) {
  const n = Number(base || 0);
  if (!includeBonusOnPay.value || !income.value?.bonusPercentage) return n;
  const pct = Number(income.value.bonusPercentage);
  if (!pct || n <= 0) return n;
  return Math.round(n * (1 + pct / 100) * 100) / 100;
}

async function markPaid(orderId) {
  if (staffMode.value) return;
  acting.value = true;
  error.value = '';
  try {
    await providerTeamApi.markPayrollPaid(selectedStaffId.value, {
      orderId,
      includeBonus: includeBonusOnPay.value,
    });
    message.value = 'Marked as paid.';
    await Promise.all([loadCalculations(), loadIncome()]);
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  } finally {
    acting.value = false;
  }
}

async function payAll() {
  if (!selectedStaffId.value || staffMode.value) return;
  acting.value = true;
  error.value = '';
  try {
    const { data } = await providerTeamApi.payAllUnpaid(selectedStaffId.value, {
      ...periodParams(),
      includeBonus: includeBonusOnPay.value,
    });
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
    PER_HOUR: 'Per hour',
    HOURLY: 'Per hour',
    PER_DAY: 'Per day',
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

const visibleCalculations = computed(() => {
  const rows = calculations.value || [];
  if (!pendingOnly.value) return rows;
  return rows.filter((r) => Number(r.unpaidPayment || 0) > 0);
});

const visibleLines = computed(() => {
  const lines = income.value?.lines || [];
  if (!pendingOnly.value) return lines;
  return lines.filter((l) => !l.payrollPaid);
});

const totals = computed(() => {
  const rows = calculations.value || [];
  return {
    expected: rows.reduce((s, r) => s + Number(r.expectedPayment || 0), 0),
    paid: rows.reduce((s, r) => s + Number(r.paidPayment || 0), 0),
    unpaid: rows.reduce((s, r) => s + Number(r.unpaidPayment || 0), 0),
    members: rows.length,
    pendingJobs: rows.reduce((s, r) => s + Number(r.jobCount || 0), 0),
  };
});

function switchMode(mine) {
  staffMode.value = mine;
  router.replace({ query: { ...route.query, mode: mine ? 'mine' : undefined } });
  reload();
}
</script>

<template>
  <div class="page-document page-document--wide pay-page">
    <header class="page-hero">
      <p class="page-hero__eyebrow">Provider</p>
      <h1 class="page-hero__title">{{ staffMode ? 'My expected income' : 'Staff payments' }}</h1>
      <p class="page-hero__lead">
        <template v-if="staffMode">
          Expected pay from collected orders attributed to you. Settlement is between you and your employer.
        </template>
        <template v-else>
          Pay staff from completed orders they collected. Amounts follow each employee’s pay method
          (per service, per hour, per day, weekly, or monthly). Bonus is applied when you mark paid.
          <router-link to="/provider/team">Team management</router-link>
        </template>
      </p>
      <div v-if="isEmployer" class="mode-switch">
        <button type="button" class="seg" :class="{ active: !staffMode }" @click="switchMode(false)">Team payouts</button>
        <button
          v-if="!auth.isProviderOwner"
          type="button"
          class="seg"
          :class="{ active: staffMode }"
          @click="switchMode(true)"
        >
          My income
        </button>
      </div>
    </header>

    <p v-if="error" class="alert alert--error">{{ error }}</p>
    <p v-if="message" class="alert alert--ok">{{ message }}</p>

    <div class="period-bar surface-panel">
      <div class="period-bar__modes">
        <button type="button" class="seg" :class="{ active: periodMode === 'month' }" @click="periodMode = 'month'">
          This month
        </button>
        <button type="button" class="seg" :class="{ active: periodMode === 'all' }" @click="periodMode = 'all'">
          All time
        </button>
      </div>
      <div v-if="periodMode === 'month'" class="period-bar__dates">
        <FormField label="From">
          <input v-model="startDate" type="date" @change="reload" />
        </FormField>
        <FormField label="To">
          <input v-model="endDate" type="date" @change="reload" />
        </FormField>
      </div>
      <label class="check">
        <input v-model="pendingOnly" type="checkbox" />
        Pending only
      </label>
      <label v-if="!staffMode" class="check">
        <input v-model="includeBonusOnPay" type="checkbox" />
        Include bonus when paying
      </label>
      <button type="button" class="btn btn-ghost" @click="reload">Refresh</button>
    </div>

    <div v-if="!staffMode" class="summary-row">
      <article class="summary-card">
        <span class="muted small">Team members</span>
        <strong>{{ totals.members }}</strong>
      </article>
      <article class="summary-card summary-card--warn">
        <span class="muted small">Pending payout</span>
        <strong>{{ money(totals.unpaid) }}</strong>
      </article>
      <article class="summary-card">
        <span class="muted small">Orders pending</span>
        <strong>{{ totals.pendingJobs }}</strong>
      </article>
      <article class="summary-card summary-card--ok">
        <span class="muted small">Already paid</span>
        <strong>{{ money(totals.paid) }}</strong>
      </article>
    </div>

    <div v-else class="summary-row">
      <article class="summary-card summary-card--warn">
        <span class="muted small">Expected (unpaid)</span>
        <strong>{{ money(income?.unpaidTotal) }}</strong>
      </article>
      <article class="summary-card summary-card--ok">
        <span class="muted small">Paid by employer</span>
        <strong>{{ money(income?.paidTotal) }}</strong>
      </article>
      <article class="summary-card">
        <span class="muted small">Pay method</span>
        <strong>{{ payLabel(income?.payMethod) }}</strong>
      </article>
    </div>

    <section v-if="!staffMode" class="surface-panel">
      <h2>Payment calculations</h2>
      <p class="muted small lead">
        Based on orders marked <strong>COLLECTED</strong>. Pending amounts exclude bonus until you mark paid with bonus included.
      </p>
      <p v-if="loading" class="muted">Loading…</p>
      <DataTableShell v-else caption="Payment calculations">
        <thead>
          <tr>
            <th>Employee</th>
            <th>Pay method</th>
            <th>Rate</th>
            <th>Pending orders</th>
            <th>Units</th>
            <th>Pending</th>
            <th>Paid</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="row in visibleCalculations"
            :key="row.staffUserId"
            :class="{ selected: String(row.staffUserId) === String(selectedStaffId) }"
            @click="selectStaff(row.staffUserId)"
          >
            <td>
              <strong>{{ row.displayName || row.email }}</strong>
              <div class="muted tiny">{{ row.email }}</div>
              <div
                v-if="row.unpaidTargetMetCount != null"
                class="muted tiny"
              >
                Target met {{ row.unpaidTargetMetCount }} / {{ row.unpaidTargetPeriods }}
              </div>
            </td>
            <td>{{ payLabel(row.payMethod) }}</td>
            <td>{{ money(row.payRate) }}</td>
            <td>{{ row.jobCount }}</td>
            <td>{{ row.units }}</td>
            <td><strong>{{ money(row.unpaidPayment) }}</strong></td>
            <td>{{ money(row.paidPayment) }}</td>
            <td>
              <button type="button" class="btn btn-ghost" @click.stop="selectStaff(row.staffUserId)">
                Details
              </button>
            </td>
          </tr>
          <tr v-if="!visibleCalculations.length">
            <td colspan="8" class="muted">
              No staff payroll yet. Enrol staff on
              <router-link to="/provider/team">Team management</router-link>, then have them collect orders.
            </td>
          </tr>
        </tbody>
      </DataTableShell>
    </section>

    <section v-if="selectedStaffId || staffMode" class="surface-panel income-panel">
      <div class="income-head">
        <div>
          <h2>{{ staffMode ? 'Your order lines' : 'Order payouts' }}</h2>
          <p class="muted small" v-if="income">
            {{ income.email || income.displayName }} · {{ payLabel(income.payMethod) }} · {{ money(income.payRate) }}
            <span v-if="income.bonusPercentage"> · bonus {{ income.bonusPercentage }}%</span>
          </p>
          <p v-if="income?.note" class="muted tiny">{{ income.note }}</p>
        </div>
        <button
          v-if="!staffMode"
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
            <th v-if="!staffMode"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="line in visibleLines" :key="line.orderId">
            <td>#{{ line.orderId }}</td>
            <td>{{ line.guestName }}</td>
            <td>{{ formatWhen(line.completedAt) }}</td>
            <td>{{ money(line.orderTotal) }}</td>
            <td>{{ line.units }}</td>
            <td>
              {{ money(staffMode ? line.lineAmount : displayAmount(line.lineAmount)) }}
              <div v-if="line.note" class="muted tiny">{{ line.note }}</div>
            </td>
            <td>
              <span class="chip" :class="line.payrollPaid ? 'chip--ok' : 'chip--warn'">
                {{ line.payrollPaid ? 'Payment Done!' : 'Unpaid' }}
              </span>
            </td>
            <td v-if="!staffMode">
              <button
                v-if="!line.payrollPaid"
                type="button"
                class="btn btn-ghost"
                :disabled="acting"
                @click="markPaid(line.orderId)"
              >
                Mark paid
              </button>
            </td>
          </tr>
          <tr v-if="!visibleLines.length">
            <td :colspan="staffMode ? 7 : 8" class="muted">
              No collected orders in the selected period{{ pendingOnly ? ' (pending only)' : '' }}.
            </td>
          </tr>
        </tbody>
      </DataTableShell>

      <div v-if="income" class="income-totals">
        <span>Pending {{ money(income.unpaidTotal) }}</span>
        <span>Paid {{ money(income.paidTotal) }}</span>
        <span v-if="income.unpaidTargetMetCount != null">
          Target met {{ income.unpaidTargetMetCount }} / {{ income.unpaidTargetPeriods }}
        </span>
      </div>
    </section>
  </div>
</template>

<style scoped>
.pay-page {
  padding-bottom: 2.5rem;
}

.mode-switch {
  display: inline-flex;
  margin-top: 0.75rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-pill);
  overflow: hidden;
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

.check {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  font-size: 0.88rem;
  color: var(--color-text-secondary);
  padding-bottom: 0.35rem;
}

.summary-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0.75rem;
  margin-bottom: 1rem;
}

@media (max-width: 900px) {
  .summary-row {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 520px) {
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
