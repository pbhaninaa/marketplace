<script setup>
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { api } from '../api';
import { useAuthStore } from '../stores/auth';
import FormField from '../components/ui/FormField.vue';

const router = useRouter();
const auth = useAuthStore();

const saving = ref(false);
const error = ref('');
const message = ref('');

const form = ref({ currentPassword: '', newPassword: '' });

onMounted(() => {
  auth.restoreFromStorage();
  if (!auth.isPlatformAdmin) {
    router.replace({ path: '/login', query: { redirect: '/admin/password' } });
  }
});

async function save() {
  saving.value = true;
  error.value = '';
  message.value = '';
  try {
    await api.post('/api/auth/change-password', {
      currentPassword: form.value.currentPassword,
      newPassword: form.value.newPassword,
    });
    form.value = { currentPassword: '', newPassword: '' };
    message.value = 'Password updated.';
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <div class="page-document admin-password-page">
    <header class="page-hero">
      <p class="page-hero__eyebrow">Platform</p>
      <h1 class="page-hero__title">Change password</h1>
      <p class="page-hero__lead">Update your platform administrator password.</p>
    </header>

    <p v-if="error" class="err-toast">{{ error }}</p>
    <p v-if="message" class="ok-msg">{{ message }}</p>

    <section class="surface-panel form-panel">
      <FormField label="Current password">
        <input v-model="form.currentPassword" type="password" minlength="8" maxlength="100" />
      </FormField>
      <FormField label="New password">
        <input v-model="form.newPassword" type="password" minlength="8" maxlength="100" />
      </FormField>

      <button type="button" class="btn btn-primary" :disabled="saving" @click="save">
        {{ saving ? 'Saving…' : 'Change password' }}
      </button>
    </section>
  </div>
</template>

<style scoped>
.form-panel {
  max-width: 520px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}
.form-panel .btn {
  margin-top: 0.75rem;
  align-self: flex-start;
}
</style>

