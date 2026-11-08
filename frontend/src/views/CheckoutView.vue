<script setup>
// Delivery vs pickup is shown only when the locked provider offers delivery; otherwise checkout is pickup-only.
// Delivery fee = distance (km) × provider rate, included in the total (matches server calculation).

import { ref, onMounted, computed, watch } from 'vue';
import { useRouter } from 'vue-router';
import { publicCartApi, publicPaymentsApi } from '../services/marketplaceApi';
import { goToOnlinePayment } from '../utils/payFastCheckout';
import { useSessionStore } from '../stores/session';
import { useCartStore } from '../stores/cart';
import { useDialog } from '../composables/useDialog';
import {
  isNonEmptyString,
  isPositiveNumber,
  personNameError,
  emailError,
  phoneError,
  numberError,
  LIMITS,
} from '../utils/validation';
import { getCurrentLocation } from "../utils/getCurrentLocation";
import FormField from '../components/ui/FormField.vue';
import CartLinesSection from '../components/cart/CartLinesSection.vue';
import MapPinPicker from '../components/ui/MapPinPicker.vue';
import {
  ONLINE_PAYMENT_LABEL,
  PEACH_PAYMENT_METHODS,
  gatewayPageName,
  peachPaymentPayload,
} from '../utils/paymentModel';

const router = useRouter();
const session = useSessionStore();
const cart = useCartStore();
const { confirm, success, error: showError, warning } = useDialog();

/* ================= FORM ================= */
const guestName = ref('');
const guestEmail = ref('');
const guestPhone = ref('');

/* ================= TOUCHED STATE ================= */
const touched = ref({
  guestName: false,
  guestEmail: false,
  guestPhone: false,
});

function markFieldTouched(fieldName) {
  touched.value[fieldName] = true;
}

function resetTouched() {
  touched.value = {
    guestName: false,
    guestEmail: false,
    guestPhone: false,
  };
}

const paymentMethod = ref('CASH');
const peachPaymentMethod = ref('');
const deliveryMode = ref(''); 
const deliveryDistanceKm = ref('');
const showCheckout = ref(false);
const submitting = ref(false);
const toggleCheckout = () => {
  showCheckout.value = !showCheckout.value;
};
function roundMoney(n) {
  return Math.round(Number(n) * 100) / 100;
}

/* ================= PAYMENT METHODS ================= */
const PAYMENT_LABELS = {
  CASH: 'Cash on collection / delivery',
  EFT: 'EFT (bank transfer)',
  PEACH: ONLINE_PAYMENT_LABEL,
};

// The online option (method PEACH) goes through whichever gateway the platform uses (PayFast or Peach).
const peachAvailable = ref(false);
const onlineGateway = ref('');
publicPaymentsApi
  .onlineStatus()
  .then((res) => {
    peachAvailable.value = !!res?.data?.available;
    onlineGateway.value = res?.data?.gateway || '';
  })
  .catch(() => {
    peachAvailable.value = false;
  });
const onlinePageName = computed(() => gatewayPageName(onlineGateway.value));

/* ================= SELLER BEING CHECKED OUT =================
 * The cart can hold several sellers. Each one is checked out separately with its own payment
 * methods, delivery and bank details; the others stay in the cart. */
const selectedSellerId = ref(null);
const activeSeller = computed(
  () => cart.sellers.find((s) => s.providerId === selectedSellerId.value) || cart.sellers[0] || null,
);
const activeSellerLines = computed(() => (activeSeller.value ? cart.linesForSeller(activeSeller.value.providerId) : []));
const activeSellerPosition = computed(() =>
  activeSeller.value ? cart.sellers.findIndex((s) => s.providerId === activeSeller.value.providerId) + 1 : 0,
);
watch(
  () => cart.sellers.map((s) => s.providerId).join(','),
  () => {
    if (!cart.sellers.some((s) => s.providerId === selectedSellerId.value)) {
      selectedSellerId.value = cart.sellers[0]?.providerId ?? null;
    }
  },
  { immediate: true },
);

function startCheckout(providerId) {
  selectedSellerId.value = providerId;
  showCheckout.value = true;
  window.scrollTo?.({ top: 0, behavior: 'smooth' });
}

const acceptedPaymentMethods = computed(() => {
  const list = (activeSeller.value?.acceptedPaymentMethods || []).filter(
    (m) => m === 'CASH' || m === 'EFT' || (m === 'PEACH' && peachAvailable.value),
  );
  return list;
});

function paymentLabel(m) {
  return PAYMENT_LABELS[m] || m;
}

watch(
  acceptedPaymentMethods,
  (methods) => {
    if (!methods.includes(paymentMethod.value)) {
      paymentMethod.value = methods.includes('CASH') ? 'CASH' : methods[0];
    }
  },
  { immediate: true },
);

watch(paymentMethod, (method) => {
  if (method !== 'PEACH') peachPaymentMethod.value = '';
});

const showBankDetails = computed(() =>
  paymentMethod.value === 'EFT' && !!activeSeller.value?.bank
);
const activeBank = computed(() => activeSeller.value?.bank || null);

/* ================= DELIVERY SETTINGS ================= */
const deliveryAvailable = computed(() =>
  !!activeSeller.value?.deliverySettings?.deliveryAvailable
);
/*=================== Delivery location (map pin) =================
 * The buyer's pin starts at their device location and can be dragged to the exact spot. The server
 * works out the distance and fee from the seller's pickup pin; nothing typed by the buyer sets it. */
const deliveryPin = ref(null);
const deliveryAddressText = ref('');
async function loadLocation() {
  try {
    const loc = await getCurrentLocation();
    if (!deliveryPin.value && loc?.coords?.latitude != null) {
      deliveryPin.value = { lat: Number(loc.coords.latitude), lng: Number(loc.coords.longitude) };
    }
    if (!deliveryAddressText.value && loc?.locationName) {
      deliveryAddressText.value = String(loc.locationName).slice(0, LIMITS.deliveryAddress);
    }
  } catch {
    // No device location: the buyer drops the pin on the map instead.
  }
}

const deliveryRate = computed(() =>
  Number(activeSeller.value?.deliverySettings?.deliveryPricePerKm) || 0
);

watch(
  deliveryAvailable,
  (available) => {
    if (!available) {
      deliveryMode.value = 'PICKUP';
    }
  },
  { immediate: true }
);

/* Server quote for this seller and pin: { distanceKm, fee }. */
const deliveryQuote = ref(null);
const deliveryQuoteError = ref('');
const quoting = ref(false);
let quoteSeq = 0;
async function refreshDeliveryQuote() {
  deliveryQuote.value = null;
  deliveryQuoteError.value = '';
  const seller = activeSeller.value;
  if (deliveryMode.value !== 'DELIVERY' || !seller || !deliveryPin.value) return;
  const seq = ++quoteSeq;
  quoting.value = true;
  try {
    const { data } = await publicCartApi.deliveryQuote(seller.providerId, deliveryPin.value.lat, deliveryPin.value.lng);
    if (seq === quoteSeq) deliveryQuote.value = { distanceKm: Number(data.distanceKm), fee: Number(data.fee) };
  } catch (e) {
    if (seq === quoteSeq) deliveryQuoteError.value = e.response?.data?.message || 'Couldn’t work out the delivery fee. Try again or choose pickup.';
  } finally {
    if (seq === quoteSeq) quoting.value = false;
  }
}
watch(
  () => [deliveryMode.value, deliveryPin.value?.lat, deliveryPin.value?.lng, activeSeller.value?.providerId],
  refreshDeliveryQuote,
);

const calculatedDeliveryFee = computed(() =>
  deliveryMode.value === 'DELIVERY' && deliveryAvailable.value && deliveryQuote.value ? deliveryQuote.value.fee : 0,
);

/* ================= VALIDATION ================= */
// Same rules as the API (utils/validation.js mirrors backend InputValidator).
const guestNameError = computed(() => (touched.value.guestName ? personNameError(guestName.value) : ''));
const guestEmailError = computed(() => (touched.value.guestEmail ? emailError(guestEmail.value) : ''));
const guestPhoneError = computed(() => (touched.value.guestPhone ? phoneError(guestPhone.value) : ''));

const isNameValid = computed(() => !personNameError(guestName.value));
const isEmailValid = computed(() => !emailError(guestEmail.value));
const isPhoneValid = computed(() => !phoneError(guestPhone.value));

const isDeliveryChoiceComplete = computed(() => {
  if (!deliveryAvailable.value) {
    return deliveryMode.value === 'PICKUP';
  }
  if (!deliveryMode.value) return false;
  if (deliveryMode.value === 'PICKUP') return true;
  return !!deliveryPin.value && !!deliveryQuote.value && !deliveryQuoteError.value && !quoting.value;
});

const canCheckout = computed(() =>
  !!activeSeller.value &&
  acceptedPaymentMethods.value.includes(paymentMethod.value) &&
  isNameValid.value &&
  isEmailValid.value &&
  isPhoneValid.value &&
  isDeliveryChoiceComplete.value &&
  (paymentMethod.value !== 'PEACH' || !!peachPaymentMethod.value)
);

/* ================= TOTAL (this seller only) ================= */
const activeSubtotal = computed(() => Number(activeSeller.value?.subtotal) || 0);
const estimatedTotalWithDelivery = computed(() => {
  const base = activeSubtotal.value;
  return roundMoney(base + calculatedDeliveryFee.value);
});

/* ================= INIT ================= */
onMounted(async () => {
  await session.ensureSession();
  await cart.refresh();
  await loadLocation();
});

/* ================= CHECKOUT ================= */
async function submitCheckout() {
  // Mark all fields as touched
  markFieldTouched('guestName');
  markFieldTouched('guestEmail');
  markFieldTouched('guestPhone');

  // Validate required fields
  if (!isNameValid.value) return warning(personNameError(guestName.value), 'Invalid Input');
  if (!isEmailValid.value) return warning(emailError(guestEmail.value), 'Invalid Input');
  if (!isPhoneValid.value) return warning(phoneError(guestPhone.value), 'Invalid Input');
  if (paymentMethod.value === 'PEACH' && !peachPaymentMethod.value) {
    return warning('Choose Card or Instant EFT for your online payment.', 'Missing payment option');
  }
  if (!deliveryAvailable.value) {
    /* pickup-only */
  } else if (!isNonEmptyString(deliveryMode.value)) {
    return warning('Please choose delivery or pickup.', 'Missing option');
  }
  if (deliveryMode.value === 'DELIVERY') {
    if (!deliveryPin.value) {
      return warning('Choose your delivery location on the map, or use your current location.', 'Delivery location needed');
    }
    if (deliveryQuoteError.value) {
      return warning(deliveryQuoteError.value, 'Delivery unavailable');
    }
    if (!deliveryQuote.value) {
      return warning('Still working out the delivery fee. Try again in a moment.', 'Please wait');
    }
  }

  submitting.value = true;
  const seller = activeSeller.value;

  try {
    const response = await publicCartApi.checkout(session.sessionId, {
      providerId: seller?.providerId ?? null,
      guestName: guestName.value.trim(),
      guestEmail: guestEmail.value.trim(),
      guestPhone: guestPhone.value.trim(),
      deliveryOrPickup: deliveryMode.value,
      paymentMethod: paymentMethod.value,
      ...peachPaymentPayload(paymentMethod.value, peachPaymentMethod.value),
      // The server prices delivery from the pin; no distance is sent.
      deliveryAddress:
        deliveryMode.value === 'DELIVERY' && deliveryAddressText.value.trim()
          ? deliveryAddressText.value.trim().slice(0, LIMITS.deliveryAddress)
          : null,
      latitude: deliveryMode.value === 'DELIVERY' && deliveryPin.value ? String(deliveryPin.value.lat) : null,
      longitude: deliveryMode.value === 'DELIVERY' && deliveryPin.value ? String(deliveryPin.value.lng) : null,
    });

    // Online checkout: leave for the secure payment page straight away (Peach redirect or PayFast
    // form post). The order stays pending until the gateway confirms payment.
    if (response.data.redirectUrl || response.data.payfast) {
      await cart.refresh();
      goToOnlinePayment(response.data);
      return;
    }

    const codes = response.data.verificationCodes || [];
    const sellerName = response.data.providerName || seller?.providerName || 'the seller';
    const remaining = Number(response.data.sellersRemaining) || 0;

    // Delivery and payment are per seller; name, email and phone are kept for the next seller.
    deliveryMode.value = '';
    peachPaymentMethod.value = '';

    await cart.refresh();

    const codesText = codes.length
      ? `\n\nKeep your verification code(s). When you collect or receive delivery, show this code to ${sellerName} after you pay so they can confirm it matches this order:\n\n${codes.map((c) => '• ' + c).join('\n')}`
      : '';

    if (remaining > 0 && cart.sellers.length) {
      const next = cart.sellers[0];
      await success(
        `Order placed with ${sellerName}.${codesText}\n\nNext: ${next.providerName} (${remaining} more seller${remaining === 1 ? '' : 's'} in your cart). Your details are already filled in.`,
        'Order Confirmed',
      );
      // Straight on to the next seller's checkout: no need to browse again.
      selectedSellerId.value = next.providerId;
      showCheckout.value = true;
      window.scrollTo?.({ top: 0, behavior: 'smooth' });
      return;
    }

    guestName.value = '';
    guestEmail.value = '';
    guestPhone.value = '';
    resetTouched();

    await success(`Order placed with ${sellerName}.${codesText}`, 'Order Confirmed');

    router.push('/');
  } catch (e) {
    const code = e.response?.data?.code || e.response?.data?.errorCode;
    if (PROVIDER_UNAVAILABLE_CODES.has(code)) {
      // This seller stopped taking orders after the items were added (e.g. trial ended). Remove only
      // their items; other sellers in the cart are unaffected.
      for (const line of seller ? cart.linesForSeller(seller.providerId) : []) {
        await cart.removeLine(line.lineId).catch(() => {});
      }
      await warning(
        `${seller?.providerName || 'This seller'} is no longer taking orders, so their items have been removed from your cart.` +
          (cart.sellers.length ? ' Your other items are still in your cart.' : ' Please choose items from another seller.'),
        'Items no longer available',
      );
      if (cart.sellers.length) {
        showCheckout.value = false;
      } else {
        router.push('/');
      }
      return;
    }
    await showError(e.response?.data?.message || e.message, 'Checkout Failed');
  } finally {
    submitting.value = false;
  }
}

const PROVIDER_UNAVAILABLE_CODES = new Set(['SUBSCRIPTION_INACTIVE', 'PROVIDER_INACTIVE', 'PROVIDER_UNAVAILABLE']);

/* ================= CART ================= */
async function clearCart() {
  const ok = await confirm('Clear cart?', 'Confirm');
  if (!ok) return;

  await cart.clearCart();
  await success('Cart cleared');
  router.push('/');
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
    <div>
      <header class="page-hero">
        <h1>Complete your order</h1>
      </header>

      <div class="checkout-grid">

        <!-- CART -->
        <section class="surface-panel" v-if="!showCheckout">
          <h2>Your cart</h2>

          <p v-if="!cart.lines.length" class="muted">Cart is empty.</p>

          <template v-else>
            <p v-if="cart.sellerCount > 1" class="seller-note">
              <span class="material-icons" aria-hidden="true">storefront</span>
              <span>
                Items from <strong>{{ cart.sellerCount }} sellers</strong>. Each seller is paid and delivers
                separately, so you check out one seller at a time. Your details are remembered between them.
              </span>
            </p>

            <!-- One group per seller, each with its own subtotal and checkout button. -->
            <article v-for="s in cart.sellers" :key="s.providerId" class="seller-group">
              <header class="seller-group__head">
                <div>
                  <h3 class="seller-group__name">{{ s.providerName }}</h3>
                  <p v-if="s.providerLocation" class="muted small seller-group__meta">
                    <span class="material-icons" aria-hidden="true">place</span>{{ s.providerLocation }}
                  </p>
                </div>
                <span class="seller-group__count">{{ s.itemCount }} item{{ s.itemCount === 1 ? '' : 's' }}</span>
              </header>
              <CartLinesSection
                :lines="cart.linesForSeller(s.providerId)"
                :estimated-total="s.subtotal"
                total-label="Subtotal"
                :checkout-label="cart.sellerCount > 1 ? `Checkout ${s.providerName}` : 'Proceed to Checkout'"
                :show-clear="false"
                @update-quantity="handleUpdateQuantity"
                @remove-line="handleRemoveLine"
                @show-limit-warning="handleLimitWarning"
                @toggle-checkout="startCheckout(s.providerId)"
              />
            </article>

            <footer class="cart-footer">
              <p v-if="cart.sellerCount > 1" class="cart-footer__total">
                Total for all {{ cart.sellerCount }} sellers: <strong>R {{ cart.estimatedTotal }}</strong>
                <span class="muted small"> (delivery fees are per seller)</span>
              </p>
              <button type="button" class="btn btn-ghost clear-btn cart-footer__clear" @click="clearCart">
                <span class="material-icons" aria-hidden="true">delete_sweep</span>
                Clear cart
              </button>
            </footer>
          </template>
        </section>

        <!-- CHECKOUT -->
        <section class="surface-panel" v-if="showCheckout">

          <h2>Checkout<template v-if="activeSeller">: {{ activeSeller.providerName }}</template></h2>
          <p v-if="activeSeller" class="muted small checkout-seller">
            <template v-if="cart.sellerCount > 1">Seller {{ activeSellerPosition }} of {{ cart.sellerCount }} · </template>
            {{ activeSellerLines.length }} item{{ activeSellerLines.length === 1 ? '' : 's' }} ·
            R {{ activeSubtotal.toFixed(2) }}<template v-if="cart.sellerCount > 1">. Your other sellers stay in your cart.</template>
          </p>

          <FormField label="Name" :error="guestNameError">
            <input v-model="guestName" @blur="markFieldTouched('guestName')" placeholder="Your full name"
              :maxlength="LIMITS.personName" autocomplete="name" />
          </FormField>

          <FormField label="Email" :error="guestEmailError">
            <input v-model="guestEmail" @blur="markFieldTouched('guestEmail')" type="email"
              placeholder="your.email@example.com" :maxlength="LIMITS.email" autocomplete="email" />
          </FormField>

          <FormField label="Phone" :error="guestPhoneError">
            <input v-model="guestPhone" @blur="markFieldTouched('guestPhone')" type="tel"
              placeholder="+27 82 123 4567 or 082 123 4567" autocomplete="tel" />
          </FormField>

          <!-- DELIVERY: only when provider offers it; otherwise pickup-only -->
          <template v-if="deliveryAvailable">
            <FormField label="How do you want to receive your order?">
              <div class="radio-group">
                <label class="radio-card">
                  <input type="radio" v-model="deliveryMode" value="DELIVERY" />
                  <span>🚚 Delivery <small>To your pin on the map. R{{ deliveryRate.toFixed(2) }} per km</small></span>
                </label>
                <label class="radio-card">
                  <input type="radio" v-model="deliveryMode" value="PICKUP" />
                  <span>🏪 Pickup <small>Collect from the provider</small></span>
                </label>
              </div>
            </FormField>

            <div v-if="deliveryMode === 'DELIVERY'" class="delivery-location">
              <span class="ctl-label">Where should we deliver?</span>
              <MapPinPicker
                v-model="deliveryPin"
                height="240px"
                hint="We used your current location. Drag the pin to your exact gate or door if needed."
              />
              <FormField label="Address or directions for the driver (optional)">
                <input
                  v-model="deliveryAddressText"
                  type="text"
                  :maxlength="LIMITS.deliveryAddress"
                  placeholder="e.g. 12 Church Street, blue gate"
                  autocomplete="street-address"
                />
              </FormField>
              <p v-if="quoting" class="muted small" role="status">Working out the delivery fee…</p>
              <p v-else-if="deliveryQuoteError" class="delivery-error" role="alert">{{ deliveryQuoteError }}</p>
              <p v-else-if="deliveryQuote" class="delivery-quote" role="status">
                Delivery fee: <strong>R{{ deliveryQuote.fee.toFixed(2) }}</strong>
                <span class="muted small">
                  ({{ deliveryQuote.distanceKm }} km by road, estimated, × R{{ deliveryRate.toFixed(2) }}/km)
                </span>
              </p>
              <p v-else class="muted small">Choose your location on the map to see the delivery fee.</p>
            </div>
          </template>
          <p v-else class="muted" style="margin: 0.5rem 0 1rem;">
            This provider offers <strong>pickup only</strong>. You will collect your order from them.
          </p>

          <!-- PAYMENT -->
          <FormField label="How will you pay?">
            <AppSelect v-model="paymentMethod">
              <option v-for="m in acceptedPaymentMethods" :key="m" :value="m">
                {{ paymentLabel(m) }}
              </option>
            </AppSelect>
          </FormField>
          <p v-if="!acceptedPaymentMethods.length" class="muted small" style="margin-top: -0.5rem;">
            This provider has no payment method available right now. Please contact the provider.
          </p>
          <p v-if="paymentMethod === 'CASH'" class="muted small" style="margin-top: -0.5rem;">
            Pay the provider in person when you collect or receive delivery. Keep your verification code ready.
          </p>
          <p v-else-if="paymentMethod === 'EFT'" class="muted small" style="margin-top: -0.5rem;">
            Transfer to the provider’s bank account below, then show your verification code when they confirm payment.
          </p>
          <p v-else-if="paymentMethod === 'PEACH'" class="muted small" style="margin-top: -0.5rem;">
            Choose how you want to pay online:
          </p>
          <div v-if="paymentMethod === 'PEACH'" class="radio-group" style="margin-bottom: 1rem;">
            <label v-for="method in PEACH_PAYMENT_METHODS" :key="method.value" class="radio-card">
              <input v-model="peachPaymentMethod" type="radio" :value="method.value" />
              <span>{{ method.label }}</span>
            </label>
            <p class="muted small" style="margin: 0;">
              You will finish paying on the secure {{ onlinePageName }} page.
            </p>
          </div>

          <!-- BANK DETAILS -->
          <div v-if="showBankDetails" class="bank-box">
            <h3>EFT payment details</h3>
            <p class="muted small">Pay the <strong>provider</strong> (not the platform). Use your name as reference if none is shown.</p>

            <p><strong>Bank:</strong> {{ activeBank?.bankName }}</p>
            <p><strong>Account:</strong> {{ activeBank?.accountName }}</p>
            <p><strong>Number:</strong> {{ activeBank?.accountNumber }}</p>
            <p><strong>Branch:</strong> {{ activeBank?.branchCode }}</p>
            <p v-if="activeBank?.reference"><strong>Reference:</strong> {{ activeBank.reference }}</p>
          </div>

          <!-- TOTAL -->
          <div class="total-box">

            <div class="row">
              <span>Subtotal</span>
              <strong>R{{ activeSubtotal.toFixed(2) }}</strong>
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
          <div class="mt-5" style="flex-grow: 1;">
            <button class="btn btn-primary" :disabled="!canCheckout || submitting" @click="submitCheckout">
              {{
                submitting
                  ? 'Processing...'
                  : cart.sellerCount > 1 && activeSeller
                    ? `Place order with ${activeSeller.providerName}`
                    : 'Place Order'
              }}
            </button>
            <button class="btn btn-ghost" @click="toggleCheckout()" style="margin-left: 0.75rem;">
              Back to Cart
            </button>
          </div> <!-- push buttons to bottom -->


        </section>

      </div>
    </div>

  </div>
</template>

<style scoped>
.checkout-page {
  display: flex;
  justify-content: center;
  min-width: 50vw;
  width: 100%;
  min-height: 100vh;
  padding: 1rem;
  box-sizing: border-box;
}

/* The page's single column: never wider than the screen. */
.checkout-page > div {
  width: 100%;
  max-width: 720px;
  min-width: 0;
}

/* DELIVERY LOCATION */
.delivery-location {
  display: grid;
  gap: 0.6rem;
  margin-bottom: 1rem;
}

.delivery-quote {
  margin: 0;
  padding: 0.55rem 0.75rem;
  border-radius: var(--radius-md);
  background: var(--color-sage-soft);
}

.delivery-error {
  margin: 0;
  padding: 0.55rem 0.75rem;
  border-radius: var(--radius-md);
  background: var(--color-danger-bg);
  color: var(--color-danger-text);
  font-size: 0.9rem;
}

/* MULTI-SELLER CART */
.seller-note {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
  margin: 0 0 1rem;
  padding: 0.7rem 0.85rem;
  border-radius: var(--radius-md);
  background: var(--color-sage-soft);
  color: var(--color-text);
  font-size: 0.9rem;
  line-height: 1.45;
}

.seller-note .material-icons {
  font-size: 1.2rem;
  color: var(--color-sage);
}

.seller-group {
  padding: 1rem 1rem 0.5rem;
  margin-bottom: 1rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface-elevated);
}

.seller-group__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0.75rem;
  margin-bottom: 0.5rem;
}

.seller-group__name {
  margin: 0;
  font-size: 1.05rem;
}

.seller-group__meta {
  display: flex;
  align-items: center;
  gap: 0.2rem;
  margin: 0.15rem 0 0;
}

.seller-group__meta .material-icons {
  font-size: 0.95rem;
}

.seller-group__count {
  flex: none;
  padding: 0.15rem 0.6rem;
  border-radius: var(--radius-pill);
  background: var(--color-sage-soft);
  color: var(--color-canopy);
  font-size: 0.8rem;
  font-weight: 600;
}

.cart-footer {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  margin-top: 0.25rem;
}

.cart-footer__total {
  margin: 0;
}

.cart-footer__clear {
  margin-left: auto;
}

.checkout-seller {
  margin: -0.35rem 0 1rem;
}

/* MAIN LAYOUT */
.checkout-grid {
  display: grid;
  grid-template-columns: 1fr;
  /* FIX: single column */
  gap: 1.5rem;
  width: 100%;
  max-width: 720px;
  /* FIX: consistent width */
}

/* BOTH CART + CHECKOUT */
.surface-panel {
  min-height: 50vh;
  min-width: 30vw;
  display: flex;
  flex-direction: column;
  padding: 1rem;
  border-radius: 16px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.05);
}

/* PUSH BUTTONS DOWN */
.surface-panel .btn {
  width: fit-content;
  margin: 5px 0 0 auto;
  display: inline;
}

/* RADIO */
.radio-group {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.radio-card {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding: 0.75rem 0.9rem;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  cursor: pointer;
  font-size: 0.9rem;
  transition: 0.2s;
}

.radio-card:hover {
  border-color: var(--color-border-strong);
  background: var(--color-sage-soft);
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

/* TOTAL */
.total-box {
  margin-top: 1rem;
  padding: 0.85rem;
  border: 1px solid #ddd;
  border-radius: 12px;
  background: #fafafa;
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
  background: #f4f7ff;
  border-radius: 12px;
}

/* HEADINGS */
.page-hero {
  width: 100%;
  max-width: 720px;
  margin-bottom: 1rem;
}

.page-hero h1 {
  margin: 0;
}

/* MOBILE */
@media (max-width: 980px) {
  .checkout-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 600px) {

  .checkout-page {
    padding: 0.5rem;
  }

  /* Fill the screen width instead of a fixed 80vw, so nothing is pushed off the right edge. */
  .surface-panel {
    height: fit-content;
    width: 100%;
    min-width: 0;
    padding: 0.75rem;
  }

  .seller-group {
    padding: 0.75rem 0.65rem 0.4rem;
  }

  .page-hero h1 {
    font-size: 1.3rem;
    text-align: center;
  }

  .radio-card {
    font-size: 0.85rem;
  }
}
</style>
