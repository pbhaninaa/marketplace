/** Seller payout (settlement) helpers shared by the admin payouts page and the provider view. */

export const SETTLEMENT_LABELS = Object.freeze({
  NONE: 'Not collected by the platform',
  HELD: 'Awaiting payout',
  PAID_OUT: 'Paid out',
  REFUND_DUE: 'Refund due to buyer',
  REFUNDED: 'Refunded to buyer',
});

export function settlementLabel(status) {
  return SETTLEMENT_LABELS[status] || status || '';
}

/** Badge colour class for a settlement status. */
export function settlementTone(status) {
  if (status === 'HELD') return 'warn';
  if (status === 'PAID_OUT' || status === 'REFUNDED') return 'ok';
  if (status === 'REFUND_DUE') return 'danger';
  return 'muted';
}

/** Sum of a money field over the rows whose paymentId is selected (cents-safe). */
export function sumSelected(rows, selectedIds, field = 'sellerPayoutAmount') {
  const ids = new Set(selectedIds || []);
  const cents = (rows || [])
    .filter((r) => ids.has(r.paymentId))
    .reduce((acc, r) => acc + Math.round(Number(r[field] || 0) * 100), 0);
  return cents / 100;
}

/** Rand amount with two decimals and thousands spaces, e.g. "R 1 234.50". */
export function formatRand(value) {
  const n = Number(value);
  if (!Number.isFinite(n)) return 'R 0.00';
  const [whole, dec] = Math.abs(n).toFixed(2).split('.');
  const grouped = whole.replace(/\B(?=(\d{3})+(?!\d))/g, ' ');
  return `${n < 0 ? '-' : ''}R ${grouped}.${dec}`;
}

/** Trimmed payout reference, or an error message when it cannot be used. */
export function payoutReferenceError(reference) {
  const ref = String(reference ?? '').trim();
  if (!ref) return 'Enter the bank transfer reference.';
  if (ref.length > 120) return 'Keep the reference to 120 characters or fewer.';
  return '';
}
