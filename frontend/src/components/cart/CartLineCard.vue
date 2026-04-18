<script setup>
import { computed } from 'vue';
import { formatRentalInclusiveRange } from '../../utils/rentalPricing';

const props = defineProps({
  line: { type: Object, required: true },
});

const rentalLabel = computed(() =>
  props.line.rentalStart && props.line.rentalEnd
    ? formatRentalInclusiveRange(props.line.rentalStart, props.line.rentalEnd)
    : '',
);
</script>

<template>
  <article class="cart-line-card">
    <div class="cart-line-card__head">
      <strong class="cart-line-card__title">{{ line.title }}</strong>
      <span class="badge-type">{{ line.listingType }}</span>
    </div>
    <p class="meta">Qty {{ line.quantity }}</p>
    <p v-if="line.rentalStart" class="meta">{{ rentalLabel }}</p>
    <p class="total">R {{ line.lineTotal }}</p>
  </article>
</template>

<style scoped>
.cart-line-card {
  background: var(--color-surface-elevated);
  border-radius: var(--radius-md);
  padding: 1rem 1.1rem;
  border: 1px solid var(--color-border);
  box-shadow: var(--shadow-sm);
}

.cart-line-card__head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 0.5rem;
}

.cart-line-card__title {
  font-family: var(--font-display);
  font-size: 0.98rem;
  font-weight: 600;
  color: var(--color-canopy);
}

.badge-type {
  font-size: 0.65rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--color-canopy-mid);
  background: var(--color-sage-soft);
  padding: 0.2rem 0.45rem;
  border-radius: var(--radius-sm);
  flex-shrink: 0;
}

.meta {
  margin: 0.35rem 0 0;
  font-size: 0.82rem;
  color: var(--color-muted);
}

.total {
  margin: 0.55rem 0 0;
  font-family: var(--font-display);
  font-weight: 700;
  color: var(--color-canopy);
  font-size: 1.08rem;
}
</style>
