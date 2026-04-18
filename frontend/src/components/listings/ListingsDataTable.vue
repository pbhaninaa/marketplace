<script setup>
import DataTableShell from '../ui/DataTableShell.vue';
import ListingTypeBadge from './ListingTypeBadge.vue';
import ListingPriceSummary from './ListingPriceSummary.vue';

defineProps({
  listings: { type: Array, default: () => [] },
  /** (listing) => boolean */
  isGreyed: { type: Function, required: true },
  /** id -> { start, end } */
  rentDefaults: { type: Object, default: () => ({}) },
});

const emit = defineEmits(['add-to-cart', 'reset-rent-dates', 'update-rent-default']);
</script>

<template>
  <DataTableShell caption="Marketplace listings">
    <thead>
      <tr>
        <th>Type</th>
        <th>Title</th>
        <th>Provider</th>
        <th>Location</th>
        <th>Pricing</th>
        <th>Rental window</th>
        <th class="col-actions">Actions</th>
      </tr>
    </thead>
    <tbody>
      <tr
        v-for="item in listings"
        :key="item.id"
        :class="{ 'is-greyed': isGreyed(item) }"
      >
        <td><ListingTypeBadge :listing-type="item.listingType" /></td>
        <td>
          <div class="cell-stack">
            <strong>{{ item.title }}</strong>
            <span class="muted small text-clamp">{{ item.description }}</span>
          </div>
        </td>
        <td>{{ item.providerName }}</td>
        <td>{{ item.providerLocation }}</td>
        <td><ListingPriceSummary :listing="item" /></td>
        <td>
          <template v-if="item.listingType === 'RENT' && rentDefaults[item.id]">
            <div class="cell-stack rent-cell">
              <input
                :value="rentDefaults[item.id].start"
                type="datetime-local"
                class="field-inline"
                @input="
                  emit('update-rent-default', {
                    id: item.id,
                    start: $event.target.value,
                    end: rentDefaults[item.id].end,
                  })
                "
              />
              <input
                :value="rentDefaults[item.id].end"
                type="datetime-local"
                class="field-inline"
                @input="
                  emit('update-rent-default', {
                    id: item.id,
                    start: rentDefaults[item.id].start,
                    end: $event.target.value,
                  })
                "
              />
              <button type="button" class="linkish" @click="emit('reset-rent-dates', item)">Reset dates</button>
            </div>
          </template>
          <span v-else class="muted small">—</span>
        </td>
        <td class="cell-actions">
          <button
            type="button"
            class="btn btn-primary btn-compact"
            :disabled="isGreyed(item)"
            @click="emit('add-to-cart', item)"
          >
            Add to cart
          </button>
        </td>
      </tr>
    </tbody>
  </DataTableShell>
</template>

<style scoped>
.col-actions {
  text-align: right;
}

.text-clamp {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  max-width: 220px;
}

.muted {
  color: var(--color-muted);
}

.small {
  font-size: 0.78rem;
}

.rent-cell {
  min-width: 200px;
}

.field-inline {
  width: 100%;
  max-width: 220px;
  padding: 0.4rem 0.55rem;
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-border);
  font: inherit;
  font-size: 0.82rem;
  background: var(--color-surface);
}

.field-inline:focus {
  outline: none;
  border-color: var(--color-sage);
  box-shadow: 0 0 0 2px rgba(61, 122, 102, 0.12);
}

.linkish {
  border: none;
  background: none;
  color: var(--color-canopy-mid);
  cursor: pointer;
  font-size: 0.76rem;
  font-weight: 600;
  text-align: left;
  padding: 0;
}

.linkish:hover {
  color: var(--color-sage);
  text-decoration: underline;
}

.btn-compact {
  padding: 0.42rem 0.85rem;
  font-size: 0.84rem;
  border-radius: var(--radius-sm);
}
</style>
