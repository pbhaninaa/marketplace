import { afterEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import MapPinPicker from '../src/components/ui/MapPinPicker.vue';

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('MapPinPicker', () => {
  it('sets the pin from the device location, rounded to 6 decimals', async () => {
    const getCurrentPosition = vi.fn((ok) => ok({ coords: { latitude: -25.74612345678, longitude: 28.18812345678 } }));
    vi.stubGlobal('navigator', { ...navigator, geolocation: { getCurrentPosition } });
    const w = mount(MapPinPicker, { props: { modelValue: null }, attachTo: document.body });

    const btn = w.findAll('button').find((b) => b.text().includes('Use my current location'));
    await btn.trigger('click');
    await flushPromises();

    expect(w.emitted('update:modelValue')?.[0]?.[0]).toEqual({ lat: -25.746123, lng: 28.188123 });
    w.unmount();
  });

  it('explains what to do when location access is refused', async () => {
    const getCurrentPosition = vi.fn((_ok, fail) => fail(new Error('denied')));
    vi.stubGlobal('navigator', { ...navigator, geolocation: { getCurrentPosition } });
    const w = mount(MapPinPicker, { props: { modelValue: null }, attachTo: document.body });

    await w.findAll('button').find((b) => b.text().includes('Use my current location')).trigger('click');
    await flushPromises();

    expect(w.text()).toContain('tap the map instead');
    expect(w.emitted('update:modelValue')).toBeUndefined();
    w.unmount();
  });

  it('shows the chosen pin', () => {
    const w = mount(MapPinPicker, { props: { modelValue: { lat: -26.2, lng: 28.04 } }, attachTo: document.body });
    expect(w.text()).toContain('Pin set at -26.20000, 28.04000');
    w.unmount();
  });
});
