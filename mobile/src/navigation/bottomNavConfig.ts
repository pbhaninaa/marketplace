import { shouldShowBottomNav } from './navigationMode';

export type BottomNavTab = {
  id: string;
  label: string;
  icon: string;
  path: string;
  matchPaths?: string[];
  badgeKey?: string;
};

/** Mirrors guest/client links in frontend/src/App.vue side-nav. */
const SHOPPER_TABS: BottomNavTab[] = [
  { id: 'shop', label: 'Shop', icon: 'storefront-outline', path: '/' },
  {
    id: 'cart',
    label: 'Cart',
    icon: 'cart-outline',
    path: '/checkout',
    matchPaths: ['/checkout', '/peach/return'],
  },
  {
    id: 'orders',
    label: 'Orders',
    icon: 'receipt-text-outline',
    path: '/order-invoice',
    matchPaths: ['/order-invoice'],
  },
  {
    id: 'settings',
    label: 'Settings',
    icon: 'cog-outline',
    path: '/settings',
    matchPaths: ['/settings', '/login', '/register', '/client', '/forgot-password', '/reset-password', '/help/client'],
  },
];

export function tabsForRole(_role?: string | null): BottomNavTab[] {
  return SHOPPER_TABS;
}

export function tabMatchesPath(tab: BottomNavTab, path: string): boolean {
  const p = path.split('?')[0] || '/';
  if (tab.path === '/' ) return p === '/';
  if (p === tab.path || p.startsWith(`${tab.path}/`)) return true;
  return (tab.matchPaths ?? []).some((m) => p === m || p.startsWith(`${m}/`));
}

export function activeTabId(tabs: BottomNavTab[], path: string): string {
  const match = tabs.find((t) => tabMatchesPath(t, path));
  return match?.id ?? tabs[0]?.id ?? 'shop';
}

export { shouldShowBottomNav };
