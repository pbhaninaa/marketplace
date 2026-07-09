<script setup>
import { ref, onMounted, computed } from 'vue';
import { useRouter } from 'vue-router';
import { providerTeamApi } from '../services/marketplaceApi';
import { useAuthStore } from '../stores/auth';
import FormField from '../components/ui/FormField.vue';
import DataTableShell from '../components/ui/DataTableShell.vue';

const router = useRouter();
const auth = useAuthStore();

const team = ref([]);
const loading = ref(true);
const error = ref('');
const message = ref('');
const context = ref(null);
const search = ref('');

const showAdd = ref(false);
const showEdit = ref(false);
const showView = ref(false);
const showDelete = ref(false);
const saving = ref(false);

const editing = ref(null);
const viewing = ref(null);
const deleting = ref(null);

const emptyForm = () => ({
  email: '',
  password: '',
  firstName: '',
  lastName: '',
  phoneNumber: '',
  role: 'PROVIDER_STAFF',
  rateUnit: 'PER_SERVICE',
  rateAmount: '',
  targetPeriod: 'MONTHLY',
  targetValue: '',
  bonusPercentage: '',
  enabled: true,
  permissions: [],
});

const createForm = ref(emptyForm());
const editForm = ref(emptyForm());

const PAY_METHODS = [
  { value: 'PER_SERVICE', label: 'Per service (order)' },
  { value: 'PER_HOUR', label: 'Per hour' },
  { value: 'PER_DAY', label: 'Per day' },
  { value: 'WEEKLY', label: 'Per week' },
  { value: 'MONTHLY', label: 'Monthly salary' },
];

const ROLE_OPTIONS = [
  { value: 'PROVIDER_ADMIN', label: 'Admin' },
  { value: 'PROVIDER_STAFF', label: 'Staff' },
  { value: 'PROVIDER_VIEWER', label: 'Viewer' },
];

const PERM_LABELS = {
  LISTINGS_READ: 'View listings',
  LISTINGS_WRITE: 'Manage listings',
  TEAM_READ: 'View team',
  TEAM_MANAGE: 'Manage team',
  PAYROLL_READ: 'View payroll',
  PAYROLL_WRITE: 'Record payouts',
  ORDERS_READ: 'View orders',
  ORDERS_WRITE: 'Manage orders',
  RENTALS_READ: 'View rentals',
  RENTALS_WRITE: 'Manage rentals',
};

onMounted(async () => {
  auth.restoreFromStorage();
  if (!auth.isProviderUser) {
    router.replace({ path: '/login', query: { redirect: '/provider/team' } });
    return;
  }
  await loadContext();
  await loadTeam();
});

async function loadContext() {
  try {
    const { data } = await providerTeamApi.getContext();
    context.value = data;
  } catch {
    context.value = null;
  }
}

async function loadTeam() {
  loading.value = true;
  error.value = '';
  try {
    const { data } = await providerTeamApi.listStaff();
    team.value = data || [];
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  } finally {
    loading.value = false;
  }
}

const canManageTeam = computed(() => context.value?.effectivePermissions?.includes?.('TEAM_MANAGE') || auth.isProviderOwner);
const canReadTeam = computed(() => context.value?.effectivePermissions?.includes?.('TEAM_READ') || auth.isProviderOwner);
const isOwnerActor = computed(() => auth.isProviderOwner);

const grantablePermissions = computed(() => {
  const applicable = context.value?.applicablePermissions || [];
  const mine = context.value?.effectivePermissions || [];
  if (isOwnerActor.value) return applicable;
  return applicable.filter((k) => mine.includes(k));
});

const defaultPermissions = computed(() => {
  const keys = grantablePermissions.value;
  const preferred = ['LISTINGS_READ', 'LISTINGS_WRITE', 'ORDERS_READ', 'ORDERS_WRITE', 'RENTALS_READ', 'RENTALS_WRITE', 'TEAM_READ'];
  return preferred.filter((k) => keys.includes(k));
});

const filteredTeam = computed(() => {
  const q = search.value.trim().toLowerCase();
  const rows = team.value || [];
  if (!q) return rows;
  return rows.filter((m) => {
    const hay = [m.email, m.firstName, m.lastName, m.displayName, m.phoneNumber, m.role, ...(m.permissions || [])]
      .filter(Boolean)
      .join(' ')
      .toLowerCase();
    return hay.includes(q);
  });
});

function formatPayMethod(v) {
  return PAY_METHODS.find((p) => p.value === v)?.label || v || '—';
}

function formatRole(v) {
  return ROLE_OPTIONS.find((r) => r.value === v)?.label || v || '—';
}

function formatPerms(perms) {
  if (!perms?.length) return '—';
  return perms.map((p) => PERM_LABELS[p] || p).join(', ');
}

function displayName(m) {
  if (m.displayName) return m.displayName;
  const n = [m.firstName, m.lastName].filter(Boolean).join(' ');
  return n || m.email;
}

function openAdd() {
  error.value = '';
  createForm.value = { ...emptyForm(), permissions: [...defaultPermissions.value] };
  showAdd.value = true;
}

function openEdit(m) {
  if (m.owner) return;
  editing.value = m;
  editForm.value = {
    email: m.email,
    password: '',
    firstName: m.firstName || '',
    lastName: m.lastName || '',
    phoneNumber: m.phoneNumber || '',
    role: m.role || 'PROVIDER_STAFF',
    rateUnit: m.rateUnit || 'PER_SERVICE',
    rateAmount: m.rateAmount != null ? String(m.rateAmount) : '',
    targetPeriod: m.targetPeriod || 'MONTHLY',
    targetValue: m.targetValue != null ? String(m.targetValue) : '',
    bonusPercentage: m.bonusPercentage != null ? String(m.bonusPercentage) : '',
    enabled: !!m.enabled,
    permissions: [...(m.permissions || [])],
  };
  showEdit.value = true;
}

function openView(m) {
  viewing.value = m;
  showView.value = true;
}

function openDelete(m) {
  if (m.owner) return;
  deleting.value = m;
  showDelete.value = true;
}

async function submitCreate() {
  if (!canManageTeam.value) return;
  saving.value = true;
  error.value = '';
  try {
    const body = {
      email: createForm.value.email,
      password: createForm.value.password,
      firstName: createForm.value.firstName || null,
      lastName: createForm.value.lastName || null,
      phoneNumber: createForm.value.phoneNumber || null,
      role: createForm.value.role,
      rateUnit: createForm.value.rateUnit,
      rateAmount: Number(createForm.value.rateAmount),
      targetPeriod: createForm.value.targetPeriod || null,
      targetValue: createForm.value.targetValue === '' ? null : Number(createForm.value.targetValue),
      bonusPercentage: createForm.value.bonusPercentage === '' ? null : Number(createForm.value.bonusPercentage),
      permissions: isOwnerActor.value ? createForm.value.permissions : undefined,
    };
    await providerTeamApi.inviteStaff(body);
    message.value = 'Employee enrolled successfully.';
    showAdd.value = false;
    await loadTeam();
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  } finally {
    saving.value = false;
  }
}

async function submitEdit() {
  if (!editing.value) return;
  saving.value = true;
  error.value = '';
  try {
    const prevPerms = [...(editing.value.permissions || [])].sort().join(',');
    const nextPerms = [...(editForm.value.permissions || [])].sort().join(',');
    await providerTeamApi.updateStaff(editing.value.id, {
      role: editForm.value.role,
      firstName: editForm.value.firstName || null,
      lastName: editForm.value.lastName || null,
      phoneNumber: editForm.value.phoneNumber || null,
      rateUnit: editForm.value.rateUnit,
      rateAmount: Number(editForm.value.rateAmount),
      targetPeriod: editForm.value.targetPeriod || null,
      targetValue: editForm.value.targetValue === '' ? null : Number(editForm.value.targetValue),
      bonusPercentage: editForm.value.bonusPercentage === '' ? null : Number(editForm.value.bonusPercentage),
      enabled: !!editForm.value.enabled,
      permissions: isOwnerActor.value ? editForm.value.permissions : editForm.value.permissions,
    });
    if (prevPerms !== nextPerms) {
      auth.signalForceLogout(editing.value.email);
      message.value = 'Team member updated. They will be signed out so new permissions apply.';
    } else {
      message.value = 'Team member updated.';
    }
    showEdit.value = false;
    await loadTeam();
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  } finally {
    saving.value = false;
  }
}

async function confirmDelete() {
  if (!deleting.value) return;
  if (!isOwnerActor.value) {
    error.value = 'Only the provider owner can permanently remove team members.';
    return;
  }
  saving.value = true;
  error.value = '';
  try {
    await providerTeamApi.removeStaff(deleting.value.id);
    message.value = 'Employee permanently removed.';
    showDelete.value = false;
    deleting.value = null;
    await loadTeam();
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  } finally {
    saving.value = false;
  }
}

function money(v) {
  if (v == null || v === '') return '—';
  return new Intl.NumberFormat('en-ZA', { style: 'currency', currency: 'ZAR' }).format(Number(v));
}
</script>

<template>
  <div class="page-document page-document--wide team-page">
    <header class="page-hero">
      <p class="page-hero__eyebrow">Provider</p>
      <h1 class="page-hero__title">Team management</h1>
      <p class="page-hero__lead">
        Enrol staff, set pay methods and permissions, then pay them from completed orders on
        <router-link to="/provider/staff-payments">Staff payments</router-link>.
      </p>
    </header>

    <p v-if="error" class="alert alert--error">{{ error }}</p>
    <p v-if="message" class="alert alert--ok">{{ message }}</p>

    <div class="team-card surface-panel">
      <div class="team-card__toolbar">
        <h2>Team directory</h2>
        <div class="team-card__actions">
          <input v-model="search" type="search" class="team-search" placeholder="Search name, email, role…" />
          <button type="button" class="btn btn-primary" :disabled="!canManageTeam" @click="openAdd">
            Add employee
          </button>
        </div>
      </div>

      <p v-if="!canReadTeam" class="muted">You do not have permission to view team members.</p>
      <p v-else-if="loading" class="muted">Loading…</p>

      <DataTableShell v-else caption="Team members">
        <thead>
          <tr>
            <th>Name</th>
            <th>Email</th>
            <th>Phone</th>
            <th>Role</th>
            <th>Pay method</th>
            <th>Rate</th>
            <th>Active</th>
            <th>Permissions</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="m in filteredTeam" :key="m.id">
            <td>
              <strong>{{ displayName(m) }}</strong>
              <span v-if="m.owner" class="chip chip--owner">Owner</span>
            </td>
            <td>{{ m.email }}</td>
            <td>{{ m.phoneNumber || '—' }}</td>
            <td>{{ formatRole(m.role) }}</td>
            <td>{{ m.owner ? '—' : formatPayMethod(m.rateUnit) }}</td>
            <td>{{ m.owner ? '—' : money(m.rateAmount) }}</td>
            <td>
              <span class="chip" :class="m.enabled ? 'chip--ok' : 'chip--off'">
                {{ m.enabled ? 'Active' : 'Disabled' }}
              </span>
            </td>
            <td class="perms-cell">{{ m.owner ? 'All' : formatPerms(m.permissions) }}</td>
            <td class="actions-cell">
              <button type="button" class="icon-btn" title="View" @click="openView(m)">👁</button>
              <button
                type="button"
                class="icon-btn"
                title="Edit"
                :disabled="m.owner || !canManageTeam"
                @click="openEdit(m)"
              >
                ✎
              </button>
              <button
                type="button"
                class="icon-btn icon-btn--danger"
                title="Remove"
                :disabled="m.owner || !isOwnerActor"
                @click="openDelete(m)"
              >
                ✕
              </button>
            </td>
          </tr>
          <tr v-if="!filteredTeam.length">
            <td colspan="9" class="muted">No team members yet. Add your first employee to get started.</td>
          </tr>
        </tbody>
      </DataTableShell>
    </div>

    <!-- Add dialog -->
    <div v-if="showAdd" class="modal" role="dialog" aria-modal="true">
      <div class="modal__backdrop" @click="showAdd = false" />
      <div class="modal__panel">
        <header class="modal__head">
          <h3>Add employee</h3>
          <button type="button" class="icon-btn" @click="showAdd = false">✕</button>
        </header>
        <p v-if="!isOwnerActor" class="hint">
          New team members receive the <strong>same permissions as you</strong>.
        </p>
        <div class="form-grid">
          <FormField label="Email"><input v-model="createForm.email" type="email" required /></FormField>
          <FormField label="Password"><input v-model="createForm.password" type="password" minlength="8" required /></FormField>
          <FormField label="First name"><input v-model="createForm.firstName" type="text" /></FormField>
          <FormField label="Last name"><input v-model="createForm.lastName" type="text" /></FormField>
          <FormField label="Phone"><input v-model="createForm.phoneNumber" type="tel" /></FormField>
          <FormField label="Role">
            <select v-model="createForm.role">
              <option v-for="r in ROLE_OPTIONS" :key="r.value" :value="r.value">{{ r.label }}</option>
            </select>
          </FormField>
          <FormField label="Pay method (payroll rate)">
            <select v-model="createForm.rateUnit">
              <option v-for="p in PAY_METHODS" :key="p.value" :value="p.value">{{ p.label }}</option>
            </select>
          </FormField>
          <FormField label="Pay rate (ZAR)">
            <input v-model="createForm.rateAmount" type="number" min="0" step="0.01" required />
          </FormField>
          <FormField label="Target period">
            <select v-model="createForm.targetPeriod">
              <option value="DAILY">Daily</option>
              <option value="WEEKLY">Weekly</option>
              <option value="MONTHLY">Monthly</option>
            </select>
          </FormField>
          <FormField label="Target value">
            <input v-model="createForm.targetValue" type="number" min="0" step="0.01" />
          </FormField>
          <FormField label="Bonus %">
            <input v-model="createForm.bonusPercentage" type="number" min="0" max="100" step="0.1" />
          </FormField>
        </div>
        <div v-if="isOwnerActor" class="perm-block">
          <p class="section-label">Permissions</p>
          <div class="perm-chips">
            <label
              v-for="k in grantablePermissions"
              :key="k"
              class="perm-chip"
              :class="{ active: createForm.permissions.includes(k) }"
            >
              <input v-model="createForm.permissions" type="checkbox" :value="k" />
              {{ PERM_LABELS[k] || k }}
            </label>
          </div>
        </div>
        <footer class="modal__actions">
          <button type="button" class="btn btn-ghost" @click="showAdd = false">Cancel</button>
          <button type="button" class="btn btn-primary" :disabled="saving" @click="submitCreate">
            {{ saving ? 'Creating…' : 'Create' }}
          </button>
        </footer>
      </div>
    </div>

    <!-- Edit dialog -->
    <div v-if="showEdit && editing" class="modal" role="dialog" aria-modal="true">
      <div class="modal__backdrop" @click="showEdit = false" />
      <div class="modal__panel">
        <header class="modal__head">
          <h3>Edit team member</h3>
          <button type="button" class="icon-btn" @click="showEdit = false">✕</button>
        </header>
        <p class="muted small">{{ editing.email }}</p>
        <div class="form-grid">
          <FormField label="First name"><input v-model="editForm.firstName" type="text" /></FormField>
          <FormField label="Last name"><input v-model="editForm.lastName" type="text" /></FormField>
          <FormField label="Phone"><input v-model="editForm.phoneNumber" type="tel" /></FormField>
          <FormField label="Role">
            <select v-model="editForm.role">
              <option v-for="r in ROLE_OPTIONS" :key="r.value" :value="r.value">{{ r.label }}</option>
            </select>
          </FormField>
          <FormField label="Pay method (payroll rate)">
            <select v-model="editForm.rateUnit">
              <option v-for="p in PAY_METHODS" :key="p.value" :value="p.value">{{ p.label }}</option>
            </select>
          </FormField>
          <FormField label="Pay rate (ZAR)">
            <input v-model="editForm.rateAmount" type="number" min="0" step="0.01" required />
          </FormField>
          <FormField label="Target period">
            <select v-model="editForm.targetPeriod">
              <option value="DAILY">Daily</option>
              <option value="WEEKLY">Weekly</option>
              <option value="MONTHLY">Monthly</option>
            </select>
          </FormField>
          <FormField label="Target value">
            <input v-model="editForm.targetValue" type="number" min="0" step="0.01" />
          </FormField>
          <FormField label="Bonus %">
            <input v-model="editForm.bonusPercentage" type="number" min="0" max="100" step="0.1" />
          </FormField>
          <FormField label="Status">
            <select v-model="editForm.enabled">
              <option :value="true">Active</option>
              <option :value="false">Disabled</option>
            </select>
          </FormField>
        </div>
        <div class="perm-block">
          <p class="section-label">Permissions</p>
          <div class="perm-chips">
            <label
              v-for="k in grantablePermissions"
              :key="k"
              class="perm-chip"
              :class="{ active: editForm.permissions.includes(k) }"
            >
              <input v-model="editForm.permissions" type="checkbox" :value="k" />
              {{ PERM_LABELS[k] || k }}
            </label>
          </div>
        </div>
        <footer class="modal__actions">
          <button type="button" class="btn btn-ghost" @click="showEdit = false">Cancel</button>
          <button type="button" class="btn btn-primary" :disabled="saving" @click="submitEdit">
            {{ saving ? 'Saving…' : 'Save' }}
          </button>
        </footer>
      </div>
    </div>

    <!-- View dialog -->
    <div v-if="showView && viewing" class="modal" role="dialog" aria-modal="true">
      <div class="modal__backdrop" @click="showView = false" />
      <div class="modal__panel modal__panel--narrow">
        <header class="modal__head">
          <h3>Team member details</h3>
          <button type="button" class="icon-btn" @click="showView = false">✕</button>
        </header>
        <dl class="detail-list">
          <div><dt>Name</dt><dd>{{ displayName(viewing) }}</dd></div>
          <div><dt>Email</dt><dd>{{ viewing.email }}</dd></div>
          <div><dt>Phone</dt><dd>{{ viewing.phoneNumber || '—' }}</dd></div>
          <div><dt>Role</dt><dd>{{ formatRole(viewing.role) }}</dd></div>
          <div><dt>Pay method</dt><dd>{{ formatPayMethod(viewing.rateUnit) }}</dd></div>
          <div><dt>Rate</dt><dd>{{ money(viewing.rateAmount) }}</dd></div>
          <div><dt>Target</dt><dd>{{ viewing.targetPeriod || '—' }} · {{ viewing.targetValue ?? '—' }}</dd></div>
          <div><dt>Bonus</dt><dd>{{ viewing.bonusPercentage != null ? `${viewing.bonusPercentage}%` : '—' }}</dd></div>
          <div><dt>Active</dt><dd>{{ viewing.enabled ? 'Yes' : 'No' }}</dd></div>
          <div><dt>Permissions</dt><dd>{{ viewing.owner ? 'All' : formatPerms(viewing.permissions) }}</dd></div>
        </dl>
        <footer class="modal__actions">
          <button type="button" class="btn btn-ghost" @click="showView = false">Close</button>
          <router-link
            v-if="!viewing.owner"
            class="btn btn-primary"
            :to="{ path: '/provider/staff-payments', query: { staff: viewing.id } }"
          >
            View payouts
          </router-link>
        </footer>
      </div>
    </div>

    <!-- Delete dialog -->
    <div v-if="showDelete && deleting" class="modal" role="dialog" aria-modal="true">
      <div class="modal__backdrop" @click="showDelete = false" />
      <div class="modal__panel modal__panel--narrow">
        <header class="modal__head">
          <h3>Remove employee?</h3>
          <button type="button" class="icon-btn" @click="showDelete = false">✕</button>
        </header>
        <p>
          This permanently deletes <strong>{{ displayName(deleting) }}</strong>, their permissions, and payroll marks. Only the owner can do this.
          Payroll history is kept.
        </p>
        <footer class="modal__actions">
          <button type="button" class="btn btn-ghost" @click="showDelete = false">Cancel</button>
          <button type="button" class="btn btn-danger" :disabled="saving" @click="confirmDelete">
            {{ saving ? 'Removing…' : 'Remove' }}
          </button>
        </footer>
      </div>
    </div>
  </div>
</template>

<style scoped>
.team-page {
  padding-bottom: 2.5rem;
}

.team-card__toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  margin-bottom: 1rem;
}

.team-card__toolbar h2 {
  margin: 0;
  font-family: var(--font-display);
  color: var(--color-canopy);
  border: none;
  padding: 0;
}

.team-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
  align-items: center;
}

.team-search {
  min-width: 14rem;
  padding: 0.5rem 0.75rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface);
}

.chip {
  display: inline-flex;
  align-items: center;
  padding: 0.12rem 0.45rem;
  border-radius: var(--radius-pill);
  font-size: 0.72rem;
  font-weight: 700;
  margin-left: 0.35rem;
}

.chip--owner {
  background: var(--color-wheat-soft);
  color: var(--color-earth);
}

.chip--ok {
  background: var(--color-success-bg);
  color: var(--color-success-text);
  margin-left: 0;
}

.chip--off {
  background: var(--color-danger-bg);
  color: var(--color-danger-text);
  margin-left: 0;
}

.perms-cell {
  max-width: 14rem;
  font-size: 0.82rem;
  color: var(--color-text-secondary);
}

.actions-cell {
  white-space: nowrap;
}

.icon-btn {
  border: 1px solid var(--color-border);
  background: var(--color-surface);
  border-radius: 8px;
  width: 2rem;
  height: 2rem;
  cursor: pointer;
}

.icon-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.icon-btn--danger {
  color: var(--color-danger-text);
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

.modal {
  position: fixed;
  inset: 0;
  z-index: 80;
  display: grid;
  place-items: center;
  padding: 1rem;
}

.modal__backdrop {
  position: absolute;
  inset: 0;
  background: rgba(28, 36, 24, 0.5);
  backdrop-filter: blur(3px);
}

.modal__panel {
  position: relative;
  width: min(560px, 100%);
  max-height: min(90vh, 44rem);
  overflow: auto;
  background: var(--color-surface-elevated);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  padding: 1.15rem 1.2rem 1rem;
}

.modal__panel--narrow {
  width: min(420px, 100%);
}

.modal__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  margin-bottom: 0.75rem;
}

.modal__head h3 {
  margin: 0;
  font-family: var(--font-display);
  color: var(--color-canopy);
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.75rem;
}

@media (max-width: 640px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}

.section-label {
  font-size: 0.78rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--color-muted);
  margin: 0.85rem 0 0.45rem;
}

.perm-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
}

.perm-chip {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.35rem 0.65rem;
  border-radius: var(--radius-pill);
  border: 1px solid var(--color-border);
  font-size: 0.8rem;
  cursor: pointer;
  background: var(--color-surface);
}

.perm-chip input {
  display: none;
}

.perm-chip.active {
  background: var(--color-sage-soft);
  border-color: var(--color-sage);
  color: var(--color-canopy);
  font-weight: 600;
}

.modal__actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.55rem;
  margin-top: 1rem;
  padding-top: 0.85rem;
  border-top: 1px solid var(--color-border);
}

.btn-danger {
  background: var(--color-danger-bg);
  color: var(--color-danger-text);
  border: 1px solid rgba(139, 44, 31, 0.25);
  border-radius: var(--radius-md);
  padding: 0.5rem 0.9rem;
  font-weight: 600;
  cursor: pointer;
}

.detail-list {
  margin: 0;
  display: grid;
  gap: 0.55rem;
}

.detail-list > div {
  display: grid;
  grid-template-columns: 7rem 1fr;
  gap: 0.5rem;
}

.detail-list dt {
  color: var(--color-muted);
  font-size: 0.82rem;
}

.detail-list dd {
  margin: 0;
}

.hint {
  background: var(--color-wheat-soft);
  border: 1px solid rgba(201, 162, 39, 0.35);
  border-radius: var(--radius-md);
  padding: 0.65rem 0.75rem;
  font-size: 0.9rem;
  margin-bottom: 0.75rem;
}
</style>
