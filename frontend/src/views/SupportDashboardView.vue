<script setup>
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { supportApi } from '../services/marketplaceApi';
import { useAuthStore } from '../stores/auth';

const router = useRouter();
const auth = useAuthStore();

const overview = ref(null);

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
    const [o] = await Promise.all([
      supportApi.getOverview(),
    ]);
    overview.value = o.data;
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
      <p class="page-hero__lead">Progress overview and quick links to support tools.</p>
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
      <h2>Progress</h2>
      <div class="progress-grid">
        <div class="progress-card">
          <div class="progress-card__top">
            <strong>Ticket load</strong>
            <span class="muted small">{{ (overview?.openTickets ?? 0) }} open</span>
          </div>
          <div class="bar">
            <div class="bar__fill bar__fill--warn" :style="{ width: `${Math.min(100, (Number(overview?.openTickets ?? 0) / 50) * 100)}%` }" />
          </div>
          <p class="muted small">Scale is relative (0–50 open tickets).</p>
        </div>
        <div class="progress-card">
          <div class="progress-card__top">
            <strong>Shadow sessions</strong>
            <span class="muted small">{{ auth.isShadowing ? 'Active' : 'Idle' }}</span>
          </div>
          <div class="bar">
            <div class="bar__fill" :style="{ width: `${auth.isShadowing ? 100 : 25}%` }" />
          </div>
          <p class="muted small">Shadow a provider to support from their perspective.</p>
        </div>
      </div>
    </section>

    <section class="surface-panel admin-panel">
      <h2>Support tools</h2>
      <div class="tools-grid">
        <router-link to="/support/users" class="tool-card">
          <strong>Users</strong>
          <span class="muted small">Search users & shadow providers</span>
        </router-link>
        <router-link to="/support/tickets" class="tool-card">
          <strong>Tickets</strong>
          <span class="muted small">Review support tickets</span>
        </router-link>
        <router-link to="/support/otp" class="tool-card">
          <strong>Client OTP</strong>
          <span class="muted small">Resend OTP to client</span>
        </router-link>
        <router-link to="/support/order-invoice" class="tool-card">
          <strong>Order invoice</strong>
          <span class="muted small">Download by order id or verification code</span>
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

.progress-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.85rem;
  margin-top: 0.5rem;
}

.progress-card {
  border: 1px solid rgba(21, 74, 122, 0.12);
  background: rgba(21, 74, 122, 0.04);
  border-radius: var(--radius-lg);
  padding: 0.9rem 0.95rem;
}

.progress-card__top {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 0.75rem;
  margin-bottom: 0.5rem;
}

.bar {
  height: 10px;
  border-radius: 999px;
  background: rgba(21, 74, 122, 0.1);
  overflow: hidden;
  border: 1px solid rgba(21, 74, 122, 0.12);
}

.bar__fill {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, var(--color-sale) 0%, var(--color-info-text) 100%);
  transition: width 0.25s var(--ease-out);
}

.bar__fill--warn {
  background: linear-gradient(90deg, var(--color-wheat) 0%, var(--color-earth) 100%);
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

@media (max-width: 900px) {
  .kv-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .progress-grid {
    grid-template-columns: 1fr;
  }
  .tools-grid {
    grid-template-columns: 1fr;
  }
}
</style>

