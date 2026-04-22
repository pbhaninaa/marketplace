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
const confirmIdInput = ref('');
const orderVerifyCode = ref('');
const orderVerifying = ref(false);
const orderVerifyError = ref('');
const orderVerifySuccess = ref('');

/* ================= DATA ================= */
const rows = computed(() =>
  tab.value === 'purchases'
    ? purchases.value.content
    : rentals.value.content
);

const isPendingPayment = computed(() => String(selectedOrder.value?.status || '').toUpperCase() === 'PENDING_PAYMENT');
const isRental = computed(() => tab.value === 'rentals');
const isPurchase = computed(() => tab.value === 'purchases');
const showPurchaseActions = computed(() => isPurchase.value && !!selectedOrder.value);
/** Reject frees reserved stock; does not require meetup code verification. */
const canRejectOrder = computed(() => {
  if (!selectedOrder.value) return false;
  const want = String(selectedOrder.value.id ?? '').trim();
  return isPurchase.value && isPendingPayment.value && confirmIdInput.value.trim() === want;
});
/** Confirm payment deducts stock (and may remove listing when sold out); requires verified meetup code first. */
const canConfirmPayment = computed(() => {
  if (!canRejectOrder.value) return false;
  return !!selectedOrder.value?.verifiedAt;
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
/** Guest receives this code at checkout; at pickup/delivery they show it so the provider can confirm the right person paid. */
const canVerifyCodePerOrder = computed(() => !!selectedOrder.value);

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
  confirmIdInput.value = '';
  orderVerifyCode.value = '';
  orderVerifyError.value = '';
  orderVerifySuccess.value = '';

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
  confirmIdInput.value = '';
  orderVerifyCode.value = '';
  orderVerifying.value = false;
  orderVerifyError.value = '';
  orderVerifySuccess.value = '';
}

function handleOrderVerifyInput(event) {
  // Auto-format to XXXX-XXXX pattern
  let value = String(event?.target?.value || '')
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, '');
  if (value.length > 4 && !value.includes('-')) {
    value = value.slice(0, 4) + '-' + value.slice(4, 8);
  }
  orderVerifyCode.value = value.slice(0, 9); // Max 8 chars + 1 hyphen
}

async function verifyCodeForThisOrder() {
  if (!selectedOrder.value) return;
  orderVerifyError.value = '';
  orderVerifySuccess.value = '';

  const code = orderVerifyCode.value.trim().toUpperCase();
  if (!code) {
    orderVerifyError.value = 'Please enter a verification code.';
    return;
  }

  orderVerifying.value = true;
  try {
    const isPurchaseTab = tab.value === 'purchases';
    const { data } = isPurchaseTab
      ? await providerOrdersApi.verifyPurchaseCode(code)
      : await providerOrdersApi.verifyBookingCode(code);
    const entity = isPurchaseTab ? data?.order : data?.booking;
    const verifiedId = entity?.id;

    if (String(verifiedId) !== String(selectedOrder.value.id)) {
      const kind = isPurchaseTab ? 'order' : 'booking';
      orderVerifyError.value = `That code belongs to a different ${kind} (#${verifiedId}).`;
      return;
    }

    orderVerifySuccess.value =
      data?.message ||
      (isPurchaseTab ? 'Code verified for this purchase order.' : 'Code verified for this rental booking.');
    await load();
    try {
      const detail = isPurchaseTab
        ? await providerOrdersApi.getPurchase(selectedOrder.value.id)
        : await providerOrdersApi.getRental(selectedOrder.value.id);
      selectedOrder.value = detail.data || selectedOrder.value;
    } catch {
      /* list refresh is enough if detail fails */
    }
  } catch (e) {
    orderVerifyError.value = e.response?.data?.message || e.message;
  } finally {
    orderVerifying.value = false;
  }
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
              <strong>Confirm order actions</strong>
              <p class="muted small" style="margin: 0.25rem 0 0.4rem;">
                Type the order number to enable Confirm/Reject: <strong>#{{ selectedOrder.id }}</strong>
              </p>
              <input v-model="confirmIdInput" type="text" placeholder="Enter order number" />
            </div>

            <div v-if="canVerifyCodePerOrder" style="margin-top: 0.9rem;">
              <strong>Meetup verification (this record only)</strong>
              <p class="muted small" style="margin: 0.25rem 0 0.4rem;">
                When you meet the guest (pickup or delivery), they should show the code from their order confirmation.
                Enter it here to record that this is the correct
                {{ tab === 'purchases' ? 'purchase' : 'rental' }}
                <strong>#{{ selectedOrder.id }}</strong>.
                <template v-if="tab === 'purchases' && isPendingPayment">
                  After verification, use <strong>Confirm Payment</strong> when payment is received — that step updates stock.
                </template>
              </p>
              <input
                type="text"
                :value="orderVerifyCode"
                placeholder="XXXX-XXXX"
                maxlength="9"
                :disabled="orderVerifying"
                @input="handleOrderVerifyInput"
              />
              <div style="display: flex; gap: 0.5rem; margin-top: 0.5rem;">
                <button class="btn btn--primary" type="button" :disabled="orderVerifying || !orderVerifyCode" @click="verifyCodeForThisOrder">
                  {{ orderVerifying ? 'Verifying…' : 'Verify code' }}
                </button>
              </div>
              <p v-if="orderVerifyError" class="err-toast" style="margin-top: 0.6rem;">
                {{ orderVerifyError }}
              </p>
              <p v-if="orderVerifySuccess" class="toast success" style="margin-top: 0.6rem;">
                {{ orderVerifySuccess }}
              </p>
            </div>

            <p v-if="actionError" class="err-toast" style="margin-top: 0.75rem;">
              {{ actionError }}
            </p>
            <p
              v-if="isPurchase && isPendingPayment && canRejectOrder && !canConfirmPayment"
              class="muted small"
              style="margin-top: 0.5rem;"
            >
              Verify the guest's meetup code above, then confirm payment to update inventory.
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
              class="btn btn--primary"
              @click="confirmOrder"
              :disabled="actionLoading || !canConfirmPayment"
            >
              {{ actionLoading ? 'Working…' : 'Confirm Payment' }}
            </button>
            <button class="btn btn--ghost" @click="closeDialog">Close</button>
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