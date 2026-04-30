<script setup>
import { ref, onMounted, computed, watch } from 'vue';
import { useRouter } from 'vue-router';
import { providerSettingsApi } from '../services/marketplaceApi';
import { useAuthStore } from '../stores/auth';
import FormField from '../components/ui/FormField.vue';
import { isNonEmptyString, isPositiveNumber } from '../utils/validation';
import { getCurrentLocation } from '../utils/getCurrentLocation';

const router = useRouter();
const auth = useAuthStore();

const loading = ref(true);
const error = ref('');
const message = ref('');

/* ================= SWITCH (ADDED) ================= */
const useCurrentLocation = ref(false);

/*=================== Get Current Location ================= */
const locationName = ref('');
const coords = ref({ latitude: null, longitude: null });

async function loadLocation() {
  try {
    const loc = await getCurrentLocation();
    locationName.value = loc.locationName;
    coords.value = loc.coords;

    // ONLY populate when switch is ON
    if (useCurrentLocation.value) {
      form.value.location = loc.locationName;
    }

  } catch (err) {
    console.error("Failed to get location:", err.message);
  }
}

/* WATCH SWITCH (ADDED) */
watch(useCurrentLocation, async (val) => {
  if (val) {
    await loadLocation();
  } else {
    form.value.location = '';
    locationName.value = '';
    coords.value = { latitude: null, longitude: null };
  }
});

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

          <!-- SWITCH (ADDED) -->
          <FormField label="Use current location">
            <label class="switch">
              <input type="checkbox" v-model="useCurrentLocation" :disabled="!canEdit" />
              <span class="slider"></span>
            </label>
          </FormField>

          <FormField label="Location">
            <input v-model="form.location" type="text" :disabled="!canEdit" />
          </FormField>

          <FormField label="Payment methods">
            <div class="payment-grid">

              <label class="check-card">
                <input type="checkbox" :disabled="!canEdit" value="EFT" v-model="form.acceptedPaymentMethods" />
                <span>EFT</span>
              </label>

              <label class="check-card">
                <input type="checkbox" :disabled="!canEdit" value="CASH" v-model="form.acceptedPaymentMethods" />
                <span>Cash</span>
              </label>

            </div>
          </FormField>
        </section>

        <!-- DELIVERY -->
        <section class="card">
          <h2>🚚 Delivery</h2>

          <label class="toggle">
            <input type="checkbox" :disabled="!canEdit" v-model="form.deliveryAvailable" />
            <span>Offer delivery</span>
          </label>

          <div v-if="form.deliveryAvailable" class="delivery-box">
            <FormField label="Price per KM">
              <input v-model="form.deliveryPricePerKm" type="number" :disabled="!canEdit" />
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
            <input v-model="form.bankName" :disabled="!canEdit" />
          </FormField>

          <FormField label="Account name">
            <input v-model="form.bankAccountName" :disabled="!canEdit" />
          </FormField>

          <FormField label="Account number">
            <input v-model="form.bankAccountNumber" :disabled="!canEdit" />
          </FormField>

          <FormField label="Branch code">
            <input v-model="form.bankBranchCode" :disabled="!canEdit" />
          </FormField>
        </section>

      </div>

      <!-- FULL WIDTH ACTION -->
      <div class="full">
        <div class="actions">
          <button type="button" class="btn btn-primary" :disabled="!canEdit" @click="save">
            Save Changes
          </button>
        </div>
      </div>  

      <!-- DANGER -->
      <div class="full">
        <section class="card danger">
          <h2>⚠ Danger Zone</h2>
          <button type="button" class="btn btn-ghost danger-btn" :disabled="!canEdit" @click="deactivateAccount">
            Deactivate Account
          </button>
        </section>
      </div>

    </div>
  </div>
</template>

<style scoped>
.provider-settings-page {
  margin: 0 auto;
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
  border: 1px solid var(--color-border);
  background: var(--color-surface);
  box-shadow: var(--shadow-md);
}

/* PAYMENT */
.payment-grid {
  display: flex;
  gap: 0.5rem;
}

.check-card {
  flex: 1;
  padding: 0.6rem;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  cursor: pointer;
  background: var(--color-surface-elevated);
  transition: background var(--transition-fast), border-color var(--transition-fast);
}
.check-card:hover {
  background: var(--color-sage-soft);
  border-color: var(--color-border-strong);
}

/* DELIVERY */
.delivery-box {
  margin-top: 0.6rem;
  padding: 0.6rem;
  border: 1px dashed var(--color-info-text);
  border-radius: 10px;
}

/* ACTIONS */
.actions {
  display: flex;
  justify-content: flex-end;
}

/* DANGER */
.danger {
  border-color: rgba(248, 113, 113, 0.45);
}

.danger-btn {
  border-color: rgba(248, 113, 113, 0.45);
  color: var(--color-danger-text);
}

/* TOAST */
.toast {
  padding: 0.6rem;
  margin-bottom: 1rem;
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border);
}

.error {
  background: var(--color-danger-bg);
  color: var(--color-danger-text);
}
.success {
  background: var(--color-success-bg);
  color: var(--color-success-text);
}

/* SWITCH */
.switch {
  position: relative;
  display: inline-block;
  width: 46px;
  height: 24px;
}

.switch input {
  opacity: 0;
  width: 0;
  height: 0;
}

.slider {
  position: absolute;
  cursor: pointer;
  inset: 0;
  background-color: var(--color-border-strong);
  transition: 0.3s;
  border-radius: 24px;
}

.slider:before {
  position: absolute;
  content: "";
  height: 18px;
  width: 18px;
  left: 3px;
  bottom: 3px;
  background-color: var(--color-surface-elevated);
  transition: 0.3s;
  border-radius: 50%;
}

input:checked + .slider {
  background-color: var(--color-sage);
}

input:checked + .slider:before {
  transform: translateX(22px);
}

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