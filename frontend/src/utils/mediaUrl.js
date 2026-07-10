function apiBase() {
  return String(import.meta.env.VITE_API_BASE || '').replace(/\/$/, '');
}

/** Turn stored listing image paths into browser-loadable URLs on any host. */
export function resolveMediaUrl(url) {
  if (url == null) return '';
  const trimmed = String(url).trim();
  if (!trimmed) return '';
  if (/^https?:\/\//i.test(trimmed)) return trimmed;
  if (trimmed.startsWith('blob:') || trimmed.startsWith('data:')) return trimmed;
  if (trimmed.startsWith('/')) {
    const base = apiBase();
    return base ? `${base}${trimmed}` : trimmed;
  }
  return trimmed;
}

export function parseMediaUrls(raw) {
  if (!raw) return [];
  if (Array.isArray(raw)) return raw.map((u) => resolveMediaUrl(u)).filter(Boolean);
  return String(raw)
    .split(',')
    .map((s) => resolveMediaUrl(s.trim()))
    .filter(Boolean);
}

export function firstMediaUrl(raw) {
  return parseMediaUrls(raw)[0] || '';
}
