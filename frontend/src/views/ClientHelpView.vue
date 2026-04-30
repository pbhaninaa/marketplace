<script setup>
import { computed } from 'vue';
import { useAuthStore } from '../stores/auth';

const auth = useAuthStore();
auth.restoreFromStorage();

const isLoggedInClient = computed(() => auth.isAuthenticated && auth.isClientUser);
</script>

<template>
  <div class="page-document page-document--wide help-page">
    <header class="page-hero">
      <p class="page-hero__eyebrow">Customer</p>
      <h1 class="page-hero__title">Help & support</h1>
      <p class="page-hero__lead">
        Simple guides for browsing, checkout, and getting invoices.
      </p>
    </header>

    <section class="surface-panel help-panel">
      <h2>Quick start</h2>
      <div class="steps">
        <div class="step">
          <strong>1) Browse items</strong>
          <p class="muted small">
            Go to <router-link to="/">Browse</router-link>, filter by category, and open any listing to view details.
          </p>
        </div>
        <div class="step">
          <strong>2) Add to cart</strong>
          <p class="muted small">
            Add items to your cart, then open <router-link to="/checkout">Checkout</router-link>.
          </p>
        </div>
        <div class="step">
          <strong>3) Place your order</strong>
          <p class="muted small">
            Fill in your details, confirm delivery/pickup, then submit your order.
          </p>
        </div>
      </div>
    </section>

    <section class="surface-panel help-panel">
      <h2>Invoices</h2>
      <div class="faq">
        <details open>
          <summary>How do I download an invoice?</summary>
          <p class="muted small">
            Use <router-link to="/order-invoice">Order invoice</router-link> and enter your order number.
            If the order number is numeric, you may be asked to provide the email used at checkout.
          </p>
        </details>
        <details>
          <summary>I can’t find my invoice</summary>
          <p class="muted small">
            Double-check the order number and email spelling. If it still fails, contact support and share your order number.
          </p>
        </details>
      </div>
    </section>

    <section class="surface-panel help-panel">
      <h2>Account & login</h2>
      <div class="faq">
        <details open>
          <summary>Do I need an account to buy?</summary>
          <p class="muted small">
            You can place orders as a guest. If you prefer account access, use <router-link to="/client">Client access</router-link>
            to request and verify an OTP.
          </p>
        </details>
        <details v-if="isLoggedInClient">
          <summary>I’m logged in as a client — what’s different?</summary>
          <p class="muted small">
            Your session is linked to your email, so invoice lookups are easier. You can still use
            <router-link to="/order-invoice">Order invoice</router-link> any time.
          </p>
        </details>
        <details>
          <summary>I forgot my password</summary>
          <p class="muted small">
            Use <router-link to="/forgot-password">Forgot password</router-link> to request a reset link.
          </p>
        </details>
      </div>
    </section>
  </div>
</template>

<style scoped>
.help-page {
  padding-bottom: 2rem;
}
.help-panel + .help-panel {
  margin-top: var(--space-5);
}
.steps {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-4);
  margin-top: var(--space-3);
}
.step {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: var(--space-4);
  background: var(--color-surface-elevated);
  box-shadow: var(--shadow-sm);
}
.faq details {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: var(--space-3) var(--space-4);
  background: var(--color-surface-elevated);
  box-shadow: var(--shadow-sm);
  margin-top: var(--space-3);
}
.faq summary {
  cursor: pointer;
  font-weight: 700;
  color: var(--color-canopy);
}
@media (max-width: 980px) {
  .steps {
    grid-template-columns: 1fr;
  }
}
</style>

