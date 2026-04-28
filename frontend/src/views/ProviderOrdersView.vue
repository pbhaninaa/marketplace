<script setup>
import { ref, onMounted, computed } from 'vue';
import { useRouter } from 'vue-router';
import { providerOrdersApi } from '../services/marketplaceApi';
import { useAuthStore } from '../stores/auth';
import ResponsiveRecordShell from '../components/layout/ResponsiveRecordShell.vue';
import DataTableShell from '../components/ui/DataTableShell.vue';
import FormField from '../components/ui/FormField.vue';
const router = useRouter();
const auth = useAuthStore();

/* ================= STATE ================= */
const tab = ref('purchases');
const loading = ref(true);
const error = ref('');
const customerCode = ref('');
const showCodeError = ref(false);
const codeErrorMessage = ref('');
const orderItems = ref([]);

const purchases = ref({ content: [], totalElements: 0 });
const rentals = ref({ content: [], totalElements: 0 });

const selectedOrder = ref(null);
const showDialog = ref(false);
const actionLoading = ref(false);
const actionError = ref('');
const detailsLoading = ref(false);
const detailsError = ref('');

/* ================= STATE: CONFIRMATION DIALOGS ================= */
const showDeleteConfirm = ref(false);
const deleteConfirmPending = ref(false);


/* ================= DATA ================= */
const rows = computed(() =>
  tab.value === 'purchases'
    ? purchases.value.content
    : rentals.value.content
);

const isPendingPayment = computed(() => String(selectedOrder.value?.status || '').toUpperCase() === 'PENDING_PAYMENT');
const isPaid = computed(() => String(selectedOrder.value?.status || '').toUpperCase() === 'PAID');
const isRental = computed(() => tab.value === 'rentals');
const isPurchase = computed(() => tab.value === 'purchases');
const showPurchaseActions = computed(() => isPurchase.value && !!selectedOrder.value);
/** Reject/cancel allowed for pending payment or paid orders. */
const canRejectOrder = computed(() => {
  if (!selectedOrder.value) return false;
  return isPurchase.value && (isPendingPayment.value || isPaid.value);
});
const canDeleteOrder = computed(() => !!selectedOrder.value);
/** Confirm payment deducts stock (and may remove listing when sold out); verification is temporarily skipped. */
const canConfirmPayment = computed(() => {
  return isPurchase.value && isPendingPayment.value;
});
const canFulfillOrder = computed(() => {
  return isPurchase.value && isPaid.value;
});



const paymentProofUrl = computed(() => String(selectedOrder.value?.paymentProofUrl || '').trim());
const isPaymentProofPdf = computed(() => paymentProofUrl.value.toLowerCase().endsWith('.pdf'));

/* ================= INIT ================= */
onMounted(async () => {
  auth.restoreFromStorage();
  if (!auth.isProviderUser) {
    router.replace({ path: '/login', query: { redirect: '/provider/orders' } });
    return;
  }
  await load();
});

/* ================= LOAD ================= */
async function load() {
  loading.value = true;
  error.value = '';

  try {
    const [p, r] = await Promise.all([
      providerOrdersApi.listPurchases({ page: 0, size: 50 }),
      providerOrdersApi.listRentals({ page: 0, size: 50 }),
    ]);

    purchases.value = p.data;
    rentals.value = r.data;
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  } finally {
    loading.value = false;
  }
}

/* ================= ACTIONS ================= */
async function openDetails(order) {
  selectedOrder.value = order;
  showDialog.value = true;
  actionError.value = '';
  detailsError.value = '';

  // Try to hydrate full order details (items, proof, etc).
  detailsLoading.value = true;
  try {
    orderItems.value = await providerOrdersApi.getOrderItems(order.id);
    const { data } =
      tab.value === 'rentals'
        ? await providerOrdersApi.getRental(order.id)
        : await providerOrdersApi.getPurchase(order.id);
    selectedOrder.value = data || order;
  } catch (e) {
    detailsError.value = e.response?.data?.message || '';
  } finally {
    detailsLoading.value = false;
  }
}

function closeDialog() {
  showDialog.value = false;
  selectedOrder.value = null;
  actionLoading.value = false;
  actionError.value = '';
  detailsLoading.value = false;
  detailsError.value = '';
}

async function confirmOrder() {
  if (!selectedOrder.value) return;

  const code = customerCode.value?.trim();
  const expected = selectedOrder.value.verificationCode;

  if (!code || code !== expected) {
    codeErrorMessage.value = 'Invalid customer verification code. Please try again.';
    showCodeError.value = true;
    return;
  }

  actionLoading.value = true;
  actionError.value = '';

  try {
    await providerOrdersApi.updatePurchaseStatus(selectedOrder.value.id, 'PAID');
    await load();
    closeDialog();
  } catch (e) {
    actionError.value = e.response?.data?.message || e.message;
  } finally {
    actionLoading.value = false;
  }
}

async function fulfillOrder() {
  if (!selectedOrder.value) return;
  actionLoading.value = true;
  actionError.value = '';
  try {
    await providerOrdersApi.updatePurchaseStatus(selectedOrder.value.id, 'COLLECTED');

    await load();
    closeDialog();
  } catch (e) {
    actionError.value = e.response?.data?.message || e.message;
  } finally {
    actionLoading.value = false;
  }
}

async function rejectOrder() {
  if (!selectedOrder.value) return;
  actionLoading.value = true;
  actionError.value = '';
  try {
    await providerOrdersApi.updateStock(orderItems.value.data);

    await providerOrdersApi.updatePurchaseStatus(selectedOrder.value.id, 'CANCELLED');

    await load();
    closeDialog();
  } catch (e) {
    actionError.value = e.response?.data?.message || e.message;
  } finally {
    actionLoading.value = false;
  }
}

async function deleteOrderById() {
  if (!selectedOrder.value) return;
  showDeleteConfirm.value = true;
  deleteConfirmPending.value = false;
}

async function confirmDeleteOrder() {
  if (!selectedOrder.value) return;
  deleteConfirmPending.value = true;
  actionLoading.value = true;
  actionError.value = '';
  try {
    await providerOrdersApi.deletePurchase(selectedOrder.value.id);
    await load();
    showDeleteConfirm.value = false;
    closeDialog();
  } catch (e) {
    actionError.value = e.response?.data?.message || e.message;
  } finally {
    actionLoading.value = false;
    deleteConfirmPending.value = false;
  }
}

function cancelDeleteOrder() {
  showDeleteConfirm.value = false;
  deleteConfirmPending.value = false;
}
</script>

<template>
  <div class="page-document page-document--wide provider-orders-page">

    <!-- HEADER -->
    <header class="page-hero">
      <p class="page-hero__eyebrow">Provider</p>
      <h1 class="page-hero__title">Orders & bookings</h1>
      <p class="page-hero__lead">
        View guest purchases and rental bookings for your listings.
      </p>
    </header>

    <p v-if="error" class="err-toast">{{ error }}</p>
    <p v-if="loading" class="muted loading-line">Loading…</p>

    <template v-else>

      <!-- ✅ YOUR PILLS (UNCHANGED) -->
      <div class="tabs">
        <button class="tab" :class="{ active: tab === 'purchases' }" @click="tab = 'purchases'">
          Purchases ({{ purchases.totalElements || 0 }})
        </button>

        <button class="tab" :class="{ active: tab === 'rentals' }" @click="tab = 'rentals'">
          Rentals ({{ rentals.totalElements || 0 }})
        </button>
      </div>




      <!-- TABLE -->
      <ResponsiveRecordShell :desktop-label="tab === 'purchases' ? 'Purchase orders' : 'Rental bookings'">

        <template #desktop>
          <DataTableShell :caption="tab === 'purchases' ? 'Purchase orders' : 'Rental bookings'">
            <thead>
              <tr>
                <th>Date</th>
                <th>Name</th>
                <th>Phone</th>
                <th>Email</th>
                <th>Status</th>
                <th>Delivery</th>
                <th>Delivery Address</th>
                <th class="col-num">Total</th>
                <th v-if="tab === 'rentals'">Window</th>
                <th>Actions</th>
              </tr>
            </thead>

            <tbody>
              <tr v-for="o in rows" :key="o.id">
                <td>{{ String(o.createdAt || '').slice(0, 19) }}</td>
                <td>{{ o.guestName || '—' }}</td>
                <td>{{ o.guestPhone }}</td>
                <td>{{ o.guestEmail || '—' }}</td>
                <td>{{ o.status }}</td>
                <td>{{ o.includesDelivery || '—' }}</td>

                <td>{{ o.deliveryAddress || o.deliveryAddress? o.deliveryAddress:'Call : ' + o.guestPhone }}</td>
                <td class="col-num">R {{ o.total }}</td>
                <td v-if="tab === 'rentals'">
                  {{ String(o.startAt || '').slice(0, 16) }} →
                  {{ String(o.endAt || '').slice(0, 16) }}
                </td>

                <td>
                  <!-- 👁 VIEW BUTTON (CLEAN) -->
                  <button class="icon-btn" @click="openDetails(o)">
                    <span class="material-icons">visibility</span>
                  </button>

                </td>
              </tr>

              <tr v-if="!rows.length">
                <td :colspan="tab === 'rentals' ? 8 : 7" class="muted small">
                  No records yet.
                </td>
              </tr>
            </tbody>
          </DataTableShell>
        </template>

        <!-- MOBILE -->
        <template #mobile>
          <div class="cards">
            <article v-for="o in rows" :key="o.id" class="order-card mobile-expanded">
              <!-- Header with Order ID and Status -->
              <div class="order-card__header">
                <strong>#{{ o.id }}</strong>
                <span class="order-card__status" :class="`status-${o.status.toLowerCase()}`">
                  {{ o.status }}
                </span>
              </div>

              <!-- Guest Information -->
              <div class="order-card__section">
                <h3 class="section-title">Guest</h3>
                <div class="info-row">
                  <span class="label">Name:</span>
                  <span class="value">{{ o.guestName || '—' }}</span>
                </div>
                <div class="info-row">
                  <span class="label">Email:</span>
                  <span class="value">{{ o.guestEmail || '—' }}</span>
                </div>
                <div class="info-row">
                  <span class="label">Phone:</span>
                  <span class="value">{{ o.guestPhone }}</span>
                </div>
              </div>

              <!-- Order Information -->
              <div class="order-card__section">
                <h3 class="section-title">Order</h3>
                <div class="info-row">
                  <span class="label">Date:</span>
                  <span class="value">{{ String(o.createdAt || '').slice(0, 19) }}</span>
                </div>
                <div class="info-row">
                  <span class="label">Delivery:</span>
                  <span class="value">{{ o.includesDelivery || '—' }}</span>
                </div>
                <div v-if="o.includesDelivery == 'DELIVERY'" class="info-row">
                  <span class="label">Address:</span>
                  <span class="value">{{ o.deliveryAddress || 'Call : ' + o.guestPhone }}</span>
                </div>
                <div class="info-row">
                  <span class="label">Total:</span>
                  <span class="value amount">R {{ o.total }}</span>
                </div>
              </div>

              <!-- Rental Window (if rental) -->
              <div v-if="tab === 'rentals'" class="order-card__section">
                <h3 class="section-title">Booking Period</h3>
                <div class="info-row">
                  <span class="label">From:</span>
                  <span class="value">{{ String(o.startAt || '').slice(0, 16) }}</span>
                </div>
                <div class="info-row">
                  <span class="label">To:</span>
                  <span class="value">{{ String(o.endAt || '').slice(0, 16) }}</span>
                </div>
              </div>

              <!-- Action Button -->
              <button class="btn btn-primary-mobile" @click="openDetails(o)">
                View Full Details
              </button>
            </article>
          </div>
        </template>

      </ResponsiveRecordShell>

      <!-- ================= MODAL ================= -->
      <div v-if="showDialog" class="dialog-backdrop" @click.self="closeDialog">
        <div class="surface-panel dialog">

          <h2>Order Details</h2>

          <div v-if="selectedOrder" class="dialog-content">

            <p><strong>Name:</strong> {{ selectedOrder.guestName }}</p>
            <p><strong>Phone:</strong> {{ selectedOrder.guestPhone }}</p>
            <p><strong>Email:</strong> {{ selectedOrder.guestEmail }}</p>
            <p><strong>Status:</strong> {{ selectedOrder.status }}</p>
            <p><strong>Total:</strong> R {{ selectedOrder.total }}</p>

            <div v-if="tab === 'rentals'">
              <p><strong>Start:</strong> {{ selectedOrder.startAt }}</p>
              <p><strong>End:</strong> {{ selectedOrder.endAt }}</p>
            </div>

            <p v-if="detailsLoading" class="muted small">Loading full order details…</p>
            <p v-else-if="detailsError" class="muted small">Some details could not be loaded. Showing summary.</p>

            <div style="margin-top: 0.75rem;">
              <ol style="padding-left: 1.2rem;">
                <li v-for="(orderItem, index) in orderItems.data" :key="index">
                  {{ orderItem.quantity }} {{ orderItem.listingName }}{{ orderItem.quantity > 1 ? 's' : '' }}
                </li>
              </ol>
            </div>

            <div v-if="isPendingPayment && paymentProofUrl" style="margin-top: 0.75rem;">
              <strong>Payment proof</strong>
              <div style="margin-top: 0.35rem;">
                <input type="text" :value="paymentProofUrl" readonly @click="$event.target.select()" />
              </div>
              <iframe v-if="isPaymentProofPdf" :src="paymentProofUrl" title="Payment proof PDF"
                style="width: 100%; height: 320px; margin-top: 0.5rem; border-radius: 6px; border: 1px solid rgba(0,0,0,0.15);" />
              <img v-else :src="paymentProofUrl" alt="Payment proof"
                style="max-width: 100%; margin-top: 0.5rem; border-radius: 4px;" />
            </div>
            <!-- CODE ERROR DIALOG -->
            <div v-if="showCodeError" class="dialog-backdrop" @click.self="showCodeError = false">
              <div class="surface-panel dialog" style="max-width: 360px;">

                <h2>Verification Failed</h2>

                <div class="dialog-content">
                  <p class="err-toast">
                    {{ codeErrorMessage }}
                  </p>
                </div>

                <div class="dialog-actions">
                  <button class="btn btn--primary" @click="showCodeError = false">
                    OK
                  </button>
                </div>

              </div>
            </div>
            <div v-if="isPendingPayment" style="margin-top: 0.9rem;">

              <FormField label="Customer Code">
                <input v-model="customerCode" type="text" placeholder="Enter Customer Code" />
              </FormField>

            </div>

            <p v-if="actionError" class="err-toast" style="margin-top: 0.75rem;">
              {{ actionError }}
            </p>

          </div>

          <!-- ACTIONS -->
          <div class="dialog-actions">

            <!-- PENDING PAYMENT -->
            <template v-if="selectedOrder?.status === 'PENDING_PAYMENT'">

              <button class="btn btn--danger" @click="rejectOrder" :disabled="actionLoading">
                {{ actionLoading ? 'Working…' : 'Reject Order' }}
              </button>

              <button class="btn btn--primary" @click="confirmOrder" :disabled="actionLoading">
                {{ actionLoading ? 'Working…' : 'Confirm Payment' }}
              </button>

              <button class="btn btn--ghost" @click="closeDialog">
                Close
              </button>

            </template>

            <!-- PAID -->
            <template v-else-if="selectedOrder?.status === 'PAID'">

              <button class="btn btn--primary" @click="fulfillOrder" :disabled="actionLoading">
                {{ actionLoading ? 'Working…' : 'Fulfil Order' }}
              </button>

              <button class="btn btn--ghost" @click="closeDialog">
                Close
              </button>

            </template>

            <!-- CANCELLED -->
            <template v-else-if="selectedOrder?.status === 'CANCELLED'">

              <button class="btn btn--danger" @click="deleteOrderById" :disabled="actionLoading">
                {{ actionLoading ? 'Working…' : 'Delete Order' }}
              </button>

              <button class="btn btn--ghost" @click="closeDialog">
                Close
              </button>

            </template>

            <!-- DEFAULT FALLBACK -->
            <template v-else>
              <button class="btn btn--ghost" @click="closeDialog">
                Close
              </button>
            </template>

          </div>

        </div>
      </div>

      <!-- ================= DELETE CONFIRMATION DIALOG ================= -->
      <div v-if="showDeleteConfirm" class="dialog-backdrop" @click.self="cancelDeleteOrder">
        <div class="surface-panel dialog" style="max-width: 420px;">
          <h2>Confirm Delete</h2>

          <div class="dialog-content">
            <p>
              <strong>Delete this order permanently?</strong>
              <br />
              This will remove order
              <strong>#{{ selectedOrder?.id }}</strong>
              and all associated payment records. This action cannot be undone.
            </p>

          </div>

          <div class="dialog-actions">
            <button class="btn btn--danger" @click="confirmDeleteOrder()"
              :disabled="deleteConfirmPending || actionLoading">
              {{ deleteConfirmPending ? 'Deleting…' : 'Delete' }}
            </button>
            <button class="btn btn--ghost" @click="cancelDeleteOrder" :disabled="deleteConfirmPending">
              Cancel
            </button>
          </div>
        </div>
      </div>

    </template>
  </div>
</template>

<style scoped>
/* KEEP YOUR PILLS */
.tabs {
  display: inline-flex;
  gap: 0.5rem;
  padding: 0.25rem;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-surface-elevated);
  margin-bottom: 1rem;
}

.tab {
  border: none;
  background: transparent;
  cursor: pointer;
  font-weight: 700;
  padding: 0.5rem 0.85rem;
  border-radius: 999px;
  color: var(--color-muted);
}

.tab.active {
  background: rgba(61, 122, 102, 0.14);
  color: var(--color-canopy);
}

/* DELETE ALL SECTION */
.delete-all-section {
  margin-bottom: 1rem;
  padding: 1rem;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-surface-elevated);
}

.delete-all-section .btn--danger {
  background: #dc3545;
  color: white;
  border: none;
  padding: 0.75rem 1.5rem;
  border-radius: 6px;
  cursor: pointer;
  font-weight: 600;
}

.delete-all-section .btn--danger:hover:not(:disabled) {
  background: #c82333;
}

.delete-all-section .btn--danger:disabled {
  background: #6c757d;
  cursor: not-allowed;
}

/* ICON BUTTON */
.icon-btn {
  background: none;
  border: none;
  cursor: pointer;
  color: var(--color-muted);
}

.icon-btn:hover {
  color: var(--color-canopy);
}

/* MODAL */
.dialog-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
}

.dialog {
  width: 420px;
  max-width: 95%;
}

.dialog-content p {
  margin: 0.4rem 0;
}

.dialog-actions {
  margin-top: 1rem;
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
}

/* ============================================
   MOBILE CARD STYLING - ALWAYS EXPANDED
   ============================================ */

.order-card {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 1rem;
  background: var(--color-surface-elevated);
  box-shadow: var(--shadow-sm);
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.order-card.mobile-expanded {
  padding: 1.25rem;
  gap: 1rem;
}

.order-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding-bottom: 0.75rem;
  border-bottom: 1px solid var(--color-border);
}

.order-card__header strong {
  font-size: 1.1rem;
  color: var(--color-text);
}

.order-card__status {
  display: inline-block;
  padding: 0.4rem 0.8rem;
  border-radius: var(--radius-pill);
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.02em;
}

.order-card__status.status-pending_payment {
  background: var(--color-wheat-soft);
  color: var(--color-earth);
}

.order-card__status.status-paid {
  background: var(--color-success-bg);
  color: var(--color-success-text);
}

.order-card__status.status-fulfilled {
  background: var(--color-sage-soft);
  color: var(--color-canopy);
}

.order-card__status.status-cancelled {
  background: var(--color-danger-bg);
  color: var(--color-danger-text);
}

.order-card__section {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.section-title {
  margin: 0;
  font-size: 0.85rem;
  font-weight: 600;
  text-transform: uppercase;
  color: var(--color-muted);
  letter-spacing: 0.02em;
}

.info-row {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  font-size: 0.95rem;
}

.info-row .label {
  font-weight: 500;
  color: var(--color-text-secondary);
  flex-shrink: 0;
}

.info-row .value {
  text-align: right;
  color: var(--color-text);
  flex-shrink: 1;
  word-break: break-word;
}

.info-row .value.amount {
  font-weight: 600;
  color: var(--color-canopy);
}

.btn-primary-mobile {
  display: block;
  width: 100%;
  padding: 0.75rem 1rem;
  margin-top: 0.5rem;
  background: var(--color-sage);
  color: #ffffff;
  border: none;
  border-radius: var(--radius-md);
  font-weight: 600;
  font-size: 0.95rem;
  cursor: pointer;
  transition: all 0.15s cubic-bezier(0.22, 1, 0.36, 1);
}

.btn-primary-mobile:hover {
  background: var(--color-canopy);
  box-shadow: var(--shadow-md);
}

.btn-primary-mobile:active {
  transform: scale(0.98);
}

@media (max-width: 980px) {
  .tabs {
    display: flex;
    justify-content: center;
    width: fit-content;
    margin: 0 auto;
  }
}
</style>