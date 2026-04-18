<script setup>
import { ref, onMounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { api } from '../api';
import { useAuthStore } from '../stores/auth';
import { useSetupStore } from '../stores/setup';
import FormField from '../components/ui/FormField.vue';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const setup = useSetupStore();

const email = ref('');
const password = ref('');
const error = ref('');

onMounted(async () => {
  try {
    await setup.fetchStatus();
  } catch {
    /* offline: leave needsFirstAdmin unchanged */
  }
  const q = route.query.email;
  if (typeof q === 'string' && q.trim()) {
    email.value = q.trim();
  }
});

watch(
  () => route.query.email,
  (q) => {
    if (typeof q === 'string' && q.trim()) {
      email.value = q.trim();
    }
  },
);

async function submit() {
  error.value = '';
  try {
    const { data } = await api.post('/api/auth/login', { email: email.value, password: password.value });
    auth.setSession(data);
    const raw = typeof route.query.redirect === 'string' ? route.query.redirect.trim() : '';
    function homeForRole() {
      if (auth.isPlatformAdmin) return '/admin';
      if (auth.isSupport) return '/support';
      if (auth.isProviderUser) return '/provider';
      return '/';
    }
    if (!raw || raw === '/') {
      router.push(homeForRole());
    } else {
      router.push(raw);
    }
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  }
}
</script>

<template>
  <div class="page-document login-page">
    <header class="page-hero">
      <p class="page-hero__eyebrow">Account access</p>
      <h1 class="page-hero__title">Sign in</h1>
      <p class="page-hero__lead">
        Sign in with your email and password. Providers use <strong>Sign up</strong> below; the platform owner may
        create the first administrator from <strong>Setup</strong> when the site is new.
      </p>
    </header>

    <form class="surface-panel login-form" @submit.prevent="submit">
      <FormField label="Email">
        <input v-model="email" type="email" required autocomplete="username" />
      </FormField>
      <FormField label="Password">
        <input v-model="password" type="password" required autocomplete="current-password" />
      </FormField>
      <p v-if="error" class="err-toast">{{ error }}</p>
      <button type="submit" class="btn btn-primary">Sign in</button>

      <nav class="login-links" aria-label="Account options">
        <router-link class="login-links__item login-links__item--primary" to="/register">Sign up</router-link>
        <span class="login-links__sep" aria-hidden="true">·</span>
        <router-link class="login-links__item" to="/forgot-password">Forgot password?</router-link>
      </nav>

      <p v-if="setup.needsFirstAdmin === true" class="login-callout">
        <span class="login-callout__label">New site?</span>
        Create the
        <router-link to="/setup">platform administrator</router-link>
        first. Provider sign-up stays available above but redirects to setup until an admin exists.
      </p>
    </form>
  </div>
</template>

<style scoped>
.login-page {
  padding: 0.5rem 0 2rem;
}

.login-form {
  max-width: 420px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.login-form .btn {
  margin-top: 0.65rem;
}

.login-links {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 0.35rem 0.5rem;
  margin-top: 1rem;
  padding-top: 1rem;
  border-top: 1px solid rgba(21, 74, 122, 0.12);
  font-size: 0.92rem;
}

.login-links__item {
  font-weight: 500;
  color: var(--color-link, #1565b5);
  text-decoration: none;
}

.login-links__item:hover {
  text-decoration: underline;
}

.login-links__item--primary {
  font-weight: 600;
}

.login-links__sep {
  color: var(--color-muted, #7a8695);
  user-select: none;
}

.login-callout {
  margin-top: 1rem;
  padding: 0.75rem 0.9rem;
  font-size: 0.86rem;
  line-height: 1.45;
  color: var(--color-info-text, #154a7a);
  background: var(--color-info-bg, #e8f2fc);
  border-radius: 8px;
  border: 1px solid rgba(21, 74, 122, 0.15);
}

.login-callout a {
  font-weight: 600;
}

.login-callout__label {
  display: block;
  font-weight: 600;
  margin-bottom: 0.25rem;
}
</style>
