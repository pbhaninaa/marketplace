/**
 * User manuals, readable in the web app at /manuals/:slug.
 * Edit the Markdown files in this folder to change a manual; the app picks them up on the next build.
 *
 * access: 'public'  - anyone, signed in or not (buyers, and providers before they sign up)
 *         'admin'   - platform administrators
 *         'support' - support staff and platform administrators
 */
export const MANUALS = [
  {
    slug: 'buyers',
    title: 'Buyer guide',
    audience: 'Buyers and renters',
    description: 'Finding items, buying from several sellers, checkout, payment, verification codes and your orders.',
    icon: 'shopping_basket',
    access: 'public',
    load: () => import('./buyer.md?raw'),
  },
  {
    slug: 'providers',
    title: 'Provider guide',
    audience: 'Sellers, owners and their staff',
    description: 'Signing up, your free trial and plan, listings, orders, payments, your team, settings and referrals.',
    icon: 'storefront',
    access: 'public',
    load: () => import('./provider.md?raw'),
  },
  {
    slug: 'admin',
    title: 'Administrator guide',
    audience: 'Platform administrators',
    description: 'Providers and plan overrides, listings, users, support staff, referrals, reports and settings.',
    icon: 'admin_panel_settings',
    access: 'admin',
    load: () => import('./admin.md?raw'),
  },
  {
    slug: 'support',
    title: 'Support guide',
    audience: 'Support team',
    description: 'Finding users, seeing what a provider sees, tickets, sign-in codes and invoices.',
    icon: 'support_agent',
    access: 'support',
    load: () => import('./support.md?raw'),
  },
];

export function findManual(slug) {
  return MANUALS.find((m) => m.slug === slug) || null;
}

/** Whether the signed-in user (auth store) may read a manual. */
export function canRead(manual, auth) {
  if (!manual) return false;
  if (manual.access === 'public') return true;
  if (manual.access === 'admin') return !!auth?.isPlatformAdmin;
  if (manual.access === 'support') return !!(auth?.isSupport || auth?.isPlatformAdmin);
  return false;
}

/** The manual a user most likely wants, based on their role. */
export function manualForRole(auth) {
  if (auth?.isPlatformAdmin) return 'admin';
  if (auth?.isSupport) return 'support';
  if (auth?.isProviderUser) return 'providers';
  return 'buyers';
}

/** URL-safe id for a heading, e.g. "Your free trial and plan" -> "your-free-trial-and-plan". */
export function slugify(text) {
  return String(text)
    .toLowerCase()
    .replace(/<[^>]+>/g, '')
    .replace(/&[a-z]+;/g, '')
    .replace(/[^a-z0-9\s-]/g, '')
    .trim()
    .replace(/\s+/g, '-')
    .replace(/-+/g, '-');
}
