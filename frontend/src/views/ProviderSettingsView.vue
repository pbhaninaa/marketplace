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
    <header class="page-hero">
      <p class="page-hero__eyebrow">Provider</p>
      <h1 class="page-hero__title">Settings</h1>
      <p class="page-hero__lead">Manage your location, accepted payment methods, and banking details (shown at checkout).</p>
    </header>

    <p v-if="error" class="err-toast">{{ error }}</p>
    <p v-if="message" class="ok-msg">{{ message }}</p>
    <p v-if="loading" class="muted loading-line">Loading…</p>

    <section v-else class="surface-panel settings-panel">
      <h2>Business</h2>
      <FormField label="Location" capitalize-first>
        <input v-model="form.location" type="text" required maxlength="500" :disabled="!canEdit" />
      </FormField>

      <FormField label="Accepted payment methods">
        <select v-model="form.acceptedPaymentMethods" multiple size="2" :disabled="!canEdit">
          <option v-for="m in allPaymentMethods" :key="m" :value="m">{{ m }}</option>
        </select>
      </FormField>

      <h2>Delivery</h2>
      <FormField label="Delivery available">
        <label style="display: flex; align-items: center; gap: 0.5rem;">
          <input v-model="form.deliveryAvailable" type="checkbox" :disabled="!canEdit" />
          <span>I offer delivery services</span>
        </label>
      </FormField>

      <FormField v-if="form.deliveryAvailable" label="Delivery price per KM">
        <input v-model="form.deliveryPricePerKm" type="number" step="0.01" min="0.01" required :disabled="!canEdit" placeholder="e.g. 5.00" />
        <p class="muted small">This will be used to calculate delivery fees based on distance.</p>
      </FormField>

      <h2>Banking details (EFT)</h2>
      <p class="muted small">These details are shown to clients when they select EFT at checkout.</p>
      <FormField label="Bank name" capitalize-first>
        <input v-model="form.bankName" type="text" maxlength="200" :disabled="!canEdit" />
      </FormField>
      <FormField label="Account name" capitalize-first>
        <input v-model="form.bankAccountName" type="text" maxlength="200" :disabled="!canEdit" />
      </FormField>
      <FormField label="Account number">
        <input v-model="form.bankAccountNumber" type="text" maxlength="50" :disabled="!canEdit" />
      </FormField>
      <FormField label="Branch code">
        <input v-model="form.bankBranchCode" type="text" maxlength="20" :disabled="!canEdit" />
      </FormField>
      <FormField label="Payment reference hint (optional)" capitalize-first>
        <input v-model="form.bankReference" type="text" maxlength="140" :disabled="!canEdit" placeholder="e.g. Use your email as reference" />
      </FormField>

      <button type="button" class="btn btn-primary" :disabled="!canEdit" @click="save">Save settings</button>
      <p v-if="!canEdit" class="muted small">Only the provider owner/admin can edit settings.</p>
    </section>

    <section v-if="!loading" class="surface-panel settings-panel danger">
      <h2>Danger zone</h2>
      <p class="muted small">
        Deactivating your provider account will suspend your provider, disable all users, and unpublish listings.
      </p>
      <button type="button" class="btn btn-ghost danger-btn" :disabled="!canEdit" @click="deactivateAccount">
        Deactivate provider account
      </button>
    </section>
  </div>
</template>

<style scoped>
.provider-settings-page {
  padding: 0.5rem 0 2rem;
}
.settings-panel {
  max-width: 520px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}
.settings-panel h2 {
  font-family: var(--font-display);
  margin-top: 0.75rem;
}
.settings-panel .btn {
  margin-top: 0.8rem;
  align-self: flex-start;
}
.danger {
  margin-top: 1rem;
}
.danger-btn {
  border-color: rgba(180, 40, 40, 0.35);
  color: rgba(140, 20, 20, 0.95);
}
</style>

