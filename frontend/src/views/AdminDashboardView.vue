<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { adminDashboardApi } from '../services/marketplaceApi';
import { useAuthStore } from '../stores/auth';

const router = useRouter();
const auth = useAuthStore();

const loading = ref(false);
const stats = ref(null);

onMounted(() => {
  auth.restoreFromStorage();
  if (!auth.isPlatformAdmin) {
    router.replace({ path: '/login', query: { redirect: '/admin' } });
    return;
  }
  load();
});

async function load() {
  loading.value = true;
  try {
    const { data } = await adminDashboardApi.stats();
    stats.value = data;
  } catch {
    stats.value = null;
  } finally {
    loading.value = false;
  }
}

const activityScore = computed(() => {
  const purchaseCount = Number(stats.value?.purchaseCount ?? 0);
  const rentalCount = Number(stats.value?.rentalCount ?? 0);
  const n = purchaseCount + rentalCount;
  return Math.max(0, Math.min(100, Math.round((Math.log10(n + 1) / Math.log10(200 + 1)) * 100)));
});
</script>

<template>
  <div class="page-document page-document--wide admin-page">
    <header class="page-hero">
      <p class="page-hero__eyebrow">Platform</p>
      <h1 class="page-hero__title">Administration</h1>
      <p class="page-hero__lead">Progress overview and quick links to admin tools.</p>
    </header>

    <section class="surface-panel admin-panel admin-panel--wide">
      <div class="panel-head">
        <h2>Platform progress</h2>
        <button type="button" class="btn btn-ghost" :disabled="loading" @click="load">
          {{ loading ? 'Refreshing…' : 'Refresh' }}
        </button>
      </div>

      <div class="kpi-grid">
        <div class="kpi">
          <span class="kpi__label">Users created</span>
          <strong class="kpi__value">{{ stats?.usersCreated ?? '—' }}</strong>
          <span class="kpi__hint">In date range</span>
        </div>
        <div class="kpi">
          <span class="kpi__label">Providers created</span>
          <strong class="kpi__value">{{ stats?.providersCreated ?? '—' }}</strong>
          <span class="kpi__hint">Onboarding</span>
        </div>
        <div class="kpi">
          <span class="kpi__label">Listings created</span>
          <strong class="kpi__value">{{ stats?.listingsCreated ?? '—' }}</strong>
          <span class="kpi__hint">Supply growth</span>
        </div>
        <div class="kpi">
          <span class="kpi__label">Activity health</span>
          <strong class="kpi__value">{{ activityScore }}%</strong>
          <span class="kpi__hint">Signal from order volume</span>
        </div>
      </div>
    </section>

    <section class="surface-panel admin-panel admin-panel--wide">
      <h2>Admin tools</h2>
      <div class="tools-grid">
        <router-link to="/admin/providers" class="tool-card">
          <strong>Providers</strong>
          <span class="muted small">Approve & support providers</span>
        </router-link>
        <router-link to="/admin/listings" class="tool-card">
          <strong>Listings</strong>
          <span class="muted small">Publish/unpublish & cleanup</span>
        </router-link>
        <router-link to="/admin/users" class="tool-card">
          <strong>Users</strong>
          <span class="muted small">Search and disable accounts</span>
        </router-link>
        <router-link to="/admin/support-users" class="tool-card">
          <strong>Support users</strong>
          <span class="muted small">Create and manage support accounts</span>
        </router-link>
        <router-link to="/admin/password" class="tool-card">
          <strong>Password</strong>
          <span class="muted small">Change your admin password</span>
        </router-link>
        <router-link to="/admin/maintenance" class="tool-card tool-card--danger">
          <strong>Maintenance</strong>
          <span class="muted small">Danger zone operations</span>
        </router-link>
      </div>
    </section>
  </div>
</template>

<style scoped>
.admin-page {
  padding: 0.5rem 0 2rem;
}

.admin-panel {
  max-width: 560px;
  margin: 0 auto 1.35rem;
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.admin-panel--wide {
  max-width: 980px;
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 0.5rem;
}

.admin-panel .muted {
  margin: 0 0 0.5rem;
}

.admin-panel h2 {
  font-family: var(--font-display);
}

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0.85rem;
  margin-top: 0.75rem;
}

.kpi {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 0.85rem 0.95rem;
  background: var(--color-surface-elevated);
  box-shadow: var(--shadow-sm);
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.kpi__label {
  font-size: 0.78rem;
  letter-spacing: 0.02em;
  color: var(--color-muted);
}

.kpi__value {
  font-family: var(--font-display);
  font-size: 1.2rem;
}

.kpi__hint {
  font-size: 0.82rem;
  color: var(--color-muted);
}

.tools-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-4);
  margin-top: var(--space-3);
}

.tool-card {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: var(--space-5);
  background: var(--color-surface-elevated);
  box-shadow: var(--shadow-sm);
  text-decoration: none;
  color: var(--color-canopy);
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.tool-card:hover {
  border-color: rgba(61, 122, 102, 0.28);
  box-shadow: var(--shadow-md);
}

.tool-card--danger {
  border-color: rgba(180, 40, 40, 0.22);
  background: rgba(252, 239, 234, 0.6);
}

@media (max-width: 980px) {
  .kpi-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .tools-grid {
    grid-template-columns: 1fr;
  }
}
</style>
