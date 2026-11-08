import { beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { MANUALS, canRead, findManual, manualForRole, slugify } from '../src/manuals';

const route = { params: { slug: 'buyers' }, query: {}, hash: '', fullPath: '/manuals/buyers' };
vi.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
}));

const stubs = { 'router-link': { props: ['to'], template: '<a :data-to="JSON.stringify(to)"><slot /></a>' } };

describe('manual registry', () => {
  it('has a public buyer and provider guide and role-only admin and support guides', () => {
    expect(MANUALS.map((m) => [m.slug, m.access])).toEqual([
      ['buyers', 'public'],
      ['providers', 'public'],
      ['admin', 'admin'],
      ['support', 'support'],
    ]);
  });

  it('only lets the right roles read admin and support guides', () => {
    const admin = findManual('admin');
    const support = findManual('support');
    expect(canRead(admin, {})).toBe(false);
    expect(canRead(admin, { isSupport: true })).toBe(false);
    expect(canRead(admin, { isPlatformAdmin: true })).toBe(true);
    expect(canRead(support, { isSupport: true })).toBe(true);
    expect(canRead(support, { isPlatformAdmin: true })).toBe(true);
    expect(canRead(support, { isProviderUser: true })).toBe(false);
    expect(canRead(findManual('providers'), {})).toBe(true);
  });

  it('suggests the manual for the signed-in role', () => {
    expect(manualForRole({})).toBe('buyers');
    expect(manualForRole({ isProviderUser: true })).toBe('providers');
    expect(manualForRole({ isSupport: true })).toBe('support');
    expect(manualForRole({ isPlatformAdmin: true, isSupport: true })).toBe('admin');
  });

  it('makes clean heading ids', () => {
    expect(slugify('Your free trial and plan')).toBe('your-free-trial-and-plan');
    expect(slugify('Orders & payments')).toBe('orders-payments');
  });

  it('every manual file loads and has a title and sections', async () => {
    for (const m of MANUALS) {
      const text = (await m.load()).default;
      expect(text.startsWith('# ')).toBe(true);
      expect((text.match(/^## /gm) || []).length).toBeGreaterThan(3);
    }
  });
});

describe('ManualView', { timeout: 20000 }, () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it('renders the buyer guide with a contents list, heading ids and scrollable tables', async () => {
    route.params.slug = 'buyers';
    const { default: ManualView } = await import('../src/views/ManualView.vue');
    const w = mount(ManualView, { global: { stubs } });
    // The Markdown file is loaded asynchronously.
    await vi.waitFor(() => expect(w.findAll('.manual-toc a').length).toBeGreaterThan(0), { timeout: 5000 });
    const tocLinks = w.findAll('.manual-toc a').map((a) => a.text());
    expect(tocLinks).toContain('Checking out');
    expect(tocLinks).toContain('Your verification code');
    expect(w.find('#checking-out').exists()).toBe(true);
    expect(w.find('.manual-content h1').exists()).toBe(false); // title is in the page header
    expect(w.find('.manual-content .manual-table table').exists()).toBe(true);
    expect(w.find('.page-hero__title').text()).toBe('Buyer guide');
  });

  it('does not show the admin guide to someone who is not an administrator', async () => {
    route.params.slug = 'admin';
    const { default: ManualView } = await import('../src/views/ManualView.vue');
    const w = mount(ManualView, { global: { stubs } });
    await flushPromises();
    expect(w.find('.manual-content').exists()).toBe(false);
    expect(w.text()).toContain('This manual is for platform administrators');
    expect(w.text()).toContain('Sign in');
  });
});
