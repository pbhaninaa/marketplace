import { defineStore } from 'pinia';
import { ref } from 'vue';
import { publicSetupApi } from '../services/marketplaceApi';

export const useSetupStore = defineStore('setup', () => {
  const needsFirstAdmin = ref(null);
  /** Provider sign-up needs a code emailed to the owner address (default on; off in local tests). */
  const signupEmailVerificationRequired = ref(true);

  async function fetchStatus() {
    const { data } = await publicSetupApi.getSetupStatus();
    needsFirstAdmin.value = data.needsFirstAdmin;
    if (typeof data.signupEmailVerificationRequired === 'boolean') {
      signupEmailVerificationRequired.value = data.signupEmailVerificationRequired;
    }
    return data.needsFirstAdmin;
  }

  return { needsFirstAdmin, signupEmailVerificationRequired, fetchStatus };
});
