import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { api, withSession } from '../api';
import { useSessionStore } from './session';

export const useCartStore = defineStore('cart', () => {
  const lines = ref([]);
  const lockedProviderId = ref(null);
  const lockedProviderName = ref(null);
  const lockedProviderLocation = ref(null);
  const lockedProviderBank = ref(null);
  const lockedProviderAcceptedPaymentMethods = ref([]);
  const estimatedTotal = ref('0.00');
  const lastError = ref(null);

  const isLocked = computed(() => lockedProviderId.value != null);

  /** Sum of line quantities (total units in cart). */
  const totalQuantity = computed(() =>
    (lines.value || []).reduce((sum, line) => sum + (Number(line.quantity) || 0), 0),
  );

  async function refresh() {
    const session = useSessionStore();
    await session.ensureSession();
    lastError.value = null;
    try {
      const { data } = await api.get('/api/public/cart', withSession(session.sessionId));
      lines.value = data.lines || [];
      lockedProviderId.value = data.lockedProviderId ?? null;
      lockedProviderName.value = data.lockedProviderName ?? null;
      lockedProviderLocation.value = data.lockedProviderLocation ?? null;
      lockedProviderBank.value =
        data.lockedProviderBankName || data.lockedProviderBankAccountNumber
          ? {
              bankName: data.lockedProviderBankName ?? '',
              accountName: data.lockedProviderBankAccountName ?? '',
              accountNumber: data.lockedProviderBankAccountNumber ?? '',
              branchCode: data.lockedProviderBankBranchCode ?? '',
              reference: data.lockedProviderBankReference ?? '',
            }
          : null;
      lockedProviderAcceptedPaymentMethods.value = data.lockedProviderAcceptedPaymentMethods || [];
      estimatedTotal.value = data.estimatedTotal ?? '0.00';
    } catch (e) {
      lastError.value = e.response?.data?.message || e.message;
    }
  }

  async function addListing(listing, opts = {}) {
    const session = useSessionStore();
    await session.ensureSession();
    lastError.value = null;
    const body = {
      listingId: listing.id,
      quantity: opts.quantity || 1,
      rentalStart: opts.rentalStart || null,
      rentalEnd: opts.rentalEnd || null,
    };
    try {
      await api.post('/api/public/cart/add', body, withSession(session.sessionId));
      await refresh();
      return { ok: true };
    } catch (e) {
      const msg = e.response?.data?.message || e.message;
      const code = e.response?.data?.code;
      lastError.value = msg;
      return { ok: false, code, message: msg };
    }
  }

  async function clearCart() {
    const session = useSessionStore();
    if (!session.sessionId) return;
    await api.delete('/api/public/cart', withSession(session.sessionId));
    await refresh();
  }

  function isGreyed(listing) {
    return isLocked.value && listing.providerId !== lockedProviderId.value;
  }

  return {
    lines,
    lockedProviderId,
    lockedProviderName,
    lockedProviderLocation,
    lockedProviderBank,
    lockedProviderAcceptedPaymentMethods,
    estimatedTotal,
    lastError,
    isLocked,
    totalQuantity,
    refresh,
    addListing,
    clearCart,
    isGreyed,
  };
});
