<script setup>
import { ref, computed, onMounted } from 'vue';
import { api } from '../api';
import { useCartStore } from '../stores/cart';
import ResponsiveRecordShell from '../components/layout/ResponsiveRecordShell.vue';
import MarketplaceFilterSidebar from '../components/filters/MarketplaceFilterSidebar.vue';
import ListingRecordCard from '../components/listings/ListingRecordCard.vue';
import PaginationBar from '../components/ui/PaginationBar.vue';
import { inclusiveDatesToRentalInstants } from '../utils/rentalPricing';

const cart = useCartStore();

const listings = ref({ content: [], totalElements: 0 });
const categories = ref([]);
const providers = ref([]);
const loading = ref(true);

const entryListingType = ref('SALE'); // controlled by the Buy/Rent toggle

const hasSidebar = computed(() => true);

const filters = ref({
  categoryId: '',
  providerId: '',
  listingType: '',
  minPrice: '',
  maxPrice: '',
  location: '',
  search: '',
  page: 0,
});

const rentDefaults = ref({});
const clearingCart = ref(false);

function toDateInput(d) {
  const pad = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

function setRentDefaults(listing) {
  const start = new Date();
  start.setHours(0, 0, 0, 0);
  const endInclusive = new Date(start);
  endInclusive.setDate(endInclusive.getDate() + 2);
  rentDefaults.value[listing.id] = {
    start: toDateInput(start),
    end: toDateInput(endInclusive),
  };
}

function patchRentField(id, field, value) {
  const cur = rentDefaults.value[id];
  if (!cur) return;
  rentDefaults.value = {
    ...rentDefaults.value,
    [id]: { ...cur, [field]: value },
  };
}

const totalPages = computed(() =>
  Math.max(1, Math.ceil((listings.value.totalElements || 0) / 20)),
);

async function loadFiltersData() {
  const lt = entryListingType.value || 'SALE';
  const [catRes, provRes] = await Promise.all([
    api.get('/api/public/category-options', { params: { listingType: lt } }),
    api.get('/api/public/provider-options', { params: { listingType: lt } }),
  ]);
  categories.value = catRes.data || [];
  providers.value = provRes.data || [];
}

async function loadListings() {
  loading.value = true;
  try {
    const params = {
      page: filters.value.page,
      size: 20,
      listingType: entryListingType.value,
    };
    if (filters.value.categoryId) params.categoryId = filters.value.categoryId;
    if (filters.value.providerId) params.providerId = filters.value.providerId;
    if (filters.value.minPrice) params.minPrice = filters.value.minPrice;
    if (filters.value.maxPrice) params.maxPrice = filters.value.maxPrice;
    if (filters.value.location) params.location = filters.value.location;
    if (filters.value.search) params.search = filters.value.search;

    const { data } = await api.get('/api/public/listings', { params });
    listings.value = data;
    for (const item of data.content || []) {
      if (item.listingType === 'RENT') {
        setRentDefaults(item);
      }
    }
  } finally {
    loading.value = false;
  }
}

function chooseEntry(type) {
  entryListingType.value = type;
  filters.value = {
    ...filters.value,
    listingType: type,
    page: 0,
    // reset other filters when switching between buy/rent
    categoryId: '',
    providerId: '',
    minPrice: '',
    maxPrice: '',
    location: '',
    search: '',
  };
  loadListings();
  loadFiltersData();
}

function onFiltersUpdate(v) {
  // lock listing type to the client's chosen mode
  filters.value = { ...v, listingType: entryListingType.value };
}

function applyFilters() {
  filters.value = { ...filters.value, page: 0 };
  loadListings();
}

function nextPage() {
  if (filters.value.page + 1 < totalPages.value) {
    filters.value = { ...filters.value, page: filters.value.page + 1 };
    loadListings();
  }
}

function prevPage() {
  if (filters.value.page > 0) {
    filters.value = { ...filters.value, page: filters.value.page - 1 };
    loadListings();
  }
}

async function cancelLockedCart() {
  clearingCart.value = true;
  cart.lastError = null;
  try {
    await cart.clearCart();
  } finally {
    clearingCart.value = false;
  }
}

async function addToCart(listing, qty = 1) {
  cart.lastError = null;
  let opts = { quantity: 1 };
  if (listing.listingType === 'RENT') {
    if (!rentDefaults.value[listing.id]) setRentDefaults(listing);
    const rd = rentDefaults.value[listing.id];
    const { rentalStart, rentalEnd } = inclusiveDatesToRentalInstants(rd.start, rd.end);
    if (!rentalStart || !rentalEnd) {
      cart.lastError = 'Choose a valid rental period (end date on or after start date).';
      return;
    }
    opts = {
      quantity: 1,
      rentalStart,
      rentalEnd,
    };
  } else {
    const q = Math.max(1, Math.floor(Number(qty) || 1));
    opts = { quantity: q };
  }
  await cart.addListing(listing, opts);
}

onMounted(async () => {
  await loadFiltersData();
  // Default to Buy mode (SALE).
  filters.value = { ...filters.value, listingType: entryListingType.value, page: 0 };
  await loadListings();
  await cart.refresh();
});
</script>

<template>
  <div class="market-layout">
    <div v-if="cart.isLocked" class="banner-lock">
      <div class="banner-lock__inner">
        <p class="banner-lock__text">
          You are currently purchasing from <strong>{{ cart.lockedProviderName }}</strong
          >. Complete or cancel this transaction before selecting items from another provider.
        </p>
        <button
          type="button"
          class="btn btn-ghost banner-lock__cancel"
          :disabled="clearingCart"
          @click="cancelLockedCart"
        >
          {{ clearingCart ? 'Clearing…' : 'Cancel & clear cart' }}
        </button>
      </div>
    </div>

    <div class="market-body" :class="{ 'market-body--no-sidebar': !hasSidebar }">
      <MarketplaceFilterSidebar
        v-if="entryListingType"
        :model-value="filters"
        :categories="categories"
        :providers="providers"
        :hide-listing-type="true"
        :for-rent="entryListingType === 'RENT'"
        @update:model-value="onFiltersUpdate"
        @apply="applyFilters"
      />

      <section class="market-content">
        <header class="feed-hero">
          <p class="feed-hero__eyebrow">Livestock · vehicles · equipment</p>
          <h1 class="feed-hero__title">Marketplace</h1>
          <p class="feed-hero__lead">
            Buy and rent from verified providers. Your cart stays with one supplier per checkout — simple for guests,
            clear for business.
          </p>
          <div v-if="entryListingType" class="mode-toggle">
            <button
              type="button"
              class="mode-btn"
              :class="{ active: entryListingType === 'SALE' }"
              @click="chooseEntry('SALE')"
            >
              Buy
            </button>
            <button
              type="button"
              class="mode-btn"
              :class="{ active: entryListingType === 'RENT' }"
              @click="chooseEntry('RENT')"
            >
              Rent
            </button>
          </div>
        </header>

        <div v-if="cart.lastError" class="err-toast">{{ cart.lastError }}</div>

        <div v-if="loading" class="loading-state">
          <span class="loading-state__dot" aria-hidden="true" />
          <span class="loading-state__dot" aria-hidden="true" />
          <span class="loading-state__dot" aria-hidden="true" />
          <span class="loading-state__text">Loading listings…</span>
        </div>

        <ResponsiveRecordShell v-else desktop-label="Marketplace listings">
          <template #desktop>
            <div class="cards-grid cards-grid--desktop">
              <ListingRecordCard
                v-for="item in listings.content"
                :key="item.id"
                :listing="item"
                :greyed="cart.isGreyed(item)"
                :rent-start="rentDefaults[item.id]?.start ?? ''"
                :rent-end="rentDefaults[item.id]?.end ?? ''"
                @add-to-cart="(q) => addToCart(item, q)"
                @reset-rent-dates="setRentDefaults(item)"
                @update:rent-start="patchRentField(item.id, 'start', $event)"
                @update:rent-end="patchRentField(item.id, 'end', $event)"
              />
            </div>
          </template>
          <template #mobile>
            <div class="cards-grid cards-grid--mobile">
              <ListingRecordCard
                v-for="item in listings.content"
                :key="item.id"
                :listing="item"
                :greyed="cart.isGreyed(item)"
                :rent-start="rentDefaults[item.id]?.start ?? ''"
                :rent-end="rentDefaults[item.id]?.end ?? ''"
                @add-to-cart="(q) => addToCart(item, q)"
                @reset-rent-dates="setRentDefaults(item)"
                @update:rent-start="patchRentField(item.id, 'start', $event)"
                @update:rent-end="patchRentField(item.id, 'end', $event)"
              />
            </div>
          </template>
        </ResponsiveRecordShell>

        <PaginationBar v-if="!loading" :page="filters.page" :total-pages="totalPages" @prev="prevPage" @next="nextPage" />
      </section>
    </div>
  </div>
</template>

<style scoped>
.market-layout {
  display: flex;
  flex-direction: column;
  min-height: calc(100vh - 56px);
}

.market-body {
  display: grid;
  grid-template-columns: 260px 1fr;
  gap: 0;
  flex: 1;
}

.market-body--no-sidebar {
  grid-template-columns: 1fr;
}

.market-content {
  padding: clamp(1rem, 2.5vw, 1.75rem) clamp(1rem, 3vw, 2.25rem) ;
  width: 100%;
  max-width: none;
  margin: 0;
}

.market-body .feed-hero {
  margin: 0 auto 1.15rem;
  padding-bottom: 1.05rem;
  border-bottom: 1px solid var(--color-border);
  text-align: center;
}

.market-body .feed-hero__eyebrow {
  font-size: 0.72rem;
  font-weight: 800;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--color-sage);
  margin: 0 0 0.4rem;
}

.market-body .feed-hero__title {
  font-family: var(--font-display);
  font-size: clamp(1.75rem, 4vw, 2.35rem);
  font-weight: 650;
  letter-spacing: -0.03em;
  color: var(--color-canopy);
  margin: 0 0 0.45rem;
}

.market-body .feed-hero__lead {
  margin: 0 auto;
  max-width: 40rem;
  font-size: 1.02rem;
  line-height: 1.55;
  color: var(--color-text-secondary);
}

.mode-toggle {
  display: inline-flex;
  gap: 0.4rem;
  padding: 0.25rem;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-surface-elevated);
  box-shadow: var(--shadow-sm);
  margin-top: 0.9rem;
}

.mode-btn {
  border: none;
  background: transparent;
  cursor: pointer;
  font: inherit;
  font-weight: 800;
  padding: 0.55rem 0.95rem;
  border-radius: 999px;
  color: var(--color-muted);
}

.mode-btn.active {
  background: rgba(61, 122, 102, 0.14);
  color: var(--color-canopy);
}

.loading-state {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 2rem 0;
  color: var(--color-muted);
  font-weight: 500;
}

.loading-state__dot {
  width: 0.45rem;
  height: 0.45rem;
  border-radius: 50%;
  background: var(--color-sage);
  animation: pulse-dot 1.1s ease-in-out infinite;
}

.loading-state__dot:nth-child(2) {
  animation-delay: 0.15s;
}

.loading-state__dot:nth-child(3) {
  animation-delay: 0.3s;
}

@keyframes pulse-dot {
  0%,
  80%,
  100% {
    opacity: 0.25;
    transform: scale(0.85);
  }
  40% {
    opacity: 1;
    transform: scale(1);
  }
}

.loading-state__text {
  margin-left: 0.35rem;
}

.cards-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 1.1rem;
}

.cards-grid--desktop {
  max-width: 1120px;
  margin: 0 auto;
}

.cards-grid--mobile {
  grid-template-columns: 1fr;
  max-width: 680px;
  margin: 0 auto;
}

.muted {
  color: var(--color-muted);
}

@media (max-width: 900px) {
  .market-body {
    grid-template-columns: 1fr;
  }

  :deep(.filters) {
    border-right: none;
    border-bottom: 1px solid var(--color-border);
  }
}
</style>
