/** Turns a DateRangeFilter value ({ preset, from, to }) into API { from, to } (YYYY-MM-DD), or null. */

export function toDateInputValue(d) {
  const yyyy = d.getFullYear();
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  return `${yyyy}-${mm}-${dd}`;
}

export function dateRangeQuery(range, now = new Date()) {
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const back = (days) => {
    const from = new Date(today);
    from.setDate(from.getDate() - days);
    return { from: toDateInputValue(from), to: toDateInputValue(today) };
  };
  switch (range?.preset) {
    case 'TODAY':
      return back(0);
    case 'LAST_7':
      return back(6);
    case 'LAST_30':
      return back(29);
    case 'LAST_90':
      return back(89);
    default:
      if (!range?.from || !range?.to) return null;
      return { from: range.from, to: range.to };
  }
}
