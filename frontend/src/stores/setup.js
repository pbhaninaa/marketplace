import { defineStore } from 'pinia';
import { ref } from 'vue';
import { api } from '../api';

export const useSetupStore = defineStore('setup', () => {
  const needsFirstAdmin = ref(null);

  async function fetchStatus() {
    const { data } = await api.get('/api/public/setup-status');
    needsFirstAdmin.value = data.needsFirstAdmin;
    return data.needsFirstAdmin;
  }

  return { needsFirstAdmin, fetchStatus };
});
