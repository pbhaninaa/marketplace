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
  api: { get: vi.fn(), post: vi.fn(), delete: vi.fn() },
  withSession: (id) => ({ headers: { 'X-Session-Id': id } }),
}));

beforeEach(() => {
  setActivePinia(createPinia());
});

describe('useCartStore', () => {
  it('isLocked is false when lockedProviderId is null', () => {
    const cart = useCartStore();
    cart.lockedProviderId = null;
    expect(cart.isLocked).toBe(false);
  });

  it('isLocked is true when lockedProviderId is set', () => {
    const cart = useCartStore();
    cart.lockedProviderId = 1;
    expect(cart.isLocked).toBe(true);
  });

  it('isGreyed is false when cart is not locked', () => {
    const cart = useCartStore();
    cart.lockedProviderId = null;
    expect(cart.isGreyed({ providerId: 99 })).toBe(false);
  });

  it('isGreyed is false for same provider when locked', () => {
    const cart = useCartStore();
    cart.lockedProviderId = 5;
    expect(cart.isGreyed({ providerId: 5 })).toBe(false);
  });

  it('isGreyed is true for different provider when locked', () => {
    const cart = useCartStore();
    cart.lockedProviderId = 5;
    expect(cart.isGreyed({ providerId: 99 })).toBe(true);
  });
});
