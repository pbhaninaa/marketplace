<script setup>
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { adminSupportUsersApi } from '../services/marketplaceApi';
import { useAuthStore } from '../stores/auth';
import FormField from '../components/ui/FormField.vue';
import DataTableShell from '../components/ui/DataTableShell.vue';

const router = useRouter();
const auth = useAuthStore();

const loading = ref(true);
const error = ref('');
const message = ref('');
const users = ref([]);

const form = ref({ email: '', password: '' });

onMounted(async () => {
  auth.restoreFromStorage();
  if (!auth.isPlatformAdmin) {
    router.replace({ path: '/login', query: { redirect: '/admin/support-users' } });
    return;
  }
  await load();
});

async function load() {
  loading.value = true;
  error.value = '';
  message.value = '';
  try {
    const { data } = await adminSupportUsersApi.list();
    users.value = data || [];
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  } finally {
    loading.value = false;
  }
}

async function create() {
  error.value = '';
  message.value = '';
  try {
    await adminSupportUsersApi.create({
      email: form.value.email,
      password: form.value.password,
    });
    form.value = { email: '', password: '' };
    message.value = 'Support user created.';
    await load();
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  }
}
</script>

<template>
  <div class="page-document page-document--wide admin-page">
    <header class="page-hero">
      <p class="page-hero__eyebrow">Platform</p>
      <h1 class="page-hero__title">Support users</h1>
      <p class="page-hero__lead">Create support users that can help customers and providers.</p>
    </header>

    <p v-if="error" class="err-toast">{{ error }}</p>
    <p v-if="message" class="ok-msg">{{ message }}</p>
    <p v-if="loading" class="muted loading-line">Loading…</p>

    <template v-else>
      <section class="surface-panel admin-panel">
        <h2>Create support user</h2>
        <div class="grid">
          <FormField label="Email">
            <input v-model="form.email" type="email" maxlength="200" />
          </FormField>
          <FormField label="Temporary password">
            <input v-model="form.password" type="password" minlength="8" maxlength="100" />
          </FormField>
        </div>
        <button type="button" class="btn btn-primary" @click="create">Create</button>
      </section>

      <section class="surface-panel admin-panel">
        <div class="toolbar">
          <h2>Existing support users</h2>
          <button type="button" class="btn btn-ghost" @click="load">Refresh</button>
        </div>
        <DataTableShell caption="Support users">
          <thead>
            <tr>
              <th>Email</th>
              <th>Enabled</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="u in users" :key="u.id">
              <td>{{ u.email }}</td>
              <td>{{ u.enabled ? 'Yes' : 'No' }}</td>
            </tr>
            <tr v-if="!(users || []).length">
              <td colspan="2" class="muted small">No support users yet.</td>
            </tr>
          </tbody>
        </DataTableShell>
      </section>
    </template>
  </div>
</template>

<style scoped>
.grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
}
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 0.75rem;
}
@media (max-width: 980px) {
  .grid {
    grid-template-columns: 1fr;
  }
}
</style>

