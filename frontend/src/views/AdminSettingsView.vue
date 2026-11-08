<script setup>
import { computed, nextTick, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '../stores/auth';
import FormField from '../components/ui/FormField.vue';
import { textError, numberError, LIMITS } from '../utils/validation';
import {
  adminSettingsApi,
  adminMaintenanceApi,
  adminOrderInvoiceApi,
  adminReferralsApi,
} from '../services/marketplaceApi';
import DisplayStyleToggle from '../components/ui/DisplayStyleToggle.vue';

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
  onlinePaymentGateway: 'AUTO',
  onlineOrderFeePercent: 0,
});

const GATEWAY_OPTIONS = [
  { value: 'AUTO', label: 'Automatic (PayFast if set up, otherwise Peach)' },
  { value: 'PAYFAST', label: 'PayFast' },
  { value: 'PEACH', label: 'Peach Payments' },
];
const GATEWAY_NAMES = { PAYFAST: 'PayFast', PEACH: 'Peach Payments' };
const gatewayInfo = ref({ payfastConfigured: false, peachConfigured: false, activeOnlineGateway: null });
const activeGatewayText = computed(() =>
  GATEWAY_NAMES[gatewayInfo.value.activeOnlineGateway] || 'none (online payments are off)',
);
const gatewayWarning = computed(() => {
  const g = form.value.onlinePaymentGateway;
  if (g === 'PAYFAST' && !gatewayInfo.value.payfastConfigured) {
    return 'PayFast is not set up on the server, so buyers will not see the online option.';
  }
  if (g === 'PEACH' && !gatewayInfo.value.peachConfigured) {
    return 'Peach Payments is not set up on the server, so buyers will not see the online option.';
  }
  return '';
});

// Referral programme settings (separate endpoint, separate save).
const referralForm = ref({ commissionPercent: 15, claimWindowDays: 30 });
const referralMeta = ref({ updatedBy: null, updatedAt: null });
const referralSaving = ref(false);
const referralError = ref('');
const referralMessage = ref('');

async function loadReferralSettings() {
  referralError.value = '';
  try {
    const { data } = await adminReferralsApi.getSettings();
    referralForm.value = {
      commissionPercent: data?.commissionPercent ?? 15,
      claimWindowDays: data?.claimWindowDays ?? 30,
    };
    referralMeta.value = { updatedBy: data?.updatedBy ?? null, updatedAt: data?.updatedAt ?? null };
  } catch (e) {
    referralError.value = e.response?.data?.message || e.message || 'Failed to load referral settings.';
  }
}

async function saveReferralSettings() {
  referralSaving.value = true;
  referralError.value = '';
  referralMessage.value = '';
  try {
    const { data } = await adminReferralsApi.updateSettings({
      commissionPercent: referralForm.value.commissionPercent,
      claimWindowDays: referralForm.value.claimWindowDays,
    });
    referralMeta.value = { updatedBy: data?.updatedBy ?? null, updatedAt: data?.updatedAt ?? null };
    referralMessage.value = 'Referral settings saved. The new rate applies to commission earned from now on.';
  } catch (e) {
    referralError.value = e.response?.data?.message || e.message || 'Failed to save referral settings.';
  } finally {
    referralSaving.value = false;
  }
}

onMounted(async () => {
  auth.restoreFromStorage();
  if (!auth.isPlatformAdmin) {
    router.replace({ path: '/login', query: { redirect: '/admin/settings' } });
    return;
  }
  await Promise.all([load(), loadReferralSettings()]);
  if (route.hash === '#maintenance' || route.hash === '#referrals') {
    await nextTick();
    document.getElementById(route.hash.slice(1))?.scrollIntoView({ behavior: 'smooth', block: 'start' });
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
      onlinePaymentGateway: data?.onlinePaymentGateway || 'AUTO',
      onlineOrderFeePercent: data?.onlineOrderFeePercent ?? 0,
    };
    gatewayInfo.value = {
      payfastConfigured: !!data?.payfastConfigured,
      peachConfigured: !!data?.peachConfigured,
      activeOnlineGateway: data?.activeOnlineGateway || null,
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
    onlineOrderFeePercent: numberError(f.onlineOrderFeePercent, {
      required: true,
      label: 'Platform fee',
      min: 0,
      max: 100,
    }),
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
    const { data } = await adminSettingsApi.update({
      ...form.value,
      // Backend still supports these fields; we keep branding simple and provider-driven.
      invoiceLegalName: null,
      invoiceAddress: null,
      invoiceVatNumber: null,
      invoiceFooterNote: null,
    });
    if (data) {
      gatewayInfo.value = {
        payfastConfigured: !!data.payfastConfigured,
        peachConfigured: !!data.peachConfigured,
        activeOnlineGateway: data.activeOnlineGateway || null,
      };
    }
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
        Platform name, subscription pricing, usage fee, online payments, invoices, order tools, and maintenance.
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

      <div id="online-payments">
        <h2 class="mt">Online payments</h2>
        <p class="muted small">
          Buyers and providers see one online option, Card / Instant EFT. Choose which payment gateway handles it.
          Automatic uses PayFast when it is set up, otherwise Peach Payments.
        </p>
        <div class="grid grid--2">
          <FormField label="Payment gateway">
            <AppSelect v-model="form.onlinePaymentGateway">
              <option v-for="g in GATEWAY_OPTIONS" :key="g.value" :value="g.value">{{ g.label }}</option>
            </AppSelect>
            <p class="muted small">
              PayFast: {{ gatewayInfo.payfastConfigured ? 'set up' : 'not set up' }}.
              Peach Payments: {{ gatewayInfo.peachConfigured ? 'set up' : 'not set up' }}.
              In use now: <strong>{{ activeGatewayText }}</strong>.
            </p>
            <p v-if="gatewayWarning" class="err-toast">{{ gatewayWarning }}</p>
          </FormField>
          <FormField label="Platform fee on online orders (%)" :error="fieldErrors.onlineOrderFeePercent">
            <input v-model.number="form.onlineOrderFeePercent" type="number" step="0.01" min="0" max="100" />
            <p class="muted small">
              Kept from each order or rental paid online before the seller is paid out (see
              <router-link to="/admin/payouts">Seller payouts</router-link>). Cash and manual EFT are paid to the seller
              directly and carry no fee. Separate from the usage fee above, which is billed on subscriptions.
            </p>
          </FormField>
        </div>
      </div>

      <div id="referrals" class="referral-block">
        <h2 class="mt">Referral programme</h2>
        <p class="muted small">
          Provider users earn this percent of a referred business's first subscription payment (paid via Peach).
          Changes apply to commission earned from now on; earned commission keeps the rate it was earned at.
          <router-link to="/admin/referrals">View referrals</router-link>
        </p>
        <div class="grid grid--2">
          <FormField label="Commission (% of first subscription payment)">
            <input v-model.number="referralForm.commissionPercent" type="number" step="0.01" min="0" max="100" />
          </FormField>
          <FormField label="Claim window (days after sign-up)">
            <input v-model.number="referralForm.claimWindowDays" type="number" step="1" min="1" max="365" />
            <p class="muted small">How long a new provider can still add a referral code from their portal.</p>
          </FormField>
        </div>
        <p v-if="referralMeta.updatedBy" class="muted small">
          Last changed by {{ referralMeta.updatedBy }}
          <span v-if="referralMeta.updatedAt">on {{ new Date(referralMeta.updatedAt).toLocaleString() }}</span>
        </p>
        <p v-if="referralError" class="err-toast">{{ referralError }}</p>
        <p v-if="referralMessage" class="ok-msg">{{ referralMessage }}</p>
        <button type="button" class="btn btn-ghost" :disabled="referralSaving" @click="saveReferralSettings">
          {{ referralSaving ? 'Saving…' : 'Save referral settings' }}
        </button>
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
    <DisplayStyleToggle />
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
.referral-block {
  margin-top: 1.25rem;
  padding-top: 0.25rem;
}
.referral-block .btn {
  margin-top: 0.6rem;
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
