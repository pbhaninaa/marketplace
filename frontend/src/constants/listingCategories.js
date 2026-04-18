/** Provider listing category dropdowns by listing type. Values are stable keys; labels are stored as categoryName on the API. */

export const SALE_CATEGORY_OPTIONS = [
  { value: 'LARGE_LIVESTOCK', label: 'Large livestock (Large stock)' },
  { value: 'SMALL_LIVESTOCK', label: 'Small livestock (Small stock)' },
  { value: 'POULTRY', label: 'Poultry' },
  { value: 'CROPS_FEED', label: 'Crops & feed' },
  { value: 'EQUIPMENT', label: 'Equipment & machinery' },
  { value: 'SPARE_PARTS', label: 'Spare parts & supplies' },
  { value: 'OTHER', label: 'Other' },
];

export const RENT_CATEGORY_OPTIONS = [
  { value: 'TRACTORS_IMPLEMENTS', label: 'Tractors & implements' },
  { value: 'HARVESTING', label: 'Harvesting equipment' },
  { value: 'IRRIGATION', label: 'Irrigation' },
  { value: 'LAND_STORAGE', label: 'Land, sheds & storage' },
  { value: 'VEHICLES', label: 'Transport & vehicles' },
  { value: 'TOOLS_SMALL', label: 'Tools & small equipment' },
  { value: 'OTHER', label: 'Other' },
];

export const LARGE_LIVESTOCK_KEY = 'LARGE_LIVESTOCK';
export const OTHER_KEY = 'OTHER';

const allLabelsByType = {
  SALE: new Map(SALE_CATEGORY_OPTIONS.map((o) => [o.value, o.label])),
  RENT: new Map(RENT_CATEGORY_OPTIONS.map((o) => [o.value, o.label])),
};

export function categoryLabelFor(listingType, value) {
  return allLabelsByType[listingType]?.get(value) || '';
}

/** Map saved categoryName from API to { categoryKey, categoryOther } for the form. */
export function parseCategoryFromName(listingType, categoryName) {
  const raw = (categoryName || '').trim();
  if (!raw) {
    return { categoryKey: '', categoryOther: '' };
  }
  const opts = listingType === 'RENT' ? RENT_CATEGORY_OPTIONS : SALE_CATEGORY_OPTIONS;
  const match = opts.find((o) => o.label === raw);
  if (match) {
    return { categoryKey: match.value, categoryOther: '' };
  }
  return { categoryKey: OTHER_KEY, categoryOther: raw };
}

/** True when categoryName is the preset “Large livestock (Large stock)” (one animal per line / qty 1 in cart). */
export function isLargeLivestockCategoryName(categoryName) {
  return (categoryName || '').trim() === categoryLabelFor('SALE', LARGE_LIVESTOCK_KEY);
}
