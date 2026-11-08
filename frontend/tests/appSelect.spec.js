import { afterEach, describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import { nextTick, ref } from 'vue';
import AppSelect from '../src/components/ui/AppSelect.vue';

const mounted = [];
function render(template, setup = () => ({})) {
  const w = mount(
    { components: { AppSelect }, template, setup },
    { attachTo: document.body },
  );
  mounted.push(w);
  return w;
}
afterEach(() => {
  while (mounted.length) mounted.pop().unmount();
  document.body.innerHTML = '';
});

const list = () => document.querySelector('.app-select__list');
const optionEls = () => Array.from(document.querySelectorAll('.app-select__option'));

describe('AppSelect', () => {
  it('shows the selected option label, opens a styled list and updates v-model', async () => {
    const value = ref('b');
    const w = render(
      `<AppSelect v-model="value"><option value="a">Apple</option><option value="b">Banana</option></AppSelect>`,
      () => ({ value }),
    );
    await nextTick();
    const btn = w.find('button[role="combobox"]');
    expect(btn.text()).toBe('Banana');
    expect(btn.attributes('aria-expanded')).toBe('false');

    await btn.trigger('click');
    expect(list()).not.toBeNull();
    expect(btn.attributes('aria-expanded')).toBe('true');
    expect(optionEls().map((o) => o.textContent.trim())).toEqual(['Apple', 'Banana']);
    expect(optionEls()[1].getAttribute('aria-selected')).toBe('true');

    optionEls()[0].click();
    await nextTick();
    expect(value.value).toBe('a');
    expect(list()).toBeNull();
    expect(btn.text()).toBe('Apple');
  });

  it('keeps non-string option values (booleans) and emits a native-like change event', async () => {
    const enabled = ref(false);
    const seen = [];
    const w = render(
      `<AppSelect v-model="enabled" @change="onChange($event.target.value)">
         <option :value="true">Active</option><option :value="false">Disabled</option>
       </AppSelect>`,
      () => ({ enabled, onChange: (v) => seen.push(v) }),
    );
    await nextTick();
    await w.find('button[role="combobox"]').trigger('click');
    optionEls()[0].click();
    await nextTick();
    expect(enabled.value).toBe(true);
    expect(seen).toEqual([true]);
  });

  it('is fully usable from the keyboard', async () => {
    const value = ref('');
    const w = render(
      `<AppSelect v-model="value"><option value="">All</option><option value="x">Xylophone</option><option value="y" disabled>Yak</option><option value="z">Zebra</option></AppSelect>`,
      () => ({ value }),
    );
    await nextTick();
    const btn = w.find('button[role="combobox"]');
    await btn.trigger('keydown', { key: 'ArrowDown' });
    expect(list()).not.toBeNull();
    await btn.trigger('keydown', { key: 'ArrowDown' }); // All -> Xylophone
    await btn.trigger('keydown', { key: 'ArrowDown' }); // skips disabled Yak -> Zebra
    await btn.trigger('keydown', { key: 'Enter' });
    expect(value.value).toBe('z');
    expect(list()).toBeNull();

    await btn.trigger('keydown', { key: 'Enter' });
    await btn.trigger('keydown', { key: 'Escape' });
    expect(list()).toBeNull();
    expect(value.value).toBe('z');
  });

  it('supports multiple selection and stays open while picking', async () => {
    const modes = ref(['HOURLY']);
    const w = render(
      `<AppSelect v-model="modes" multiple><option value="HOURLY">Hourly</option><option value="DAILY">Daily</option></AppSelect>`,
      () => ({ modes }),
    );
    await nextTick();
    await w.find('button[role="combobox"]').trigger('click');
    optionEls()[1].click();
    await nextTick();
    expect(modes.value).toEqual(['HOURLY', 'DAILY']);
    expect(list()).not.toBeNull();
    expect(w.find('button[role="combobox"]').text()).toBe('Hourly, Daily');
  });

  it('keeps a real required select for form validation, in step with the model', async () => {
    const value = ref('');
    const w = render(
      `<AppSelect v-model="value" required><option value="">Choose</option><option value="k">Kale</option></AppSelect>`,
      () => ({ value }),
    );
    await nextTick();
    const native = w.find('select').element;
    expect(native.required).toBe(true);
    expect(native.checkValidity()).toBe(false);
    value.value = 'k';
    await nextTick();
    await nextTick();
    expect(native.value).toBe('k');
    expect(native.checkValidity()).toBe(true);
  });

  it('works with a plain :value + @change binding and shows the empty option when unset', async () => {
    const filters = ref({ categoryId: null });
    const w = render(
      `<AppSelect :value="String(filters.categoryId ?? '')" @change="filters.categoryId = $event.target.value || null">
         <option value="">All categories</option><option value="7">Livestock</option>
       </AppSelect>`,
      () => ({ filters }),
    );
    await nextTick();
    const btn = w.find('button[role="combobox"]');
    expect(btn.text()).toBe('All categories');
    await btn.trigger('click');
    optionEls()[1].click();
    await nextTick();
    expect(filters.value.categoryId).toBe('7');
    expect(btn.text()).toBe('Livestock');
  });
});
