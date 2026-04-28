<script setup>
import { ref, onMounted, computed, watch } from 'vue';
import { useRouter } from 'vue-router';
import { providerListingsApi } from '../services/marketplaceApi';
import { useAuthStore } from '../stores/auth';
import {
  SALE_CATEGORY_OPTIONS,
  RENT_CATEGORY_OPTIONS,
  LARGE_LIVESTOCK_KEY,
  OTHER_KEY,
  categoryLabelFor,
  parseCategoryFromName,
} from '../constants/listingCategories';
import FormField from '../components/ui/FormField.vue';
import ResponsiveRecordShell from '../components/layout/ResponsiveRecordShell.vue';
import DataTableShell from '../components/ui/DataTableShell.vue';
import TablePager from '../components/ui/TablePager.vue';
import TextWithTooltip from '../components/ui/TextWithTooltip.vue';
import { isNonEmptyString, isPositiveNumber, isMinInt } from '../utils/validation';

const router = useRouter();
const auth = useAuthStore();

const loading = ref(true);
const error = ref('');
const saving = ref(false);
const pickedImageFiles = ref([]);
const pickedPreviewUrls = ref([]);
const showDialog = ref(false);
const editing = ref(null); // listing object when editing

const listings = ref([]);

/* ================= TABLE: SEARCH + PAGINATION ================= */
const q = ref('');
const PAGE_SIZE = 5;
const page = ref(1);

const filteredListings = computed(() => {
  const raw = (q.value || '').trim().toLowerCase();
  const all = listings.value || [];
  if (!raw) return all;
  return all.filter((l) => {
    const hay = [
      l?.title,
      l?.description,
      l?.categoryName,
      l?.listingType,
      l?.unitPrice != null ? String(l.unitPrice) : '',
    ]
      .filter(Boolean)
      .join(' ')
      .toLowerCase();
    return hay.includes(raw);
  });
});

const pageCount = computed(() => Math.max(1, Math.ceil(filteredListings.value.length / PAGE_SIZE)));
const pagedListings = computed(() =>
  filteredListings.value.slice((page.value - 1) * PAGE_SIZE, page.value * PAGE_SIZE),
);

watch(q, () => {
  page.value = 1;
});

const form = ref({
  listingType: 'SALE',
  title: '',
  description: '',
  imageUrls: '',
  unitPrice: '',
  stockQuantity: '',
  rentPriceHourly: '',
  rentPriceDaily: '',
  rentPriceWeekly: '',
  /** @type {('HOURLY'|'DAILY'|'WEEKLY')[]} */
  rentRateModes: [],
  categoryKey: '',
  categoryOther: '',
  active: true,
});

const isRent = computed(() => form.value.listingType === 'RENT');

const categoryOptions = computed(() =>
  form.value.listingType === 'RENT' ? RENT_CATEGORY_OPTIONS : SALE_CATEGORY_OPTIONS,
);

const showCategoryOther = computed(() => form.value.categoryKey === OTHER_KEY);

const showStockQuantity = computed(
  () =>
    form.value.listingType === 'SALE' &&
    !!form.value.categoryKey &&
    form.value.categoryKey !== LARGE_LIVESTOCK_KEY,
);

watch(
  () => form.value.listingType,
  (next, prev) => {
    if (prev === undefined || next === prev) return;
    form.value.categoryKey = '';
    form.value.categoryOther = '';
    if (next !== 'RENT') {
      form.value.rentRateModes = [];
      form.value.rentPriceHourly = '';
      form.value.rentPriceDaily = '';
      form.value.rentPriceWeekly = '';
    }
  },
);

watch(
  () => [...(form.value.rentRateModes || [])],
  (modes) => {
    if (!modes.includes('HOURLY')) form.value.rentPriceHourly = '';
    if (!modes.includes('DAILY')) form.value.rentPriceDaily = '';
    if (!modes.includes('WEEKLY')) form.value.rentPriceWeekly = '';
  },
);

function rentModesFromListing(l) {
  const modes = [];
  if (l.rentPriceHourly != null && Number(l.rentPriceHourly) > 0) modes.push('HOURLY');
  if (l.rentPriceDaily != null && Number(l.rentPriceDaily) > 0) modes.push('DAILY');
  if (l.rentPriceWeekly != null && Number(l.rentPriceWeekly) > 0) modes.push('WEEKLY');
  return modes;
}

function resolvedCategoryNameForSubmit() {
  const key = form.value.categoryKey;
  const type = form.value.listingType;
  if (!key) return '';
  if (key === OTHER_KEY) {
    return (form.value.categoryOther || '').trim();
  }
  return categoryLabelFor(type, key);
}
const canEdit = computed(() => auth.role === 'PROVIDER_OWNER' || auth.role === 'PROVIDER_ADMIN');

onMounted(async () => {
  auth.restoreFromStorage();
  if (!auth.isProviderUser) {
    router.replace({ path: '/login', query: { redirect: '/provider/listings' } });
    return;
  }
  await loadAll();
});

async function loadAll() {
  loading.value = true;
  error.value = '';
  try {
    const { data } = await providerListingsApi.list();
    listings.value = data?.content || [];
    page.value = 1;
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  } finally {
    loading.value = false;
  }
}

function onPickImages(e) {
  const files = Array.from(e.target.files || []).filter(Boolean);
  pickedImageFiles.value = files;
  pickedPreviewUrls.value.forEach((u) => URL.revokeObjectURL(u));
  pickedPreviewUrls.value = files.map((f) => URL.createObjectURL(f));
  e.target.value = '';
}

function listingFirstImageUrl(l) {
  const raw = (l?.imageUrls || '').split(',')[0]?.trim();
  return raw || '';
}

function listingImageCount(l) {
  return (l?.imageUrls || '')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean).length;
}

const existingDialogImageUrls = computed(() =>
  (form.value.imageUrls || '')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean),
);

function resetForm() {
  form.value = {
    listingType: 'SALE',
    title: '',
    description: '',
    imageUrls: '',
    unitPrice: '',
    stockQuantity: '',
    rentPriceHourly: '',
    rentPriceDaily: '',
    rentPriceWeekly: '',
    rentRateModes: [],
    categoryKey: '',
    categoryOther: '',
    active: true,
  };
}

function openAdd() {
  if (!canEdit.value) return;
  editing.value = null;
  resetForm();
  showDialog.value = true;
}

function openEdit(l) {
  if (!canEdit.value) return;
  editing.value = l;
  const type = l.listingType || 'SALE';
  const parsed = parseCategoryFromName(type, l.categoryName);
  form.value = {
    listingType: type,
    title: l.title || '',
    description: l.description || '',
    imageUrls: l.imageUrls || '',
    unitPrice: l.unitPrice != null ? String(l.unitPrice) : '',
    stockQuantity: l.stockQuantity != null ? String(l.stockQuantity) : '',
    rentPriceHourly: l.rentPriceHourly != null ? String(l.rentPriceHourly) : '',
    // rentPriceDaily: l.rentPriceDaily != null ? String(l.rentPriceDaily) : '',
    rentPriceWeekly: l.rentPriceWeekly != null ? String(l.rentPriceWeekly) : '',
    rentRateModes: type === 'RENT' ? rentModesFromListing(l) : [],
    categoryKey: parsed.categoryKey,
    categoryOther: parsed.categoryOther,
    active: !!l.active,
  };
  pickedImageFiles.value = [];
  pickedPreviewUrls.value = [];
  showDialog.value = true;
}

function closeDialog() {
  showDialog.value = false;
  editing.value = null;
  pickedPreviewUrls.value.forEach((u) => URL.revokeObjectURL(u));
  pickedImageFiles.value = [];
  pickedPreviewUrls.value = [];
}

async function createListing() {
  if (!canEdit.value) return;
  const categoryName = resolvedCategoryNameForSubmit();
  if (!isNonEmptyString(form.value.title)) {
    error.value = 'Please enter a title.';
    return;
  }
  if (!isPositiveNumber(form.value.unitPrice)) {
    error.value = 'Please enter a unit price greater than zero.';
    return;
  }
  if (!form.value.categoryKey) {
    error.value = 'Please select a category.';
    return;
  }
  if (form.value.categoryKey === OTHER_KEY && !categoryName) {
    error.value = 'Please specify the category.';
    return;
  }
  if (isRent.value) {
    const modes = form.value.rentRateModes || [];
    if (!modes.length) {
      error.value = 'Select at least one rental rate type (hourly, daily, or weekly) in the list.';
      return;
    }
    const need = (key, label) => {
      const raw = Number(form.value[key]);
      return modes.includes(label) && (!Number.isFinite(raw) || raw <= 0);
    };
    // if (need('rentPriceHourly', 'HOURLY')) {
    //   error.value = 'Enter an hourly rate greater than zero.';
    //   return;
    // }
    // if (need('rentPriceDaily', 'DAILY')) {
    //   error.value = 'Enter a daily rate greater than zero.';
    //   return;
    // }
    // if (need('rentPriceWeekly', 'WEEKLY')) {
    //   error.value = 'Enter a weekly rate greater than zero.';
    //   return;
    // }
  }
  saving.value = true;
  error.value = '';
  try {
    const rawPrice = Number(form.value.unitPrice);
    const unitPrice = Number.isFinite(rawPrice) ? rawPrice : 0;
    const imageStr = (form.value.imageUrls && String(form.value.imageUrls).trim()) || '';
    let stockQuantity = null;
    if (form.value.listingType === 'SALE') {
      if (form.value.categoryKey === LARGE_LIVESTOCK_KEY) {
        stockQuantity = 1;
      } else {
        const raw = String(form.value.stockQuantity || '').trim();
        const next = raw === '' ? null : Number(raw);
        if (next != null && !isMinInt(next, 0)) {
          error.value = 'Stock quantity must be a whole number (0 or more).';
          return;
        }
        stockQuantity = next;
      }
    }
    const body = {
      listingType: form.value.listingType || 'SALE',
      title: (form.value.title || '').trim(),
      description: (form.value.description && String(form.value.description).trim()) || null,
      imageUrls: imageStr,
      unitPrice,
      stockQuantity,
      rentPriceHourly:
        isRent.value && (form.value.rentRateModes || []).includes('HOURLY') && form.value.rentPriceHourly
          ? Number(form.value.rentPriceHourly)
          : null,
      rentPriceDaily:
        isRent.value && (form.value.rentRateModes || []).includes('DAILY') && form.value.rentPriceDaily
          ? Number(form.value.rentPriceDaily)
          : null,
      rentPriceWeekly:
        isRent.value && (form.value.rentRateModes || []).includes('WEEKLY') && form.value.rentPriceWeekly
          ? Number(form.value.rentPriceWeekly)
          : null,
      categoryName,
      active: !!form.value.active,
    };

    const hasNewFiles = pickedImageFiles.value.length > 0;
    if (hasNewFiles) {
      const fd = new FormData();
      fd.append('listing', new Blob([JSON.stringify(body)], { type: 'application/json' }));
      for (const f of pickedImageFiles.value) {
        fd.append('files', f);
      }
      if (editing.value?.id) {
        await providerListingsApi.updateWithImages(editing.value.id, fd);
      } else {
        await providerListingsApi.createWithImages(fd);
      }
    } else if (editing.value?.id) {
      await providerListingsApi.update(editing.value.id, body);
    } else {
      await providerListingsApi.create(body);
    }
    closeDialog();
    resetForm();
    await loadAll();
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  } finally {
    saving.value = false;
  }
}

async function deleteListing(id) {
  if (!canEdit.value) return;
  error.value = '';
  try {
    await providerListingsApi.remove(id);
    await loadAll();
  } catch (e) {
    error.value = e.response?.data?.message || e.message;
  }
}
</script>

<template>
  <div class="page-document page-document--wide provider-listings-page">
    <header class="page-hero">
      <p class="page-hero__eyebrow">Provider</p>
      <h1 class="page-hero__title">Listings & services</h1>
      <p class="page-hero__lead">
        Add what you sell or rent out. Add at least one photo (upload or capture) and a clear description.
      </p>
    </header>

    <p v-if="error" class="err-toast">{{ error }}</p>
    <p v-if="loading" class="muted loading-line">Loading…</p>

    <template v-else>
      <section v-if="!showDialog" class="surface-panel listings-panel">
        <div class="panel-head">
          <h2>Your listings</h2>
          <button type="button" class="btn btn-primary" :disabled="!canEdit" @click="openAdd">Add new</button>
        </div>
        <div class="toolbar">
          <FormField label="">
            <input v-model="q" type="text" placeholder="Search listings…" style="min-width: 240px" />
          </FormField>
          <TablePager v-model:page="page" :page-count="pageCount" />
        </div>
        <p v-if="!canEdit" class="muted small">Only provider owner/admin can create or delete listings.</p>
        <ResponsiveRecordShell desktop-label="Your listings">
          <template #desktop>
            <DataTableShell caption="Your listings">
              <thead>
                <tr>
                  <th>Type</th>
                  <th>Image</th>
                  <th>Title</th>
                  <th>Category</th>
                  <th>Price</th>
                  <th>Active</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="l in pagedListings" :key="l.id">
                  <td>{{ l.listingType }}</td>
                  <td class="thumb-cell">
                    <div v-if="listingFirstImageUrl(l)" class="thumb">
                      <img :src="listingFirstImageUrl(l)" :alt="l.title" />
                    </div>
                    <div v-else class="thumb thumb--empty">No image</div>
                  </td>
                  <td>
                    <strong><TextWithTooltip class="cell-text" :text="l.title || '—'" :max-length="22" /></strong>
                    <div class="small muted clamp">
                      <TextWithTooltip class="cell-text" :text="l.description || '—'" :max-length="32" />
                    </div>
                    <div class="tiny muted">{{ listingImageCount(l) }} photo{{ listingImageCount(l) === 1 ? '' : 's' }}</div>
                  </td>
                  <td class="small">{{ l.categoryName || '—' }}</td>
                  <td>R {{ l.unitPrice }}</td>
                  <td>{{ l.active ? 'Yes' : 'No' }}</td>
                  <td class="cell-actions">
                    <button type="button" class="btn btn-ghost" :disabled="!canEdit" @click="openEdit(l)">Edit</button>
                    <button type="button" class="btn btn-ghost" :disabled="!canEdit" @click="deleteListing(l.id)">
                      Delete
                    </button>
                  </td>
                </tr>
                <tr v-if="!pagedListings.length">
                  <td colspan="7" class="muted small">No listings found.</td>
                </tr>
              </tbody>
            </DataTableShell>
          </template>
          <template #mobile>
            <div class="cards">
              <article v-for="l in pagedListings" :key="l.id" class="listing-card">
                <div v-if="listingFirstImageUrl(l)" class="card-thumb">
                  <img :src="listingFirstImageUrl(l)" :alt="l.title" />
                </div>
                <strong><TextWithTooltip :text="l.title || '—'" :max-length="26" /></strong>
                <span class="meta"
                  >{{ l.listingType }} · {{ l.categoryName || '—' }} · R{{ l.unitPrice }} ·
                  {{ l.active ? 'Active' : 'Draft' }}</span
                >
                <span class="meta clamp">{{ l.description }}</span>
                <span class="meta tiny">{{ listingImageCount(l) }} photo{{ listingImageCount(l) === 1 ? '' : 's' }}</span>
                <button type="button" class="btn btn-ghost" :disabled="!canEdit" @click="openEdit(l)">Edit</button>
                <button type="button" class="btn btn-ghost" :disabled="!canEdit" @click="deleteListing(l.id)">
                  Delete
                </button>
              </article>
            </div>
          </template>
        </ResponsiveRecordShell>
      </section>

      <dialog class="mp-dialog" :open="showDialog">
        <div class="mp-dialog__panel">
          <div class="mp-dialog__head">
            <h2 class="mp-dialog__title">{{ editing ? 'Edit listing' : 'Add listing' }}</h2>
            <button type="button" class="btn btn-ghost" @click="closeDialog">X</button>
          </div>
 <p v-if="error" class="err-toast">{{ error }}</p>
          <div class="grid">
            <FormField label="Type">
              <select v-model="form.listingType" :disabled="!canEdit">
                <option value="SALE">For sale</option>
                <option value="RENT">For rent</option>
              </select>
            </FormField>

            <FormField label="Category">
              <select v-model="form.categoryKey" required :disabled="!canEdit">
                <option value="" disabled>Select category</option>
                <option v-for="opt in categoryOptions" :key="opt.value" :value="opt.value">
                  {{ opt.label }}
                </option>
              </select>
            </FormField>
          </div>

          <FormField v-if="showCategoryOther" label="Specify category" capitalize-first>
            <input
              v-model="form.categoryOther"
              type="text"
              maxlength="200"
              placeholder="Describe your category"
              :disabled="!canEdit"
            />
          </FormField>
          <p v-if="!showCategoryOther" class="muted small">
            Categories depend on whether the listing is for sale or for rent.
          </p>

          <FormField label="Title" capitalize-first>
            <input v-model="form.title" type="text" maxlength="200" required :disabled="!canEdit" />
          </FormField>

          <FormField label="Description" capitalize-first>
            <textarea v-model="form.description" rows="4" maxlength="4000" :disabled="!canEdit" />
          </FormField>

          <FormField label="Photos">
            <p class="muted small">
              Choose photos or use the camera; they are saved together when you create or update the listing. You can add
              more before saving.
            </p>
            <div v-if="existingDialogImageUrls.length" class="existing-previews">
              <p class="muted small">Current photos</p>
              <div class="previews">
                <img v-for="u in existingDialogImageUrls" :key="u" :src="u" :alt="'Listing photo'" />
              </div>
            </div>
            <input
              type="file"
              accept="image/*"
              capture="environment"
              multiple
              :disabled="!canEdit || saving"
              @change="onPickImages"
            />
            <div v-if="pickedPreviewUrls.length" class="previews">
              <img v-for="u in pickedPreviewUrls" :key="u" :src="u" alt="Preview" />
            </div>
          </FormField>

         
          <div v-if="isRent" class="rent-section">
            <FormField label="Rental rate types">
               <p class="muted small">
                Choose which rates you offer. Hold <kbd>Ctrl</kbd> (Windows) or <kbd>⌘</kbd> (Mac) to select more
                than one. Then enter the price for each selected type below.
              </p> <select
                v-model="form.rentRateModes"
                multiple
                class="rent-rate-select"
                size="3"
                :disabled="!canEdit"
              >
                <option value="HOURLY">Hourly</option>
                <option value="DAILY">Daily</option>
                <option value="WEEKLY">Weekly</option>
              </select>
            
            </FormField>
            <!-- <div class="rent-grid">
              <FormField v-if="form.rentRateModes.includes('HOURLY')" label="Hourly rate (ZAR)">
                <input v-model="form.rentPriceHourly" type="number" min="0" step="0.01" :disabled="!canEdit" />
              </FormField>
              <FormField v-if="form.rentRateModes.includes('DAILY')" label="Daily rate (ZAR)">
                <input v-model="form.rentPriceDaily" type="number" min="0" step="0.01" :disabled="!canEdit" />
              </FormField>
              <FormField v-if="form.rentRateModes.includes('WEEKLY')" label="Weekly rate (ZAR)">
                <input v-model="form.rentPriceWeekly" type="number" min="0" step="0.01" :disabled="!canEdit" />
              </FormField>
            </div> -->
          </div>
 <div class="grid">
            <FormField label="Price (ZAR)">
              <input v-model="form.unitPrice" type="number" min="0" step="0.01" required :disabled="!canEdit" />
            </FormField>

            <FormField v-if="showStockQuantity" label="Stock quantity">
              <input v-model="form.stockQuantity" type="number" min="0" step="1" :disabled="!canEdit" />
            </FormField>
          </div>

          <FormField label="Publish">
            <label class="toggle">
              <input v-model="form.active" type="checkbox" :disabled="!canEdit" />
              <span class="toggle__label">Active</span>
            </label>
          </FormField>

          <div class="mp-dialog__actions">
            <button type="button" class="btn btn-primary" :disabled="!canEdit || saving" @click="createListing">
              {{ saving ? 'Saving…' : editing ? 'Save changes' : 'Create listing' }}
            </button>
          </div>
        </div>
      </dialog>
    </template>
  </div>
</template>

<style scoped>
.provider-listings-page {
  padding-bottom: 2rem;
}
.listings-panel {
  margin-bottom: 1.25rem;
}
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 0.5rem;
}
.toolbar {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 0.75rem;
  flex-wrap: wrap;
}
.grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
}
.rent-section {
  margin-top: 0.25rem;
}
.rent-rate-select {
  width: 100%;
  max-width: 20rem;
  min-height: 6.5rem;
  padding: 0.35rem 0.5rem;
  border-radius: 10px;
  border: 1px solid rgba(26, 60, 52, 0.18);
  background: var(--color-surface, #fff);
}
.rent-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(11rem, 1fr));
  gap: 1rem;
  margin-top: 0.75rem;
}
.toggle {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
}
.toggle__label {
  font-weight: 600;
}
.actions {
  text-align: right;
}
.thumb-cell {
  width: 96px;
}
.thumb {
  width: 80px;
  height: 56px;
  border-radius: 12px;
  border: 1px solid rgba(26, 60, 52, 0.10);
  overflow: hidden;
  background: rgba(26, 60, 52, 0.03);
  display: grid;
  place-items: center;
  font-size: 0.75rem;
  color: var(--color-muted);
}
.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.card-thumb {
  width: 100%;
  height: 140px;
  border-radius: 14px;
  overflow: hidden;
  border: 1px solid rgba(26, 60, 52, 0.10);
  background: rgba(26, 60, 52, 0.03);
}
.card-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 0.85rem;
}
.listing-card {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 0.85rem 0.95rem;
  background: var(--color-surface-elevated);
  box-shadow: var(--shadow-sm);
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}
.meta {
  color: var(--color-muted);
  font-size: 0.85rem;
}
.tiny {
  font-size: 0.78rem;
}
.clamp {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
@media (max-width: 980px) {
  .grid {
    grid-template-columns: 1fr;
  }
  .rent-grid {
    grid-template-columns: 1fr;
  }
}

/* ============================================
   MODAL DIALOG STYLING - PROPER POPUP
   ============================================ */

.mp-dialog {
  position: fixed;
  margin: 20rem 87rem;
  transform: translate(-50%, -50%);
  width: min(40%);
  max-height: 90vh;
  border: none;
  padding: 0;
  background: transparent;
  z-index: var(--z-modal, 1000);
  animation: slideUp 0.3s var(--ease-out, cubic-bezier(0.22, 1, 0.36, 1));
}
@media (max-width: 980px) {
  .mp-dialog {
    width: 90%;
    margin: 12rem 50%;
  background: transparent;
  z-index: var(--z-modal, 1000);
  animation: slideUp 0.3s var(--ease-out, cubic-bezier(0.22, 1, 0.36, 1));
  }
  .mp-dialog::backdrop {
  background: rgba(28, 36, 24, 0.5);
  backdrop-filter: blur(4px);
  animation: fadeIn 0.3s var(--ease-out, cubic-bezier(0.22, 1, 0.36, 1));
}

.mp-dialog[open] {
  display: flex;
  align-items: center;
  justify-content: center;
}
}


.mp-dialog::backdrop {
  background: rgba(28, 36, 24, 0.5);
  backdrop-filter: blur(4px);
  animation: fadeIn 0.3s var(--ease-out, cubic-bezier(0.22, 1, 0.36, 1));
}

.mp-dialog[open] {
  display: flex;
  align-items: center;
  justify-content: center;
}

/* ============================================
   MODAL ANIMATIONS
   ============================================ */

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

@keyframes fadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

/* ============================================
   MODAL PANEL
   ============================================ */

.mp-dialog__panel {
  background: var(--color-surface-elevated);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 1.5rem;
  box-shadow: var(--shadow-lg);
  width: 100%;
  max-height: 85vh;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}

/* ============================================
   MODAL HEADER
   ============================================ */

.mp-dialog__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-lg, 1rem);
  margin-bottom: var(--space-lg, 1rem);
  padding-bottom: var(--space-lg, 1rem);
  border-bottom: 1px solid var(--color-border);
}

.mp-dialog__title {
  font-family: var(--font-display);
  margin: 0;
  font-size: 1.5rem;
  font-weight: 600;
  color: var(--color-text);
}

/* ============================================
   MODAL ACTIONS
   ============================================ */

.mp-dialog__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-md, 0.75rem);
  margin-top: var(--space-lg, 1rem);
  padding-top: var(--space-lg, 1rem);
  border-top: 1px solid var(--color-border);
  justify-content: flex-end;
}

.previews {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0.5rem;
  margin-top: 0.65rem;
}

.previews img {
  width: 100%;
  height: 72px;
  object-fit: cover;
  border-radius: 12px;
  border: 1px solid rgba(26, 60, 52, 0.10);
}

@media (max-width: 980px) {
  .previews {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
</style>

