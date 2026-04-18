<script setup>
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { api } from '../api';
import FormField from '../components/ui/FormField.vue';

const router = useRouter();
const target = ref('');
const error = ref('');
const done = ref(false);

async function submit() {
  error.value = '';
  done.value = false;
  try {
    await api.post('/api/public/client/otp/request', { target: target.value.trim() });
    done.value = true;
    router.push({ path: '/client/verify', query: { target: target.value.trim() } });
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  }
}
</script>

<template>
  <div class="page-document login-page">
    <header class="page-hero">
      <p class="page-hero__eyebrow">Client access</p>
      <h1 class="page-hero__title">Continue as guest</h1>
      <p class="page-hero__lead">
        Enter your email or phone number. We'll send a one-time code. In local development, check the API server log
        for the code.
      </p>
    </header>

    <form class="surface-panel login-form" @submit.prevent="submit">
      <FormField label="Email or phone">
        <input v-model="target" type="text" required autocomplete="email" />
      </FormField>
      <p v-if="error" class="err-toast">{{ error }}</p>
      <p v-if="done" class="ok-msg">Code requested. Redirecting…</p>
      <button type="submit" class="btn btn-primary" :disabled="done">Send code</button>
    </form>
  </div>
</template>

