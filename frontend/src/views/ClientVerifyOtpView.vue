<script setup>
import { ref, onMounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { publicClientOtpApi } from '../services/marketplaceApi';
import { useAuthStore } from '../stores/auth';
import FormField from '../components/ui/FormField.vue';
import { apiErrorMessage } from '../utils/apiError';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();

const target = ref('');
const code = ref('');
const error = ref('');
const done = ref(false);
/** True when SIT/local handed back a test code (no email/SMS is sent there). */
const testCodeFilled = ref(false);

function syncTarget() {
  const q = route.query.target;
  target.value = typeof q === 'string' ? q : '';
}

function fillTestCode() {
  const testCode = typeof window !== 'undefined' ? window.history.state?.testCode : null;
  if (testCode && !code.value) {
    code.value = String(testCode);
    testCodeFilled.value = true;
  }
}

onMounted(() => {
  syncTarget();
  fillTestCode();
});
watch(() => route.query.target, syncTarget);

async function submit() {
  error.value = '';
  done.value = false;
  try {
    const { data } = await publicClientOtpApi.verify({
      target: target.value.trim(),
      code: code.value.trim(),
    });
    auth.setSession({
      token: data.token,
      role: data.role,
      email: data.clientTarget,
      displayName: '',
      providerId: null,
    });
    done.value = true;
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '';
    router.push(redirect.startsWith('/') && !redirect.startsWith('//') ? redirect : '/');
  } catch (e) {
    error.value = apiErrorMessage(e, 'Something went wrong. Please try again.');
  }
}
</script>

<template>
  <div class="page-document login-page">
    <header class="page-hero">
      <p class="page-hero__eyebrow">Client access</p>
      <h1 class="page-hero__title">Enter your code</h1>
      <p class="page-hero__lead">Enter the 6 digit code sent to <strong>{{ target }}</strong>.</p>
    </header>

    <form class="surface-panel login-form" @submit.prevent="submit">
      <FormField label="Code">
        <input v-model="code" type="text" inputmode="numeric" required minlength="4" maxlength="12" />
      </FormField>
      <p v-if="testCodeFilled" class="muted small" role="status">
        Test environment: no email or SMS is sent. The code has been filled in for you.
      </p>
      <p v-if="error" class="err-toast">{{ error }}</p>
      <p v-if="done" class="ok-msg">Signed in. Redirecting…</p>
      <button type="submit" class="btn btn-primary" :disabled="done">Verify</button>
      <p class="login-footer">
        Didn't get a code?
        <router-link :to="{ path: '/client', query: { target } }">Request a new one</router-link>
      </p>
    </form>
  </div>
</template>

