<script setup>
defineProps({
  listingType: { type: String, required: true },
});
</script>

<template>
  <span class="badge" :class="listingType === 'SALE' ? 'badge--sale' : 'badge--rent'">
    {{ listingType === 'SALE' ? 'For sale' : 'For rent' }}
  </span>
</template>

<style scoped>
.badge {
  font-size: 0.65rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.07em;
  padding: 0.28rem 0.55rem;
  border-radius: var(--radius-sm);
  display: inline-block;
  width: fit-content;
  border: 1px solid transparent;
}

.badge--sale {
  background: var(--color-sale-soft);
  color: var(--color-sale);
  border-color: rgba(30, 90, 138, 0.12);
}

.badge--rent {
  background: var(--color-rent-soft);
  color: var(--color-rent);
  border-color: rgba(107, 61, 122, 0.12);
}
</style>
