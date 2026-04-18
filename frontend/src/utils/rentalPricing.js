/**
 * Rental window uses half-open [start, end) instants in local time:
 * start = first rental day at 00:00, end = first instant after the last rental day (00:00).
 */

export function inclusiveDatesToRentalInstants(startDateStr, endDateStr) {
  if (!startDateStr || !endDateStr) return { rentalStart: null, rentalEnd: null };
  const [ys, ms, ds] = startDateStr.split('-').map(Number);
  const [ye, me, de] = endDateStr.split('-').map(Number);
  const start = new Date(ys, ms - 1, ds, 0, 0, 0, 0);
  const endInclusive = new Date(ye, me - 1, de, 0, 0, 0, 0);
  if (endInclusive < start) return { rentalStart: null, rentalEnd: null };
  const endExclusive = new Date(endInclusive);
  endExclusive.setDate(endExclusive.getDate() + 1);
  return {
    rentalStart: start.toISOString(),
    rentalEnd: endExclusive.toISOString(),
  };
}

/** Inclusive calendar days from first rental day through last rental day (same day = 1). */
export function countInclusiveRentalDays(startDateStr, endDateStr) {
  if (!startDateStr || !endDateStr) return 0;
  const [ys, ms, ds] = startDateStr.split('-').map(Number);
  const [ye, me, de] = endDateStr.split('-').map(Number);
  const a = new Date(ys, ms - 1, ds, 0, 0, 0, 0);
  const b = new Date(ye, me - 1, de, 0, 0, 0, 0);
  if (b < a) return 0;
  return Math.round((b - a) / 86400000) + 1;
}

/** Mirrors backend RentalPricingService (weekly if days >= 6 when weekly rate exists). */
export function estimateRentalAmount(listing, inclusiveDays) {
  if (!listing || listing.listingType !== 'RENT' || inclusiveDays < 1) return null;
  const daily = listing.rentPriceDaily != null ? Number(listing.rentPriceDaily) : null;
  const weekly = listing.rentPriceWeekly != null ? Number(listing.rentPriceWeekly) : null;
  const hourly = listing.rentPriceHourly != null ? Number(listing.rentPriceHourly) : null;
  const unit = Number(listing.unitPrice) || 0;
  if (weekly != null && inclusiveDays >= 6) {
    const weeks = Math.max(1, Math.ceil(inclusiveDays / 7));
    return weekly * weeks;
  }
  if (daily != null) {
    return daily * inclusiveDays;
  }
  if (hourly != null) {
    return hourly * inclusiveDays * 24;
  }
  return unit * inclusiveDays;
}

/** For cart lines where rentalEnd is exclusive (API). */
export function formatRentalInclusiveRange(startIso, endIso) {
  if (!startIso || !endIso) return '';
  const start = new Date(startIso);
  const endEx = new Date(endIso);
  const endIncl = new Date(endEx);
  endIncl.setDate(endIncl.getDate() - 1);
  const opts = { year: 'numeric', month: 'short', day: 'numeric' };
  return `${start.toLocaleDateString(undefined, opts)} – ${endIncl.toLocaleDateString(undefined, opts)}`;
}
