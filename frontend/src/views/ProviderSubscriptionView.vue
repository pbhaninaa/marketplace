<script setup>
import { ref, onMounted, computed } from 'vue';
import { useRouter } from 'vue-router';
import { api } from '../api';
import { useAuthStore } from '../stores/auth';
import FormField from '../components/ui/FormField.vue';

const router = useRouter();
const auth = useAuthStore();

const loading = ref(true);
const error = ref('');
const message = ref('');

const status = ref(null);

const selectForm = ref({
  plan: 'BASIC',
  billingCycle: 'MONTHLY',
});

const isValid = computed(() => !!status.value?.valid);

onMounted(async () => {
  auth.restoreFromStorage();
  if (!auth.isProviderUser) {
    router.replace({ path: '/login', query: { redirect: '/provider/subscription' } });
    return;
  }
  await load();
});

async function load() {
  loading.value = true;
  error.value = '';
  message.value = '';
  try {
    const { data } = await api.get('/api/provider/me/subscription/status');
    status.value = data;
    if (data?.plan) {
      selectForm.value.plan = data.plan;
    }
    if (data?.billingCycle) {
      selectForm.value.billingCycle = data.billingCycle;
    }
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  } finally {
    loading.value = false;
  }
}

async function selectPlan() {
  error.value = '';
  message.value = '';
  try {
    const { data } = await api.post('/api/provider/me/subscription/select', {
      plan: selectForm.value.plan,
      billingCycle: selectForm.value.billingCycle,
    });
    status.value = data;
    message.value = 'Subscription updated.';
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  }
}
</script>

<template>
  <div class="page-document page-document--wide sub-page">
    <header class="page-hero sub-hero">
      <p class="page-hero__eyebrow">Provider</p>
      <h1 class="page-hero__title">Subscription</h1>
      <p class="page-hero__lead">
        Choose a plan to unlock workspace features. This page mirrors the Wheel Hub subscription flow (same sections), with our
        Marketplace theme.
      </p>
    </header>

    <p v-if="error" class="err-toast">{{ error }}</p>
    <p v-if="message" class="ok-msg">{{ message }}</p>
    <p v-if="loading" class="muted loading-line">Loading…</p>

    <template v-else>
      <section class="surface-panel sub-panel">
        <div class="sub-status">
          <div class="sub-status__card">
            <span class="muted small">Current plan</span>
            <strong>{{ status?.plan || '—' }}</strong>
          </div>
          <div class="sub-status__card">
            <span class="muted small">Billing cycle</span>
            <strong>{{ status?.billingCycle || '—' }}</strong>
          </div>
          <div class="sub-status__card">
            <span class="muted small">Status</span>
            <strong>{{ isValid ? 'Active' : 'Not active' }}</strong>
          </div>
          <div class="sub-status__card">
            <span class="muted small">Expires</span>
            <strong>{{ status?.expiresAt ? String(status.expiresAt).slice(0, 19) : '—' }}</strong>
          </div>
        </div>
        <div class="sub-actions">
          <button type="button" class="btn btn-ghost" @click="load">Refresh</button>
          <button v-if="isValid" type="button" class="btn btn-primary" @click="router.push('/provider')">Continue →</button>
        </div>
      </section>

      <section class="surface-panel sub-panel">
        <h2>Choose your plan</h2>
        <div class="sub-grid">
          <label class="plan-card" :class="{ 'plan-card--selected': selectForm.plan === 'BASIC' }">
            <input v-model="selectForm.plan" type="radio" value="BASIC" />
            <div class="plan-card__head">
              <strong>Basic</strong>
              <span class="pill">Starter</span>
            </div>
            <p class="muted small">Core provider workspace: listings + orders.</p>
          </label>

          <label class="plan-card" :class="{ 'plan-card--selected': selectForm.plan === 'PREMIUM' }">
            <input v-model="selectForm.plan" type="radio" value="PREMIUM" />
            <div class="plan-card__head">
              <strong>Premium</strong>
              <span class="pill pill--gold">Pro</span>
            </div>
            <p class="muted small">Adds advanced tools (priority support & extended access).</p>
          </label>
        </div>

        <div class="sub-form">
          <FormField label="Billing cycle">
            <select v-model="selectForm.billingCycle">
              <option value="MONTHLY">Monthly</option>
              <option value="YEARLY">Yearly</option>
            </select>
          </FormField>
          <button type="button" class="btn btn-primary" @click="selectPlan">Activate plan</button>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.sub-page {
  padding-bottom: 2rem;
}
.sub-status {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0.75rem;
}
.sub-status__card {
  border: 1px solid rgba(21, 74, 122, 0.12);
  background: rgba(21, 74, 122, 0.04);
  border-radius: 12px;
  padding: 0.85rem;
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}
.sub-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  margin-top: 0.85rem;
}
.sub-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.85rem;
  margin-top: 0.75rem;
}
.plan-card {
  cursor: pointer;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 0.95rem 1rem;
  background: var(--color-surface-elevated);
  box-shadow: var(--shadow-sm);
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  transition: border-color var(--transition-fast), transform var(--transition-fast);
}
.plan-card input {
  position: absolute;
  opacity: 0;
  pointer-events: none;
}
.plan-card--selected {
  border-color: rgba(61, 122, 102, 0.65);
  box-shadow: 0 6px 20px rgba(61, 122, 102, 0.14);
  transform: translateY(-1px);
}
.plan-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
}
.pill {
  display: inline-flex;
  align-items: center;
  padding: 0.2rem 0.55rem;
  border-radius: var(--radius-pill);
  border: 1px solid rgba(26, 60, 52, 0.18);
  background: rgba(26, 60, 52, 0.06);
  font-size: 0.78rem;
  font-weight: 700;
  color: var(--color-canopy);
}
.pill--gold {
  border-color: rgba(201, 162, 39, 0.35);
  background: rgba(201, 162, 39, 0.1);
  color: #6d5200;
}
.sub-form {
  margin-top: 1rem;
  display: flex;
  align-items: flex-end;
  gap: 0.85rem;
  flex-wrap: wrap;
}
@media (max-width: 900px) {
  .sub-status {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .sub-grid {
    grid-template-columns: 1fr;
  }
}
</style>

