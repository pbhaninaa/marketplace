import { describe, it, expect, beforeEach, vi } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';

vi.mock('../src/api', () => ({
  api: {
    post: vi.fn(),
  },
}));

import { api } from '../src/api';
import { useSessionStore } from '../src/stores/session';

beforeEach(() => {
  localStorage.clear();
  api.post.mockReset();
  setActivePinia(createPinia());
});

describe('useSessionStore', () => {
  it('ensureSession returns existing id without calling api', async () => {
    localStorage.setItem('agrimarket_session_id', 'sess-existing');
    setActivePinia(createPinia());
    const session = useSessionStore();
    const id = await session.ensureSession();
    expect(id).toBe('sess-existing');
    expect(api.post).not.toHaveBeenCalled();
  });

  it('ensureSession creates session when missing', async () => {
    api.post.mockResolvedValueOnce({ data: { sessionId: 'new-sess' } });
    const session = useSessionStore();
    const id = await session.ensureSession();
    expect(id).toBe('new-sess');
    expect(api.post).toHaveBeenCalledWith('/api/public/cart/session');
    expect(localStorage.getItem('agrimarket_session_id')).toBe('new-sess');
  });

  it('resetSession clears id and storage', () => {
    localStorage.setItem('agrimarket_session_id', 'x');
    setActivePinia(createPinia());
    const session = useSessionStore();
    session.resetSession();
    expect(session.sessionId).toBe('');
    expect(localStorage.getItem('agrimarket_session_id')).toBeNull();
  });
});

// Basic sanity check for guest-client OTP route existence is covered by router config;
// remaining UI is integration-tested manually (OTP code is logged by backend in local dev).
