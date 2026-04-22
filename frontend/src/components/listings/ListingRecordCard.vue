<script setup>
import { computed, ref, watch } from 'vue';
import ListingTypeBadge from './ListingTypeBadge.vue';
import ListingPriceSummary from './ListingPriceSummary.vue';
import { isLargeLivestockCategoryName } from '../../constants/listingCategories';
import { countInclusiveRentalDays, estimateRentalAmount } from '../../utils/rentalPricing';

const props = defineProps({
  listing: { type: Object, required: true },
  greyed: { type: Boolean, default: false },
  rentStart: { type: String, default: '' },
  rentEnd: { type: String, default: '' },
});

const emit = defineEmits(['add-to-cart', 'reset-rent-dates', 'update:rentStart', 'update:rentEnd']);

const quantity = ref(1);

watch(
  () => props.listing.id,
  () => {
    quantity.value = 1;
  },
);

/** For sale only: show qty except Large livestock (one animal per listing). */
const showSaleQuantity = computed(
  () =>
    props.listing.listingType === 'SALE' && !isLargeLivestockCategoryName(props.listing.categoryName),
);

const maxSaleQty = computed(() => {
  const s = props.listing.stockQuantity;
  if (s == null || s === '') return 9999;
  const n = Number(s);
  return Number.isFinite(n) && n > 0 ? Math.min(Math.floor(n), 9999) : 9999;
});

function clampQty() {
  let q = Math.floor(Number(quantity.value) || 1);
  if (q < 1) q = 1;
  if (q > maxSaleQty.value) q = maxSaleQty.value;
  quantity.value = q;
}

function onAddToCart() {
  if (props.listing.listingType === 'RENT') {
    emit('add-to-cart', 1);
    return;
  }
  if (!showSaleQuantity.value) {
    emit('add-to-cart', 1);
    return;
  }
  clampQty();
  emit('add-to-cart', quantity.value);
}

const firstImageUrl = computed(() => {
  const raw = props.listing?.imageUrls;
  if (!raw) return '';
  if (Array.isArray(raw)) return raw[0] || '';
  return String(raw)
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)[0] || '';
});

const rentalDays = computed(() => countInclusiveRentalDays(props.rentStart, props.rentEnd));

const rentalEstimate = computed(() => {
  if (props.listing.listingType !== 'RENT') return null;
  const d = rentalDays.value;
  if (d < 1) return null;
  return estimateRentalAmount(props.listing, d);
});

const isOutOfStock = computed(() => {
  if (props.listing.listingType !== 'SALE') return false;
  const raw = props.listing.stockQuantity;
  if (raw == null || raw === '') return false;
  const n = Number(raw);
  return Number.isFinite(n) && n <= 0;
});

const isGreyed = computed(() => props.greyed || isOutOfStock.value);
</script>

<template>
  <article class="listing-card" :class="{ 'listing-card--greyed': isGreyed }">
    <div class="listing-card__media" :class="{ 'listing-card__media--empty': !firstImageUrl }">
      <img v-if="firstImageUrl" :src="firstImageUrl" :alt="listing.title" loading="lazy" />
      <div v-else class="listing-card__media-fallback" aria-hidden="true">No image</div>
    </div>
    <div class="listing-card__top">
      <ListingTypeBadge :listing-type="listing.listingType" />
      <span class="provider-chip">{{ listing.providerName }}</span>
    </div>
    <h3 class="listing-card__title">{{ listing.title }}</h3>
    <p class="muted small">{{ listing.providerLocation }}</p>
    <p class="listing-card__desc">{{ listing.description }}</p>
    <ListingPriceSummary :listing="listing" />

    <p v-if="isOutOfStock" class="stock-pill">Out of stock</p>

    <div v-if="showSaleQuantity" class="qty-row">
      <label class="small" :for="'listing-qty-' + listing.id">Quantity</label>
      <input
        :id="'listing-qty-' + listing.id"
        v-model.number="quantity"
        type="number"
        min="1"
        :max="maxSaleQty"
        class="field qty-input"
        :disabled="isGreyed"
        @change="clampQty"
      />
      <p v-if="listing.stockQuantity != null" class="muted tiny">Up to {{ maxSaleQty }} in stock</p>
    </div>

    <div v-if="listing.listingType === 'RENT'" class="rent-inputs">
      <label class="small" :for="'rent-start-' + listing.id">Start date</label>
      <input
        :id="'rent-start-' + listing.id"
        :value="rentStart"
        type="date"
        class="field"
        @input="emit('update:rentStart', $event.target.value)"
      />
      <label class="small" :for="'rent-end-' + listing.id">End date</label>
      <input
        :id="'rent-end-' + listing.id"
        :value="rentEnd"
        type="date"
        class="field"
        @input="emit('update:rentEnd', $event.target.value)"
      />
      <p v-if="rentalDays >= 1 && rentalEstimate != null" class="muted tiny rent-estimate">
        {{ rentalDays }} calendar day<span v-if="rentalDays !== 1">s</span>
        · Est. R {{ Number(rentalEstimate).toFixed(2) }}
      </p>
      <button type="button" class="linkish" @click="emit('reset-rent-dates')">Reset dates</button>
    </div>

    <button type="button" class="btn btn-primary listing-card__cta" :disabled="isGreyed" @click="onAddToCart">
      Add to cart
    </button>
  </article>
</template>

<style scoped>
.listing-card {
  position: relative;
  background: var(--color-surface-elevated);
  border-radius: var(--radius-lg);
  padding: 0.9rem 0.95rem 1rem;
  border: 1px solid var(--color-border);
  box-shadow: var(--shadow-md);
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
  transition:
    box-shadow 0.2s ease,
    border-color 0.2s ease;
}

.listing-card:not(.listing-card--greyed):hover {
  border-color: rgba(61, 122, 102, 0.35);
  box-shadow: var(--shadow-lg);
}

.listing-card--greyed {
  opacity: 0.44;
  pointer-events: none;
  filter: grayscale(0.2);
}

.listing-card__media {
  border-radius: 14px;
  overflow: hidden;
  border: 1px solid rgba(26, 60, 52, 0.10);
  background: rgba(26, 60, 52, 0.03);
  aspect-ratio: 16 / 9;
}

.listing-card__media img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.listing-card__media--empty {
  display: grid;
  place-items: center;
}

.listing-card__media-fallback {
  font-size: 0.8rem;
  font-weight: 700;
  color: var(--color-muted);
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.listing-card__title {
  font-family: var(--font-display);
  margin: 0;
  font-size: 1.05rem;
  font-weight: 600;
  color: var(--color-canopy);
  letter-spacing: -0.02em;
}

.listing-card__desc {
  font-size: 0.88rem;
  color: var(--color-muted);
  margin: 0;
  line-height: 1.45;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.listing-card__top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 0.5rem;
}

.provider-chip {
  font-size: 0.72rem;
  font-weight: 600;
  color: var(--color-earth);
  text-align: right;
  max-width: 48%;
  line-height: 1.3;
}

.muted {
  color: var(--color-muted);
}

.small {
  font-size: 0.82rem;
}

.qty-row {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  padding-top: 0.25rem;
  margin-top: 0.15rem;
  border-top: 1px dashed var(--color-border);
}

.qty-input {
  max-width: 8rem;
}

.tiny {
  font-size: 0.75rem;
  margin: 0;
}

.rent-inputs {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  padding-top: 0.25rem;
  margin-top: 0.15rem;
  border-top: 1px dashed var(--color-border);
}

.field {
  width: 100%;
  padding: 0.5rem 0.65rem;
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-border);
  font: inherit;
  font-size: 0.88rem;
  background: var(--color-surface);
}

.field:focus {
  outline: none;
  border-color: var(--color-sage);
  box-shadow: 0 0 0 3px rgba(61, 122, 102, 0.15);
}

.linkish {
  border: none;
  background: none;
  color: var(--color-canopy-mid);
  cursor: pointer;
  font-size: 0.82rem;
  font-weight: 600;
  text-align: left;
  padding: 0.15rem 0;
}

.linkish:hover {
  color: var(--color-sage);
  text-decoration: underline;
}

.rent-estimate {
  margin: 0;
  font-weight: 600;
  color: var(--color-canopy-mid);
}

.listing-card__cta {
  width: 100%;
  margin-top: 0.35rem;
}

.stock-pill {
  margin: 0;
  padding: 0.25rem 0.5rem;
  border-radius: 999px;
  border: 1px solid rgba(180, 40, 40, 0.25);
  background: rgba(255, 245, 245, 0.8);
  color: rgba(140, 20, 20, 1);
  font-size: 0.78rem;
  font-weight: 700;
  width: fit-content;
}
</style>
