import { defineStore } from 'pinia';
import { ref } from 'vue';
import { api } from '../api';

const STORAGE_KEY = 'agrimarket_session_id';

export const useSessionStore = defineStore('session', () => {
  const sessionId = ref(localStorage.getItem(STORAGE_KEY) || '');

  async function ensureSession() {
    if (sessionId.value) return sessionId.value;
    const { data } = await api.post('/api/public/cart/session');
    sessionId.value = data.sessionId;
    localStorage.setItem(STORAGE_KEY, sessionId.value);
    return sessionId.value;
  }

  function resetSession() {
    sessionId.value = '';
    localStorage.removeItem(STORAGE_KEY);
  }

  return { sessionId, ensureSession, resetSession };
});
