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

export function peachPaymentPayload(paymentMethod, peachPaymentMethod) {
  return paymentMethod === 'PEACH' ? { peachPaymentMethod } : {};
}
