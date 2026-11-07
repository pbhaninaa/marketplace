<script setup>
import OrderPaymentHistory from '../components/history/OrderPaymentHistory.vue';
import { providerHistoryApi } from '../services/marketplaceApi';
</script>

<template>
  <div class="page-document page-document--wide">
    <header class="page-hero">
      <p class="page-hero__eyebrow">Provider</p>
      <h1 class="page-hero__title">Order &amp; payment history</h1>
      <p class="page-hero__lead">
        All of your orders and payments in one place. Filter, check totals and download reports.
        To confirm or fulfil an order, use <router-link to="/provider/orders">Orders</router-link>.
      </p>
    </header>
    <OrderPaymentHistory :api="providerHistoryApi" audience="provider" can-export />
  </div>
</template>
