<script setup>
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { supportApi } from '../services/marketplaceApi';
import { useAuthStore } from '../stores/auth';
import FormField from '../components/ui/FormField.vue';
import { isNonEmptyString } from '../utils/validation';

const router = useRouter();
const auth = useAuthStore();

const otpTarget = ref('');
const error = ref('');
const message = ref('');

onMounted(() => {
  auth.restoreFromStorage();
  if (!auth.isSupport && !auth.isPlatformAdmin) {
    router.replace({ path: '/login', query: { redirect: '/support/otp' } });
  }
});

async function resendOtp() {
  message.value = '';
  error.value = '';
  if (!isNonEmptyString(otpTarget.value)) {
    error.value = 'Please enter a client email or phone.';
    return;
  }
  try {
    await supportApi.resendClientOtp({ target: otpTarget.value.trim() });
    message.value = 'OTP re-issued (check logs in local dev).';
    otpTarget.value = '';
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  }
}
</script>

<template>
  <div class="page-document page-document--wide admin-page">
    <header class="page-hero">
      <p class="page-hero__eyebrow">Support</p>
      <h1 class="page-hero__title">Client OTP</h1>
      <p class="page-hero__lead">Resend a client OTP to help them verify access.</p>
    </header>

    <p v-if="error" class="err-toast">{{ error }}</p>
    <p v-if="message" class="ok-msg">{{ message }}</p>

    <section class="surface-panel admin-panel">
      <h2>Resend OTP</h2>
      <FormField label="Client email / phone">
        <input v-model="otpTarget" type="text" />
      </FormField>
      <button type="button" class="btn btn-primary" @click="resendOtp">Resend OTP</button>
    </section>
  </div>
</template>

