import { providerSubscriptionApi } from '../services/marketplaceApi';

export function isSubscriptionActive(subStatus, auth) {
  if (subStatus != null) return !!subStatus.valid;
  return !!auth?.providerSubValid;
}

export function isPlanPremium(subStatus, auth) {
  const plan = String(subStatus?.plan || auth?.providerPlan || '').toUpperCase();
  return plan === 'PREMIUM';
}

/** Load subscription status for provider users and sync the auth store. */
export async function refreshProviderSubscription(auth) {
  if (!auth?.isProviderUser) return null;
  try {
    const { data } = await providerSubscriptionApi.status();
    auth.setProviderSubscriptionStatus(data);
    return data;
  } catch {
    if (!auth.providerSubValid) {
      auth.setProviderSubscriptionStatus({ valid: false, plan: null });
    }
    return null;
  }
}

/**
 * Inactive subscription → subscription page only.
 * Active subscription → routes allowed per plan (Premium for team/payroll).
 * @returns {null|{ path: string, query?: object }}
 */
export function providerRouteGuard(to, auth, subStatus) {
  if (!auth.isAuthenticated || !auth.isProviderUser) return null;
  if (typeof to.path !== 'string' || !to.path.startsWith('/provider')) return null;

  const active = isSubscriptionActive(subStatus, auth);
  const onSubscriptionPage = to.name === 'provider-subscription';

  if (!active) {
    return onSubscriptionPage
      ? null
      : { path: '/provider/subscription', query: { redirect: to.fullPath } };
  }

  if (onSubscriptionPage) return null;

  if (to.meta.requiresPremium && !isPlanPremium(subStatus, auth)) {
    return { path: '/provider/subscription', query: { redirect: to.fullPath, upgrade: 'premium' } };
  }

  return null;
}
