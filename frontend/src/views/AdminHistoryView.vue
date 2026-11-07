<script setup>
import { onMounted, ref } from 'vue';
import OrderPaymentHistory from '../components/history/OrderPaymentHistory.vue';
import { adminHistoryApi, publicCatalogApi } from '../services/marketplaceApi';

const providers = ref([]);

onMounted(async () => {
  try {
    const { data } = await publicCatalogApi.providerOptions();
    providers.value = Array.isArray(data) ? data : [];
  } catch {
    providers.value = [];
  }
});
</script>

<template>
  <div class="page-document page-document--wide">
    <header class="page-hero">
      <p class="page-hero__eyebrow">Platform</p>
      <h1 class="page-hero__title">Orders &amp; payments</h1>
      <p class="page-hero__lead">Every order and payment across all providers, with filters, totals and reports.</p>
    </header>
    <OrderPaymentHistory :api="adminHistoryApi" audience="admin" :providers="providers" can-export />
  </div>
</template>
