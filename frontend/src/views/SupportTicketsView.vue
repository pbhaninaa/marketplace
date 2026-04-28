<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { supportApi } from '../services/marketplaceApi';
import { useAuthStore } from '../stores/auth';
import DataTableShell from '../components/ui/DataTableShell.vue';
import TablePager from '../components/ui/TablePager.vue';
import TextWithTooltip from '../components/ui/TextWithTooltip.vue';

const router = useRouter();
const auth = useAuthStore();

const loading = ref(true);
const error = ref('');
const tickets = ref([]);

const PAGE_SIZE = 5;
const page = ref(1);
const pageCount = computed(() => Math.max(1, Math.ceil((tickets.value || []).length / PAGE_SIZE)));
const paged = computed(() => (tickets.value || []).slice((page.value - 1) * PAGE_SIZE, page.value * PAGE_SIZE));

onMounted(async () => {
  auth.restoreFromStorage();
  if (!auth.isSupport && !auth.isPlatformAdmin) {
    router.replace({ path: '/login', query: { redirect: '/support/tickets' } });
    return;
  }
  await load();
});

async function load() {
  loading.value = true;
  error.value = '';
  try {
    const { data } = await supportApi.getTickets();
    tickets.value = data || [];
    page.value = 1;
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="page-document page-document--wide admin-page">
    <header class="page-hero">
      <p class="page-hero__eyebrow">Support</p>
      <h1 class="page-hero__title">Tickets</h1>
      <p class="page-hero__lead">Review support tickets and track progress.</p>
    </header>

    <p v-if="error" class="err-toast">{{ error }}</p>
    <p v-if="loading" class="muted loading-line">Loading…</p>

    <section v-else class="surface-panel admin-panel">
      <div class="toolbar">
        <button type="button" class="btn btn-ghost" @click="load">Refresh</button>
        <TablePager v-model:page="page" :page-count="pageCount" />
      </div>

      <DataTableShell caption="Tickets">
        <thead>
          <tr>
            <th>Status</th>
            <th>Subject</th>
            <th>Created</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="t in paged" :key="t.id">
            <td><TextWithTooltip class="cell-text" :text="t.status || '—'" :max-length="18" /></td>
            <td><TextWithTooltip class="cell-text" :text="t.subject || '—'" :max-length="26" /></td>
            <td class="small muted">{{ t.createdAt?.slice(0, 19) }}</td>
          </tr>
          <tr v-if="!paged.length">
            <td colspan="3" class="muted small">No tickets.</td>
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
</style>

