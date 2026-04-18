<script setup>
import { ref, onMounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { api } from '../api';
import FormField from '../components/ui/FormField.vue';

const route = useRoute();
const router = useRouter();

const token = ref('');
const newPassword = ref('');
const confirmPassword = ref('');
const error = ref('');
const done = ref(false);

function syncTokenFromRoute() {
  const q = route.query.token;
  token.value = typeof q === 'string' ? q : '';
}

onMounted(syncTokenFromRoute);
watch(() => route.query.token, syncTokenFromRoute);

async function submit() {
  error.value = '';
  if (newPassword.value !== confirmPassword.value) {
    error.value = 'Passwords do not match';
    return;
  }
  if (!token.value.trim()) {
    error.value = 'Missing reset token. Open the link from your email or server log.';
    return;
  }
  try {
    await api.post('/api/public/reset-password', {
      token: token.value.trim(),
      newPassword: newPassword.value,
    });
    done.value = true;
    setTimeout(() => router.push('/login'), 1500);
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  }
}
</script>

<template>
  <div class="page-document reset-page">
    <header class="page-hero">
      <p class="page-hero__eyebrow">Account recovery</p>
      <h1 class="page-hero__title">Set new password</h1>
      <p class="page-hero__lead">Choose a new password for your account.</p>
    </header>

    <form class="surface-panel reset-form" @submit.prevent="submit">
      <FormField label="New password">
        <input v-model="newPassword" type="password" required minlength="8" maxlength="100" autocomplete="new-password" />
      </FormField>
      <FormField label="Confirm password">
        <input
          v-model="confirmPassword"
          type="password"
          required
          minlength="8"
          maxlength="100"
          autocomplete="new-password"
        />
      </FormField>
      <p v-if="error" class="err-toast">{{ error }}</p>
      <p v-if="done" class="ok-msg">Password updated. Redirecting to sign in…</p>
      <button type="submit" class="btn btn-primary" :disabled="done || !token.trim()">Update password</button>
      <p class="reset-footer">
        <router-link to="/login">Back to sign in</router-link>
      </p>
    </form>
  </div>
</template>

<style scoped>
.reset-page {
  padding: 0.5rem 0 2rem;
}

.reset-form {
  max-width: 400px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.reset-form .btn {
  margin-top: 0.75rem;
}

.reset-footer {
  margin-top: 1rem;
  font-size: 0.9rem;
  text-align: center;
}
</style>
