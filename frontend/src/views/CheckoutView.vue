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

const guestName = ref('');
const guestEmail = ref('');
const guestPhone = ref('');
const deliveryOrPickup = ref('');
const paymentMethod = ref('EFT');
const submitting = ref(false);
const message = ref('');
const error = ref('');

const canCheckout = computed(
  () =>
    cart.lines.length > 0 &&
    guestName.value &&
    guestEmail.value &&
    guestPhone.value &&
    deliveryOrPickup.value,
);

const acceptedPaymentMethods = computed(() => {
  const list = cart.lockedProviderAcceptedPaymentMethods || [];
  return Array.isArray(list) && list.length ? list : ['EFT', 'CASH'];
});

const showBankDetails = computed(
  () => paymentMethod.value === 'EFT' && !!cart.lockedProviderBank,
);

onMounted(async () => {
  await session.ensureSession();
  await cart.refresh();
});

async function submitCheckout() {
  error.value = '';
  message.value = '';
  submitting.value = true;
  try {
    const response = await api.post(
      '/api/public/cart/checkout',
      {
        guestName: guestName.value,
        guestEmail: guestEmail.value,
        guestPhone: guestPhone.value,
        deliveryOrPickup: deliveryOrPickup.value,
        paymentMethod: paymentMethod.value,
      },
      withSession(session.sessionId),
    );

    const verificationCodes = response.data.verificationCodes || [];

    guestName.value = '';
    guestEmail.value = '';
    guestPhone.value = '';
    deliveryOrPickup.value = '';
    await cart.refresh();

    // Display verification codes
    if (verificationCodes.length > 0) {
      const codesList = verificationCodes.map(code => `• ${code}`).join('\n');
      await success(
        `Order placed successfully!\n\nYour verification code(s):\n${codesList}\n\nPlease provide this code to the provider when collecting your order or upon delivery.`,
        'Order Confirmed'
      );
    } else {
      await success('Order placed successfully. Your cart has been cleared.', 'Order Confirmed');
    }

    router.push('/');
  } catch (e) {
    await showError(e.response?.data?.message || e.message, 'Checkout Failed');
  } finally {
    submitting.value = false;
  }
}

async function clearCart() {
  const confirmed = await confirm('Are you sure you want to clear your entire cart?', 'Clear Cart');
  if (!confirmed) return;

  await cart.clearCart();
  await success('Cart has been cleared.', 'Cart Cleared');
}

/* ✅ NEW: update quantity */
async function handleUpdateQuantity({ id, quantity }) {
  // Find the cart line to check stock limits
  const cartLine = cart.lines.find(line => line.lineId === id);

  if (!cartLine) return;

  // Check minimum quantity
  if (quantity < 1) {
    await warning(
      'Quantity cannot be less than 1. Use the delete button to remove this item from your cart.',
      'Minimum Quantity Reached'
    );
    return;
  }

  // Check maximum stock for SALE items
  if (cartLine.listingType === 'SALE' && cartLine.availableStock !== null) {
    if (quantity > cartLine.availableStock) {
      await warning(
        `Maximum available stock is ${cartLine.availableStock} units. You cannot add more than what's in stock.`,
        'Maximum Stock Reached'
      );
      return;
    }
  }

  try {
    await cart.updateLineQuantity(id, quantity);
  } catch (e) {
    await showError(e.response?.data?.message || 'Failed to update quantity', 'Update Failed');
  }
}

/* ✅ NEW: remove item */
async function handleRemoveLine(id) {
  const confirmed = await confirm('Remove this item from your cart?', 'Remove Item');
  if (!confirmed) return;

  try {
    await cart.removeLine(id);
  } catch (e) {
    await showError(e.response?.data?.message || 'Failed to remove item', 'Remove Failed');
  }
}

/* ✅ NEW: show limit warning */
async function handleLimitWarning({ type, message }) {
  const title = type === 'max' ? 'Maximum Stock Reached' : 'Minimum Quantity Reached';
  await warning(message, title);
}
</script>

<template>
  <div class="page-document checkout-page">
    <header class="page-hero">
      <p class="page-hero__eyebrow">Guest checkout</p>
      <h1 class="page-hero__title">Complete your order</h1>
      <p class="page-hero__lead">
        No account needed. Payments are per provider — the same rules as on the marketplace apply here.
      </p>
    </header>

    <div v-if="cart.isLocked" class="banner-lock">
      Checking out with <strong>{{ cart.lockedProviderName }}</strong>
    </div>

    <div class="checkout-grid">
      <!-- CART -->
      <section class="surface-panel checkout-panel">
        <h2>Your cart</h2>

        <CartLinesSection
          :lines="cart.lines"
          :estimated-total="cart.estimatedTotal"
          empty-message="Your cart is empty. Add listings from the marketplace."
          @clear="clearCart"
          @update-quantity="handleUpdateQuantity"
          @remove-line="handleRemoveLine"
          @show-limit-warning="handleLimitWarning"
        />
      </section>

      <!-- FORM -->
      <section class="surface-panel checkout-panel">
        <h2>Contact & delivery</h2>

        <FormField label="Full name" capitalize-first>
          <input v-model="guestName" type="text" autocomplete="name" />
        </FormField>

        <FormField label="Email">
          <input v-model="guestEmail" type="email" autocomplete="email" />
        </FormField>

        <FormField label="Phone">
          <input v-model="guestPhone" type="tel" autocomplete="tel" />
        </FormField>

        <FormField label="Delivery or pickup" capitalize-first>
          <textarea
            v-model="deliveryOrPickup"
            rows="4"
            placeholder="Farm address, gate instructions, or pickup window"
          ></textarea>
        </FormField>

        <FormField label="Payment method">
          <select v-model="paymentMethod">
            <option v-for="m in acceptedPaymentMethods" :key="m" :value="m">
              {{ m === 'EFT' ? 'EFT' : 'Cash' }}
            </option>
          </select>
        </FormField>

        <div v-if="showBankDetails" class="bank-box">
          <h3>Pay {{ cart.lockedProviderName }} via EFT</h3>
          <p class="muted small">
            Use these banking details to pay the provider. Include the reference so they can match your payment.
          </p>

          <dl class="bank-grid">
            <div>
              <dt>Bank</dt>
              <dd>{{ cart.lockedProviderBank.bankName || '—' }}</dd>
            </div>
            <div>
              <dt>Account name</dt>
              <dd>{{ cart.lockedProviderBank.accountName || '—' }}</dd>
            </div>
            <div>
              <dt>Account number</dt>
              <dd>{{ cart.lockedProviderBank.accountNumber || '—' }}</dd>
            </div>
            <div>
              <dt>Branch code</dt>
              <dd>{{ cart.lockedProviderBank.branchCode || '—' }}</dd>
            </div>
            <div class="bank-ref">
              <dt>Reference</dt>
              <dd>
                <strong>{{ cart.lockedProviderBank.reference || guestEmail || '—' }}</strong>
              </dd>
            </div>
          </dl>
        </div>

        <button
          type="button"
          class="btn btn-primary checkout-submit"
          :disabled="!canCheckout || submitting"
          @click="submitCheckout"
        >
          {{ submitting ? 'Submitting…' : 'Confirm & place order' }}
        </button>
      </section>
    </div>
  </div>
</template>

<style scoped>
.checkout-page {
  padding-bottom: 2rem;
}

.checkout-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1.5rem;
  align-items: start;
}

.checkout-panel h2 {
  font-family: var(--font-display);
}

.checkout-submit {
  margin-top: 0.75rem;
  width: 100%;
  padding-top: 0.7rem;
  padding-bottom: 0.7rem;
}

.bank-box {
  margin-top: 1rem;
  padding: 0.85rem 0.95rem;
  border-radius: 12px;
  border: 1px solid rgba(21, 74, 122, 0.16);
  background: rgba(21, 74, 122, 0.04);
}

.bank-box h3 {
  font-family: var(--font-display);
  margin: 0 0 0.35rem;
}

.bank-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.65rem 1rem;
  margin: 0.75rem 0 0;
}

.bank-grid dt {
  font-size: 0.78rem;
  color: var(--color-muted);
}

.bank-grid dd {
  margin: 0.15rem 0 0;
  font-weight: 600;
}

.bank-ref {
  grid-column: 1 / -1;
}

@media (max-width: 880px) {
  .checkout-grid {
    grid-template-columns: 1fr;
  }
}
</style>