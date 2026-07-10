import { describe, expect, it } from 'vitest';
import { isDefaultMarketplaceFilters, isMeaningfulMinPrice } from '../src/utils/marketplaceFilters.js';

describe('marketplaceFilters', () => {
  it('treats blank or zero min price as no filter', () => {
    expect(isMeaningfulMinPrice('')).toBe(false);
    expect(isMeaningfulMinPrice('0')).toBe(false);
    expect(isMeaningfulMinPrice(0)).toBe(false);
    expect(isMeaningfulMinPrice('50')).toBe(true);
  });

  it('detects default sidebar state as show-all', () => {
    expect(
      isDefaultMarketplaceFilters({
        categoryId: '',
        providerId: '',
        minPrice: '0',
        maxPrice: '',
        location: '',
        search: '',
      }),
    ).toBe(true);
  });
});
