<script setup>
import { ref, onMounted, computed } from 'vue';
import { useRouter } from 'vue-router';
import { api } from '../api';
import { useAuthStore } from '../stores/auth';
import ResponsiveRecordShell from '../components/layout/ResponsiveRecordShell.vue';
import DataTableShell from '../components/ui/DataTableShell.vue';
import VerifyCodePanel from '../components/provider/VerifyCodePanel.vue';

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

/* ================= DATA ================= */
const rows = computed(() =>
  tab.value === 'purchases'
    ? purchases.value.content
    : rentals.value.content
);

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
      api.get('/api/provider/me/orders/purchases', { params: { page: 0, size: 50 } }),
      api.get('/api/provider/me/orders/rentals', { params: { page: 0, size: 50 } }),
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
function openDetails(order) {
  selectedOrder.value = order;
  showDialog.value = true;
}

function closeDialog() {
  showDialog.value = false;
  selectedOrder.value = null;
}

async function confirmOrder() {
  await api.post(`/api/provider/orders/${selectedOrder.value.id}/confirm`);
  await load();
  closeDialog();
}

async function rejectOrder() {
  await api.post(`/api/provider/orders/${selectedOrder.value.id}/reject`);
  await load();
  closeDialog();
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

        <button class="tab" :class="{ active: tab === 'verify' }" @click="tab = 'verify'">
          <span class="material-icons tab-icon">verified_user</span>
          Verify Code
        </button>
      </div>

      <VerifyCodePanel v-if="tab === 'verify'" />

      <!-- TABLE -->
      <ResponsiveRecordShell v-else :desktop-label="tab === 'purchases' ? 'Purchase orders' : 'Rental bookings'">

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
            <!-- 
             TODO 
             -add payment proof display
             - make sure the buttons are only shown if order is in pending state (and hide payment proof if not pending since it can only be uploaded during pending state) 
             - confirm payment button should update status to confirmed and reject button should update status to rejected (and both should trigger email notification to guest about status change)
             - also add error handling and loading states for these actions (disable buttons while loading and show
            -->
            <input  type="text" :value="selectedOrder.paymentProofUrl" readonly @click="$event.target.select()" />
           <img  :src="selectedOrder.paymentProofUrl" alt="Payment proof" style="max-width: 100%; margin-top: 0.5rem; border-radius: 4px;" />

          </div>

          <!-- ACTIONS -->
          <div class="dialog-actions">
            

            <button
       
              class="btn btn--danger"
              @click="rejectOrder"
            >
              Reject Order
            </button>

            <button
             
              class="btn btn--primary"
              @click="confirmOrder"
            >
              Confirm Payment
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