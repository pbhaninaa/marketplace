<script setup>
// Delivery vs pickup is shown only when the locked provider offers delivery; otherwise checkout is pickup-only.
// Delivery fee = distance (km) × provider rate, included in the total (matches server calculation).

import { ref, onMounted, computed, watch } from 'vue';
import { useRouter } from 'vue-router';
import { publicCartApi } from '../services/marketplaceApi';
import { useSessionStore } from '../stores/session';
import { useCartStore } from '../stores/cart';
import { useDialog } from '../composables/useDialog';
import { isNonEmptyString, isValidEmail, isPositiveNumber } from '../utils/validation';

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

function roundMoney(n) {
  return Math.round(Number(n) * 100) / 100;
}

/* ================= PAYMENT METHODS ================= */
const acceptedPaymentMethods = computed(() => {
  const list = cart.lockedProviderAcceptedPaymentMethods || [];
  return Array.isArray(list) && list.length ? list : ['EFT', 'CASH'];
});

/* ================= DELIVERY SETTINGS ================= */
const deliveryAvailable = computed(() =>
  !!cart.lockedProviderDeliverySettings?.deliveryAvailable
);

const deliveryRate = computed(() =>
  Number(cart.lockedProviderDeliverySettings?.deliveryPricePerKm) || 0
);

watch(
  deliveryAvailable,
  (available) => {
    if (!available) {
      deliveryMode.value = 'PICKUP';
      deliveryDistanceKm.value = '';
    }
  },
  { immediate: true }
);

watch(deliveryMode, (mode) => {
  if (mode !== 'DELIVERY') {
    deliveryDistanceKm.value = '';
  }
});

const calculatedDeliveryFee = computed(() => {
  if (deliveryMode.value !== 'DELIVERY' || !deliveryAvailable.value) return 0;
  const km = Number(deliveryDistanceKm.value);
  if (!Number.isFinite(km) || km <= 0) return 0;
  const rate = deliveryRate.value;
  if (!rate || rate <= 0) return 0;
  return roundMoney(km * rate);
});

/* ================= VALIDATION ================= */
const isDeliveryChoiceComplete = computed(() => {
  if (!deliveryAvailable.value) {
    return deliveryMode.value === 'PICKUP';
  }
  if (!deliveryMode.value) return false;
  if (deliveryMode.value === 'PICKUP') return true;
  if (!deliveryRate.value || deliveryRate.value <= 0) return false;
  return isPositiveNumber(deliveryDistanceKm.value) && calculatedDeliveryFee.value > 0;
});

const canCheckout = computed(() =>
  cart.lines.length > 0 &&
  guestName.value &&
  guestEmail.value &&
  guestPhone.value &&
  isDeliveryChoiceComplete.value
);

/* ================= TOTAL ================= */
const estimatedTotalWithDelivery = computed(() => {
  const base = Number(cart.estimatedTotal) || 0;
  return roundMoney(base + calculatedDeliveryFee.value);
});

/* ================= EFT BANK DETAILS ================= */
const showBankDetails = computed(() =>
  paymentMethod.value === 'EFT' && !!cart.lockedProviderBank
);

/* ================= INIT ================= */
onMounted(async () => {
  await session.ensureSession();
  await cart.refresh();
});

/* ================= CHECKOUT ================= */
async function submitCheckout() {
  if (!isNonEmptyString(guestName.value)) return warning('Please enter your name.', 'Missing name');
  if (!isValidEmail(guestEmail.value)) return warning('Please enter a valid email address.', 'Invalid email');
  if (!isNonEmptyString(guestPhone.value)) return warning('Please enter your phone number.', 'Missing phone');
  if (!deliveryAvailable.value) {
    /* pickup-only */
  } else if (!isNonEmptyString(deliveryMode.value)) {
    return warning('Please choose delivery or pickup.', 'Missing option');
  }
  if (deliveryMode.value === 'DELIVERY') {
    if (!deliveryRate.value || deliveryRate.value <= 0) {
      return warning('This provider has not set a delivery price per km yet.', 'Delivery unavailable');
    }
    if (!isPositiveNumber(deliveryDistanceKm.value)) {
      return warning('Please enter how far delivery is in km (greater than zero).', 'Invalid distance');
    }
    if (calculatedDeliveryFee.value <= 0) {
      return warning('Please enter a valid delivery distance to calculate the fee.', 'Invalid distance');
    }
  }

  submitting.value = true;

  try {
    const response = await publicCartApi.checkout(session.sessionId, {
      guestName: guestName.value,
      guestEmail: guestEmail.value,
      guestPhone: guestPhone.value,
      deliveryOrPickup: deliveryMode.value,
      paymentMethod: paymentMethod.value,
      deliveryDistanceKm:
        deliveryMode.value === 'DELIVERY'
          ? Number(deliveryDistanceKm.value)
          : null,
    });

    const codes = response.data.verificationCodes || [];

    guestName.value = '';
    guestEmail.value = '';
    guestPhone.value = '';
    deliveryMode.value = '';
    deliveryDistanceKm.value = '';

    await cart.refresh();

    await success(
      codes.length
        ? `Order placed!\n\nKeep your verification code(s). When you collect or receive delivery, show this code to the provider after you pay so they can confirm it matches this order:\n\n${codes.map((c) => '• ' + c).join('\n')}`
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

  if (quantity < 1) return warning('Minimum quantity is 1', 'Warning');

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

        <FormField label="Email" >
          <input v-model="guestEmail" type="email"/>
        </FormField>

        <FormField label="Phone">
          <input v-model="guestPhone" type="tel"/>
        </FormField>

        <!-- DELIVERY: only when provider offers it; otherwise pickup-only -->
        <template v-if="deliveryAvailable">
          <FormField label="How do you want to receive your order?">
            <div class="radio-group">
              <label class="radio-card">
                <input type="radio" v-model="deliveryMode" value="DELIVERY" />
                <span>🚚 Delivery <small>To your address — fee = distance × R{{ deliveryRate.toFixed(2) }}/km</small></span>
              </label>
              <label class="radio-card">
                <input type="radio" v-model="deliveryMode" value="PICKUP" />
                <span>🏪 Pickup <small>Collect from the provider</small></span>
              </label>
            </div>
          </FormField>

          <div v-if="deliveryMode === 'DELIVERY'">
            <FormField label="Delivery distance (km)">
              <input
                v-model="deliveryDistanceKm"
                type="number"
                min="0"
                step="0.1"
                placeholder="e.g. 12.5"
              />
            </FormField>
            <p v-if="deliveryRate > 0 && calculatedDeliveryFee > 0" class="muted" style="margin-top: -0.35rem;">
              Delivery fee: <strong>R{{ calculatedDeliveryFee.toFixed(2) }}</strong>
              ({{ Number(deliveryDistanceKm) || 0 }} km × R{{ deliveryRate.toFixed(2) }}/km)
            </p>
            <p v-else-if="deliveryMode === 'DELIVERY' && deliveryRate <= 0" class="muted small">
              This provider has delivery enabled but no per-km rate — contact them or choose pickup.
            </p>
          </div>
        </template>
        <p v-else class="muted" style="margin: 0.5rem 0 1rem;">
          This provider offers <strong>pickup only</strong>. You will collect your order from them.
        </p>

        <!-- PAYMENT -->
        <FormField label="Payment method">
          <select v-model="paymentMethod">
            <option v-for="m in acceptedPaymentMethods" :key="m" :value="m">
              {{ m }}
            </option>
          </select>
        </FormField>

        <!-- BANK DETAILS -->
        <div v-if="showBankDetails" class="bank-box">
          <h3>EFT Payment Details</h3>

          <p><strong>Bank:</strong> {{ cart.lockedProviderBank?.bankName }}</p>
          <p><strong>Account:</strong> {{ cart.lockedProviderBank?.accountName }}</p>
          <p><strong>Number:</strong> {{ cart.lockedProviderBank?.accountNumber }}</p>
          <p><strong>Branch:</strong> {{ cart.lockedProviderBank?.branchCode }}</p>
        </div>

        <!-- TOTAL -->
        <div class="total-box">

          <div class="row">
            <span>Subtotal</span>
            <strong>R{{ Number(cart.estimatedTotal).toFixed(2) }}</strong>
          </div>

          <div class="row" v-if="deliveryAvailable && deliveryMode === 'DELIVERY' && calculatedDeliveryFee > 0">
            <span>Delivery</span>
            <strong>R{{ calculatedDeliveryFee.toFixed(2) }}</strong>
          </div>

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

/* RADIO SMALL FIX */
.radio-group {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.radio-card {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding: 0.5rem 0.7rem;
  border: 1px solid #ddd;
  border-radius: 10px;
  cursor: pointer;
  font-size: 0.9rem;
}

.radio-card input {
  width: 16px;
  height: 16px;
}

.radio-card small {
  display: block;
  font-size: 0.75rem;
  color: #777;
}

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

.bank-box {
  margin-top: 1rem;
  padding: 0.8rem;
  background: #f4f7ff;
  border-radius: 12px;
}

@media (max-width: 900px) {
  .checkout-grid {
    grid-template-columns: 1fr;
  }
}
</style>
