<script setup>
import { ref } from 'vue';
import { api } from '../api';
import FormField from '../components/ui/FormField.vue';

const email = ref('');
const error = ref('');
const done = ref(false);

async function submit() {
  error.value = '';
  done.value = false;
  try {
    await api.post('/api/public/forgot-password', { email: email.value.trim() });
    done.value = true;
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  }
}
</script>

<template>
  <div class="page-document forgot-page">
    <header class="page-hero">
      <p class="page-hero__eyebrow">Account recovery</p>
      <h1 class="page-hero__title">Forgot password</h1>
      <p class="page-hero__lead">
        Enter your account email. If it exists, you can set a new password using the reset link. In local
        development, check the API server log for the link.
      </p>
    </header>

    <form class="surface-panel forgot-form" @submit.prevent="submit">
      <FormField label="Email">
        <input v-model="email" type="email" required autocomplete="email" />
      </FormField>
      <p v-if="error" class="err-toast">{{ error }}</p>
      <p v-if="done" class="ok-msg">
        If that email is registered, reset instructions have been processed. Check your inbox or server logs, then open
        the reset link.
      </p>
      <button type="submit" class="btn btn-primary" :disabled="done">Send reset</button>
      <p class="forgot-footer">
        <router-link to="/login">Back to sign in</router-link>
      </p>
    </form>
  </div>
</template>

<style scoped>
.forgot-page {
  padding: 0.5rem 0 2rem;
}

.forgot-form {
  max-width: 400px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.forgot-form .btn {
  margin-top: 0.75rem;
}

.forgot-footer {
  margin-top: 1rem;
  font-size: 0.9rem;
  text-align: center;
}
</style>
