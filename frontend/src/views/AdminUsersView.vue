<script setup>
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { adminUsersApi } from '../services/marketplaceApi';
import { useAuthStore } from '../stores/auth';
import DataTableShell from '../components/ui/DataTableShell.vue';
import TextWithTooltip from '../components/ui/TextWithTooltip.vue';
import ResponsiveRecordShell from '../components/layout/ResponsiveRecordShell.vue';

const router = useRouter();
const auth = useAuthStore();

const loading = ref(true);
const error = ref('');
const message = ref('');
const page = ref(0);
const rows = ref({ content: [] });

onMounted(async () => {
  auth.restoreFromStorage();
  if (!auth.isPlatformAdmin) {
    router.replace({ path: '/login', query: { redirect: '/admin/users' } });
    return;
  }
  await load();
});

async function load() {
  loading.value = true;
  error.value = '';
  message.value = '';
  try {
    const { data } = await adminUsersApi.list({ page: page.value, size: 50 });
    rows.value = data;
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  } finally {
    loading.value = false;
  }
}

async function remove(u) {
  error.value = '';
  message.value = '';
  try {
    await adminUsersApi.remove(u.id);
    message.value = 'User deleted (disabled).';
    await load();
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  }
}

async function removeAll() {
  error.value = '';
  message.value = '';
  try {
    const { data } = await adminUsersApi.removeAll();
    const n = data?.disabled ?? 0;
    message.value = `Disabled ${n} users (kept only your admin enabled).`;
    page.value = 0;
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
      <h1 class="page-hero__title">Users</h1>
      <p class="page-hero__lead">All users in the system. Delete disables accounts (safe).</p>
    </header>

    <p v-if="error" class="err-toast">{{ error }}</p>
    <p v-if="message" class="ok-msg">{{ message }}</p>
    <p v-if="loading" class="muted loading-line">Loading…</p>

    <section v-else class="surface-panel admin-panel">
      <div class="toolbar">
        <div class="toolbar-left">
          <button type="button" class="btn btn-ghost" @click="load">Refresh</button>
          <button type="button" class="btn btn-ghost btn-danger" @click="removeAll">Delete all</button>
        </div>
        <div class="pager">
          <button type="button" class="btn btn-ghost" :disabled="page === 0" @click="page--; load()">Prev</button>
          <span class="muted small">Page {{ page + 1 }}</span>
          <button
            type="button"
            class="btn btn-ghost"
            :disabled="(rows.content || []).length < 50"
            @click="page++; load()"
          >
            Next
          </button>
        </div>
      </div>

      <ResponsiveRecordShell desktop-label="All users">
        <template #desktop>
          <DataTableShell caption="All users">
            <thead>
              <tr>
                <th>Email</th>
                <th>Role</th>
                <th>Provider</th>
                <th>Enabled</th>
                <th class="col-actions"></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="u in rows.content" :key="u.id">
                <td>
                  <div class="cell-stack">
                    <strong>{{ u.email }}</strong>
                    <span class="muted small">{{ u.displayName || '—' }}</span>
                  </div>
                </td>
                <td>{{ u.role }}</td>
                <td class="small muted">{{ u.providerName ? `${u.providerName} (#${u.providerId})` : '—' }}</td>
                <td>{{ u.enabled ? 'Yes' : 'No' }}</td>
                <td class="cell-actions">
                  <button type="button" class="btn btn-ghost btn-danger" :disabled="!u.enabled" @click="remove(u)">
                    Delete
                  </button>
                </td>
              </tr>
              <tr v-if="!(rows.content || []).length">
                <td colspan="5" class="muted small">No users.</td>
              </tr>
            </tbody>
          </DataTableShell>
        </template>
        <template #mobile>
          <div class="cards">
            <article v-for="u in rows.content" :key="u.id" class="record-card">
              <strong class="title">
                <TextWithTooltip :text="u.email" />
              </strong>
              <span class="meta">
                <TextWithTooltip :text="u.displayName || '—'" />
              </span>
              <span class="meta">Role: {{ u.role }}</span>
              <span class="meta">Provider: <TextWithTooltip :text="u.providerName ? `${u.providerName} (#${u.providerId})` : '—'" /></span>
              <span class="meta">{{ u.enabled ? 'Enabled' : 'Disabled' }}</span>
              <button type="button" class="btn btn-ghost btn-danger" :disabled="!u.enabled" @click="remove(u)">
                Delete
              </button>
            </article>
          </div>
        </template>
      </ResponsiveRecordShell>
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
.toolbar-left {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
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
.btn-danger {
  border-color: rgba(180, 40, 40, 0.35);
  color: rgba(140, 20, 20, 0.95);
}
</style>

