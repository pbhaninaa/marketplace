<script setup>
import { computed, nextTick, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '../stores/auth';
import FormField from '../components/ui/FormField.vue';
import { textError, numberError, LIMITS } from '../utils/validation';
import { adminSettingsApi, adminMaintenanceApi, adminOrderInvoiceApi } from '../services/marketplaceApi';

const router = useRouter();
const route = useRoute();
const auth = useAuthStore();

const loading = ref(true);
const saving = ref(false);
const error = ref('');
const message = ref('');

const cleaning = ref(false);
const cleanMessage = ref('');
const cleanError = ref('');

const invoiceOrderRef = ref('');
const invoiceDownloading = ref(false);
const invoiceDownloadError = ref('');

const form = ref({
  systemName: 'Agri Marketplace',
  bankName: '',
  accountName: '',
  accountNumber: '',
  branchCode: '',
  referenceHint: '',
  basicMonthly: 199,
  premiumMonthly: 499,
  usageFeePercent: 0,
});

onMounted(async () => {
  auth.restoreFromStorage();
  if (!auth.isPlatformAdmin) {
    router.replace({ path: '/login', query: { redirect: '/admin/settings' } });
    return;
  }
  await load();
  if (route.hash === '#maintenance') {
    await nextTick();
    document.getElementById('maintenance')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }
});

async function load() {
  loading.value = true;
  error.value = '';
  message.value = '';
  try {
    const { data } = await adminSettingsApi.get();
    form.value = {
      systemName: data?.systemName ?? 'Agri Marketplace',
      bankName: data?.bankName ?? '',
      accountName: data?.accountName ?? '',
      accountNumber: data?.accountNumber ?? '',
      branchCode: data?.branchCode ?? '',
      referenceHint: data?.referenceHint ?? '',
      basicMonthly: data?.basicMonthly ?? 199,
      premiumMonthly: data?.premiumMonthly ?? 499,
      usageFeePercent: data?.usageFeePercent ?? 0,
    };
  } catch (e) {
    error.value = e.response?.data?.message || e.message || 'Failed to load settings.';
  } finally {
    loading.value = false;
  }
}

/** Inline messages (same limits as the API). */
const fieldErrors = computed(() => {
  const f = form.value;
  const fee = (v, label) => numberError(v, { label, min: 0, max: LIMITS.maxPrice });
  return {
    systemName: textError(f.systemName, { required: true, label: 'System name', max: LIMITS.systemName }),
    basicMonthly: fee(f.basicMonthly, 'Basic price'),
    premiumMonthly: fee(f.premiumMonthly, 'Premium price'),
    usageFeePercent: numberError(f.usageFeePercent, { required: true, label: 'Usage fee', min: 0, max: 100 }),
  };
});

async function save() {
  error.value = '';
  message.value = '';
  const firstFieldError = Object.values(fieldErrors.value).find(Boolean);
  if (firstFieldError) {
    error.value = firstFieldError;
    return;
  }
  saving.value = true;
  try {
    await adminSettingsApi.update({
      ...form.value,
      // Backend still supports these fields; we keep branding simple and provider-driven.
      invoiceLegalName: null,
      invoiceAddress: null,
      invoiceVatNumber: null,
      invoiceFooterNote: null,
    });
    message.value = 'Settings saved.';
  } catch (e) {
    error.value = e.response?.data?.message || e.message || 'Failed to save settings.';
  } finally {
    saving.value = false;
  }
}

async function cleanDb() {
  cleaning.value = true;
  cleanMessage.value = '';
  cleanError.value = '';
  try {
    const { data } = await adminMaintenanceApi.cleanDb();
    const deletedUsers = data?.users ?? 0;
    cleanMessage.value = `Database cleaned. Deleted ${deletedUsers} users (kept only your admin).`;
  } catch (e) {
    cleanError.value = e.response?.data?.message || e.message;
  } finally {
    cleaning.value = false;
  }
}

async function downloadStaffInvoice() {
  invoiceDownloadError.value = '';
  const ref = invoiceOrderRef.value?.trim();
  if (!ref) {
    invoiceDownloadError.value = 'Enter an order number or verification code.';
    return;
  }
  invoiceDownloading.value = true;
  try {
    await adminOrderInvoiceApi.download(ref);
  } catch (e) {
    invoiceDownloadError.value = e.response?.data?.message || e.message || 'Download failed.';
  } finally {
    invoiceDownloading.value = false;
  }
}
</script>

<template>
  <div class="page-document page-document--wide">
    <header class="page-hero">
      <p class="page-hero__eyebrow">Admin</p>
      <h1 class="page-hero__title">Settings</h1>
      <p class="page-hero__lead">
        Platform name, subscription pricing, usage fee, invoices, order tools, and maintenance.
      </p>
    </header>

    <p v-if="error" class="err-toast">{{ error }}</p>
    <p v-if="message" class="ok-msg">{{ message }}</p>
    <p v-if="loading" class="muted">Loading…</p>

    <section v-else class="surface-panel settings-panel">
      <h2>Platform</h2>
      <div class="grid">
        <FormField label="System name" :error="fieldErrors.systemName">
          <input v-model="form.systemName" type="text" :maxlength="LIMITS.systemName" />
        </FormField>
      </div>

      <h2 class="mt">Subscription pricing (Monthly)</h2>
      <div class="grid grid--2">
        <FormField label="Basic (ZAR)" :error="fieldErrors.basicMonthly">
          <input v-model.number="form.basicMonthly" type="number" step="0.01" min="0" />
        </FormField>
        <FormField label="Premium (ZAR)" :error="fieldErrors.premiumMonthly">
          <input v-model.number="form.premiumMonthly" type="number" step="0.01" min="0" />
        </FormField>
        <FormField label="Usage fee (% per paid order)" :error="fieldErrors.usageFeePercent">
          <input v-model.number="form.usageFeePercent" type="number" step="0.01" min="0" max="100" />
          <p class="muted small">
            Applied to each order total once the order is PAID. First subscription invoice: plan price plus the sum of
            (order total × this percent ÷ 100) for paid orders in the billing window.
          </p>
        </FormField>
      </div>

      <h2 class="mt">Order invoice (admin)</h2>
      <p class="muted small">
        Enter the numeric order id or the customer verification code (same as on the receipt). Guests can also use
        <router-link to="/order-invoice">Order invoice</router-link> (email required with numeric id).
      </p>
      <div class="grid grid--2 inv-row">
        <FormField label="Order number or verification code">
          <input v-model="invoiceOrderRef" type="text" autocomplete="off" placeholder="e.g. 42 or AB12CD34" />
        </FormField>
        <div class="inv-actions">
          <button
            type="button"
            class="btn btn-ghost"
            :disabled="invoiceDownloading || !invoiceOrderRef.trim()"
            @click="downloadStaffInvoice"
          >
            {{ invoiceDownloading ? 'Downloading…' : 'Download PDF invoice' }}
          </button>
        </div>
      </div>
      <p v-if="invoiceDownloadError" class="err-toast">{{ invoiceDownloadError }}</p>

      <div id="maintenance" class="maint-block">
        <h2 class="mt">Maintenance</h2>
        <p class="muted small">
          Sensitive operations. This will delete almost everything in the database and leave only your platform admin
          account.
        </p>
        <p v-if="cleanError" class="err-toast">{{ cleanError }}</p>
        <p v-if="cleanMessage" class="ok-msg">{{ cleanMessage }}</p>
        <button type="button" class="btn btn-ghost danger-btn" :disabled="cleaning" @click="cleanDb">
          {{ cleaning ? 'Cleaning…' : 'Clean database (keep only me)' }}
        </button>
      </div>

      <div class="actions">
        <button type="button" class="btn btn-primary" :disabled="saving" @click="save">
          {{ saving ? 'Saving…' : 'Save changes' }}
        </button>
        <button type="button" class="btn btn-ghost" :disabled="saving" @click="load">Refresh</button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.settings-panel h2 {
  margin: 0.25rem 0 0.6rem;
}
.mt {
  margin-top: 1.25rem !important;
}
.grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 0.85rem;
}
.grid--2 {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}
.grid-span-2 {
  grid-column: 1 / -1;
}
.actions {
  display: flex;
  gap: 0.6rem;
  flex-wrap: wrap;
  margin-top: 1.1rem;
}
.small {
  font-size: 0.85rem;
  margin-top: 0.35rem;
}
.inv-row {
  align-items: end;
}
.inv-actions {
  display: flex;
  align-items: center;
  padding-bottom: 0.15rem;
}
.maint-block {
  margin-top: 1.5rem;
  padding-top: 1.25rem;
  border-top: 1px solid rgba(0, 0, 0, 0.08);
}
.danger-btn {
  border-color: rgba(180, 40, 40, 0.35);
  color: rgba(140, 20, 20, 0.95);
}
@media (max-width: 900px) {
  .grid--2 {
    grid-template-columns: 1fr;
  }
  .inv-row {
    grid-template-columns: 1fr;
  }
}
</style>
