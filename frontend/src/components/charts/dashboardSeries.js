/**
 * Turns a /dashboard/trends response into chart props. Colour slots are fixed per entity and assigned
 * in display order, so adjacent parts are always validated neighbours and a filter never repaints them.
 */

const num = (v) => Number(v) || 0;

/** Slot per series/part key: identity, never rank. */
export const SLOTS = {
  sales: 0,
  rentals: 1,
  users: 0,
  providers: 1,
  PENDING_PAYMENT: 0,
  PAID: 1,
  COLLECTED: 2,
  CANCELLED: 3,
  TRIAL: 0,
  BASIC: 1,
  PREMIUM: 2,
  LAPSED: 3,
  INACTIVE: 4,
};

function buckets(trends) {
  return Array.isArray(trends?.buckets) ? trends.buckets : [];
}

export function labelsOf(trends) {
  return buckets(trends).map((b) => b.start);
}

export function revenueSeries(trends) {
  const b = buckets(trends);
  return [
    { key: 'sales', label: 'Sales orders', slot: SLOTS.sales, values: b.map((x) => num(x.ordersRevenue)) },
    { key: 'rentals', label: 'Rentals', slot: SLOTS.rentals, values: b.map((x) => num(x.rentalsRevenue)) },
  ];
}

export function volumeSeries(trends) {
  const b = buckets(trends);
  return [
    { key: 'sales', label: 'Sales orders', slot: SLOTS.sales, values: b.map((x) => num(x.orders)) },
    { key: 'rentals', label: 'Rental bookings', slot: SLOTS.rentals, values: b.map((x) => num(x.rentals)) },
  ];
}

/**
 * Running total of sign-ups within the period. Daily sign-up counts are small and spiky (0, 3, 0, 5),
 * which reads as noise; a running total shows the growth the admin is actually asking about.
 */
export function signupSeries(trends) {
  const b = buckets(trends);
  const running = (pick) => {
    let total = 0;
    return b.map((x) => (total += num(pick(x))));
  };
  return [
    { key: 'users', label: 'Users', slot: SLOTS.users, values: running((x) => x.newUsers) },
    { key: 'providers', label: 'Providers', slot: SLOTS.providers, values: running((x) => x.newProviders) },
  ];
}

function parts(list) {
  return (Array.isArray(list) ? list : []).map((p) => ({
    key: p.key,
    label: p.label,
    value: num(p.count),
    slot: SLOTS[p.key] ?? 0,
  }));
}

export function orderStatusParts(trends) {
  return parts(trends?.orderStatus);
}

export function providerStateParts(trends) {
  return parts(trends?.providerStates);
}

export function paymentMethodItems(trends) {
  return (trends?.paymentMethods || []).map((p) => ({
    key: p.key,
    label: p.label,
    value: num(p.amount),
    detail: `${num(p.count)} payment${num(p.count) === 1 ? '' : 's'}`,
  }));
}

/** Admin: providers by revenue (money). Provider: listings by units sold + bookings (count). */
export function topItems(trends, { money = false } = {}) {
  return (trends?.topItems || []).map((p) => ({
    key: p.key,
    label: p.label || 'Unnamed',
    value: money ? num(p.amount) : num(p.count),
  }));
}

/** "per day" / "per week" for chart subtitles. */
export function perBucket(trends) {
  return trends?.granularity === 'week' ? 'per week' : 'per day';
}
