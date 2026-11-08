/** Shared helpers for the dashboard charts (pure functions, unit-tested). */

/** Fixed categorical slot order; colour follows the entity's slot, never its rank. */
export const SERIES_VARS = [
  'var(--viz-series-1)',
  'var(--viz-series-2)',
  'var(--viz-series-3)',
  'var(--viz-series-4)',
  'var(--viz-series-5)',
];

/** Round an axis maximum up to a clean step so ticks read 0 / 500 / 1,000 rather than 0 / 437 / 874. */
export function niceScale(maxValue, tickCount = 4) {
  const max = Number(maxValue) || 0;
  if (max <= 0) return { max: tickCount, step: 1, ticks: range(tickCount + 1, 1) };
  // Smallest clean step (1, 2, 2.5, 5 × 10^n) that covers the max in at most tickCount + 1 intervals,
  // so the axis hugs the data (437 -> 0..500, not 0..600).
  const mag = 10 ** Math.floor(Math.log10(max / tickCount));
  let step = 10 * mag;
  for (const nice of [1, 2, 2.5, 5, 10]) {
    const candidate = nice * mag;
    // Whole-number data (counts) never gets fractional ticks like 2.5.
    if (Number.isInteger(max) && !Number.isInteger(candidate)) continue;
    if (Math.ceil(max / candidate) <= tickCount + 1) {
      step = candidate;
      break;
    }
  }
  // Counts are whole numbers: never a 0.5 tick.
  if (step < 1 && Number.isInteger(max)) step = 1;
  const top = Math.ceil(max / step) * step;
  const ticks = [];
  for (let v = 0; v <= top + step / 2; v += step) ticks.push(round(v));
  return { max: top, step, ticks };
}

function range(n, step) {
  return Array.from({ length: n }, (_, i) => i * step);
}

function round(v) {
  return Math.round(v * 1e6) / 1e6;
}

/** Axis ticks: full under 10,000, compact above (10K, 1.5M). One style per axis. */
export function formatTick(value, axisMax, { money = false } = {}) {
  const v = Number(value) || 0;
  const prefix = money ? 'R' : '';
  if (axisMax >= 10000) {
    if (Math.abs(v) >= 1e6) return `${prefix}${trim(v / 1e6)}M`;
    if (Math.abs(v) >= 1e3) return `${prefix}${trim(v / 1e3)}K`;
  }
  return `${prefix}${v.toLocaleString('en-ZA', { maximumFractionDigits: 0 })}`;
}

function trim(n) {
  return Number(n.toFixed(1)).toString();
}

/** Value text for tooltips, tables and labels. */
export function formatValue(value, { money = false } = {}) {
  const v = Number(value) || 0;
  if (money) {
    return `R ${v.toLocaleString('en-ZA', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
  }
  return v.toLocaleString('en-ZA', { maximumFractionDigits: 0 });
}

/** Compact value for labels printed on marks (R12.9K, 1,284). */
export function formatCompact(value, { money = false } = {}) {
  const v = Number(value) || 0;
  const prefix = money ? 'R' : '';
  if (Math.abs(v) >= 1e6) return `${prefix}${trim(v / 1e6)}M`;
  if (Math.abs(v) >= 1e4) return `${prefix}${trim(v / 1e3)}K`;
  return `${prefix}${v.toLocaleString('en-ZA', { maximumFractionDigits: money ? 0 : 0 })}`;
}

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

/** "3 Mar" for days; "Week of 3 Mar" in tooltips for weekly buckets. */
export function formatBucketLabel(isoDate, granularity = 'day', { long = false } = {}) {
  if (!isoDate) return '';
  const [y, m, d] = String(isoDate).split('-').map(Number);
  const short = `${d} ${MONTHS[(m || 1) - 1]}`;
  if (!long) return short;
  return granularity === 'week' ? `Week of ${short} ${y}` : `${short} ${y}`;
}

/** Pick roughly `want` evenly spaced indices (always including the first and last). */
export function tickIndices(count, want = 6) {
  if (count <= 0) return [];
  if (count <= want) return range(count, 1);
  const step = (count - 1) / (want - 1);
  const out = new Set();
  for (let i = 0; i < want; i++) out.add(Math.round(i * step));
  return [...out].sort((a, b) => a - b);
}

/** Share of a whole as a whole-number percent string; tiny non-zero parts show "<1%". */
export function formatShare(part, total) {
  if (!total) return '0%';
  const pct = (part / total) * 100;
  if (pct > 0 && pct < 1) return '<1%';
  return `${Math.round(pct)}%`;
}
