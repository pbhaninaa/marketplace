<script setup>
import { ref, onMounted, computed } from 'vue';
import { useRouter } from 'vue-router';
import { providerSettingsApi } from '../services/marketplaceApi';
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

const canEdit = computed(() =>
  auth.role === 'PROVIDER_OWNER' || auth.role === 'PROVIDER_ADMIN'
);

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
  try {
    const { data } = await providerSettingsApi.get();
    form.value = {
      location: data.location || '',
      bankName: data.bankName || '',
      bankAccountName: data.bankAccountName || '',
      bankAccountNumber: data.bankAccountNumber || '',
      bankBranchCode: data.bankBranchCode || '',
      bankReference: data.bankReference || '',
      acceptedPaymentMethods: data.acceptedPaymentMethods?.length
        ? data.acceptedPaymentMethods
        : ['EFT', 'CASH'],
      deliveryAvailable: data.deliveryAvailable || false,
      deliveryPricePerKm: data.deliveryPricePerKm || '',
    };
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}

async function save() {
  if (!isNonEmptyString(form.value.location)) {
    error.value = 'Location is required.';
    return;
  }

  if (!form.value.acceptedPaymentMethods.length) {
    error.value = 'Select at least one payment method.';
    return;
  }

  if (form.value.deliveryAvailable && !isPositiveNumber(form.value.deliveryPricePerKm)) {
    error.value = 'Enter valid delivery price.';
    return;
  }

  try {
    await providerSettingsApi.patch({
      ...form.value,
      deliveryPricePerKm: form.value.deliveryAvailable
        ? parseFloat(form.value.deliveryPricePerKm)
        : null,
    });
    message.value = 'Settings saved.';
  } catch (e) {
    error.value = e.message;
  }
}
</script>

<template>
  <div class="provider-settings-page">

    <!-- HEADER -->
    <header class="hero">
      <h1>Business Settings</h1>
      <p>Manage your store configuration, payments, and delivery.</p>
    </header>

    <p v-if="error" class="toast error">{{ error }}</p>
    <p v-if="message" class="toast success">{{ message }}</p>
    <p v-if="loading" class="loading">Loading...</p>

    <div v-else class="grid">

      <!-- LEFT -->
      <div class="column">

        <!-- BUSINESS -->
        <section class="card">
          <h2>📍 Business</h2>

          <FormField label="Location">
            <input v-model="form.location" type="text" :disabled="!canEdit" />
          </FormField>

          <FormField label="Payment methods">
            <div class="payment-grid">

              <label class="check-card">
                <input type="checkbox" value="EFT" v-model="form.acceptedPaymentMethods" />
                <span>EFT</span>
              </label>

              <label class="check-card">
                <input type="checkbox" value="CASH" v-model="form.acceptedPaymentMethods" />
                <span>Cash</span>
              </label>

            </div>
          </FormField>
        </section>

        <!-- DELIVERY -->
        <section class="card">
          <h2>🚚 Delivery</h2>

          <label class="toggle">
            <input type="checkbox" v-model="form.deliveryAvailable" />
            <span>Offer delivery</span>
          </label>

          <div v-if="form.deliveryAvailable" class="delivery-box">
            <FormField label="Price per KM">
              <input v-model="form.deliveryPricePerKm" type="number" />
            </FormField>
          </div>
        </section>

      </div>

      <!-- RIGHT -->
      <div class="column">

        <!-- BANKING -->
        <section class="card">
          <h2>🏦 Banking</h2>

          <FormField label="Bank name">
            <input v-model="form.bankName" />
          </FormField>

          <FormField label="Account name">
            <input v-model="form.bankAccountName" />
          </FormField>

          <FormField label="Account number">
            <input v-model="form.bankAccountNumber" />
          </FormField>

          <FormField label="Branch code">
            <input v-model="form.bankBranchCode" />
          </FormField>
        </section>

      </div>

      <!-- FULL WIDTH ACTION -->
      <div class="full">
        <div class="actions">
          <button class="btn" @click="save">Save Changes</button>
        </div>
      </div>

      <!-- DANGER -->
      <div class="full">
        <section class="card danger">
          <h2>⚠ Danger Zone</h2>
          <button class="danger-btn">Deactivate Account</button>
        </section>
      </div>

    </div>
  </div>
</template>

<style scoped>
.provider-settings-page {
  max-width: 1100px;
  margin: auto;
}

/* HERO */
.hero {
  margin-bottom: 1rem;
}

/* GRID */
.grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1.2rem;
}

.column {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.full {
  grid-column: span 2;
}

/* CARD */
.card {
  padding: 1.2rem;
  border-radius: 14px;
  border: 1px solid #eee;
  background: white;
  box-shadow: 0 4px 12px rgba(0,0,0,0.05);
}

/* PAYMENT */
.payment-grid {
  display: flex;
  gap: 0.5rem;
}

.check-card {
  flex: 1;
  padding: 0.6rem;
  border: 1px solid #ddd;
  border-radius: 10px;
  cursor: pointer;
}

/* DELIVERY */
.delivery-box {
  margin-top: 0.6rem;
  padding: 0.6rem;
  border: 1px dashed #3b82f6;
  border-radius: 10px;
}

/* ACTIONS */
.actions {
  display: flex;
  justify-content: flex-end;
}

.btn {
  padding: 0.6rem 1.2rem;
  background: #2563eb;
  color: white;
  border-radius: 8px;
  border: none;
}

/* DANGER */
.danger {
  border-color: #fca5a5;
}

.danger-btn {
  background: #fee2e2;
  padding: 0.6rem;
  border-radius: 8px;
}

/* TOAST */
.toast {
  padding: 0.6rem;
  margin-bottom: 1rem;
}

.error { background: #fee2e2; }
.success { background: #dcfce7; }

/* MOBILE */
@media (max-width: 768px) {
  .grid {
    grid-template-columns: 1fr;
  }
  .full {
    grid-column: span 1;
  }
}
</style>