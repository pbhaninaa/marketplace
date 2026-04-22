<script setup>
import { ref, onMounted, computed } from 'vue';
import { useRouter } from 'vue-router';
import { api } from '../api';
import { useAuthStore } from '../stores/auth';
import FormField from '../components/ui/FormField.vue';
import { isNonEmptyString, isPositiveNumber } from '../utils/validation';

const router = useRouter();
const auth = useAuthStore();

const loading = ref(true);
const error = ref('');
const message = ref('');

const form = ref({
  location: '',
  bankName: '',
  bankAccountName: '',
  bankAccountNumber: '',
  bankBranchCode: '',
  bankReference: '',
  acceptedPaymentMethods: ['EFT', 'CASH'],
  deliveryAvailable: false,
  deliveryPricePerKm: '',
});

const allPaymentMethods = ['EFT', 'CASH'];

const canEdit = computed(() => auth.role === 'PROVIDER_OWNER' || auth.role === 'PROVIDER_ADMIN');

onMounted(async () => {
  auth.restoreFromStorage();
  if (!auth.isProviderUser) {
    router.replace({ path: '/login', query: { redirect: '/provider/settings' } });
    return;
  }
  await load();
});

async function load() {
  loading.value = true;
  error.value = '';
  message.value = '';
  try {
    const { data } = await api.get('/api/provider/me/settings');
    form.value = {
      location: data.location || '',
      bankName: data.bankName || '',
      bankAccountName: data.bankAccountName || '',
      bankAccountNumber: data.bankAccountNumber || '',
      bankBranchCode: data.bankBranchCode || '',
      bankReference: data.bankReference || '',
      acceptedPaymentMethods: data.acceptedPaymentMethods?.length ? data.acceptedPaymentMethods : ['EFT', 'CASH'],
      deliveryAvailable: data.deliveryAvailable || false,
      deliveryPricePerKm: data.deliveryPricePerKm || '',
    };
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  } finally {
    loading.value = false;
  }
}

async function save() {
  error.value = '';
  message.value = '';
  if (!isNonEmptyString(form.value.location)) {
    error.value = 'Location is required.';
    return;
  }
  if (!Array.isArray(form.value.acceptedPaymentMethods) || form.value.acceptedPaymentMethods.length === 0) {
    error.value = 'Please select at least one accepted payment method.';
    return;
  }
  if (form.value.deliveryAvailable) {
    if (!isPositiveNumber(form.value.deliveryPricePerKm)) {
      error.value = 'Please enter a delivery price per KM greater than zero.';
      return;
    }
  }
  try {
    const { data } = await api.patch('/api/provider/me/settings', {
      location: form.value.location,
      bankName: form.value.bankName || null,
      bankAccountName: form.value.bankAccountName || null,
      bankAccountNumber: form.value.bankAccountNumber || null,
      bankBranchCode: form.value.bankBranchCode || null,
      bankReference: form.value.bankReference || null,
      acceptedPaymentMethods: form.value.acceptedPaymentMethods || [],
      deliveryAvailable: form.value.deliveryAvailable,
      deliveryPricePerKm: form.value.deliveryAvailable && form.value.deliveryPricePerKm ? parseFloat(form.value.deliveryPricePerKm) : null,
    });
    message.value = 'Settings saved.';
    // refresh local cart (bank details shown during checkout)
    form.value.acceptedPaymentMethods = data.acceptedPaymentMethods || form.value.acceptedPaymentMethods;
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  }
}

async function deactivateAccount() {
  if (!canEdit.value) return;
  error.value = '';
  message.value = '';
  try {
    await api.delete('/api/provider/me/account');
    auth.logout();
    router.replace({ path: '/login' });
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  }
}
</script>

<template>
  <div class="page-document provider-settings-page">

    <!-- HERO -->
    <header class="page-hero">
      <p class="page-hero__eyebrow">Provider settings</p>
      <h1 class="page-hero__title">Business configuration</h1>
      <p class="page-hero__lead">
        Manage your location, payments, delivery, and banking details.
      </p>
    </header>

    <!-- STATUS -->
    <p v-if="error" class="toast error">{{ error }}</p>
    <p v-if="message" class="toast success">{{ message }}</p>
    <p v-if="loading" class="muted loading">Loading settings…</p>

    <div v-else class="settings-grid">

      <!-- MAIN SETTINGS -->
      <section class="card">

        <h2 class="card-title">Business details</h2>

        <FormField label="Location">
          <input v-model="form.location" type="text" :disabled="!canEdit" />
        </FormField>

     <FormField label="Accepted payment methods">

  <label class="check-card">
    <input
      type="checkbox"
      value="EFT"
      v-model="form.acceptedPaymentMethods"
      :disabled="!canEdit"
    />
    <span class="check-label">
      EFT <small>(Bank transfer)</small>
    </span>
  </label>

  <label class="check-card">
    <input
      type="checkbox"
      value="CASH"
      v-model="form.acceptedPaymentMethods"
      :disabled="!canEdit"
    />
    <span class="check-label">
      Cash <small>(Pay on delivery)</small>
    </span>
  </label>

</FormField>

      </section>

      <!-- DELIVERY -->
      <section class="card">

        <h2 class="card-title">Delivery</h2>

        <label class="toggle">
          <input v-model="form.deliveryAvailable" type="checkbox" :disabled="!canEdit" />
          <span>I offer delivery services</span>
        </label>

        <div v-if="form.deliveryAvailable" class="fade-in">
          <FormField label="Price per KM (ZAR)">
            <input
              v-model="form.deliveryPricePerKm"
              type="number"
              step="0.01"
              min="0.01"
              :disabled="!canEdit"
              placeholder="e.g. 5.00"
            />
          </FormField>

          <p class="hint">
            Delivery fees are calculated based on distance.
          </p>
        </div>

      </section>

      <!-- BANKING -->
      <section class="card">

        <h2 class="card-title">Banking (EFT)</h2>
        <p class="hint">
          Shown to customers when they choose EFT at checkout.
        </p>

        <FormField label="Bank name">
          <input v-model="form.bankName" type="text" :disabled="!canEdit" />
        </FormField>

        <FormField label="Account name">
          <input v-model="form.bankAccountName" type="text" :disabled="!canEdit" />
        </FormField>

        <FormField label="Account number">
          <input v-model="form.bankAccountNumber" type="text" :disabled="!canEdit" />
        </FormField>

        <FormField label="Branch code">
          <input v-model="form.bankBranchCode" type="text" :disabled="!canEdit" />
        </FormField>

        <FormField label="Reference hint (optional)">
          <input v-model="form.bankReference" type="text" :disabled="!canEdit" />
        </FormField>

      </section>

      <!-- ACTIONS -->
      <section class="actions-bar">
        <button class="btn btn-primary" :disabled="!canEdit" @click="save">
          Save changes
        </button>

        <p v-if="!canEdit" class="muted small">
          Only admins can edit settings.
        </p>
      </section>

      <!-- DANGER -->
      <section class="card danger">

        <h2 class="card-title danger-title">Danger zone</h2>

        <p class="hint">
          Deactivating will suspend your provider account and unpublish listings.
        </p>

        <button
          class="btn btn-ghost danger-btn"
          :disabled="!canEdit"
          @click="deactivateAccount"
        >
          Deactivate account
        </button>

      </section>

    </div>
  </div>
</template>

<style scoped>

/* PAGE */
.provider-settings-page {
  padding: 1rem 0 2.5rem;
}

/* GRID */
.settings-grid {
  max-width: 720px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

/* CARD */
.card {
  background: var(--color-surface-elevated);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xl);
  padding: 1.2rem;
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
}

/* TITLES */
.card-title {
  font-family: var(--font-display);
  font-size: 1.05rem;
  margin: 0;
}

/* HINT TEXT */
.hint {
  font-size: 0.85rem;
  color: var(--color-muted);
  line-height: 1.4;
  margin-top: -0.3rem;
}

/* TOGGLE */
.toggle {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  font-size: 0.95rem;
}

/* BUTTON BAR */
.actions-bar {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 0.5rem 0;
}

/* DANGER */
.danger {
  border-color: rgba(180, 40, 40, 0.25);
  background: rgba(255, 245, 245, 0.6);
}

.danger-title {
  color: rgba(140, 20, 20, 1);
}

.danger-btn {
  border-color: rgba(180, 40, 40, 0.4);
  color: rgba(140, 20, 20, 1);
}

/* TOASTS */
.toast {
  max-width: 720px;
  margin: 0 auto 1rem;
  padding: 0.75rem 1rem;
  border-radius: 10px;
  font-size: 0.9rem;
}

.toast.error {
  background: #fff1f2;
  border: 1px solid #fecdd3;
  color: #9f1239;
}

.toast.success {
  background: #ecfdf5;
  border: 1px solid #a7f3d0;
  color: #065f46;
}

/* LOADING */
.loading {
  text-align: center;
  padding: 1rem 0;
}

/* ANIMATION */
.fade-in {
  animation: fadeIn 0.2s ease-in;
}

.check-card {
  display: flex;
  align-items: center;
  gap: 0.75rem;

  padding: 0.75rem 0.9rem;
  border: 1px solid var(--color-border);
  border-radius: 12px;

  cursor: pointer;
  margin-bottom: 0.6rem;

  transition: all 0.15s ease;
  background: var(--color-surface);
}

/* hover state */
.check-card:hover {
  border-color: #2563eb;
  background: rgba(37, 99, 235, 0.04);
}

/* checkbox */
.check-card input {
  width: 16px;
  height: 16px;
  accent-color: #2563eb;
  flex-shrink: 0;
}

/* label block */
.check-label {
  display: flex;
  flex-direction: column;
  font-size: 0.95rem;
  color: var(--color-text);
  line-height: 1.2;
}

/* subtitle */
.check-label small {
  font-size: 0.78rem;
  color: var(--color-muted);
  margin-top: 2px;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(-4px); }
  to { opacity: 1; transform: translateY(0); }
}

</style>

