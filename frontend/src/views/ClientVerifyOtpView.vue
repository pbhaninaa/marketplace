<script setup>
import { ref, onMounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { publicClientOtpApi } from '../services/marketplaceApi';
import { useAuthStore } from '../stores/auth';
import FormField from '../components/ui/FormField.vue';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();

const target = ref('');
const code = ref('');
const error = ref('');
const done = ref(false);

function syncTarget() {
  const q = route.query.target;
  target.value = typeof q === 'string' ? q : '';
}

onMounted(syncTarget);
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
    router.push('/');
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  }
}
</script>

<template>
  <div class="page-document login-page">
    <header class="page-hero">
      <p class="page-hero__eyebrow">Client access</p>
      <h1 class="page-hero__title">Enter your code</h1>
      <p class="page-hero__lead">Enter the 6-digit code sent to <strong>{{ target }}</strong>.</p>
    </header>

    <form class="surface-panel login-form" @submit.prevent="submit">
      <FormField label="Code">
        <input v-model="code" type="text" inputmode="numeric" required minlength="4" maxlength="12" />
      </FormField>
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

