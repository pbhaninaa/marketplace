export function isMeaningfulMinPrice(value) {
  if (value === null || value === undefined) return false;
  const raw = String(value).trim();
  if (!raw) return false;
  const n = Number(raw);
  return !(Number.isFinite(n) && n <= 0);
}

export function isMeaningfulMaxPrice(value) {
  if (value === null || value === undefined) return false;
  return String(value).trim() !== '';
}

/** Blank / "all" sidebar state — client has not narrowed the list. */
export function isDefaultMarketplaceFilters(filters) {
  if (!filters) return true;
  return !(
    filters.categoryId ||
    filters.providerId ||
    isMeaningfulMinPrice(filters.minPrice) ||
    isMeaningfulMaxPrice(filters.maxPrice) ||
    String(filters.location || '').trim() ||
    String(filters.search || '').trim()
  );
}

export function emptyMarketplaceFilters(overrides = {}) {
  return {
    categoryId: '',
    providerId: '',
    listingType: '',
    minPrice: '',
    maxPrice: '',
    location: '',
    search: '',
    page: 0,
    ...overrides,
  };
}
