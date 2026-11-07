import { activeTabId, tabsForRole } from '../bottomNavConfig';

describe('bottomNavConfig', () => {
  const tabs = tabsForRole('CLIENT');

  it('includes a Profile tab between Orders and Settings', () => {
    const ids = tabs.map((t) => t.id);
    expect(ids).toEqual(['shop', 'cart', 'orders', 'profile', 'settings']);
  });

  it.each([
    ['/', 'shop'],
    ['/checkout', 'cart'],
    ['/order-invoice?ref=1', 'orders'],
    ['/profile', 'profile'],
    ['/login?redirect=/profile', 'profile'],
    ['/client/verify', 'profile'],
    ['/settings', 'settings'],
    ['/help/client', 'settings'],
  ])('highlights the right tab for %s', (path, expected) => {
    expect(activeTabId(tabs, path)).toBe(expected);
  });
});
