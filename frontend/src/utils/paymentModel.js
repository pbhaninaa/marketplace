export const PEACH_PAYMENT_METHODS = Object.freeze([
  { value: 'CARD', label: 'Card' },
  { value: 'EFT', label: 'Instant EFT' },
]);

/** Convert historical provider methods into the current Cash / Peach settings model. */
export function normalizeAcceptedPaymentMethods(methods) {
  const out = new Set();
  for (const method of Array.isArray(methods) ? methods : []) {
    if (method === 'CASH' || method === 'PEACH') out.add(method);
    if (method === 'EFT') out.add('PEACH');
    if (method === 'BOTH') {
      out.add('CASH');
      out.add('PEACH');
    }
  }
  if (!out.size) out.add('CASH');
  return [...out];
}

export function peachPaymentPayload(paymentMethod, peachPaymentMethod) {
  return paymentMethod === 'PEACH' ? { peachPaymentMethod } : {};
}
