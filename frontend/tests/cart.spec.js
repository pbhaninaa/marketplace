import { describe, it, expect, beforeEach, vi } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { useCartStore } from '../src/stores/cart';

vi.mock('../src/stores/session', () => ({
  useSessionStore: () => ({
    sessionId: 'test-session',
    ensureSession: vi.fn().mockResolvedValue('test-session'),
  }),
}));

vi.mock('../src/api', () => ({
  api: { get: vi.fn(), post: vi.fn(), delete: vi.fn(), patch: vi.fn() },
  withSession: (id) => ({ headers: { 'X-Session-Id': id } }),
}));

beforeEach(() => {
  setActivePinia(createPinia());
});

describe('useCartStore', () => {
  it('never locks the cart to one seller', () => {
    const cart = useCartStore();
    cart.lockedProviderId = 5;
    expect(cart.isLocked).toBe(false);
  });

  it("never greys out another seller's listings", () => {
    const cart = useCartStore();
    cart.lockedProviderId = 5;
    expect(cart.isGreyed({ providerId: 99 })).toBe(false);
    expect(cart.isGreyed({ providerId: 5 })).toBe(false);
  });

  it('groups cart lines per seller from the API response', async () => {
    const { api } = await import('../src/api');
    api.get.mockResolvedValueOnce({
      data: {
        lines: [
          { lineId: 1, listingId: 10, title: 'Hay', listingType: 'SALE', quantity: 2, lineTotal: '100.00', providerId: 7, providerName: 'Alpha Farm' },
          { lineId: 2, listingId: 20, title: 'Trailer', listingType: 'SALE', quantity: 1, lineTotal: '200.00', providerId: 8, providerName: 'Beta Hire' },
        ],
        sellers: [
          { providerId: 7, providerName: 'Alpha Farm', acceptedPaymentMethods: ['CASH'], deliveryAvailable: false, lineIds: [1], itemCount: 2, subtotal: '100.00' },
          { providerId: 8, providerName: 'Beta Hire', acceptedPaymentMethods: ['EFT'], bankName: 'Test Bank', bankAccountNumber: '123', deliveryAvailable: true, deliveryPricePerKm: 9, lineIds: [2], itemCount: 1, subtotal: '200.00' },
        ],
        estimatedTotal: '300.00',
      },
    });
    const cart = useCartStore();
    await cart.refresh();
    expect(cart.sellerCount).toBe(2);
    expect(cart.linesForSeller(8).map((l) => l.title)).toEqual(['Trailer']);
    expect(cart.sellers[1].bank.bankName).toBe('Test Bank');
    expect(cart.sellers[1].deliverySettings).toEqual({ deliveryAvailable: true, deliveryPricePerKm: 9 });
    expect(cart.sellers[0].bank).toBeNull();
  });

  it('updateLineQuantity rejects quantities < 1 without API call', async () => {
    const { api } = await import('../src/api');
    const cart = useCartStore();
    const res = await cart.updateLineQuantity(123, 0);
    expect(res.ok).toBe(false);
    expect(api.patch).not.toHaveBeenCalled();
  });
});
