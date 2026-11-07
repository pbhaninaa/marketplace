<script setup>
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { publicSetupApi } from '../services/marketplaceApi';
import { useSetupStore } from '../stores/setup';
import FormField from '../components/ui/FormField.vue';
import { emailError, LIMITS } from '../utils/validation';
import PasswordHint from '../components/ui/PasswordHint.vue';
import { apiErrorMessage } from '../utils/apiError';
import { passwordProblem } from '../utils/passwordPolicy';

const router = useRouter();
const setup = useSetupStore();

const email = ref('');
const password = ref('');
const error = ref('');
const done = ref(false);
const showErrors = ref(false);
const emailMsg = computed(() => (showErrors.value ? emailError(email.value, { label: 'Administrator email' }) : ''));
const passwordMsg = computed(() =>
  showErrors.value
    ? password.value
      ? passwordProblem(password.value, email.value)
      : 'Password is required.'
    : ''
);

onMounted(async () => {
  await setup.fetchStatus();
  if (!setup.needsFirstAdmin) {
    router.replace('/');
  }
});

async function submit() {
  error.value = '';
  showErrors.value = true;
  if (emailMsg.value || passwordMsg.value) return;
  try {
    await publicSetupApi.createFirstAdmin({ email: email.value.trim(), password: password.value });
    done.value = true;
    await setup.fetchStatus();
    setTimeout(() => router.push('/login'), 1500);
  } catch (e) {
    error.value = apiErrorMessage(e, 'Something went wrong. Please try again.');
  }
}
</script>

<template>
  <div class="page-document setup-page">
    <header class="page-hero">
      <p class="page-hero__eyebrow">First time setup</p>
      <h1 class="page-hero__title">Platform administrator</h1>
      <p class="page-hero__lead">
        The first user with full platform access. You will use this account to create support users and manage
        providers.
      </p>
    </header>

    <form class="surface-panel setup-form" @submit.prevent="submit">
      <FormField label="Administrator email" :error="emailMsg">
        <input v-model="email" type="email" required :maxlength="LIMITS.email" autocomplete="email" />
      </FormField>
      <FormField label="Password" :error="passwordMsg">
        <input v-model="password" type="password" required minlength="8" maxlength="64" autocomplete="new-password" />
      </FormField>
      <PasswordHint :password="password" :email="email" />
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
