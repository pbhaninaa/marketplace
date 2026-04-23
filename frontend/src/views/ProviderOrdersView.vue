<script setup>
import { ref, onMounted, computed } from 'vue';
import { useRouter } from 'vue-router';
import { providerOrdersApi } from '../services/marketplaceApi';
import { useAuthStore } from '../stores/auth';
import ResponsiveRecordShell from '../components/layout/ResponsiveRecordShell.vue';
import DataTableShell from '../components/ui/DataTableShell.vue';

const router = useRouter();
const auth = useAuthStore();

/* ================= STATE ================= */
const tab = ref('purchases');
const loading = ref(true);
const error = ref('');

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
const deleteConfirmType = ref('single'); // 'single' or 'all'

/* ================= STATE ================= */
const deleteAllLoading = ref(false);
const deleteAllError = ref('');
const deleteAllSuccess = ref('');

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

const orderItems = computed(() => {
  const o = selectedOrder.value;
  if (!o) return [];
  const list = o.items || o.lines || o.orderLines || o.lineItems || [];
  if (!Array.isArray(list)) return [];
  return list.map((it) => ({
    ...it,
    title:
      it.title ||
      it.listingTitle ||
      it.name ||
      it.listingTitleSnapshot ||
      it.listing?.title ||
      'Item',
  }));
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
    const { data } =
      tab.value === 'rentals'
        ? await providerOrdersApi.getRental(order.id)
        : await providerOrdersApi.getPurchase(order.id);
    selectedOrder.value = data || order;
  } catch (e) {
    // Keep fallback row data if endpoint is unavailable.
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
  actionLoading.value = true;
  actionError.value = '';
  try {
    if (tab.value !== 'purchases') {
      throw new Error('Confirm is currently supported for purchases only.');
    }
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
    if (tab.value !== 'purchases') {
      throw new Error('Fulfill is currently supported for purchases only.');
    }
    await providerOrdersApi.updatePurchaseStatus(selectedOrder.value.id, 'FULFILLED');
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
    if (tab.value !== 'purchases') {
      throw new Error('Reject is currently supported for purchases only.');
    }
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
  deleteConfirmType.value = 'single';
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

async function deleteAllPurchases() {
  showDeleteConfirm.value = true;
  deleteConfirmType.value = 'all-purchases';
  deleteConfirmPending.value = false;
}

async function confirmDeleteAllPurchases() {
  deleteConfirmPending.value = true;
  deleteAllLoading.value = true;
  deleteAllError.value = '';
  deleteAllSuccess.value = '';

  try {
    const { data } = await providerOrdersApi.deleteAllPurchases();
    deleteAllSuccess.value = `Successfully deleted ${data.deletedCount} purchase orders.`;
    await load();
    showDeleteConfirm.value = false;
  } catch (e) {
    deleteAllError.value = e.response?.data?.message || e.message;
  } finally {
    deleteAllLoading.value = false;
    deleteConfirmPending.value = false;
  }
}

async function deleteAllRentals() {
  showDeleteConfirm.value = true;
  deleteConfirmType.value = 'all-rentals';
  deleteConfirmPending.value = false;
}

async function confirmDeleteAllRentals() {
  deleteConfirmPending.value = true;
  deleteAllLoading.value = true;
  deleteAllError.value = '';
  deleteAllSuccess.value = '';

  try {
    const { data } = await providerOrdersApi.deleteAllRentals();
    deleteAllSuccess.value = `Successfully deleted ${data.deletedCount} rental orders.`;
    await load();
    showDeleteConfirm.value = false;
  } catch (e) {
    deleteAllError.value = e.response?.data?.message || e.message;
  } finally {
    deleteAllLoading.value = false;
    deleteConfirmPending.value = false;
  }
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

      <!-- DELETE ALL BUTTONS -->
      <div class="delete-all-section">
        <button
          v-if="tab === 'purchases' && purchases.totalElements > 0"
          class="btn btn--danger"
          @click="deleteAllPurchases"
          :disabled="deleteAllLoading"
        >
          {{ deleteAllLoading ? 'Deleting…' : 'Delete All Purchases' }}
        </button>

        <button
          v-if="tab === 'rentals' && rentals.totalElements > 0"
          class="btn btn--danger"
          @click="deleteAllRentals"
          :disabled="deleteAllLoading"
        >
          {{ deleteAllLoading ? 'Deleting…' : 'Delete All Rentals' }}
        </button>
      </div>

      <!-- DELETE ALL MESSAGES -->
      <p v-if="deleteAllError" class="err-toast">{{ deleteAllError }}</p>
      <p v-if="deleteAllSuccess" class="toast success">{{ deleteAllSuccess }}</p>

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
            <article v-for="o in rows" :key="o.id" class="order-card">
              <strong>#{{ o.id }} · {{ o.status }}</strong>
              <span class="meta">{{ o.guestEmail }}</span>
              <span class="meta">R {{ o.total }}</span>

              <button class="icon-btn" @click="openDetails(o)">
                <span class="material-icons">visibility</span>
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

            <div v-if="orderItems.length" style="margin-top: 0.75rem;">
              <strong>Items</strong>
              <ul style="margin: 0.4rem 0 0; padding-left: 1.1rem;">
                <li v-for="(it, idx) in orderItems" :key="it.id || it.lineId || idx">
                  {{ it.title || it.listingTitle || it.name || 'Item' }}
                  <span v-if="it.quantity != null"> · Qty {{ it.quantity }}</span>
                  <span v-if="it.unitPrice != null"> · R {{ it.unitPrice }}</span>
                  <span v-if="it.lineTotal != null"> · Line R {{ it.lineTotal }}</span>
                </li>
              </ul>
            </div>

            <div v-if="isPendingPayment && paymentProofUrl" style="margin-top: 0.75rem;">
              <strong>Payment proof</strong>
              <div style="margin-top: 0.35rem;">
                <input type="text" :value="paymentProofUrl" readonly @click="$event.target.select()" />
              </div>
              <iframe
                v-if="isPaymentProofPdf"
                :src="paymentProofUrl"
                title="Payment proof PDF"
                style="width: 100%; height: 320px; margin-top: 0.5rem; border-radius: 6px; border: 1px solid rgba(0,0,0,0.15);"
              />
              <img
                v-else
                :src="paymentProofUrl"
                alt="Payment proof"
                style="max-width: 100%; margin-top: 0.5rem; border-radius: 4px;"
              />
            </div>

            <div v-if="isPendingPayment && showPurchaseActions" style="margin-top: 0.9rem;">
              <strong>Order actions</strong>
              <p class="muted small" style="margin: 0.25rem 0 0.4rem;">
                Meetup verification is parked for now. Use <strong>Confirm Payment</strong> to move this order to PAID.
                After the purchase is completed, use <strong>Fulfill Order</strong> to mark the order as FULFILLED.
              </p>
            </div>

            <p v-if="actionError" class="err-toast" style="margin-top: 0.75rem;">
              {{ actionError }}
            </p>

          </div>

          <!-- ACTIONS -->
          <div class="dialog-actions">
            
            <button
              class="btn btn--danger"
              @click="rejectOrder"
              :disabled="actionLoading || !canRejectOrder"
            >
              {{ actionLoading ? 'Working…' : 'Reject Order' }}
            </button>
            <button
              class="btn btn--danger btn--outline"
              @click="deleteOrderById"
              :disabled="actionLoading || !canDeleteOrder"
            >
              {{ actionLoading ? 'Working…' : 'Delete Order' }}
            </button>

            <button
              class="btn btn--primary"
              v-if="isPendingPayment"
              @click="confirmOrder"
              :disabled="actionLoading || !canConfirmPayment"
            >
              {{ actionLoading ? 'Working…' : 'Confirm Payment' }}
            </button>
            <button
              class="btn btn--primary"
              v-else-if="isPaid"
              @click="fulfillOrder"
              :disabled="actionLoading || !canFulfillOrder"
            >
              {{ actionLoading ? 'Working…' : 'Fulfill Order' }}
            </button>
            <button class="btn btn--ghost" @click="closeDialog">Close</button>
          </div>

        </div>
      </div>

      <!-- ================= DELETE CONFIRMATION DIALOG ================= -->
      <div v-if="showDeleteConfirm" class="dialog-backdrop" @click.self="cancelDeleteOrder">
        <div class="surface-panel dialog" style="max-width: 420px;">
          <h2>Confirm Delete</h2>
          
          <div class="dialog-content">
            <p v-if="deleteConfirmType === 'single'">
              <strong>Delete this order permanently?</strong>
              <br />
              This will remove order
              <strong>#{{ selectedOrder?.id }}</strong>
              and all associated payment records. This action cannot be undone.
            </p>
            <p v-else-if="deleteConfirmType === 'all-purchases'">
              <strong>Delete ALL purchase orders?</strong>
              <br />
              This will remove all cancelled and pending payment orders and their associated payment records.
              This action cannot be undone.
            </p>
            <p v-else-if="deleteConfirmType === 'all-rentals'">
              <strong>Delete ALL rental bookings?</strong>
              <br />
              This will remove all cancelled and pending payment rental bookings.
              This action cannot be undone.
            </p>
          </div>

          <div class="dialog-actions">
            <button
              class="btn btn--danger"
              @click="
                deleteConfirmType === 'single' ? confirmDeleteOrder() :
                deleteConfirmType === 'all-purchases' ? confirmDeleteAllPurchases() :
                deleteConfirmType === 'all-rentals' ? confirmDeleteAllRentals() : null
              "
              :disabled="deleteConfirmPending || (deleteConfirmType === 'all-purchases' ? deleteAllLoading : deleteConfirmType === 'all-rentals' ? deleteAllLoading : actionLoading)"
            >
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
  background: rgba(0,0,0,0.4);
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
</style>