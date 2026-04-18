<script setup>
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { api } from '../api';
import { useAuthStore } from '../stores/auth';
import FormField from '../components/ui/FormField.vue';

const router = useRouter();
const auth = useAuthStore();

const supportEmail = ref('');
const supportPassword = ref('');
const message = ref('');
const error = ref('');

const currentPassword = ref('');
const newPassword = ref('');
const confirmNewPassword = ref('');
const pwdMessage = ref('');
const pwdError = ref('');

const cleanMessage = ref('');
const cleanError = ref('');
const cleaning = ref(false);

onMounted(() => {
  auth.restoreFromStorage();
  if (!auth.isPlatformAdmin) {
    router.replace({ path: '/login', query: { redirect: '/admin' } });
  }
});

async function createSupport() {
  message.value = '';
  error.value = '';
  try {
    await api.post('/api/admin/create-support-user', {
      email: supportEmail.value,
      password: supportPassword.value,
    });
    message.value = 'Support user created.';
    supportEmail.value = '';
    supportPassword.value = '';
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  }
}

async function changeOwnPassword() {
  pwdMessage.value = '';
  pwdError.value = '';
  if (newPassword.value !== confirmNewPassword.value) {
    pwdError.value = 'New passwords do not match';
    return;
  }
  try {
    await api.post('/api/auth/change-password', {
      currentPassword: currentPassword.value,
      newPassword: newPassword.value,
    });
    pwdMessage.value = 'Password updated. Use it next time you sign in.';
    currentPassword.value = '';
    newPassword.value = '';
    confirmNewPassword.value = '';
  } catch (e) {
    pwdError.value = e.response?.data?.message || e.message;
  }
}

async function cleanDb() {
  cleaning.value = true;
  cleanMessage.value = '';
  cleanError.value = '';
  try {
    const { data } = await api.post('/api/admin/maintenance/clean-db');
    const deletedUsers = data?.users ?? 0;
    cleanMessage.value = `Database cleaned. Deleted ${deletedUsers} users (kept only your admin).`;
  } catch (e) {
    cleanError.value = e.response?.data?.message || e.message;
  } finally {
    cleaning.value = false;
  }
}
</script>

<template>
  <div class="page-document admin-page">
    <header class="page-hero">
      <p class="page-hero__eyebrow">Platform</p>
      <h1 class="page-hero__title">Administration</h1>
      <p class="page-hero__lead">
        Change your administrator password anytime. Support accounts are invite-only and cannot self-register.
      </p>
    </header>

    <section class="surface-panel admin-panel">
      <h2>Your password</h2>
      <p class="muted small">Use your current password, then choose a new one (at least 8 characters).</p>
      <FormField label="Current password">
        <input v-model="currentPassword" type="password" autocomplete="current-password" />
      </FormField>
      <FormField label="New password">
        <input v-model="newPassword" type="password" minlength="8" autocomplete="new-password" />
      </FormField>
      <FormField label="Confirm new password">
        <input v-model="confirmNewPassword" type="password" minlength="8" autocomplete="new-password" />
      </FormField>
      <p v-if="pwdError" class="err-toast">{{ pwdError }}</p>
      <p v-if="pwdMessage" class="ok-msg">{{ pwdMessage }}</p>
      <button type="button" class="btn btn-primary" @click="changeOwnPassword">Update password</button>
    </section>

    <section class="surface-panel admin-panel">
      <h2>New support user</h2>
      <FormField label="Email">
        <input v-model="supportEmail" type="email" required />
      </FormField>
      <FormField label="Password">
        <input v-model="supportPassword" type="password" required minlength="8" />
      </FormField>
      <p v-if="error" class="err-toast">{{ error }}</p>
      <p v-if="message" class="ok-msg">{{ message }}</p>
      <button type="button" class="btn btn-primary" @click="createSupport">Create support user</button>
    </section>

    <section class="surface-panel admin-panel danger">
      <h2>Danger zone</h2>
      <p class="muted small">
        This will delete almost everything in the database and leave only your platform admin account.
      </p>
      <p v-if="cleanError" class="err-toast">{{ cleanError }}</p>
      <p v-if="cleanMessage" class="ok-msg">{{ cleanMessage }}</p>
      <button type="button" class="btn btn-ghost danger-btn" :disabled="cleaning" @click="cleanDb">
        {{ cleaning ? 'Cleaning…' : 'Clean database (keep only me)' }}
      </button>
    </section>
  </div>
</template>

<style scoped>
.admin-page {
  padding: 0.5rem 0 2rem;
}

.admin-panel {
  max-width: 480px;
  margin: 0 auto 1.5rem;
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.admin-panel .muted {
  margin: 0 0 0.5rem;
}

.admin-panel h2 {
  font-family: var(--font-display);
}

.admin-panel .btn {
  margin-top: 0.65rem;
  align-self: flex-start;
}

.danger-btn {
  border-color: rgba(180, 40, 40, 0.35);
  color: rgba(140, 20, 20, 0.95);
}
</style>
