<script setup>
import { ref, onMounted, computed } from 'vue';
import { useRouter } from 'vue-router';
import { providerTeamApi } from '../services/marketplaceApi';
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

/* ================= PAYROLL FORM ================= */
const payrollForm = ref({
  staffUserId: '',
  unitsWorked: '',
  notes: '',
});

/* ================= PAY MODAL ================= */
const selectedStaff = ref(null);
const showPayModal = ref(false);
const paying = ref(false);

/* ================= LOAD ================= */
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
    const [t, p] = await Promise.all([
      providerTeamApi.listStaff(),
      providerTeamApi.listPayrollEntries(),
    ]);

    team.value = t.data || [];
    payroll.value = p.data || [];

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

/* ================= STAFF FILTER ================= */
const staffOptions = computed(() =>
  (team.value || []).filter(
    (m) => !m.owner && m.rateAmount != null && Number(m.rateAmount) > 0
  )
);

/* ================= PAY CALC ================= */
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
    }))
);

const totalExpected = computed(() =>
  payoutRows.value.reduce((sum, m) => sum + (Number(m._expected) || 0), 0)
);

/* ================= PAY ACTION ================= */
function openPayModal(staff) {
  selectedStaff.value = staff;
  showPayModal.value = true;
}

async function confirmPayment() {
  if (!selectedStaff.value) return;

  paying.value = true;
  error.value = '';
  message.value = '';

  try {
    await providerTeamApi.addPayroll(selectedStaff.value.id, {
      unitsWorked: Number(selectedStaff.value._units),
      notes: 'Paid via staff payments screen',
    });

    showPayModal.value = false;
    selectedStaff.value = null;

    message.value = 'Payment recorded successfully.';
    await load();

  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  } finally {
    paying.value = false;
  }
}
</script>

<template>
  <div class="page-document page-document--wide staff-payments-page">

    <header class="page-hero">
      <h1>Staff payments</h1>
    </header>

    <p v-if="error" class="err-toast">{{ error }}</p>
    <p v-if="message" class="ok-msg">{{ message }}</p>

    <!-- ================= TABLE ================= -->
    <section v-if="!loading" class="surface-panel">

      <div class="toolbar">
        <button class="btn btn-ghost" @click="load">Refresh</button>

        <div class="total">
          <span class="muted small">Total expected</span>
          <strong>R {{ totalExpected.toFixed(2) }}</strong>
        </div>
      </div>

      <ResponsiveRecordShell desktop-label="Staff payments">

        <template #desktop>
          <DataTableShell>

            <thead>
              <tr>
                <th>Staff</th>
                <th>Rate</th>
                <th>Units</th>
                <th>Expected</th>
                <th>Actions</th>
              </tr>
            </thead>

            <tbody>
              <tr v-for="m in payoutRows" :key="m.id">

                <td>{{ m.email }}</td>

                <td>R {{ Number(m.rateAmount).toFixed(2) }}</td>

                <td>
                  <input v-model.number="unitsByStaffId[m.id]" type="number" min="0" />
                </td>

                <td>
                  <strong>R {{ Number(m._expected).toFixed(2) }}</strong>
                </td>

                <td class="cell-actions">
                  <button
                    class="btn btn-primary"
                    :disabled="!m._units || m._units <= 0"
                    @click="openPayModal(m)"
                  >
                    Pay
                  </button>
                </td>

              </tr>

              <tr v-if="!payoutRows.length">
                <td colspan="5">No staff members.</td>
              </tr>

            </tbody>

          </DataTableShell>
        </template>

      </ResponsiveRecordShell>
    </section>

    <!-- ================= PAYROLL HISTORY ================= -->
    <section v-if="!loading" class="surface-panel">
      <h2>Payroll history</h2>

      <DataTableShell>
        <thead>
          <tr>
            <th>Staff</th>
            <th>Units</th>
            <th>Amount</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="e in payroll" :key="e.id">
            <td>{{ e.staffEmail }}</td>
            <td>{{ e.unitsWorked }}</td>
            <td>R {{ e.amount }}</td>
          </tr>
        </tbody>
      </DataTableShell>

    </section>

    <!-- ================= PAY MODAL ================= -->
    <div v-if="showPayModal" class="modal-backdrop">
      <div class="modal">

        <h3>Confirm Payment</h3>

        <p><strong>Staff:</strong> {{ selectedStaff?.email }}</p>
        <p><strong>Units:</strong> {{ selectedStaff?._units }}</p>
        <p><strong>Total:</strong> R {{ selectedStaff?._expected }}</p>

        <div class="modal-actions">
          <button class="btn btn-primary" :disabled="paying" @click="confirmPayment">
            {{ paying ? 'Processing...' : 'Confirm Pay' }}
          </button>

          <button class="btn btn-ghost" @click="showPayModal = false">
            Cancel
          </button>
        </div>

      </div>
    </div>

  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  margin-bottom: 1rem;
}

.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.4);
  display: flex;
  align-items: center;
  justify-content: center;
}

.modal {
  background: #fff;
  padding: 1.2rem;
  border-radius: 12px;
  width: 100%;
  max-width: 420px;
}

.modal-actions {
  display: flex;
  gap: 1rem;
  margin-top: 1rem;
}
</style> 