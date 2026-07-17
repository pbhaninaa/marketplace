<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { publicPeachApi } from '../services/marketplaceApi';
import { useCartStore } from '../stores/cart';

const route = useRoute();
const router = useRouter();
const cart = useCartStore();

const ref_ = ref('');
const status = ref('');
const verificationCodes = ref([]);
const polling = ref(false);
const error = ref('');
let pollTimer = null;

const statusLabel = computed(() => {
  if (status.value === 'PAID') return 'successful';
  if (status.value === 'CANCELLED') return 'cancelled';
  if (status.value === 'PENDING_PAYMENT') return 'processing';
  return 'unknown';
});

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
  polling.value = false;
}

async function fetchStatus() {
  if (!ref_.value) return;
  try {
    const { data } = await publicPeachApi.status(ref_.value);
    status.value = data?.status || '';
    verificationCodes.value = data?.verificationCodes || [];
    if (status.value === 'PAID' || status.value === 'CANCELLED') {
      stopPolling();
      await cart.refresh();
    }
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
    stopPolling();
  }
}

function startPolling() {
  stopPolling();
  polling.value = true;
  void fetchStatus();
  let attempts = 0;
  pollTimer = setInterval(() => {
    attempts += 1;
    void fetchStatus();
    if (attempts >= 15) {
      stopPolling();
    }
  }, 2000);
}

function goHome() {
  router.push('/');
}

onMounted(() => {
  ref_.value = String(route.query.ref || '');
  if (ref_.value) {
    startPolling();
  } else {
    error.value = 'Missing payment reference.';
  }
});

onBeforeUnmount(() => {
  stopPolling();
});
</script>

<template>
  <div class="peach-return-page">
    <div class="surface-panel">
      <h1>Payment {{ statusLabel }}</h1>

      <div v-if="polling" class="spinner" aria-hidden="true" />

      <p v-if="error" class="toast error">{{ error }}</p>

      <p v-else-if="status === 'PAID'" class="toast success">
        Your payment was received. This order is now confirmed as paid.
      </p>

      <p v-else-if="status === 'CANCELLED'" class="toast error">
        The Peach payment was cancelled. Your order has not been marked as paid.
      </p>

      <p v-else-if="status === 'PENDING_PAYMENT'" class="muted">
        We are waiting for confirmation from Peach. This usually takes a few seconds.
      </p>

      <p v-else class="muted">
        Payment status: {{ status || 'unknown' }}. If you completed payment, check again shortly.
      </p>

      <div v-if="verificationCodes.length" class="codes-box">
        <p class="muted small">Keep your verification code(s) — show them to the provider when you collect / receive delivery:</p>
        <ul>
          <li v-for="c in verificationCodes" :key="c"><strong>{{ c }}</strong></li>
        </ul>
      </div>

      <div class="mt-5">
        <button class="btn btn-primary" @click="goHome">Back to marketplace</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.peach-return-page {
  display: flex;
  justify-content: center;
  padding: 1rem;
  min-height: 60vh;
}

.surface-panel {
  width: 100%;
  max-width: 560px;
  padding: 1.5rem;
  border-radius: 16px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.05);
}

.spinner {
  width: 32px;
  height: 32px;
  margin: 0.5rem 0 1rem;
  border: 3px solid #ddd;
  border-top-color: #3d7a66;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.toast {
  padding: 0.6rem 0.8rem;
  border-radius: 10px;
  margin: 0.5rem 0 1rem;
}

.error {
  background: #fee2e2;
  color: #b91c1c;
}

.success {
  background: #dcfce7;
  color: #15803d;
}

.codes-box {
  margin-top: 1rem;
  padding: 0.8rem;
  background: #f4f7ff;
  border-radius: 12px;
}

.codes-box ul {
  margin: 0.4rem 0 0;
  padding-left: 1.2rem;
}

.mt-5 {
  margin-top: 1.25rem;
}
</style>
