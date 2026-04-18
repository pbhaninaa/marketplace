<script setup>
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { api } from '../api';
import { useSetupStore } from '../stores/setup';
import FormField from '../components/ui/FormField.vue';

const router = useRouter();
const setup = useSetupStore();

const email = ref('');
const password = ref('');
const error = ref('');
const done = ref(false);

onMounted(async () => {
  await setup.fetchStatus();
  if (!setup.needsFirstAdmin) {
    router.replace('/');
  }
});

async function submit() {
  error.value = '';
  try {
    await api.post('/api/public/first-admin', { email: email.value, password: password.value });
    done.value = true;
    await setup.fetchStatus();
    setTimeout(() => router.push('/login'), 1500);
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  }
}
</script>

<template>
  <div class="page-document setup-page">
    <header class="page-hero">
      <p class="page-hero__eyebrow">One-time setup</p>
      <h1 class="page-hero__title">Platform administrator</h1>
      <p class="page-hero__lead">
        The first user with full platform access. You will use this account to create support users and manage
        providers.
      </p>
    </header>

    <form class="surface-panel setup-form" @submit.prevent="submit">
      <FormField label="Administrator email">
        <input v-model="email" type="email" required autocomplete="email" />
      </FormField>
      <FormField label="Password">
        <input v-model="password" type="password" required minlength="8" autocomplete="new-password" />
      </FormField>
      <p v-if="error" class="err-toast">{{ error }}</p>
      <p v-if="done" class="ok-msg">Administrator created. Redirecting to sign in…</p>
      <button type="submit" class="btn btn-primary" :disabled="done">Create administrator</button>
    </form>
  </div>
</template>

<style scoped>
.setup-page {
  padding: 0.5rem 0 2rem;
}

.setup-form {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  max-width: 440px;
  margin: 0 auto;
}

.setup-form .btn {
  margin-top: 0.75rem;
}
</style>
