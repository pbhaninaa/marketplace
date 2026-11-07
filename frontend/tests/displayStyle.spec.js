import { describe, it, expect, beforeEach, vi } from 'vitest';
import { nextTick } from 'vue';
import { setActivePinia, createPinia } from 'pinia';

vi.mock('../src/api', () => ({
  api: { post: vi.fn(), get: vi.fn() },
  setAuthToken: vi.fn(),
}));

import { useAuthStore } from '../src/stores/auth';
import {
  useDisplayStyleStore,
  displayStyleKey,
  readDisplayStyle,
  DISPLAY_STYLES,
} from '../src/stores/displayStyle';
import { computeTilt } from '../src/composables/useGlassTilt';

const root = () => document.documentElement;

beforeEach(() => {
  localStorage.clear();
  root().removeAttribute('data-display');
  setActivePinia(createPinia());
});

describe('display style store', () => {
  it('defaults to classic and leaves the root untouched', () => {
    const display = useDisplayStyleStore();
    display.init();
    expect(display.style).toBe(DISPLAY_STYLES.CLASSIC);
    expect(root().hasAttribute('data-display')).toBe(false);
  });

  it('toggling glass sets the root attribute and persists per user', () => {
    const auth = useAuthStore();
    auth.email = 'Farmer@Example.com';
    const display = useDisplayStyleStore();
    display.init();
    display.toggle();
    expect(display.isGlass).toBe(true);
    expect(root().getAttribute('data-display')).toBe('glass');
    expect(localStorage.getItem(displayStyleKey('farmer@example.com'))).toBe('glass');
    expect(readDisplayStyle('someone-else@example.com')).toBe(DISPLAY_STYLES.CLASSIC);
  });

  it('switches to the next user preference on sign-in change', async () => {
    localStorage.setItem(displayStyleKey('a@example.com'), 'glass');
    const auth = useAuthStore();
    auth.email = 'a@example.com';
    const display = useDisplayStyleStore();
    display.init();
    expect(root().getAttribute('data-display')).toBe('glass');
    auth.email = 'b@example.com';
    await nextTick();
    expect(display.isGlass).toBe(false);
    expect(root().hasAttribute('data-display')).toBe(false);
  });

  it('ignores unknown stored values and survives storage errors', () => {
    localStorage.setItem(displayStyleKey(''), 'neon');
    expect(readDisplayStyle('')).toBe(DISPLAY_STYLES.CLASSIC);
    const spy = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('quota');
    });
    const display = useDisplayStyleStore();
    expect(() => display.setStyle('glass')).not.toThrow();
    expect(root().getAttribute('data-display')).toBe('glass');
    spy.mockRestore();
  });
});

describe('computeTilt', () => {
  const rect = { left: 0, top: 0, width: 200, height: 100 };

  it('is flat at the centre', () => {
    const t = computeTilt(rect, 100, 50);
    expect(t.rx).toBeCloseTo(0);
    expect(t.ry).toBeCloseTo(0);
    expect(t.mx).toBe(50);
    expect(t.my).toBe(50);
  });

  it('tilts toward the pointer and clamps outside the card', () => {
    const t = computeTilt(rect, 500, -50);
    expect(t.ry).toBeCloseTo(6);
    expect(t.rx).toBeCloseTo(6);
  });

  it('returns null for zero-size rects', () => {
    expect(computeTilt({ left: 0, top: 0, width: 0, height: 0 }, 1, 1)).toBeNull();
  });
});
