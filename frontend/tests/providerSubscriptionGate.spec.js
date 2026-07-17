import { describe, expect, it } from 'vitest';
import {
  isOnFreeTrial,
  isPlanPremium,
  isSubscriptionActive,
  providerRouteGuard,
  trialDaysRemaining,
} from '../src/utils/providerSubscriptionGate';

describe('provider subscription gate with free trial', () => {
  const auth = {
    isAuthenticated: true,
    isProviderUser: true,
    providerSubValid: false,
    providerOnTrial: false,
    providerTrialDaysRemaining: 0,
    providerPlan: '',
  };

  it('treats on-trial status as active entitlement', () => {
    const status = { valid: true, onTrial: true, trialDaysRemaining: 12, plan: null };
    expect(isSubscriptionActive(status, auth)).toBe(true);
    expect(isOnFreeTrial(status, auth)).toBe(true);
    expect(trialDaysRemaining(status, auth)).toBe(12);
    expect(isPlanPremium(status, auth)).toBe(false);
  });

  it('allows provider routes during free trial', () => {
    const status = { valid: true, onTrial: true, trialDaysRemaining: 5, plan: null };
    expect(providerRouteGuard({ path: '/provider', name: 'provider-home', meta: {}, fullPath: '/provider' }, auth, status))
      .toBeNull();
  });

  it('still requires premium plan for premium routes during trial', () => {
    const status = { valid: true, onTrial: true, trialDaysRemaining: 5, plan: null };
    expect(
      providerRouteGuard(
        { path: '/provider/team', name: 'provider-team', meta: { requiresPremium: true }, fullPath: '/provider/team' },
        auth,
        status,
      ),
    ).toEqual({ path: '/provider/subscription', query: { redirect: '/provider/team', upgrade: 'premium' } });
  });

  it('blocks non-subscription routes when trial and paid are inactive', () => {
    const status = { valid: false, onTrial: false, trialDaysRemaining: 0, plan: null };
    expect(
      providerRouteGuard(
        { path: '/provider', name: 'provider-home', meta: {}, fullPath: '/provider' },
        auth,
        status,
      ),
    ).toEqual({ path: '/provider/subscription', query: { redirect: '/provider' } });
  });
});
