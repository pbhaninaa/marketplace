<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { supportApi } from '../services/marketplaceApi';
import { useAuthStore } from '../stores/auth';
import FormField from '../components/ui/FormField.vue';
import DataTableShell from '../components/ui/DataTableShell.vue';
import TablePager from '../components/ui/TablePager.vue';
import TextWithTooltip from '../components/ui/TextWithTooltip.vue';

const router = useRouter();
const auth = useAuthStore();

const loading = ref(true);
const error = ref('');
const message = ref('');
const q = ref('');
const users = ref([]);
const shadowing = ref(false);

const PAGE_SIZE = 5;
const page = ref(1);
const pageCount = computed(() => Math.max(1, Math.ceil((users.value || []).length / PAGE_SIZE)));
const paged = computed(() => (users.value || []).slice((page.value - 1) * PAGE_SIZE, page.value * PAGE_SIZE));

onMounted(async () => {
  auth.restoreFromStorage();
  if (!auth.isSupport && !auth.isPlatformAdmin) {
    router.replace({ path: '/login', query: { redirect: '/support/users' } });
    return;
  }
  await load();
});

async function load() {
  loading.value = true;
  error.value = '';
  try {
    const { data } = await supportApi.getUsers();
    users.value = data || [];
    page.value = 1;
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  } finally {
    loading.value = false;
  }
}

async function search() {
  message.value = '';
  error.value = '';
  try {
    const { data } = await supportApi.searchUsers({ q: q.value });
    users.value = data || [];
    page.value = 1;
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  }
}

async function shadowProvider(providerId) {
  if (!providerId) return;
  shadowing.value = true;
  message.value = '';
  error.value = '';
  try {
    const { data } = await supportApi.shadowProvider(providerId);
    auth.beginShadow(data);
    router.push('/provider');
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  } finally {
    shadowing.value = false;
  }
}
</script>

<template>
  <div class="page-document page-document--wide admin-page">
    <header class="page-hero">
      <p class="page-hero__eyebrow">Support</p>
      <h1 class="page-hero__title">Users</h1>
      <p class="page-hero__lead">Search users and start provider shadow sessions.</p>
    </header>

    <p v-if="error" class="err-toast">{{ error }}</p>
    <p v-if="message" class="ok-msg">{{ message }}</p>
    <p v-if="loading" class="muted loading-line">Loading…</p>

    <section v-else class="surface-panel admin-panel">
      <div class="toolbar">
        <div class="toolbar-left">
          <button type="button" class="btn btn-ghost" @click="load">Refresh</button>
          <FormField label="">
            <input v-model="q" type="text" placeholder="Search by email…" style="min-width: 220px" />
          </FormField>
          <button type="button" class="btn btn-primary" @click="search">Search</button>
        </div>
        <TablePager v-model:page="page" :page-count="pageCount" />
      </div>

      <DataTableShell caption="Users">
        <thead>
          <tr>
            <th>Email</th>
            <th>Role</th>
            <th>Enabled</th>
            <th>Provider</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="u in paged" :key="u.id">
            <td><TextWithTooltip class="cell-text" :text="u.email" :max-length="24" /></td>
            <td><TextWithTooltip class="cell-text" :text="u.role" :max-length="18" /></td>
            <td>{{ u.enabled ? 'Yes' : 'No' }}</td>
            <td>{{ u.providerId ?? '—' }}</td>
            <td class="cell-actions">
              <button
                v-if="u.providerId"
                type="button"
                class="btn btn-ghost btn-xs"
                :disabled="shadowing"
                @click="shadowProvider(u.providerId)"
              >
                Shadow provider
              </button>
            </td>
          </tr>
          <tr v-if="!paged.length">
            <td colspan="5" class="muted small">No users.</td>
          </tr>
        </tbody>
      </DataTableShell>
    </section>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 0.75rem;
  flex-wrap: wrap;
}
.toolbar-left {
  display: flex;
  align-items: flex-end;
  gap: 0.6rem;
  flex-wrap: wrap;
}
</style>

