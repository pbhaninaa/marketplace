import { defineStore } from 'pinia';
import { ref } from 'vue';
import { publicSetupApi } from '../services/marketplaceApi';

export const useSetupStore = defineStore('setup', () => {
  const needsFirstAdmin = ref(null);

  async function fetchStatus() {
    const { data } = await publicSetupApi.getSetupStatus();
    needsFirstAdmin.value = data.needsFirstAdmin;
    return data.needsFirstAdmin;
  }

  return { needsFirstAdmin, fetchStatus };
});
