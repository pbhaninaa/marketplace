<script setup>
defineProps({
  listing: { type: Object, required: true },
});
</script>

<template>
  <div class="price-summary">
    <template v-if="listing.listingType === 'SALE'">
      <strong>R {{ listing.unitPrice }}</strong>
      <span v-if="listing.stockQuantity != null" class="muted small">Stock: {{ listing.stockQuantity }}</span>
    </template>
    <template v-else>
      <div class="rent-grid muted small">
        <span v-if="listing.rentPriceHourly">Hourly R{{ listing.rentPriceHourly }}</span>
        <span v-if="listing.rentPriceDaily">Daily R{{ listing.rentPriceDaily }}</span>
        <span v-if="listing.rentPriceWeekly">Weekly R{{ listing.rentPriceWeekly }}</span>
      </div>
    </template>
  </div>
</template>

<style scoped>
.price-summary {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.price-summary strong {
  font-family: var(--font-display);
  font-size: 1.08rem;
  color: var(--color-canopy);
}

.rent-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem 0.75rem;
}

.muted {
  color: var(--color-muted);
}

.small {
  font-size: 0.82rem;
}
</style>
