<script setup>
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { adminProvidersApi } from '../services/marketplaceApi';
import { useAuthStore } from '../stores/auth';
import DataTableShell from '../components/ui/DataTableShell.vue';
import FormField from '../components/ui/FormField.vue';

const router = useRouter();
const auth = useAuthStore();

const loading = ref(true);
const error = ref('');
const message = ref('');

const providers = ref({ content: [], totalElements: 0 });
const page = ref(0);

const statuses = ['PENDING', 'ACTIVE', 'SUSPENDED'];

onMounted(async () => {
  auth.restoreFromStorage();
  if (!auth.isPlatformAdmin) {
    router.replace({ path: '/login', query: { redirect: '/admin/providers' } });
    return;
  }
  await load();
});

async function load() {
  loading.value = true;
  error.value = '';
  message.value = '';
  try {
    const { data } = await adminProvidersApi.list({ page: page.value, size: 50 });
    providers.value = data;
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  } finally {
    loading.value = false;
  }
}

async function updateStatus(p, status) {
  message.value = '';
  error.value = '';
  try {
    await adminProvidersApi.setStatus(p.id, status);
    message.value = `Updated ${p.name} → ${status}.`;
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
      <h1 class="page-hero__title">Providers</h1>
      <p class="page-hero__lead">Approve, activate or suspend providers.</p>
    </header>

    <p v-if="error" class="err-toast">{{ error }}</p>
    <p v-if="message" class="ok-msg">{{ message }}</p>
    <p v-if="loading" class="muted loading-line">Loading…</p>

    <section v-else class="surface-panel admin-panel">
      <div class="toolbar">
        <button type="button" class="btn btn-ghost" @click="load">Refresh</button>
        <div class="pager">
          <button type="button" class="btn btn-ghost" :disabled="page === 0" @click="page--; load()">Prev</button>
          <span class="muted small">Page {{ page + 1 }}</span>
          <button
            type="button"
            class="btn btn-ghost"
            :disabled="(providers.content || []).length < 50"
            @click="page++; load()"
          >
            Next
          </button>
        </div>
      </div>

      <DataTableShell caption="Providers">
        <thead>
          <tr>
            <th>ID</th>
            <th>Name</th>
            <th>Location</th>
            <th>Status</th>
            <th class="col-actions">Actions</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="p in providers.content" :key="p.id">
            <td>{{ p.id }}</td>
            <td>
              <div class="cell-stack">
                <strong>{{ p.name }}</strong>
                <span class="muted small">{{ p.slug }}</span>
              </div>
            </td>
            <td>{{ p.location || '—' }}</td>
            <td>
              <FormField label="">
                <select :value="p.status" @change="updateStatus(p, $event.target.value)">
                  <option v-for="s in statuses" :key="s" :value="s">{{ s }}</option>
                </select>
              </FormField>
            </td>
            <td class="col-actions">
              <router-link class="btn btn-ghost" :to="`/admin/providers/${p.id}`">Support</router-link>
              <button type="button" class="btn btn-ghost" @click="updateStatus(p, 'ACTIVE')">Activate</button>
              <button type="button" class="btn btn-ghost" @click="updateStatus(p, 'SUSPENDED')">Suspend</button>
            </td>
          </tr>
          <tr v-if="!(providers.content || []).length">
            <td colspan="5" class="muted small">No providers yet.</td>
          </tr>
        </tbody>
      </DataTableShell>
    </section>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 0.75rem;
}
.pager {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
}
.col-actions {
  text-align: right;
}
.cell-stack {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}
</style>

