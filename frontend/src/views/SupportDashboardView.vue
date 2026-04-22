<script setup>
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { api } from '../api';
import { useAuthStore } from '../stores/auth';
import FormField from '../components/ui/FormField.vue';
import DataTableShell from '../components/ui/DataTableShell.vue';
import { isNonEmptyString } from '../utils/validation';

const router = useRouter();
const auth = useAuthStore();

const overview = ref(null);
const users = ref([]);
const tickets = ref([]);
const q = ref('');
const otpTarget = ref('');

const error = ref('');
const message = ref('');

onMounted(async () => {
  auth.restoreFromStorage();
  if (!auth.isSupport && !auth.isPlatformAdmin) {
    router.replace({ path: '/login', query: { redirect: '/support' } });
    return;
  }
  await refreshAll();
});

async function refreshAll() {
  error.value = '';
  try {
    const [o, t, u] = await Promise.all([
      api.get('/api/support/overview'),
      api.get('/api/support/tickets'),
      api.get('/api/support/users'),
    ]);
    overview.value = o.data;
    tickets.value = t.data;
    users.value = u.data;
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  }
}

async function searchUsers() {
  message.value = '';
  error.value = '';
  try {
    const { data } = await api.get('/api/support/users', { params: { q: q.value } });
    users.value = data;
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  }
}

async function resendOtp() {
  message.value = '';
  error.value = '';
  if (!isNonEmptyString(otpTarget.value)) {
    error.value = 'Please enter a client email or phone.';
    return;
  }
  try {
    await api.post('/api/support/client/otp/resend', { target: otpTarget.value.trim() });
    message.value = 'OTP re-issued (check logs in local dev).';
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  }
}
</script>

<template>
  <div class="page-document page-document--wide admin-page">
    <header class="page-hero">
      <p class="page-hero__eyebrow">Support</p>
      <h1 class="page-hero__title">Dashboard</h1>
      <p class="page-hero__lead">Read-only tools, plus client OTP resend.</p>
    </header>

    <p v-if="error" class="err-toast">{{ error }}</p>
    <p v-if="message" class="ok-msg">{{ message }}</p>

    <section class="surface-panel admin-panel">
      <h2>Overview</h2>
      <div v-if="overview" class="kv-grid">
        <div><span class="muted small">Users</span><strong>{{ overview.userCount }}</strong></div>
        <div><span class="muted small">Providers</span><strong>{{ overview.providerCount }}</strong></div>
        <div><span class="muted small">Orders</span><strong>{{ overview.purchaseOrderCount }}</strong></div>
        <div><span class="muted small">Open tickets</span><strong>{{ overview.openTickets }}</strong></div>
      </div>
      <button type="button" class="btn btn-ghost" @click="refreshAll">Refresh</button>
    </section>

    <section class="surface-panel admin-panel">
      <h2>Resend client OTP</h2>
      <FormField label="Client email / phone">
        <input v-model="otpTarget" type="text" />
      </FormField>
      <button type="button" class="btn btn-primary" @click="resendOtp">Resend OTP</button>
    </section>

    <section class="surface-panel admin-panel">
      <h2>User search</h2>
      <FormField label="Search by email">
        <input v-model="q" type="text" placeholder="e.g. @gmail.com" />
      </FormField>
      <button type="button" class="btn btn-primary" @click="searchUsers">Search</button>

      <DataTableShell caption="Users">
        <thead>
          <tr>
            <th>ID</th>
            <th>Email</th>
            <th>Role</th>
            <th>Enabled</th>
            <th>Provider</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="u in users" :key="u.id">
            <td>{{ u.id }}</td>
            <td>{{ u.email }}</td>
            <td>{{ u.role }}</td>
            <td>{{ u.enabled ? 'Yes' : 'No' }}</td>
            <td>{{ u.providerId ?? '—' }}</td>
          </tr>
        </tbody>
      </DataTableShell>
    </section>

    <section class="surface-panel admin-panel">
      <h2>Tickets</h2>
      <DataTableShell caption="Tickets">
        <thead>
          <tr>
            <th>ID</th>
            <th>Status</th>
            <th>Subject</th>
            <th>Created</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="t in tickets" :key="t.id">
            <td>{{ t.id }}</td>
            <td>{{ t.status }}</td>
            <td>{{ t.subject }}</td>
            <td class="small muted">{{ t.createdAt?.slice(0, 19) }}</td>
          </tr>
        </tbody>
      </DataTableShell>
    </section>
  </div>
</template>

<style scoped>
.admin-page {
  padding: 0.5rem 0 2rem;
}

.admin-panel {
  margin-top: 1.35rem;
}

.admin-panel h2 {
  font-family: var(--font-display);
}

.kv-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0.75rem;
  margin: 0.75rem 0 1rem;
}

.kv-grid > div {
  background: rgba(21, 74, 122, 0.04);
  border: 1px solid rgba(21, 74, 122, 0.12);
  border-radius: 10px;
  padding: 0.75rem 0.85rem;
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

@media (max-width: 900px) {
  .kv-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>

