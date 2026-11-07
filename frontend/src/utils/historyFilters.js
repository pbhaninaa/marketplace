/**
 * Filter state <-> query params for the order & payment history pages.
 * Dates are yyyy-MM-dd and inclusive on the server.
 */

export const ORDER_STATUS_OPTIONS = [
  { value: 'PENDING_PAYMENT', label: 'Pending payment' },
  { value: 'PAID', label: 'Paid' },
  { value: 'COLLECTED', label: 'Collected' },
  { value: 'CANCELLED', label: 'Cancelled' },
];

export const PAYMENT_STATUS_OPTIONS = [
  { value: 'PENDING', label: 'Pending' },
  { value: 'PAID', label: 'Paid' },
];

export const PAYMENT_METHOD_OPTIONS = [
  { value: 'CASH', label: 'Cash' },
  { value: 'EFT', label: 'EFT (bank transfer)' },
  { value: 'PEACH', label: 'Online (Peach)' },
];

export const PAYMENT_KIND_OPTIONS = [
  { value: 'ORDER', label: 'Purchases' },
  { value: 'RENTAL', label: 'Rentals' },
];

export const DATE_PRESETS = [
  { value: 'ALL', label: 'All time' },
  { value: 'TODAY', label: 'Today' },
  { value: 'LAST_7', label: 'Last 7 days' },
  { value: 'LAST_30', label: 'Last 30 days' },
  { value: 'THIS_MONTH', label: 'This month' },
  { value: 'CUSTOM', label: 'Custom range' },
];

export function emptyHistoryFilters() {
  return {
    status: '',
    paymentStatus: '',
    method: '',
    providerId: '',
    kind: '',
    q: '',
    preset: 'ALL',
    from: '',
    to: '',
  };
}

export function toDateInputValue(d) {
  const yyyy = d.getFullYear();
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  return `${yyyy}-${mm}-${dd}`;
}

/** Resolves a date preset to { from, to } (or {} for all time / incomplete custom ranges). */
export function resolveDateRange(preset, from, to, now = new Date()) {
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const daysBack = (n) => {
    const d = new Date(today);
    d.setDate(d.getDate() - n);
    return d;
  };
  switch (preset) {
    case 'TODAY':
      return { from: toDateInputValue(today), to: toDateInputValue(today) };
    case 'LAST_7':
      return { from: toDateInputValue(daysBack(6)), to: toDateInputValue(today) };
    case 'LAST_30':
      return { from: toDateInputValue(daysBack(29)), to: toDateInputValue(today) };
    case 'THIS_MONTH':
      return {
        from: toDateInputValue(new Date(today.getFullYear(), today.getMonth(), 1)),
        to: toDateInputValue(today),
      };
    case 'CUSTOM': {
      const out = {};
      if (from) out.from = from;
      if (to) out.to = to;
      if (out.from && out.to && out.from > out.to) return { from: out.to, to: out.from };
      return out;
    }
    default:
      return {};
  }
}

/**
 * Builds API query params for a tab ("orders" | "payments"), dropping empty values and filters that do
 * not apply to that tab or audience.
 */
export function buildHistoryParams(filters, { tab = 'orders', allowProvider = false, now } = {}) {
  const f = { ...emptyHistoryFilters(), ...(filters || {}) };
  const params = {};
  if (tab === 'orders' && f.status) params.status = f.status;
  if (f.paymentStatus) params.paymentStatus = f.paymentStatus;
  if (f.method) params.method = f.method;
  if (tab === 'payments' && f.kind) params.kind = f.kind;
  if (allowProvider && f.providerId) params.providerId = f.providerId;
  const q = String(f.q || '').trim();
  if (q) params.q = q;
  Object.assign(params, resolveDateRange(f.preset, f.from, f.to, now));
  return params;
}

export function countActiveFilters(filters, opts = {}) {
  return Object.keys(buildHistoryParams(filters, opts)).length;
}

export function formatMoney(amount) {
  const n = Number(amount ?? 0);
  if (!Number.isFinite(n)) return 'Not set';
  return `R ${n.toLocaleString('en-ZA', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

export function labelFor(options, value) {
  if (!value) return 'Not set';
  return options.find((o) => o.value === value)?.label || String(value).replace(/_/g, ' ').toLowerCase();
}
