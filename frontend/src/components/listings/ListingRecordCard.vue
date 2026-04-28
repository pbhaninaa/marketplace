<script setup>
import { computed, ref, watch } from 'vue';
import ListingTypeBadge from './ListingTypeBadge.vue';
import ListingPriceSummary from './ListingPriceSummary.vue';
import TextWithTooltip from '../ui/TextWithTooltip.vue';
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
const showLightbox = ref(false);
const currentImageIndex = ref(0);

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

const allImageUrls = computed(() => {
  const raw = props.listing?.imageUrls;
  if (!raw) return [];
  if (Array.isArray(raw)) return raw.filter(Boolean);
  return String(raw)
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean);
});

const currentLightboxImage = computed(() => allImageUrls.value[currentImageIndex.value] || '');

function openLightbox() {
  currentImageIndex.value = 0;
  showLightbox.value = true;
}

function closeLightbox() {
  showLightbox.value = false;
}

function nextImage() {
  if (currentImageIndex.value < allImageUrls.value.length - 1) {
    currentImageIndex.value++;
  }
}

function prevImage() {
  if (currentImageIndex.value > 0) {
    currentImageIndex.value--;
  }
}

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
    <div class="listing-card__media" :class="{ 'listing-card__media--empty': !firstImageUrl }" @click="openLightbox" role="button" tabindex="0">
      <img v-if="firstImageUrl" :src="firstImageUrl" :alt="listing.title || 'Listing image'" loading="lazy" />
      <div v-else class="listing-card__media-fallback" aria-hidden="true">No image</div>
    </div>
    <div class="listing-card__top">
      <ListingTypeBadge :listing-type="listing.listingType" />
      <span class="provider-chip">{{ listing.providerName }}</span>
    </div>
    <h3 class="listing-card__title">
      <TextWithTooltip :text="listing.title || '—'" />
    </h3>
    <p class="muted small">📍{{ listing.providerLocation || '—' }}</p>
    <p class="listing-card__desc">
      <TextWithTooltip :text="listing.description || ''" :max-length="100" />
    </p>
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

    <!-- IMAGE LIGHTBOX MODAL -->
    <dialog class="image-lightbox" :open="showLightbox">
      <div class="lightbox-backdrop" @click="closeLightbox" />
      <div class="lightbox-container">
        <button type="button" class="lightbox-close" @click="closeLightbox" aria-label="Close">
          <span>✕</span>
        </button>
        
        <div class="lightbox-main">
          <img v-if="currentLightboxImage" :src="currentLightboxImage" :alt="listing.title || 'Listing image'" class="lightbox-image" />
        </div>

        <!-- Navigation Buttons -->
        <button v-if="allImageUrls.length > 1" type="button" class="lightbox-nav lightbox-nav--prev" @click="prevImage" aria-label="Previous image" :disabled="currentImageIndex === 0">
          ❮
        </button>
        <button v-if="allImageUrls.length > 1" type="button" class="lightbox-nav lightbox-nav--next" @click="nextImage" aria-label="Next image" :disabled="currentImageIndex === allImageUrls.length - 1">
          ❯
        </button>

        <!-- Image Counter -->
        <div v-if="allImageUrls.length > 1" class="lightbox-counter">
          {{ currentImageIndex + 1 }} / {{ allImageUrls.length }}
        </div>

        <!-- Thumbnails -->
        <div v-if="allImageUrls.length > 1" class="lightbox-thumbnails">
          <button
            v-for="(url, idx) in allImageUrls"
            :key="idx"
            type="button"
            class="lightbox-thumbnail"
            :class="{ 'lightbox-thumbnail--active': idx === currentImageIndex }"
            @click="currentImageIndex = idx"
            :aria-label="`View image ${idx + 1}`"
          >
            <img :src="url" :alt="`Thumbnail ${idx + 1}`" />
          </button>
        </div>
      </div>
    </dialog>
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
  cursor: pointer;
  transition: all 0.2s ease;
}

.listing-card__media:hover {
  border-color: rgba(61, 122, 102, 0.35);
  box-shadow: 0 0 12px rgba(61, 122, 102, 0.2);
  transform: scale(1.02);
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

/* ==================== LIGHTBOX STYLES ==================== */
.image-lightbox {
  all: revert;
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: transparent;
  border: none;
  padding: 0;
  margin: 0;
  z-index: 9999;
}

.image-lightbox:not([open]) {
  display: none;
}

.image-lightbox::backdrop {
  background: rgba(0, 0, 0, 0.7);
  backdrop-filter: blur(4px);
}

.lightbox-backdrop {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: transparent;
  cursor: pointer;
  z-index: -1;
}

.lightbox-container {
  position: fixed;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 90%;
  max-width: 900px;
  height: 90vh;
  max-height: 800px;
  background: var(--color-surface-elevated, white);
  border-radius: var(--radius-lg, 16px);
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  display: flex;
  flex-direction: column;
  z-index: 10000;
  animation: slideUp 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translate(-50%, calc(-50% + 20px));
  }
  to {
    opacity: 1;
    transform: translate(-50%, -50%);
  }
}

.lightbox-close {
  position: absolute;
  top: 1rem;
  right: 1rem;
  width: 40px;
  height: 40px;
  border: none;
  background: rgba(26, 60, 52, 0.1);
  border-radius: 50%;
  color: var(--color-canopy, #1a3c34);
  font-size: 1.5rem;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 10001;
  transition: all 0.2s ease;
}

.lightbox-close:hover {
  background: rgba(26, 60, 52, 0.2);
  transform: rotate(90deg);
}

.lightbox-main {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: rgba(26, 60, 52, 0.05);
  position: relative;
  padding: 0.5rem;
}

.lightbox-image {
  width: 100%;
  height: 100%;
  object-fit: contain;
  border-radius: var(--radius-md, 8px);
  display: block;
}

.lightbox-nav {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 50px;
  height: 50px;
  border: none;
  background: rgba(26, 60, 52, 0.8);
  color: white;
  border-radius: 50%;
  font-size: 1.5rem;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
  z-index: 10001;
}

.lightbox-nav:hover:not(:disabled) {
  background: rgba(26, 60, 52, 1);
  transform: translateY(-50%) scale(1.1);
}

.lightbox-nav:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.lightbox-nav--prev {
  left: 1rem;
}

.lightbox-nav--next {
  right: 1rem;
}

.lightbox-counter {
  position: absolute;
  top: 1rem;
  left: 50%;
  transform: translateX(-50%);
  background: rgba(26, 60, 52, 0.9);
  color: white;
  padding: 0.5rem 1rem;
  border-radius: 20px;
  font-size: 0.9rem;
  font-weight: 600;
  z-index: 10001;
}

.lightbox-thumbnails {
  display: flex;
  gap: 0.5rem;
  padding: 1rem;
  background: rgba(26, 60, 52, 0.03);
  border-top: 1px solid rgba(26, 60, 52, 0.1);
  overflow-x: auto;
  justify-content: center;
  flex-wrap: wrap;
}

.lightbox-thumbnail {
  flex-shrink: 0;
  width: 70px;
  height: 70px;
  border: 2px solid transparent;
  border-radius: var(--radius-md, 8px);
  padding: 0;
  background: none;
  cursor: pointer;
  overflow: hidden;
  transition: all 0.2s ease;
}

.lightbox-thumbnail img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.lightbox-thumbnail:hover {
  border-color: rgba(61, 122, 102, 0.5);
  transform: scale(1.05);
}

.lightbox-thumbnail--active {
  border-color: var(--color-sage, #3d7a66);
  box-shadow: 0 0 8px rgba(61, 122, 102, 0.3);
}

@media (max-width: 768px) {
  .lightbox-container {
    width: 95%;
    height: 90vh;
    max-height: 90vh;
  }

  .lightbox-main {
    padding: 0.25rem;
  }

  .lightbox-nav {
    width: 36px;
    height: 36px;
    font-size: 1.1rem;
  }

  .lightbox-nav--prev {
    left: 0.35rem;
  }

  .lightbox-nav--next {
    right: 0.35rem;
  }

  .lightbox-close {
    width: 32px;
    height: 32px;
    font-size: 1.2rem;
    top: 0.5rem;
    right: 0.5rem;
  }

  .lightbox-counter {
    font-size: 0.8rem;
    padding: 0.4rem 0.8rem;
  }

  .lightbox-thumbnails {
    padding: 0.5rem;
    gap: 0.25rem;
    max-height: 100px;
  }

  .lightbox-thumbnail {
    width: 55px;
    height: 55px;
  }
}

@media (max-width: 480px) {
  .lightbox-container {
    width: 98%;
    height: 85vh;
    border-radius: var(--radius-md, 8px);
  }

  .lightbox-main {
    padding: 0;
  }

  .lightbox-nav {
    width: 32px;
    height: 32px;
    font-size: 1rem;
  }

  .lightbox-nav--prev {
    left: 0.25rem;
  }

  .lightbox-nav--next {
    right: 0.25rem;
  }

  .lightbox-close {
    width: 28px;
    height: 28px;
    font-size: 1rem;
    top: 0.35rem;
    right: 0.35rem;
  }

  .lightbox-thumbnails {
    padding: 0.4rem;
    gap: 0.2rem;
    max-height: 80px;
  }

  .lightbox-thumbnail {
    width: 50px;
    height: 50px;
  }

  .lightbox-counter {
    font-size: 0.75rem;
    padding: 0.35rem 0.7rem;
  }
}
</style>
