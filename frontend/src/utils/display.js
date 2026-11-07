/**
 * Display helpers: render real values, or a short human phrase when a value is missing.
 * Never fall back to a dash ("-", "–", "—") on screen.
 */

const isBlank = (v) => v == null || (typeof v === 'string' && v.trim() === '');

/** The value as text, or a readable fallback like "Not set". */
export function orText(value, fallback = 'Not set') {
  return isBlank(value) ? fallback : String(value);
}

const rand = (n) => {
  const [whole, cents] = Math.abs(n).toFixed(2).split('.');
  const grouped = whole.replace(/\B(?=(\d{3})+(?!\d))/g, ' ');
  return `${n < 0 ? 'minus ' : ''}R ${grouped}.${cents}`;
};

/** Rand in the app's usual style, e.g. "R 1 250.00". Missing values show R 0.00 unless a fallback is given. */
export function formatMoney(value, fallback) {
  const n = isBlank(value) ? NaN : Number(value);
  if (!Number.isFinite(n)) return fallback ?? rand(0);
  return rand(n);
}

/** Whole numbers with thousands separators; missing values use the fallback. */
export function formatCount(value, fallback = '0') {
  if (isBlank(value)) return fallback;
  const n = Number(value);
  return Number.isFinite(n) ? n.toLocaleString('en-ZA') : fallback;
}

function toDate(value) {
  if (isBlank(value)) return null;
  // Plain "YYYY-MM-DD" is a calendar date: build it locally so it doesn't shift a day by timezone.
  const m = typeof value === 'string' && /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  const d = m ? new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3])) : new Date(value);
  return Number.isNaN(d.getTime()) ? null : d;
}

/** e.g. "7 Nov 2026". */
export function formatDate(value, fallback = 'Not set') {
  const d = toDate(value);
  return d
    ? d.toLocaleDateString('en-ZA', { day: 'numeric', month: 'short', year: 'numeric' })
    : fallback;
}

/** e.g. "7 Nov 2026, 14:05". */
export function formatDateTime(value, fallback = 'Not set') {
  const d = toDate(value);
  return d
    ? d.toLocaleString('en-ZA', {
        day: 'numeric',
        month: 'short',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      })
    : fallback;
}

/** e.g. "8 Oct 2026 to 7 Nov 2026", or a single date when both ends match. */
export function formatDateRange(from, to, fallback = 'Not set') {
  const a = toDate(from);
  const b = toDate(to);
  if (!a && !b) return fallback;
  if (a && b && a.getTime() === b.getTime()) return formatDate(a);
  if (a && b) return `${formatDate(a)} to ${formatDate(b)}`;
  return a ? `From ${formatDate(a)}` : `Until ${formatDate(b)}`;
}

/** "PREMIUM" / "MONTHLY" style enums as "Premium" / "Monthly". */
export function formatEnum(value, fallback = 'Not set') {
  if (isBlank(value)) return fallback;
  return String(value)
    .toLowerCase()
    .split('_')
    .map((w) => (w ? w[0].toUpperCase() + w.slice(1) : w))
    .join(' ');
}

/** "3 images" / "1 image" / "No images" from the comma/newline separated imageUrls string. */
export function imageCountLabel(raw) {
  const n = String(raw || '')
    .split(/[\n,]+/)
    .map((s) => s.trim())
    .filter(Boolean).length;
  if (n === 0) return 'No images';
  return `${n} image${n === 1 ? '' : 's'}`;
}
