<script setup>
import { computed, ref } from 'vue';
import OrderPaymentHistory from '../components/history/OrderPaymentHistory.vue';
import { clientHistoryApi, publicOrderInvoiceApi } from '../services/marketplaceApi';
import { useAuthStore } from '../stores/auth';

const auth = useAuthStore();
const signedIn = computed(() => auth.isAuthenticated && auth.isClientUser);
const invoiceError = ref('');

async function downloadInvoice(order) {
  invoiceError.value = '';
  try {
    await publicOrderInvoiceApi.download({ orderRef: order.reference || String(order.id), email: auth.email });
  } catch (e) {
    invoiceError.value = e.response?.data?.message || e.message || 'Could not download the invoice.';
  }
}
</script>

<template>
  <div class="page-document page-document--wide">
    <header class="page-hero">
      <p class="page-hero__eyebrow">Customer</p>
      <h1 class="page-hero__title">My orders &amp; payments</h1>
      <p class="page-hero__lead">Everything you have ordered, from every provider, and how it was paid.</p>
    </header>

    <section v-if="!signedIn" class="surface-panel">
      <p>Confirm your email with a one-time code to see your orders.</p>
      <router-link class="btn btn-primary" :to="{ path: '/client', query: { redirect: '/my-orders' } }">
        Get a code
      </router-link>
    </section>

    <template v-else>
      <p v-if="invoiceError" class="err-toast">{{ invoiceError }}</p>
      <OrderPaymentHistory :api="clientHistoryApi" audience="client" :on-invoice="downloadInvoice" />
    </template>
  </div>
</template>
