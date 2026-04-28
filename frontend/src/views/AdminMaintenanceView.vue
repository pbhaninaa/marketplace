<script setup>
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { api } from '../api';
import { useAuthStore } from '../stores/auth';

const router = useRouter();
const auth = useAuthStore();

const cleaning = ref(false);
const cleanMessage = ref('');
const cleanError = ref('');

onMounted(() => {
  auth.restoreFromStorage();
  if (!auth.isPlatformAdmin) {
    router.replace({ path: '/login', query: { redirect: '/admin/maintenance' } });
  }
});

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
  <div class="page-document admin-maint-page">
    <header class="page-hero">
      <p class="page-hero__eyebrow">Platform</p>
      <h1 class="page-hero__title">Maintenance</h1>
      <p class="page-hero__lead">Sensitive operations. Keep this page small and intentional.</p>
    </header>

    <section class="surface-panel danger-panel">
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
.danger-panel {
  max-width: 560px;
  margin: 0 auto;
}
.danger-btn {
  border-color: rgba(180, 40, 40, 0.35);
  color: rgba(140, 20, 20, 0.95);
}
</style>

