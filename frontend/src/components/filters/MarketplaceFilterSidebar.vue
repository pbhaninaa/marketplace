<script setup>
import FormField from '../ui/FormField.vue';

const props = defineProps({
  categories: { type: Array, default: () => [] },
  providers: { type: Array, default: () => [] },
  modelValue: { type: Object, required: true },
  hideListingType: { type: Boolean, default: false },
  /** When true, price filters apply to rental rates (hourly/daily/weekly or unit), not only a single sale price. */
  forRent: { type: Boolean, default: false },
});

const emit = defineEmits(['update:modelValue', 'apply']);

function patch(field, raw) {
  const value = raw === undefined || raw === null ? '' : raw;
  emit('update:modelValue', { ...props.modelValue, [field]: value });
}
</script>

<template>
  <aside class="filters">
    <div class="filters__head">
      <h2 class="filters__title">Filters</h2>
      <p class="filters__hint">Refine listings, then apply.</p>
    </div>

    <FormField label="Category">
      <select :value="String(modelValue.categoryId ?? '')" @change="patch('categoryId', $event.target.value)">
        <option value="">All categories</option>
        <option v-for="c in categories" :key="c.id" :value="String(c.id)">{{ c.name }}</option>
      </select>
    </FormField>

    <FormField label="Provider">
      <select :value="String(modelValue.providerId ?? '')" @change="patch('providerId', $event.target.value)">
        <option value="">All providers</option>
        <option v-for="p in providers" :key="p.id" :value="String(p.id)">{{ p.name }}</option>
      </select>
    </FormField>

    <FormField v-if="!hideListingType" label="Listing type">
      <select :value="modelValue.listingType ?? ''" @change="patch('listingType', $event.target.value)">
        <option value="">Sale & rent</option>
        <option value="SALE">For sale</option>
        <option value="RENT">For rent</option>
      </select>
    </FormField>

    <FormField :label="forRent ? 'Min rate (R)' : 'Min price (R)'">
      <input :value="modelValue.minPrice" type="number" placeholder="0" @input="patch('minPrice', $event.target.value)" />
    </FormField>
    <p v-if="forRent" class="filters__price-hint muted small">
      Matches listings where at least one rate (hourly, daily, weekly, or the listed amount) is in range.
    </p>

    <FormField :label="forRent ? 'Max rate (R)' : 'Max price (R)'">
      <input :value="modelValue.maxPrice" type="number" @input="patch('maxPrice', $event.target.value)" />
    </FormField>

    <FormField label="Location" capitalize-first>
      <input
        :value="modelValue.location"
        type="text"
        placeholder="City or region"
        @input="patch('location', $event.target.value)"
      />
    </FormField>

    <FormField label="Search">
      <input
        :value="modelValue.search"
        type="text"
        placeholder="Search title or description"
        @input="patch('search', $event.target.value)"
      />
    </FormField>

    <button type="button" class="btn btn-primary filter-apply" @click="emit('apply')">Apply filters</button>
  </aside>
</template>

<style scoped>
.filters {
  background: var(--color-surface-elevated);
  padding: 1.35rem 1.35rem 1.75rem;
  border-right: 1px solid var(--color-border);
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  box-shadow: inset -1px 0 0 rgba(217, 211, 199, 0.5);
}

.filters__head {
  margin-bottom: 0.35rem;
}

.filters__title {
  font-family: var(--font-display);
  font-size: 1.2rem;
  font-weight: 600;
  margin: 0;
  color: var(--color-canopy);
}

.filters__hint {
  margin: 0.25rem 0 0.75rem;
  font-size: 0.82rem;
  color: var(--color-muted);
  line-height: 1.4;
}

.filters__price-hint {
  margin: -0.35rem 0 0.5rem;
  line-height: 1.35;
}

.filter-apply {
  margin-top: 1.1rem;
  width: 100%;
}

@media (min-width: 901px) {
  .filters {
    position: sticky;
    top: 4.5rem;
    align-self: start;
    max-height: calc(100vh - 5rem);
    overflow-y: auto;
    border-radius: 0 var(--radius-lg) var(--radius-lg) 0;
  }
}
</style>
