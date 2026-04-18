<script setup>
import { ref, onMounted, computed } from 'vue';
import { useRouter } from 'vue-router';
import { api, withSession } from '../api';
import { useSessionStore } from '../stores/session';
import { useCartStore } from '../stores/cart';
import { useDialog } from '../composables/useDialog';

import FormField from '../components/ui/FormField.vue';
import CartLinesSection from '../components/cart/CartLinesSection.vue';

const router = useRouter();
const session = useSessionStore();
const cart = useCartStore();
const { confirm, success, error: showError, warning } = useDialog();

/* ================= FORM ================= */
const guestName = ref('');
const guestEmail = ref('');
const guestPhone = ref('');

const paymentMethod = ref('CASH');
const deliveryMode = ref(''); // DELIVERY | PICKUP
const deliveryDistanceKm = ref('');
const submitting = ref(false);

/* DELIVERY CONFIRMATION (NEW) */
const deliveryFeeConfirmed = ref(false);

/* ================= VALIDATION ================= */
const canCheckout = computed(() =>
  cart.lines.length > 0 &&
  guestName.value &&
  guestEmail.value &&
  guestPhone.value &&
  deliveryMode.value
);

/* ================= PAYMENT METHODS ================= */
const acceptedPaymentMethods = computed(() => {
  const list = cart.lockedProviderAcceptedPaymentMethods || [];
  return Array.isArray(list) && list.length ? list : ['EFT', 'CASH'];
});

/* ================= DELIVERY SETTINGS ================= */
const deliveryAvailable = computed(() =>
  !!cart.lockedProviderDeliveryAvailable
);

const deliveryRate = computed(() =>
  Number(cart.lockedProviderDeliveryPricePerKm) || 0
);

/* ================= DELIVERY FEE =================
   ONLY CALCULATED AFTER BUTTON CLICK
================================================== */
const estimatedDeliveryFee = computed(() => {
  if (!deliveryFeeConfirmed.value) return 0;
  if (deliveryMode.value !== 'DELIVERY') return 0;

  const km = Number(deliveryDistanceKm.value);

  if (!deliveryAvailable.value) return 0;
  if (!km || km <= 0) return 0;
  if (!deliveryRate.value) return 0;
alert(km * deliveryRate.value);
  return km * deliveryRate.value;
});

/* ================= TOTAL ================= */
const estimatedTotalWithDelivery = computed(() => {
  const base = Number(cart.estimatedTotal) || 0;
  return base + Number(estimatedDeliveryFee.value);
});

/* ================= BANK DETAILS ================= */
const showBankDetails = computed(() =>
  paymentMethod.value === 'EFT' && !!cart.lockedProviderBankName
);

/* ================= INIT ================= */
onMounted(async () => {
  await session.ensureSession();
  await cart.refresh();
});

/* ================= CHECKOUT ================= */
async function submitCheckout() {
  submitting.value = true;

  try {
    const response = await api.post(
      '/api/public/cart/checkout',
      {
        guestName: guestName.value,
        guestEmail: guestEmail.value,
        guestPhone: guestPhone.value,
        deliveryOrPickup: deliveryMode.value,
        paymentMethod: paymentMethod.value,
        deliveryDistanceKm: deliveryMode.value === 'DELIVERY'
          ? Number(deliveryDistanceKm.value)
          : null,
      },
      withSession(session.sessionId),
    );

    const codes = response.data.verificationCodes || [];

    guestName.value = '';
    guestEmail.value = '';
    guestPhone.value = '';
    deliveryMode.value = '';
    deliveryDistanceKm.value = '';
    deliveryFeeConfirmed.value = false;

    await cart.refresh();

    await success(
      codes.length
        ? `Order placed!\n\n${codes.map(c => '• ' + c).join('\n')}`
        : 'Order placed successfully.',
      'Order Confirmed'
    );

    router.push('/');
  } catch (e) {
    await showError(e.response?.data?.message || e.message, 'Checkout Failed');
  } finally {
    submitting.value = false;
  }
}

/* ================= CART ================= */
async function clearCart() {
  const ok = await confirm('Clear cart?', 'Confirm');
  if (!ok) return;

  await cart.clearCart();
  await success('Cart cleared');
}

async function handleUpdateQuantity({ id, quantity }) {
  const line = cart.lines.find(l => l.lineId === id);
  if (!line) return;

  if (quantity < 1) {
    return warning('Minimum quantity is 1', 'Warning');
  }

  if (line.listingType === 'SALE' && line.availableStock != null) {
    if (quantity > line.availableStock) {
      return warning(`Max stock is ${line.availableStock}`, 'Stock limit');
    }
  }

  try {
    await cart.updateLineQuantity(id, quantity);
  } catch (e) {
    await showError(e.message, 'Update failed');
  }
}

async function handleRemoveLine(id) {
  const ok = await confirm('Remove item?', 'Remove');
  if (!ok) return;

  try {
    await cart.removeLine(id);
  } catch (e) {
    await showError(e.message, 'Remove failed');
  }
}

async function handleLimitWarning({ message, type }) {
  await warning(message, type === 'max' ? 'Max limit' : 'Min limit');
}

/* ================= DELIVERY BUTTON ================= */
function calculateDelivery() {
  deliveryFeeConfirmed.value = true;
}
</script>

<template>
  <div class="checkout-page">

    <header class="page-hero">
      <h1>Complete your order</h1>
    </header>

    <div class="checkout-grid">

      <!-- CART -->
      <section class="surface-panel">
        <h2>Your cart</h2>

        <CartLinesSection
          :lines="cart.lines"
          :estimated-total="cart.estimatedTotal"
          @clear="clearCart"
          @update-quantity="handleUpdateQuantity"
          @remove-line="handleRemoveLine"
          @show-limit-warning="handleLimitWarning"
        />
      </section>

      <!-- CHECKOUT -->
      <section class="surface-panel">

        <h2>Checkout</h2>

        <FormField label="Name">
          <input v-model="guestName" />
        </FormField>

        <FormField label="Email">
          <input v-model="guestEmail" />
        </FormField>

        <FormField label="Phone">
          <input v-model="guestPhone" />
        </FormField>

        <!-- DELIVERY OPTIONS -->
        <FormField label="Delivery option">

          <div class="radio-group">

            <label class="radio-card">
              <input type="radio" v-model="deliveryMode" value="DELIVERY" />
              <span>🚚 Delivery <small>Delivered to you</small></span>
            </label>

            <label class="radio-card">
              <input type="radio" v-model="deliveryMode" value="PICKUP" />
              <span>🏪 Pickup <small>Collect yourself</small></span>
            </label>

          </div>

        </FormField>

        <!-- DELIVERY INPUT -->
        <div v-if="deliveryMode === 'DELIVERY'">

          <FormField label="Distance (KM)">
            <input
              v-model="deliveryDistanceKm"
              type="number"
              min="0"
              step="0.1"
              @input="deliveryFeeConfirmed = false"
            />
          </FormField>

          <button
            type="button"
            class="btn btn-secondary"
            @click="calculateDelivery"
            :disabled="!deliveryDistanceKm || deliveryDistanceKm <= 0"
          >
            Calculate Delivery Fee
          </button>

          <p v-if="deliveryFeeConfirmed" class="muted">
            Delivery fee: R{{ estimatedDeliveryFee.toFixed(2) }}
          </p>

        </div>

        <!-- PAYMENT -->
        <FormField label="Payment method">
          <select v-model="paymentMethod">
            <option v-for="m in acceptedPaymentMethods" :key="m" :value="m">
              {{ m }}
            </option>
          </select>
        </FormField>

        <!-- BANK -->
        <div v-if="showBankDetails" class="bank-box">
          <h3>EFT Payment Details</h3>
          <p>{{ cart.lockedProviderBankName }}</p>
        </div>

        <!-- TOTAL -->
        <div class="total-box">

          <div class="row">
            <span>Subtotal</span>
            <strong>R{{ (Number(cart.estimatedTotal) || 0).toFixed(2) }}</strong>
          </div>
<!-- TODO: Implement delivery fee calculation -->
          <div class="row" v-if="deliveryMode === 'DELIVERY' && deliveryFeeConfirmed">
            <span>Delivery</span>
            <strong>R{{ estimatedDeliveryFee.toFixed(2) }}</strong>
          </div>
<!--  TODO: Implement total calculation and add it to total -->
          <div class="row total">
            <span>Total</span>
            <strong>R{{ estimatedTotalWithDelivery.toFixed(2) }}</strong>
          </div>

        </div>

        <button
          class="btn btn-primary"
          :disabled="!canCheckout || submitting"
          @click="submitCheckout"
        >
          {{ submitting ? 'Processing...' : 'Place Order' }}
        </button>

      </section>

    </div>
  </div>
</template>

<style scoped>
.checkout-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1.5rem;
}

/* RADIO FIX */
.radio-group {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.radio-card {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding: 0.55rem 0.75rem;
  border: 1px solid #ddd;
  border-radius: 10px;
  cursor: pointer;
  font-size: 0.9rem;
}

.radio-card input {
  width: 16px;
  height: 16px;
}

/* TOTAL */
.total-box {
  margin-top: 1rem;
  padding: 0.85rem;
  border: 1px solid #ddd;
  border-radius: 12px;
}

.total-box .row {
  display: flex;
  justify-content: space-between;
  margin-bottom: 0.4rem;
}

.total-box .total {
  font-weight: 700;
  border-top: 1px solid #ddd;
  padding-top: 0.5rem;
}

/* BANK */
.bank-box {
  margin-top: 1rem;
  padding: 0.8rem;
  background: #f5f8ff;
  border-radius: 12px;
}

@media (max-width: 900px) {
  .checkout-grid {
    grid-template-columns: 1fr;
  }
}
</style>