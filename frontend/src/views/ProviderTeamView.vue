<script setup>
import { ref, onMounted, computed } from 'vue';
import { useRouter } from 'vue-router';
import { providerTeamApi } from '../services/marketplaceApi';
import { useAuthStore } from '../stores/auth';
import FormField from '../components/ui/FormField.vue';
import ResponsiveRecordShell from '../components/layout/ResponsiveRecordShell.vue';
import DataTableShell from '../components/ui/DataTableShell.vue';

const router = useRouter();
const auth = useAuthStore();

const team = ref([]);
const payroll = ref([]);
const loading = ref(true);
const error = ref('');
const context = ref(null);

const editing = ref(null); // staff member object being edited
const showEditDialog = ref(false);
const showAddDialog = ref(false);
const editForm = ref({
  role: 'PROVIDER_STAFF',
  rateUnit: 'HOURLY',
  rateAmount: '',
  enabled: true,
  permissions: [],
});

const newStaff = ref({
  email: '',
  password: '',
  role: 'PROVIDER_STAFF',
  rateUnit: 'HOURLY',
  rateAmount: '',
  permissions: [],
});

const payrollForm = ref({
  staffUserId: '',
  unitsWorked: '',
  notes: '',
});

const staffOptions = computed(() =>
  team.value.filter((m) => !m.owner && m.rateAmount != null && Number(m.rateAmount) > 0),
);

onMounted(async () => {
  auth.restoreFromStorage();
  if (!auth.isProviderUser) {
    router.replace({ path: '/login', query: { redirect: '/provider/team' } });
    return;
  }
  await loadContext();
  await loadAll();
});

async function loadContext() {
  try {
    const { data } = await providerTeamApi.getContext();
    context.value = data;
  } catch (e) {
    // Context is used for UX only; server still enforces permissions.
    context.value = null;
  }
}

async function loadAll() {
  loading.value = true;
  error.value = '';
  try {
    const [t, p] = await Promise.all([
      providerTeamApi.listStaff(),
      providerTeamApi.listPayrollEntries(),
    ]);
    team.value = t.data;
    payroll.value = p.data;
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  } finally {
    loading.value = false;
  }
}

const canManageTeam = computed(() => context.value?.effectivePermissions?.includes?.('TEAM_MANAGE'));
const canReadTeam = computed(() => context.value?.effectivePermissions?.includes?.('TEAM_READ'));
const canReadPayroll = computed(() => context.value?.effectivePermissions?.includes?.('PAYROLL_READ'));
const canWritePayroll = computed(() => context.value?.effectivePermissions?.includes?.('PAYROLL_WRITE'));

const applicablePermissionOptions = computed(() => {
  const keys = context.value?.applicablePermissions || [];
  // hide purely internal/future keys if needed later
  return keys;
});

const grantablePermissionOptions = computed(() => {
  const applicable = applicablePermissionOptions.value || [];
  const mine = context.value?.effectivePermissions || [];
  // Rule: you cannot grant permissions you don't have. (Server enforces too.)
  return applicable.filter((k) => mine.includes(k));
});

function openEdit(member) {
  if (!canManageTeam.value) return;
  editing.value = member;
  editForm.value = {
    role: member.role,
    rateUnit: member.rateUnit || 'HOURLY',
    rateAmount: member.rateAmount != null ? String(member.rateAmount) : '0',
    enabled: !!member.enabled,
    permissions: [...(member.permissions || [])],
  };
  showEditDialog.value = true;
}

function closeEdit() {
  editing.value = null;
  showEditDialog.value = false;
}

function openAdd() {
  if (!canManageTeam.value) return;
  showAddDialog.value = true;
}

function closeAdd() {
  showAddDialog.value = false;
  newStaff.value = {
    email: '',
    password: '',
    role: 'PROVIDER_STAFF',
    rateUnit: 'HOURLY',
    rateAmount: '',
    permissions: [],
  };
}

async function saveEdit() {
  if (!editing.value) return;
  error.value = '';
  try {
    await providerTeamApi.updateStaff(editing.value.id, {
      role: editForm.value.role,
      rateUnit: editForm.value.rateUnit,
      rateAmount: Number(editForm.value.rateAmount),
      enabled: !!editForm.value.enabled,
      permissions: editForm.value.permissions,
    });
    closeEdit();
    await loadContext();
    await loadAll();
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  }
}

async function deleteStaff(id) {
  if (!id) return;
  error.value = '';
  try {
    await providerTeamApi.removeStaff(id);
    if (editing.value?.id === id) closeEdit();
    await loadContext();
    await loadAll();
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  }
}

async function createStaff() {
  error.value = '';
  try {
    await providerTeamApi.inviteStaff({
      email: newStaff.value.email,
      password: newStaff.value.password,
      role: newStaff.value.role,
      rateUnit: newStaff.value.rateUnit,
      rateAmount: Number(newStaff.value.rateAmount),
      permissions: newStaff.value.permissions,
    });
    closeAdd();
    await loadAll();
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  }
}

async function recordPayroll() {
  error.value = '';
  try {
    await providerTeamApi.addPayroll(payrollForm.value.staffUserId, {
      unitsWorked: Number(payrollForm.value.unitsWorked),
      notes: payrollForm.value.notes || null,
    });
    payrollForm.value = { staffUserId: '', unitsWorked: '', notes: '' };
    await loadAll();
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  }
}
</script>

<template>
  <div class="page-document page-document--wide team-page">
    <header class="page-hero">
      <p class="page-hero__eyebrow">Your organisation</p>
      <h1 class="page-hero__title">Team & payroll</h1>
      <p class="page-hero__lead">
        Invite staff with a rate type (hourly/daily/weekly/monthly) and a rate amount. Each payroll line stores the rate at the
        time of entry and calculates <strong>units × rate</strong>.
      </p>
    </header>

    <p v-if="error" class="err-toast">{{ error }}</p>
    <p v-if="loading" class="muted loading-line">Loading…</p>

    <template v-else>
      <section class="surface-panel team-section">
        <div class="panel-head">
          <h2>Team members</h2>
          <button type="button" class="btn btn-primary" :disabled="!canManageTeam" @click="openAdd">Add new</button>
        </div>
        <p v-if="!canReadTeam" class="muted small">You do not have permission to view team members.</p>
        <ResponsiveRecordShell desktop-label="Team directory">
          <template #desktop>
            <DataTableShell caption="Team members">
              <thead>
                <tr>
                  <th>Email</th>
                  <th>Role</th>
                  <th>Owner</th>
                  <th>Rate type</th>
                  <th>Rate</th>
                  <th>Enabled</th>
                  <th>Permissions</th>
                  <th v-if="canManageTeam"></th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="m in team" :key="m.id">
                  <td>{{ m.email }}</td>
                  <td>{{ m.role }}</td>
                  <td>{{ m.owner ? 'Yes' : '—' }}</td>
                  <td>{{ m.rateUnit || '—' }}</td>
                  <td>{{ m.rateAmount != null ? `R ${m.rateAmount}` : '—' }}</td>
                  <td>{{ m.enabled ? 'Yes' : 'No' }}</td>
                  <td class="small muted">{{ (m.permissions || []).join(', ') || '—' }}</td>
                  <td v-if="canManageTeam" class="actions">
                    <button type="button" class="btn btn-ghost" :disabled="m.owner" @click="openEdit(m)">Edit</button>
                    <button type="button" class="btn btn-ghost btn-danger" :disabled="m.owner" @click="deleteStaff(m.id)">
                      Delete
                    </button>
                  </td>
                </tr>
              </tbody>
            </DataTableShell>
          </template>
          <template #mobile>
            <div class="cards">
              <article v-for="m in team" :key="m.id" class="member-card">
                <strong>{{ m.email }}</strong>
                <span class="meta">{{ m.role }}{{ m.owner ? ' · Owner' : '' }}</span>
                <span class="meta">Rate: {{ m.rateUnit || '—' }} · R{{ m.rateAmount ?? '—' }}</span>
                <span class="meta">{{ m.enabled ? 'Active' : 'Disabled' }}</span>
                <span class="meta">Perms: {{ (m.permissions || []).join(', ') || '—' }}</span>
                <button v-if="canManageTeam" type="button" class="btn btn-ghost" :disabled="m.owner" @click="openEdit(m)">
                  Edit
                </button>
                <button
                  v-if="canManageTeam"
                  type="button"
                  class="btn btn-ghost btn-danger"
                  :disabled="m.owner"
                  @click="deleteStaff(m.id)"
                >
                  Delete
                </button>
              </article>
            </div>
          </template>
        </ResponsiveRecordShell>
      </section>

      <dialog class="mp-dialog" :open="showEditDialog">
        <div class="mp-dialog__panel">
          <div class="mp-dialog__head">
            <h2 class="mp-dialog__title">Edit staff</h2>
            <button type="button" class="btn btn-ghost" @click="closeEdit">Close</button>
          </div>

          <p v-if="editing" class="muted small"><strong>Editing:</strong> {{ editing.email }}</p>

          <div class="grid-2">
            <FormField label="Role">
              <select v-model="editForm.role">
                <option value="PROVIDER_ADMIN">Provider admin</option>
                <option value="PROVIDER_STAFF">Staff</option>
                <option value="PROVIDER_VIEWER">Viewer (read-only)</option>
              </select>
            </FormField>
            <FormField label="Enabled">
              <select v-model="editForm.enabled">
                <option :value="true">Enabled</option>
                <option :value="false">Disabled</option>
              </select>
            </FormField>
          </div>

          <div class="grid-2">
            <FormField label="Rate type">
              <select v-model="editForm.rateUnit" required>
                <option value="HOURLY">Hourly</option>
                <option value="DAILY">Daily</option>
                <option value="WEEKLY">Weekly</option>
                <option value="MONTHLY">Monthly</option>
              </select>
            </FormField>
            <FormField label="Rate amount (ZAR)">
              <input v-model="editForm.rateAmount" type="number" min="0" step="0.01" required />
            </FormField>
          </div>

          <FormField label="Permissions">
            <div class="perm-grid">
              <label v-for="k in grantablePermissionOptions" :key="k" class="perm-item">
                <input type="checkbox" :value="k" v-model="editForm.permissions" />
                <span>{{ k }}</span>
              </label>
            </div>
            <p class="muted small">Tick the permissions to grant. You can only grant permissions you already have.</p>
          </FormField>

          <div class="mp-dialog__actions">
            <button type="button" class="btn btn-primary" @click="saveEdit">Save changes</button>
            <button type="button" class="btn btn-ghost" @click="closeEdit">Cancel</button>
            <button
              v-if="editing && !editing.owner"
              type="button"
              class="btn btn-ghost btn-danger"
              @click="deleteStaff(editing.id)"
            >
              Delete staff
            </button>
          </div>
        </div>
      </dialog>

     <dialog class="mp-dialog" :open="showAddDialog">
  <div class="mp-dialog__overlay" @click="closeAdd"></div>

  <div class="mp-dialog__panel">

    <!-- Header -->
    <header class="mp-dialog__head">
      <div>
        <h2 class="mp-dialog__title">Add staff member</h2>
        <p class="mp-dialog__subtitle">Create a new team member and assign role & permissions</p>
      </div>

      <button type="button" class="btn btn-icon" @click="closeAdd" aria-label="Close">
        ✕
      </button>
    </header>

    <!-- Permission warning -->
    <div v-if="!canManageTeam" class="mp-alert">
      You do not have permission to create or edit staff.
    </div>

    <!-- Content -->
    <section class="mp-dialog__body">

      <div class="form-section">
        <h3 class="section-title">Account details</h3>

        <FormField label="Email">
          <input v-model="newStaff.email" type="email" required :disabled="!canManageTeam" />
        </FormField>

        <FormField label="Password">
          <input v-model="newStaff.password" type="password" required minlength="8" :disabled="!canManageTeam" />
        </FormField>
      </div>

      <div class="form-section">
        <h3 class="section-title">Role & access</h3>

        <FormField label="Role">
          <select v-model="newStaff.role" :disabled="!canManageTeam">
            <option value="PROVIDER_ADMIN">Provider admin</option>
            <option value="PROVIDER_STAFF">Staff</option>
            <option value="PROVIDER_VIEWER">Viewer (read-only)</option>
          </select>
        </FormField>
      </div>

      <div class="form-section">
        <h3 class="section-title">Billing</h3>

        <div class="grid-2">
          <FormField label="Rate type">
            <select v-model="newStaff.rateUnit" required :disabled="!canManageTeam">
              <option value="HOURLY">Hourly</option>
              <option value="DAILY">Daily</option>
              <option value="WEEKLY">Weekly</option>
              <option value="MONTHLY">Monthly</option>
            </select>
          </FormField>

          <FormField label="Rate amount (ZAR)">
            <input v-model="newStaff.rateAmount" type="number" min="0" step="0.01" required :disabled="!canManageTeam" />
          </FormField>
        </div>
      </div>

      <div class="form-section">
        <h3 class="section-title">Permissions</h3>

        <div class="perm-chips">
          <label
            v-for="k in grantablePermissionOptions"
            :key="k"
            class="perm-chip"
            :class="{ active: newStaff.permissions.includes(k) }"
          >
            <input
              type="checkbox"
              :value="k"
              v-model="newStaff.permissions"
              :disabled="!canManageTeam"
            />
            <span>{{ k }}</span>
          </label>
        </div>

        <p class="muted small">Select what this staff member is allowed to access.</p>
      </div>

    </section>

    <!-- Actions -->
    <footer class="mp-dialog__actions">
      <button type="button" class="btn btn-primary" :disabled="!canManageTeam" @click="createStaff">
        Create staff
      </button>
      <button type="button" class="btn btn-ghost" @click="closeAdd">
        Cancel
      </button>
    </footer>

  </div>
</dialog>

      <section class="surface-panel team-section">
        <h2>Payroll</h2>
        <p class="muted small">
          Payroll recording and history have moved to <router-link to="/provider/staff-payments">Staff payments</router-link>.
        </p>
      </section>
    </template>
  </div>
</template>

<style scoped>
/* =========================
   DIALOG BASE
========================= */
.mp-dialog {
  position: fixed;
  inset: 0;
  width: min(760px, calc(100vw - 2rem));
  border: none;
  padding: 0;
  background: transparent;
}

.mp-dialog::backdrop {
  background: rgba(0, 0, 0, 0.35);
}

/* =========================
   OVERLAY (optional if used)
========================= */
.mp-dialog__overlay {
  position: absolute;
  inset: 0;
  background: rgba(15, 23, 42, 0.55);
  backdrop-filter: blur(4px);
}

/* =========================
   PANEL
========================= */
.mp-dialog__panel {
  background: var(--color-surface-elevated);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xl);
  padding: 1rem 1rem 1.1rem;

  /* remove all shadow/elevation */
  box-shadow: none !important;

  position: relative;
  width: min(720px, 92vw);
  max-height: 90vh;
  overflow: auto;
  margin: 5vh auto;

  display: flex;
  flex-direction: column;
}

/* =========================
   HEADER
========================= */
.mp-dialog__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 0.75rem;
}

.mp-dialog__title {
  font-family: var(--font-display);
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}

.mp-dialog__subtitle {
  margin: 4px 0 0;
  font-size: 13px;
  color: #64748b;
}

/* =========================
   BODY
========================= */
.mp-dialog__body {
  padding: 20px 24px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* =========================
   SECTIONS
========================= */
.form-section {
  padding-bottom: 16px;
  border-bottom: 1px solid #f1f5f9;
}

.section-title {
  font-size: 13px;
  font-weight: 600;
  text-transform: uppercase;
  color: #64748b;
  margin-bottom: 12px;
}

/* =========================
   GRID (DEDUPED)
========================= */
.grid-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
}

@media (max-width: 980px) {
  .grid-2 {
    grid-template-columns: 1fr;
  }
}

/* =========================
   PERMISSIONS
========================= */
.perm-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.perm-chip {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  border: 1px solid #e2e8f0;
  border-radius: 999px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s ease;
  user-select: none;
}

.perm-chip input {
  display: none;
}

.perm-chip.active {
  background: #2563eb;
  color: white;
  border-color: #2563eb;
}

/* legacy compatibility (your original style kept) */
.perm-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.35rem 0.6rem;
  padding: 0.35rem;
  border: 1px solid rgba(26, 60, 52, 0.12);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.55);
  max-height: 180px;
  overflow: auto;
}

@media (max-width: 980px) {
  .perm-grid {
    grid-template-columns: 1fr;
    max-height: 220px;
  }
}

.perm-item {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  font-size: 0.88rem;
  color: var(--color-canopy);
  line-height: 1.2;
  padding: 0.15rem 0.25rem;
  border-radius: 10px;
}

.perm-item:hover {
  background: rgba(26, 60, 52, 0.05);
}

/* =========================
   ALERT
========================= */
.mp-alert {
  margin: 0 24px;
  padding: 10px 12px;
  background: #fff7ed;
  border: 1px solid #fed7aa;
  color: #9a3412;
  border-radius: 8px;
  font-size: 13px;
}

/* =========================
   ACTIONS (DEDUPED)
========================= */
.mp-dialog__actions {
  position: sticky;
  bottom: 0;
  background: #fff;
  padding: 16px 24px;
  border-top: 1px solid #eee;

  display: flex;
  justify-content: flex-end;
  gap: 0.6rem;
  margin-top: 0.75rem;
}

/* =========================
   ICON BUTTON
========================= */
.btn-icon {
  border: none;
  background: transparent;
  font-size: 18px;
  cursor: pointer;
}

/* =========================
   TEAM PAGE (unchanged, grouped)
========================= */
.team-page {
  padding: 0.5rem 0 2.5rem;
}

.team-section {
  margin-top: 1.35rem;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 0.5rem;
}

.team-section h2 {
  font-family: var(--font-display);
}

.team-section .btn-primary {
  margin-top: 0.65rem;
}

.loading-line {
  padding: 1rem 0;
}

.muted {
  color: var(--color-muted);
  line-height: 1.5;
}

/* =========================
   CARDS
========================= */
.cards {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.member-card,
.pay-card {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 0.95rem 1.05rem;
  background: var(--color-surface);
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  box-shadow: var(--shadow-sm);
}

.meta {
  font-size: 0.82rem;
  color: var(--color-muted);
}

.col-num {
  text-align: right;
}

.actions {
  text-align: right;
}

.edit-panel {
  max-width: 680px;
}

.edit-actions {
  display: flex;
  gap: 0.6rem;
  margin-top: 0.75rem;
}

.btn-danger {
  border-color: rgba(180, 40, 40, 0.35);
  color: rgba(140, 20, 20, 0.95);
}
</style>