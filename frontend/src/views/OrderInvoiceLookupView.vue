<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import FormField from '../components/ui/FormField.vue';
import { publicOrderInvoiceApi, supportOrderInvoiceApi } from '../services/marketplaceApi';
import { useAuthStore } from '../stores/auth';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();

const isSupport = computed(() => route.path.startsWith('/support'));

const orderRef = ref('');
const email = ref('');
const busy = ref(false);
const error = ref('');

onMounted(() => {
  if (isSupport.value) {
    auth.restoreFromStorage();
    if (!auth.isPlatformAdmin && !auth.isSupport) {
      router.replace({ path: '/login', query: { redirect: route.fullPath } });
    }
  }
});

async function download() {
  error.value = '';
  busy.value = true;
  try {
    if (isSupport.value) {
      await supportOrderInvoiceApi.download(orderRef.value.trim());
    } else {
      await publicOrderInvoiceApi.download({
        orderRef: orderRef.value.trim(),
        email: email.value.trim() || undefined,
      });
    }
  } catch (e) {
    error.value = e.response?.data?.message || e.message || 'Download failed.';
  } finally {
    busy.value = false;
  }
}
</script>

<template>
  <div class="page-document page-document--narrow">
    <header class="page-hero">
      <p class="page-hero__eyebrow">{{ isSupport ? 'Support' : 'Marketplace' }}</p>
      <h1 class="page-hero__title">Order invoice</h1>
      <p class="page-hero__lead">
        <template v-if="isSupport">
          Enter the numeric order id or the customer verification code (as on the receipt).
        </template>
        <template v-else>
          Use your <strong>verification code</strong> from the checkout email, or your <strong>numeric order number</strong> plus
          the <strong>email</strong> you used when placing the order.
        </template>
      </p>
    </header>

    <section class="surface-panel inv-panel">
      <p v-if="error" class="err-toast">{{ error }}</p>
      <FormField label="Order number or verification code">
        <input v-model="orderRef" type="text" autocomplete="off" placeholder="e.g. 42 or AB12-CD34" />
      </FormField>
      <FormField v-if="!isSupport" label="Email (required if you use numeric order number)">
        <input v-model="email" type="email" autocomplete="email" placeholder="you@example.com" />
      </FormField>
      <button type="button" class="btn btn-primary" :disabled="busy || !orderRef.trim()" @click="download">
        {{ busy ? 'Downloading…' : 'Download PDF invoice' }}
      </button>
    </section>
  </div>
</template>

<style scoped>
.inv-panel {
  max-width: 480px;
  margin: 0 auto;
  display: grid;
  gap: 0.85rem;
}
.page-document--narrow {
  max-width: 720px;
  margin: 0 auto;
}
</style>
