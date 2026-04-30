<script setup>
import { ref, onMounted, computed } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '../stores/auth';
import FormField from '../components/ui/FormField.vue';
import { providerSubscriptionApi } from '../services/marketplaceApi';

const router = useRouter();
const auth = useAuthStore();

const loading = ref(true);
const error = ref('');
const message = ref('');

const status = ref(null);
const bank = ref(null);
const proofFile = ref(null);
const uploading = ref(false);
const showActivateDialog = ref(false);
const activating = ref(false);
const paymentDate = ref('');
const paymentAmount = ref('');
const paymentRef = ref('');
const quoteIntentId = ref(null);
/** Last subscription quote (plan price + usage fees) */
const quoteDetail = ref(null);

const selectForm = ref({
  plan: 'BASIC',
});

const isValid = computed(() => !!status.value?.valid);
const isPending = computed(() => status.value?.status === 'PENDING_VERIFICATION');
const isRejected = computed(() => status.value?.status === 'REJECTED');

const amountLabel = computed(() => {
  const n = status.value?.amountDue;
  if (n == null || n === '') return '—';
  return `R ${Number(n).toFixed(2)}`;
});

const dialogAmountText = computed(() => {
  if (paymentAmount.value != null && String(paymentAmount.value).trim() !== '') {
    const n = Number(paymentAmount.value);
    if (!Number.isFinite(n)) return '—';
    return `R ${n.toFixed(2)}`;
  }
  return amountLabel.value;
});

const dialogRefText = computed(() => {
  if (paymentRef.value && String(paymentRef.value).trim() !== '') return String(paymentRef.value);
  return status.value?.paymentReference || '—';
});

function formatMoney(v) {
  const n = typeof v === 'number' ? v : Number(v);
  if (!Number.isFinite(n)) return '—';
  // Keep formatting simple + consistent with ZAR style used elsewhere.
  return new Intl.NumberFormat('en-ZA', {
    style: 'currency',
    currency: 'ZAR',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(n);
}

function formatPercent(v) {
  const n = typeof v === 'number' ? v : Number(v);
  if (!Number.isFinite(n)) return '—';
  return `${n.toFixed(2)}%`;
}

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
    const [{ data: st }, { data: bk }] = await Promise.all([
      providerSubscriptionApi.status(),
      providerSubscriptionApi.bankDetails().catch(() => ({ data: null })),
    ]);
    status.value = st;
    bank.value = bk;
    if (st?.plan) {
      selectForm.value.plan = st.plan;
    }
    paymentAmount.value = st?.amountDue != null ? String(st.amountDue) : paymentAmount.value;
    paymentRef.value = st?.paymentReference ? String(st.paymentReference) : paymentRef.value;
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  } finally {
    loading.value = false;
  }
}

async function selectPlan() {
  error.value = '';
  message.value = '';
  activating.value = true;
  try {
    const { data } = await providerSubscriptionApi.select({
      plan: selectForm.value.plan,
      billingCycle: 'MONTHLY',
    });
    status.value = data;
    message.value = 'Plan selected. Please pay via bank transfer and upload proof of payment for verification.';
    paymentAmount.value = data?.amountDue != null ? String(data.amountDue) : '';
    paymentRef.value = data?.paymentReference || '';
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  } finally {
    activating.value = false;
  }
}

async function uploadProof() {
  if (!proofFile.value) return;
  uploading.value = true;
  error.value = '';
  message.value = '';
  try {
    await providerSubscriptionApi.uploadProof({
      file: proofFile.value,
      intentId: quoteIntentId.value,
    });
    message.value = 'Proof uploaded. If auto-verification succeeds your subscription will activate immediately; otherwise Support will verify it manually.';
    proofFile.value = null;
    await load();
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  } finally {
    uploading.value = false;
  }
}

function openActivateDialog() {
  quoteDetail.value = null;
  initPaymentFields();
  showActivateDialog.value = true;
}

function closeActivateDialog() {
  showActivateDialog.value = false;
}

async function activatePlan(plan) {
  selectForm.value.plan = plan;
  openActivateDialog();
  // Fetch a quote (amount + ref) without activating anything yet.
  try {
    const { data } = await providerSubscriptionApi.quote(plan);
    quoteIntentId.value = data?.intentId ?? null;
    quoteDetail.value = data;
    paymentAmount.value = data?.amountDue != null ? String(data.amountDue) : '';
    paymentRef.value = data?.paymentReference || '';
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  }
}

function initPaymentFields() {
  const today = new Date();
  const pad = (n) => String(n).padStart(2, '0');
  paymentDate.value = `${today.getFullYear()}-${pad(today.getMonth() + 1)}-${pad(today.getDate())}`;
  paymentAmount.value = status.value?.amountDue != null ? String(status.value.amountDue) : '';
  paymentRef.value = status.value?.paymentReference || '';
}
</script>

<template>
  <div class="page-document sub-page">
    <header class="page-hero sub-hero">
      <p class="page-hero__eyebrow">Provider</p>
      <h1 class="page-hero__title">Subscription</h1>
      <p class="page-hero__lead">
        Choose a plan to unlock workspace features. This page mirrors the Wheel Hub subscription flow (same sections),
        with our
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
            <strong>MONTHLY</strong>
          </div>
          <div class="sub-status__card">
            <span class="muted small">Status</span>
            <strong>
              <span v-if="isValid">Active</span>
              <span v-else-if="isPending">Pending verification</span>
              <span v-else-if="isRejected">Rejected</span>
              <span v-else>Not active</span>
            </strong>
          </div>
          <div class="sub-status__card">
            <span class="muted small">Expires</span>
            <strong>{{ status?.expiresAt ? String(status.expiresAt).slice(0, 19) : '—' }}</strong>
          </div>
        </div>
        <div class="sub-actions">
          <button type="button" class="btn btn-ghost" @click="load">Refresh</button>
          <button v-if="isValid" type="button" class="btn btn-primary" @click="router.push('/provider')">Continue
            →</button>
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
            <p class="muted small">Included in Basic:</p>
            <ul class="plan-features">
              <li>Provider dashboard</li>
              <li>Manage listings</li>
              <li>Manage orders</li>
              <li>Subscription & billing</li>
              <li>Business settings</li>
            </ul>
            <button type="button" class="btn btn-primary plan-cta" @click.prevent.stop="activatePlan('BASIC')">
              Activate Basic
            </button>
          </label>

          <label class="plan-card" :class="{ 'plan-card--selected': selectForm.plan === 'PREMIUM' }">
            <input v-model="selectForm.plan" type="radio" value="PREMIUM" />
            <div class="plan-card__head">
              <strong>Premium</strong>
              <span class="pill pill--gold">Pro</span>
            </div>
            <p class="muted small">Everything in Basic, plus:</p>
            <ul class="plan-features">
              <li>Team Management</li>
              <li>Payroll</li>
              <li>Staff Payments</li>
              <li>Priority support</li>
            </ul>
            <button type="button" class="btn btn-primary plan-cta" @click.prevent.stop="activatePlan('PREMIUM')">
              Activate Premium
            </button>
          </label>
        </div>

        <div class="sub-form" />
      </section>

      <dialog class="activate-dialog" :open="showActivateDialog" @cancel.prevent="closeActivateDialog">
        <div class="activate-dialog__backdrop" @click="closeActivateDialog" />
        <div class="activate-dialog__panel" role="document" aria-label="Activate subscription">
          <button type="button" class="activate-dialog__close" @click="closeActivateDialog"
            aria-label="Close">✕</button>
          <h2 class="activate-dialog__title">Activate subscription</h2>
          <p class="muted small">
            Pay using the admin banking details below, then upload your proof of payment. Your subscription only
            activates after verification succeeds.
          </p>



       <div class="amount-card">

  <!-- LEFT: Amount Info -->
  <div class="amount-card__section">
    <span class="label">Amount to pay (this quote)</span>

    <div class="amount-card__value">
      {{ dialogAmountText }}
    </div>

    <div class="meta">
      <div><span>Plan</span><strong>{{ formatMoney(quoteDetail?.baseMonthly ?? 0) }}</strong></div>
      <div><span>Paid orders</span><strong>{{ quoteDetail?.paidTransactionsCounted ?? 0 }}</strong></div>
      <div><span>Volume</span><strong>{{ formatMoney(quoteDetail?.paidOrderTotalsSum ?? 0) }}</strong></div>
      <div><span>Usage %</span><strong>{{ formatPercent(quoteDetail?.usageFeePercent ?? 0) }}</strong></div>
      <div><span>Usage fee</span><strong>{{ formatMoney(quoteDetail?.usageFeesTotal ?? 0) }}</strong></div>
    </div>

    <div class="meta meta--secondary">
      <div><span>Date</span><strong>{{ paymentDate || '—' }}</strong></div>
      <div><span>Plan</span><strong>{{ selectForm.plan }}</strong></div>
      <div><span>Cycle</span><strong>MONTHLY</strong></div>
    </div>
  </div>

  <!-- RIGHT: Bank Info -->
  <div class="amount-card__section">
    <span class="label">Bank Details</span>

    <div class="meta">
      <div><span>Bank Name</span><strong>{{ bank?.bankName || '—' }}</strong></div>
      <div><span>Account Name</span><strong>{{ bank?.accountName || '—' }}</strong></div>
      <div><span>Account Number</span><strong>{{ bank?.accountNumber || '—' }}</strong></div>
      <div><span>Branch Code</span><strong>{{ bank?.branchCode || '—' }}</strong></div>
    </div>
  </div>

</div>

          <div class="ref-card">
            <span class="muted small">Auto-generated reference</span>
            <strong class="ref-card__value">{{ dialogRefText }}</strong>
            <p class="muted tiny">
              Use this exact reference when paying. We will verify it against your proof of payment.
            </p>
          </div>

          <div class="proof-upload proof-upload--dialog">
            <h3 class="proof-upload__title">Upload proof of payment</h3>
            <FormField label="Proof file (image or PDF)">
              <input type="file" accept="image/*,application/pdf"
                @change="(e) => (proofFile = e.target.files?.[0] || null)" />
            </FormField>
            <button type="button" class="btn btn-primary"
              :disabled="!quoteIntentId || !proofFile || uploading || activating" @click="uploadProof">
              {{ uploading ? 'Uploading…' : activating ? 'Preparing…' : 'Submit proof' }}
            </button>
          </div>
        </div>
      </dialog>
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
  align-items: stretch;
  grid-auto-rows: 1fr;
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
  height: 100%;
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

.plan-features {
  margin: 0.15rem 0 0;
  padding-left: 1.05rem;
  display: grid;
  gap: 0.2rem;
  color: var(--color-text-secondary);
  font-size: 0.88rem;
  line-height: 1.35;
}

.plan-features li {
  margin: 0;
}

.plan-cta {
  margin-top: auto;
  align-self: flex-end;
  width: fit-content;
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

.bank-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.75rem;
  margin-top: 0.85rem;
}

.bank-row {
  border: 1px solid var(--color-border);
  background: var(--color-surface-elevated);
  border-radius: 12px;
  padding: 0.75rem 0.85rem;
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.proof-upload {
  margin-top: 1rem;
  display: flex;
  align-items: flex-end;
  gap: 0.85rem;
  flex-wrap: wrap;
}

@media (max-width: 900px) {
  .bank-grid {
    grid-template-columns: 1fr;
  }
}

.activate-dialog {
  all: revert;
  border: none;
  padding: 0;
  margin: 0;
  background: transparent;
}

.activate-dialog:not([open]) {
  display: none;
}

.activate-dialog::backdrop {
  background: rgba(0, 0, 0, 0.55);
  backdrop-filter: blur(2px);
}

.activate-dialog__backdrop {
  position: fixed;
  inset: 0;
}

.activate-dialog__panel {
  position: fixed;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  width: min(94vw, 46rem);
  max-height: min(80vh, 44rem);
  overflow: auto;
  padding: 1.05rem 1.05rem 1rem;
  border-radius: var(--radius-lg, 16px);
  border: 1px solid var(--color-border);
  background: var(--color-surface-elevated);
  color: var(--color-text, #111827);
  box-shadow: var(--shadow-lg);
}

.activate-dialog__close {
  position: sticky;
  top: 0;
  margin-left: auto;
  display: block;
  border: none;
  background: rgba(26, 60, 52, 0.08);
  color: var(--color-canopy, #1a3c34);
  width: 36px;
  height: 36px;
  border-radius: 999px;
  cursor: pointer;
  font-size: 1.1rem;
  line-height: 1;
}

.activate-dialog__title {
  margin: 0.35rem 0 0.4rem;
  font-family: var(--font-display);
  color: var(--color-canopy);
}

.amount-card {
  display: flex;
  border: 1px solid rgba(61, 122, 102, 0.22);
  background: rgba(61, 122, 102, 0.07);
  border-radius: 14px;
  padding: 0.85rem 0.9rem;
  margin-top: 0.85rem;
}

.amount-card__value {
  display: block;
  font-size: 1.35rem;
  margin-top: 0.15rem;
}

.ref-card {
  border: 1px solid rgba(26, 60, 52, 0.18);
  background: rgba(26, 60, 52, 0.05);
  border-radius: 14px;
  padding: 0.85rem 0.9rem;
  margin-top: 0.75rem;
}

.ref-card__value {
  display: block;
  margin-top: 0.2rem;
  font-size: 1.05rem;
  letter-spacing: 0.02em;
  word-break: break-word;
}

.dialog-actions {
  margin-top: 0.95rem;
  display: flex;
  gap: 0.6rem;
  flex-wrap: wrap;
}

.proof-upload--dialog {
  margin-top: 1.05rem;
  padding-top: 0.9rem;
  border-top: 1px dashed var(--color-border);
}

.proof-meta {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0.75rem;
  margin-bottom: 0.75rem;
}

.proof-meta__row {
  border: 1px solid var(--color-border);
  background: var(--color-surface-elevated);
  border-radius: 12px;
  padding: 0.7rem 0.75rem;
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

@media (max-width: 900px) {
  .proof-meta {
    grid-template-columns: 1fr;
  }
}

.proof-upload__title {
  margin: 0 0 0.65rem;
  font-size: 1rem;
  color: var(--color-canopy);
}
</style>
