<script setup>
import { ref, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { adminProviderDetailApi } from '../services/marketplaceApi';
import { useAuthStore } from '../stores/auth';
import DataTableShell from '../components/ui/DataTableShell.vue';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();

const providerId = Number(route.params.id);

const loading = ref(true);
const error = ref('');
const message = ref('');

const provider = ref(null);
const listings = ref([]);
const staff = ref([]);

onMounted(async () => {
  auth.restoreFromStorage();
  if (!auth.isPlatformAdmin) {
    router.replace({ path: '/login', query: { redirect: `/admin/providers/${providerId}` } });
    return;
  }
  await loadAll();
});

async function loadAll() {
  loading.value = true;
  error.value = '';
  message.value = '';
  try {
    const [p, l, s] = await Promise.all([
      adminProviderDetailApi.getProvider(providerId),
      adminProviderDetailApi.getListings(providerId),
      adminProviderDetailApi.getStaff(providerId),
    ]);
    provider.value = p.data;
    listings.value = l.data || [];
    staff.value = s.data || [];
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  } finally {
    loading.value = false;
  }
}

async function deleteListing(listingId) {
  error.value = '';
  message.value = '';
  try {
    await adminProviderDetailApi.deleteListing(providerId, listingId);
    message.value = 'Listing removed.';
    await loadAll();
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  }
}

async function disableStaff(userId) {
  error.value = '';
  message.value = '';
  try {
    await adminProviderDetailApi.disableStaff(providerId, userId);
    message.value = 'Staff user disabled.';
    await loadAll();
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  }
}
</script>

<template>
  <div class="page-document page-document--wide admin-page">
    <header class="page-hero">
      <p class="page-hero__eyebrow">Platform</p>
      <h1 class="page-hero__title">Provider support</h1>
      <p class="page-hero__lead">View provider listings and staff, and take quick actions to support them.</p>
    </header>

    <p v-if="error" class="err-toast">{{ error }}</p>
    <p v-if="message" class="ok-msg">{{ message }}</p>
    <p v-if="loading" class="muted loading-line">Loading…</p>

    <template v-else>
      <section class="surface-panel admin-panel">
        <h2>Provider</h2>
        <div class="kv">
          <div><span class="k">ID</span><span class="v">{{ provider?.id }}</span></div>
          <div><span class="k">Name</span><span class="v">{{ provider?.name }}</span></div>
          <div><span class="k">Slug</span><span class="v">{{ provider?.slug }}</span></div>
          <div><span class="k">Location</span><span class="v">{{ provider?.location || '—' }}</span></div>
          <div><span class="k">Status</span><span class="v">{{ provider?.status }}</span></div>
        </div>
      </section>

      <section class="surface-panel admin-panel">
        <div class="toolbar">
          <h2>Listings</h2>
          <button type="button" class="btn btn-ghost" @click="loadAll">Refresh</button>
        </div>
        <DataTableShell caption="Provider listings">
          <thead>
            <tr>
              <th>ID</th>
              <th>Type</th>
              <th>Title</th>
              <th>Active</th>
              <th class="col-actions"></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="l in listings" :key="l.id">
              <td>{{ l.id }}</td>
              <td>{{ l.listingType }}</td>
              <td>
                <strong>{{ l.title }}</strong>
                <div class="tiny muted clamp">Images: {{ l.imageUrls || '—' }}</div>
              </td>
              <td>{{ l.active ? 'Yes' : 'No' }}</td>
              <td class="col-actions">
                <button type="button" class="btn btn-ghost" @click="deleteListing(l.id)">Delete</button>
              </td>
            </tr>
            <tr v-if="!(listings || []).length">
              <td colspan="5" class="muted small">No listings.</td>
            </tr>
          </tbody>
        </DataTableShell>
      </section>

      <section class="surface-panel admin-panel">
        <h2>Staff</h2>
        <DataTableShell caption="Provider staff">
          <thead>
            <tr>
              <th>ID</th>
              <th>Email</th>
              <th>Role</th>
              <th>Enabled</th>
              <th class="col-actions"></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="u in staff" :key="u.id">
              <td>{{ u.id }}</td>
              <td>
                <strong>{{ u.email }}</strong>
                <div class="tiny muted clamp">Perms: {{ (u.permissions || []).join(', ') || '—' }}</div>
              </td>
              <td>{{ u.role }}</td>
              <td>{{ u.enabled ? 'Yes' : 'No' }}</td>
              <td class="col-actions">
                <button type="button" class="btn btn-ghost" :disabled="u.owner || !u.enabled" @click="disableStaff(u.id)">
                  Disable
                </button>
              </td>
            </tr>
            <tr v-if="!(staff || []).length">
              <td colspan="5" class="muted small">No staff users.</td>
            </tr>
          </tbody>
        </DataTableShell>
      </section>
    </template>
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
.col-actions {
  text-align: right;
}
.kv {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.5rem 1.25rem;
}
.kv .k {
  display: inline-block;
  min-width: 82px;
  color: var(--color-muted);
  font-size: 0.85rem;
}
.kv .v {
  font-weight: 700;
}
.clamp {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
@media (max-width: 980px) {
  .kv {
    grid-template-columns: 1fr;
  }
}
</style>

