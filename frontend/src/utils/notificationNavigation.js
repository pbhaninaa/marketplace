export function resolveNotificationFallback(notification, auth) {
  const type = String(notification?.notificationType || '').trim().toUpperCase();
  if (!type) return '';

  if (auth?.isPlatformAdmin) {
    if (type === 'SUBSCRIPTION_PROOF_PENDING') return '/admin/manual-verifications';
    if (type === 'SUBSCRIPTION_REMINDER') return '/admin/providers';
    return '/admin';
  }

  if (auth?.isSupport) {
    if (type === 'SUBSCRIPTION_PROOF_PENDING') return '/support';
    return '/support';
  }

  if (auth?.isProviderUser) {
    if (type === 'SUBSCRIPTION_PROOF_DECISION' || type === 'SUBSCRIPTION_REMINDER') return '/provider/subscription';
    return '/provider/orders';
  }

  if (auth?.isClientUser) {
    if (type === 'SUBSCRIPTION_REMINDER') return '/';
    return '/marketplace';
  }

  return '';
}
