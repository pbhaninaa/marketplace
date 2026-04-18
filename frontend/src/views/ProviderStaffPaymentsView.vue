<script setup>
import { ref, onMounted, computed } from 'vue';
import { useRouter } from 'vue-router';
import { api } from '../api';
import { useAuthStore } from '../stores/auth';
import ResponsiveRecordShell from '../components/layout/ResponsiveRecordShell.vue';
import DataTableShell from '../components/ui/DataTableShell.vue';
import FormField from '../components/ui/FormField.vue';

const router = useRouter();
const auth = useAuthStore();

const loading = ref(true);
const error = ref('');
const message = ref('');

const team = ref([]);
const unitsByStaffId = ref({});
const payroll = ref([]);

const payrollForm = ref({
  staffUserId: '',
  unitsWorked: '',
  notes: '',
});

onMounted(async () => {
  auth.restoreFromStorage();
  if (!auth.isProviderUser) {
    router.replace({ path: '/login', query: { redirect: '/provider/staff-payments' } });
    return;
  }
  if (!auth.canManageStaff) {
    router.replace({ path: '/provider' });
    return;
  }
  await load();
});

async function load() {
  loading.value = true;
  error.value = '';
  message.value = '';
  try {
    const [t, p] = await Promise.all([api.get('/api/provider/me/staff'), api.get('/api/provider/me/payroll-entries')]);
    team.value = t.data || [];
    payroll.value = p.data || [];
    // initialize units to 0 for non-owner staff
    const next = {};
    for (const m of team.value) {
      if (m.owner) continue;
      next[m.id] = unitsByStaffId.value[m.id] ?? 0;
    }
    unitsByStaffId.value = next;
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  } finally {
    loading.value = false;
  }
}

const staffOptions = computed(() =>
  (team.value || []).filter((m) => !m.owner && m.rateAmount != null && Number(m.rateAmount) > 0),
);

async function recordPayroll() {
  error.value = '';
  message.value = '';
  try {
    await api.post(`/api/provider/me/staff/${payrollForm.value.staffUserId}/payroll`, {
      unitsWorked: Number(payrollForm.value.unitsWorked),
      notes: payrollForm.value.notes || null,
    });
    payrollForm.value = { staffUserId: '', unitsWorked: '', notes: '' };
    message.value = 'Payroll line saved.';
    await load();
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  }
}

function rateAmount(m) {
  const n = Number(m?.rateAmount ?? 0);
  return Number.isFinite(n) ? n : 0;
}

function units(m) {
  const n = Number(unitsByStaffId.value?.[m.id] ?? 0);
  return Number.isFinite(n) ? n : 0;
}

function expectedPayout(m) {
  return units(m) * rateAmount(m);
}

const payoutRows = computed(() =>
  (team.value || [])
    .filter((m) => !m.owner)
    .map((m) => ({
      ...m,
      _units: units(m),
      _expected: expectedPayout(m),
    })),
);

const totalExpected = computed(() =>
  payoutRows.value.reduce((sum, m) => sum + (Number(m._expected) || 0), 0),
);
</script>

<template>
  <div class="page-document page-document--wide staff-payments-page">
    <header class="page-hero">
      <p class="page-hero__eyebrow">Provider</p>
      <h1 class="page-hero__title">Staff payments</h1>
      <p class="page-hero__lead">
        Enter expected units for each staff member (based on their rate type) to calculate expected payouts.
      </p>
    </header>

    <p v-if="error" class="err-toast">{{ error }}</p>
    <p v-if="message" class="ok-msg">{{ message }}</p>
    <p v-if="loading" class="muted loading-line">Loading…</p>

    <section v-else class="surface-panel payments-panel">
      <div class="toolbar">
        <button type="button" class="btn btn-ghost" @click="load">Refresh</button>
        <div class="total">
          <span class="muted small">Total expected</span>
          <strong>R {{ totalExpected.toFixed(2) }}</strong>
        </div>
      </div>

      <ResponsiveRecordShell desktop-label="Staff payments">
        <template #desktop>
          <DataTableShell caption="Staff payments">
            <thead>
              <tr>
                <th>Staff</th>
                <th>Rate type</th>
                <th class="col-num">Rate</th>
                <th class="col-num">Units expected</th>
                <th class="col-num">Expected payout</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="m in payoutRows" :key="m.id">
                <td>
                  <div class="cell-stack">
                    <strong>{{ m.email }}</strong>
                    <span class="muted small">{{ m.role }}</span>
                  </div>
                </td>
                <td>{{ m.rateUnit || '—' }}</td>
                <td class="col-num">R {{ Number(m.rateAmount ?? 0).toFixed(2) }}</td>
                <td class="col-num">
                  <FormField label="">
                    <input
                      v-model.number="unitsByStaffId[m.id]"
                      type="number"
                      min="0"
                      step="0.01"
                      style="max-width: 140px"
                    />
                  </FormField>
                </td>
                <td class="col-num">
                  <strong>R {{ Number(m._expected ?? 0).toFixed(2) }}</strong>
                </td>
              </tr>
              <tr v-if="!payoutRows.length">
                <td colspan="5" class="muted small">No staff members.</td>
              </tr>
            </tbody>
          </DataTableShell>
        </template>
        <template #mobile>
          <div class="cards">
            <article v-for="m in payoutRows" :key="m.id" class="record-card">
              <strong>{{ m.email }}</strong>
              <span class="meta">{{ m.role }}</span>
              <span class="meta">Rate: {{ m.rateUnit || '—' }} · R{{ Number(m.rateAmount ?? 0).toFixed(2) }}</span>
              <FormField label="Units expected">
                <input v-model.number="unitsByStaffId[m.id]" type="number" min="0" step="0.01" />
              </FormField>
              <span class="meta"><strong>Expected: R {{ Number(m._expected ?? 0).toFixed(2) }}</strong></span>
            </article>
          </div>
        </template>
      </ResponsiveRecordShell>
    </section>

    <section v-if="!loading" class="surface-panel payments-panel">
      <h2>Record payroll</h2>
      <FormField label="Staff member">
        <select v-model="payrollForm.staffUserId" required>
          <option value="" disabled>Select staff</option>
          <option v-for="s in staffOptions" :key="s.id" :value="String(s.id)">
            {{ s.email }} ({{ s.rateUnit }} · R{{ s.rateAmount }})
          </option>
        </select>
      </FormField>
      <FormField label="Units worked">
        <input v-model="payrollForm.unitsWorked" type="number" min="0.01" step="0.01" required />
      </FormField>
      <FormField label="Notes (optional)" capitalize-first>
        <input v-model="payrollForm.notes" type="text" />
      </FormField>
      <button type="button" class="btn btn-primary" @click="recordPayroll">Save payroll line</button>
    </section>

    <section v-if="!loading" class="surface-panel payments-panel">
      <h2>Payroll history</h2>
      <ResponsiveRecordShell desktop-label="Payroll entries">
        <template #desktop>
          <DataTableShell caption="Payroll history">
            <thead>
              <tr>
                <th>When</th>
                <th>Staff</th>
                <th>Units</th>
                <th>Rate</th>
                <th>Type</th>
                <th class="col-num">Amount</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="e in payroll" :key="e.id">
                <td>{{ e.createdAt?.slice(0, 19) }}</td>
                <td>{{ e.staffEmail }}</td>
                <td>{{ e.unitsWorked }}</td>
                <td>R {{ e.rateSnapshot }}</td>
                <td>{{ e.rateUnitSnapshot }}</td>
                <td class="col-num"><strong>R {{ e.amount }}</strong></td>
              </tr>
              <tr v-if="!(payroll || []).length">
                <td colspan="6" class="muted small">No payroll entries yet.</td>
              </tr>
            </tbody>
          </DataTableShell>
        </template>
        <template #mobile>
          <div class="cards">
            <article v-for="e in payroll" :key="e.id" class="record-card">
              <strong>R {{ e.amount }}</strong>
              <span class="meta">{{ e.staffEmail }}</span>
              <span class="meta">{{ e.unitsWorked }} @ R{{ e.rateSnapshot }} · {{ e.rateUnitSnapshot }}</span>
              <span class="meta">{{ e.createdAt?.slice(0, 16) }}</span>
            </article>
          </div>
        </template>
      </ResponsiveRecordShell>
    </section>
  </div>
</template>

<style scoped>
.payments-panel {
  margin-bottom: 1.25rem;
}
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 0.75rem;
}
.total {
  text-align: right;
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
}
.cell-stack {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}
.col-num {
  text-align: right;
}
.cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 0.85rem;
}
.record-card {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 0.85rem 0.95rem;
  background: var(--color-surface-elevated);
  box-shadow: var(--shadow-sm);
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}
.meta {
  color: var(--color-muted);
  font-size: 0.85rem;
}
</style>

