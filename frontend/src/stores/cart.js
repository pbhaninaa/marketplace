import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { publicCartApi } from '../services/marketplaceApi';
import { useSessionStore } from './session';
import { isMinInt } from '../utils/validation';

export const useCartStore = defineStore('cart', () => {
  const lines = ref([]);
  const lockedProviderId = ref(null);
  const lockedProviderName = ref(null);
  const lockedProviderLocation = ref(null);
  const lockedProviderBank = ref(null);
  const lockedProviderAcceptedPaymentMethods = ref([]);
  const lockedProviderDeliverySettings = ref(null);
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
      const { data } = await publicCartApi.getCart(session.sessionId);
      // Map backend response to frontend format
      lines.value = (data.lines || []).map(line => ({
        lineId: line.lineId,
        listingId: line.listingId,
        title: line.title,
        listingType: line.listingType,
        quantity: line.quantity,
        lineTotal: line.lineTotal,
        rentalStart: line.rentalStart,
        rentalEnd: line.rentalEnd,
        availableStock: line.availableStock,
      }));
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
      lockedProviderDeliverySettings.value = data.lockedProviderDeliveryAvailable !== undefined
        ? {
            deliveryAvailable: data.lockedProviderDeliveryAvailable ?? false,
            deliveryPricePerKm: data.lockedProviderDeliveryPricePerKm ?? null,
          }
        : null;
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
      await publicCartApi.addLine(session.sessionId, body);
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
    await publicCartApi.clear(session.sessionId);
    await refresh();
  }

  async function updateLineQuantity(cartLineId, quantity) {
    const session = useSessionStore();
    await session.ensureSession();
    lastError.value = null;
    if (!isMinInt(quantity, 1)) {
      lastError.value = 'Quantity must be at least 1.';
      return { ok: false, code: 'VALIDATION', message: lastError.value };
    }
    try {
      await publicCartApi.updateLineQuantity(session.sessionId, cartLineId, quantity);
      await refresh();
      return { ok: true };
    } catch (e) {
      const msg = e.response?.data?.message || e.message;
      const code = e.response?.data?.code;
      lastError.value = msg;
      return { ok: false, code, message: msg };
    }
  }

  async function removeLine(cartLineId) {
    const session = useSessionStore();
    await session.ensureSession();
    lastError.value = null;
    try {
      await publicCartApi.removeLine(session.sessionId, cartLineId);
      await refresh();
      return { ok: true };
    } catch (e) {
      const msg = e.response?.data?.message || e.message;
      const code = e.response?.data?.code;
      lastError.value = msg;
      return { ok: false, code, message: msg };
    }
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
    lockedProviderDeliverySettings,
    estimatedTotal,
    lastError,
    isLocked,
    totalQuantity,
    refresh,
    addListing,
    clearCart,
    updateLineQuantity,
    removeLine,
    isGreyed,
  };
});
