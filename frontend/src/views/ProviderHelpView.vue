<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '../stores/auth';
import { providerDashboardApi, providerSubscriptionApi } from '../services/marketplaceApi';

const router = useRouter();
const auth = useAuthStore();

const loading = ref(true);
const error = ref('');
const stats = ref(null);
const subscription = ref(null);

const plan = computed(() => String(subscription.value?.plan || '').toUpperCase() || '—');
const isActive = computed(() => !!subscription.value?.valid);
const isPremium = computed(() => String(subscription.value?.plan || '').toUpperCase() === 'PREMIUM');

const purchaseCount = computed(() => Number(stats.value?.purchasesCount ?? 0));
const rentalCount = computed(() => Number(stats.value?.rentalsCount ?? 0));
const totalOrders = computed(() => purchaseCount.value + rentalCount.value);
const listingsCount = computed(() => Number(stats.value?.listingsCount ?? 0));

onMounted(async () => {
  auth.restoreFromStorage();
  if (!auth.isProviderUser) {
    router.replace({ path: '/login', query: { redirect: '/provider/help' } });
    return;
  }
  await load();
});

async function load() {
  loading.value = true;
  error.value = '';
  try {
    const [{ data: sub }, { data: st }] = await Promise.all([
      providerSubscriptionApi.status().catch(() => ({ data: null })),
      providerDashboardApi.stats().catch(() => ({ data: null })),
    ]);
    subscription.value = sub;
    auth.setProviderSubscriptionStatus(sub);
    stats.value = st;
  } catch (e) {
    error.value = e.response?.data?.message || e.message || 'Failed to load help context.';
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="page-document page-document--wide help-page">
    <header class="page-hero">
      <p class="page-hero__eyebrow">Provider</p>
      <h1 class="page-hero__title">Help & guides</h1>
      <p class="page-hero__lead">
        Quick steps and tips based on your current plan and activity.
      </p>
    </header>

    <p v-if="error" class="err-toast">{{ error }}</p>
    <p v-if="loading" class="muted loading-line">Loading…</p>

    <template v-else>
      <section class="surface-panel help-panel">
        <div class="panel-head">
          <div>
            <h2>Your status</h2>
            <p class="muted small">
              Plan: <strong>{{ plan }}</strong> ·
              Subscription: <strong>{{ isActive ? 'Active' : 'Not active' }}</strong> ·
              Orders: <strong>{{ totalOrders }}</strong> ·
              Listings: <strong>{{ listingsCount }}</strong>
            </p>
          </div>
          <button type="button" class="btn btn-ghost" :disabled="loading" @click="load">
            Refresh
          </button>
        </div>

        <div v-if="!isActive" class="callout callout--warn">
          <strong>Subscription required</strong>
          <p class="muted small">
            Your provider tools are locked until a subscription is active.
            Go to <router-link to="/provider/subscription">Subscription</router-link> to select a plan and upload proof.
          </p>
        </div>

        <div v-else-if="!isPremium" class="callout callout--info">
          <strong>Premium features locked</strong>
          <p class="muted small">
            Team management & payroll are Premium-only. Upgrade in
            <router-link to="/provider/subscription">Subscription</router-link>.
          </p>
        </div>
      </section>

      <section class="surface-panel help-panel">
        <h2>Getting started checklist</h2>
        <div class="steps">
          <div class="step">
            <strong>1) Set up your business settings</strong>
            <p class="muted small">
              Add your location and payment methods in <router-link to="/provider/settings">Settings</router-link>.
            </p>
          </div>
          <div class="step">
            <strong>2) Create listings</strong>
            <p class="muted small">
              {{ listingsCount > 0 ? 'You already have listings — keep them up to date.' : 'You have no listings yet — start by creating your first listing.' }}
              Go to <router-link to="/provider/listings">Listings</router-link>.
            </p>
          </div>
          <div class="step">
            <strong>3) Manage orders</strong>
            <p class="muted small">
              {{ totalOrders > 0 ? 'Review new orders and download invoices when needed.' : 'No orders yet — once you have listings, customers can place orders.' }}
              Go to <router-link to="/provider/orders">Orders</router-link>.
            </p>
          </div>
        </div>
      </section>

      <section class="surface-panel help-panel">
        <h2>Common tasks</h2>
        <div class="faq">
          <details open>
            <summary>How do I activate or upgrade my plan?</summary>
            <p class="muted small">
              Open <router-link to="/provider/subscription">Subscription</router-link>, choose a plan, then upload proof of payment.
              If auto-verification fails, an admin will verify it manually.
            </p>
          </details>
          <details>
            <summary>Where do I download order invoices?</summary>
            <p class="muted small">
              Go to <router-link to="/provider/orders">Orders</router-link> and use the “Download invoice” action for purchases or rentals.
            </p>
          </details>
          <details v-if="auth.canManageStaff">
            <summary>How do I manage staff and payroll?</summary>
            <p class="muted small">
              This is a Premium feature. Once on Premium, use <router-link to="/provider/team">Team & payroll</router-link> and
              <router-link to="/provider/staff-payments">Staff payments</router-link>.
            </p>
          </details>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.help-page {
  padding-bottom: 2rem;
}
.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 0.5rem;
}
.help-panel + .help-panel {
  margin-top: var(--space-5);
}
.callout {
  margin-top: var(--space-4);
  padding: var(--space-4);
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border);
  background: var(--color-surface);
}
.callout--warn {
  background: var(--color-wheat-soft);
  border-color: rgba(201, 162, 39, 0.35);
}
.callout--info {
  background: var(--color-info-bg);
  border-color: rgba(21, 74, 122, 0.18);
}
.steps {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-4);
  margin-top: var(--space-3);
}
.step {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: var(--space-4);
  background: var(--color-surface-elevated);
  box-shadow: var(--shadow-sm);
}
.faq details {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: var(--space-3) var(--space-4);
  background: var(--color-surface-elevated);
  box-shadow: var(--shadow-sm);
  margin-top: var(--space-3);
}
.faq summary {
  cursor: pointer;
  font-weight: 700;
  color: var(--color-canopy);
}
@media (max-width: 980px) {
  .steps {
    grid-template-columns: 1fr;
  }
}
</style>

