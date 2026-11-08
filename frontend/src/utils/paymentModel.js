export const PEACH_PAYMENT_METHODS = Object.freeze([
  { value: 'CARD', label: 'Card' },
  { value: 'EFT', label: 'Instant EFT' },
]);

/** Normalize provider accepted methods for settings UI (Cash, Manual EFT, Peach). */
export function normalizeAcceptedPaymentMethods(methods) {
  const out = new Set();
  for (const method of Array.isArray(methods) ? methods : []) {
    if (method === 'CASH' || method === 'EFT' || method === 'PEACH') out.add(method);
    if (method === 'BOTH') {
      out.add('CASH');
      out.add('EFT');
    }
  }
  if (!out.size) out.add('CASH');
  return [...out];
}

/** The single online option buyers and providers see (method PEACH, served by PayFast or Peach). */
export const ONLINE_PAYMENT_LABEL = 'Card / Instant EFT';

/** Name of the secure payment page for the active gateway. */
export function gatewayPageName(gateway) {
  if (gateway === 'PAYFAST') return 'PayFast';
  if (gateway === 'PEACH') return 'Peach Payments';
  return 'payment';
}

export function peachPaymentPayload(paymentMethod, peachPaymentMethod) {
  return paymentMethod === 'PEACH' ? { peachPaymentMethod } : {};
}
