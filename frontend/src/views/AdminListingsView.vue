<script setup>
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { adminListingsApi } from '../services/marketplaceApi';
import { useAuthStore } from '../stores/auth';
import DataTableShell from '../components/ui/DataTableShell.vue';
import FormField from '../components/ui/FormField.vue';
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
    router.replace({ path: '/login', query: { redirect: '/admin/listings' } });
    return;
  }
  await load();
});

async function load() {
  loading.value = true;
  error.value = '';
  message.value = '';
  try {
    const { data } = await adminListingsApi.list({ page: page.value, size: 50 });
    rows.value = data;
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  } finally {
    loading.value = false;
  }
}

async function setActive(l, active) {
  error.value = '';
  message.value = '';
  try {
    await adminListingsApi.setActive(l.id, active);
    message.value = active ? 'Listing published.' : 'Listing unpublished.';
    await load();
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  }
}

async function remove(l) {
  error.value = '';
  message.value = '';
  try {
    await adminListingsApi.remove(l.id);
    message.value = 'Listing deleted.';
    await load();
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  }
}

async function removeAll() {
  error.value = '';
  message.value = '';
  try {
    const { data } = await adminListingsApi.removeAll();
    const n = data?.deleted ?? 0;
    message.value = `Deleted ${n} listings. Any listing used in orders/bookings was unpublished instead.`;
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
      <h1 class="page-hero__title">Listings</h1>
      <p class="page-hero__lead">All marketplace listings across all providers.</p>
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

      <ResponsiveRecordShell desktop-label="All listings">
        <template #desktop>
          <DataTableShell caption="All listings">
            <thead>
              <tr>
                <th>ID</th>
                <th>Provider</th>
                <th>Category</th>
                <th>Type</th>
                <th>Title</th>
                <th>Active</th>
                <th class="col-actions">Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="l in rows.content" :key="l.id">
                <td>{{ l.id }}</td>
                <td>
                  <div class="cell-stack">
                    <strong>{{ l.providerName }}</strong>
                    <span class="muted small">#{{ l.providerId }} · {{ l.providerLocation }}</span>
                  </div>
                </td>
                <td>{{ l.categoryName }}</td>
                <td>{{ l.listingType }}</td>
                <!-- <td> 
                  <strong>{{ l.title }}</strong>
                  <div class="tiny muted clamp">Images: {{ l.imageUrls || '—' }}</div>
                </td>-->
                <td>
                  <FormField label="">
                    <select :value="l.active ? 'YES' : 'NO'" @change="setActive(l, $event.target.value === 'YES')">
                      <option value="YES">Yes</option>
                      <option value="NO">No</option>
                    </select>
                  </FormField>
                </td>
                <td class="col-actions">
                  <button v-if="l.active" type="button" class="btn btn-ghost" @click="setActive(l, false)">
                    Unpublish
                  </button>
                  <button v-else type="button" class="btn btn-ghost" @click="setActive(l, true)">Publish</button>
                  <button type="button" class="btn btn-ghost btn-danger" @click="remove(l)">Delete</button>
                </td>
              </tr>
              <tr v-if="!(rows.content || []).length">
                <td colspan="7" class="muted small">No listings.</td>
              </tr>
            </tbody>
          </DataTableShell>
        </template>
        <template #mobile>
          <div class="cards">
            <article v-for="l in rows.content" :key="l.id" class="record-card">
              <strong>{{ l.title }}</strong>
              <span class="meta">#{{ l.id }} · {{ l.listingType }} · {{ l.active ? 'Active' : 'Draft' }}</span>
              <span class="meta">Provider: {{ l.providerName }} (#{{ l.providerId }})</span>
              <span class="meta">Category: {{ l.categoryName }}</span>
              <span class="meta tiny clamp">Images: {{ l.imageUrls || '—' }}</span>
              <div class="card-actions">
                <button v-if="l.active" type="button" class="btn btn-ghost" @click="setActive(l, false)">Unpublish</button>
                <button v-else type="button" class="btn btn-ghost" @click="setActive(l, true)">Publish</button>
                <button type="button" class="btn btn-ghost btn-danger" @click="remove(l)">Delete</button>
              </div>
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
.tiny {
  font-size: 0.78rem;
}
.card-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin-top: 0.25rem;
}
.btn-danger {
  border-color: rgba(180, 40, 40, 0.35);
  color: rgba(140, 20, 20, 0.95);
}
.clamp {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>

