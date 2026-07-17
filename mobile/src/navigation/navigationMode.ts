/** Single source of truth: which mobile shell nav is shown (never both). */

export type MobileNavMode = 'bottom' | 'hamburger' | 'none';

const AUTH_PATHS = new Set([
  '/login',
  '/register',
  '/forgot-password',
  '/reset-password',
  '/setup',
]);

export function normalizeNavRole(role: string | null | undefined): string {
  return (role || '').toUpperCase().trim();
}

export function isShopperNavRole(role: string | null | undefined): boolean {
  const r = normalizeNavRole(role);
  return !r || r === 'CLIENT';
}

export function isHamburgerNavRole(role: string | null | undefined): boolean {
  const r = normalizeNavRole(role);
  return (
    r === 'PLATFORM_ADMIN' ||
    r === 'SUPPORT' ||
    r === 'PROVIDER_OWNER' ||
    r === 'PROVIDER_ADMIN' ||
    r === 'PROVIDER_STAFF' ||
    r === 'PROVIDER_VIEWER'
  );
}

/**
 * bottom    → native bottom tabs (guests + CLIENT shoppers)
 * hamburger → web top bar + side-nav (provider / admin / support)
 * none      → auth screens
 */
export function resolveMobileNavMode(
  role: string | null | undefined,
  loggedIn: boolean,
  path?: string,
): MobileNavMode {
  const p = (path || '/').split('?')[0] || '/';
  if (AUTH_PATHS.has(p)) return 'none';

  if (isHamburgerNavRole(role)) return 'hamburger';
  if (isShopperNavRole(role)) return 'bottom';

  return loggedIn ? 'none' : 'bottom';
}

export function shouldShowBottomNav(
  path: string,
  loggedIn: boolean,
  role: string | null | undefined,
): boolean {
  return resolveMobileNavMode(role, loggedIn, path) === 'bottom';
}
