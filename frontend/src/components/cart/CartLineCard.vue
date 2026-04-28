<script setup>
import { computed } from 'vue';
import TextWithTooltip from '../ui/TextWithTooltip.vue';
import { formatRentalInclusiveRange } from '../../utils/rentalPricing';

const props = defineProps({
  line: { type: Object, required: true },
});

const emit = defineEmits(['update-quantity', 'remove-line', 'show-limit-warning']);

function handleDecrement() {
  if (props.line.quantity <= 1) {
    emit('show-limit-warning', {
      type: 'min',
      message: 'Quantity cannot be less than 1. Use the delete button to remove this item.',
    });
    return;
  }
  emit('update-quantity', { id: props.line.lineId, quantity: props.line.quantity - 1 });
}

function handleIncrement() {
  const atMaxStock = props.line.listingType === 'SALE' &&
                     props.line.availableStock !== null &&
                     props.line.quantity >= props.line.availableStock;

  if (atMaxStock) {
    emit('show-limit-warning', {
      type: 'max',
      message: `Maximum available stock is ${props.line.availableStock} units. You cannot add more than what's in stock.`,
    });
    return;
  }
  emit('update-quantity', { id: props.line.lineId, quantity: props.line.quantity + 1 });
}

const rentalLabel = computed(() =>
  props.line.rentalStart && props.line.rentalEnd
    ? formatRentalInclusiveRange(props.line.rentalStart, props.line.rentalEnd)
    : '',
);
</script>

<template>
  <article class="cart-line-card">
    <div class="cart-line-card__head">
      <TextWithTooltip :text="line.title" tag="strong" class="cart-line-card__title" />
      <span class="badge-type">{{ line.listingType }}</span>
    </div>

    <!-- Quantity controls -->
    <div class="qty-controls">
      <span class="meta-label">Quantity:</span>
      <div class="qty-box">
        <button
          class="icon-btn"
          @click="handleDecrement"
          :class="{ 'at-limit': line.quantity <= 1 }"
          title="Decrease quantity"
        >
          <span class="material-icons">remove</span>
        </button>

        <span class="qty-value">{{ line.quantity }}</span>

        <button
          class="icon-btn"
          @click="handleIncrement"
          :class="{ 'at-limit': line.listingType === 'SALE' && line.availableStock !== null && line.quantity >= line.availableStock }"
          :title="line.availableStock !== null && line.quantity >= line.availableStock ? 'Maximum stock reached' : 'Increase quantity'"
        >
          <span class="material-icons">add</span>
        </button>
      </div>
    </div>

    <!-- Stock indicator for SALE items -->
    <p v-if="line.listingType === 'SALE' && line.availableStock !== null" class="stock-info">
      <span v-if="line.quantity >= line.availableStock" class="stock-max">
        <span class="material-icons stock-icon">inventory_2</span>
        Maximum stock reached
      </span>
      <span v-else class="stock-available">
        <span class="material-icons stock-icon">inventory</span>
        {{ line.availableStock }} available
      </span>
    </p>

    <p v-if="line.rentalStart" class="meta">{{ rentalLabel }}</p>

    <div class="cart-line-card__footer">
      <p class="total">R {{ line.lineTotal }}</p>

      <button
        class="icon-btn danger"
        @click="$emit('remove-line', line.lineId)"
        title="Remove from cart"
      >
        <span class="material-icons">delete</span>
      </button>
    </div>
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
  margin: 0;
  font-family: var(--font-display);
  font-weight: 700;
  color: var(--color-canopy);
  font-size: 1.08rem;
}

/* Quantity controls */
.qty-controls {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-top: 0.6rem;
}

.meta-label {
  font-size: 0.82rem;
  color: var(--color-muted);
  font-weight: 500;
}

.qty-box {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: 0.15rem 0.3rem;
}

.qty-value {
  min-width: 24px;
  text-align: center;
  font-weight: 600;
  font-size: 0.9rem;
}

/* Icon buttons */
.icon-btn {
  border: none;
  background: transparent;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 4px;
  color: var(--color-canopy);
  transition: color 0.2s, opacity 0.2s;
}

.icon-btn:hover:not(.at-limit) {
  color: var(--color-canopy-dark);
}

.icon-btn .material-icons {
  font-size: 18px;
}

.icon-btn.at-limit {
  opacity: 0.4;
  cursor: not-allowed;
}

.icon-btn.at-limit:hover {
  color: var(--color-earth);
}

.icon-btn.danger {
  color: #d32f2f;
}

.icon-btn.danger:hover {
  color: #b71c1c;
}

/* Footer with total and delete */
.cart-line-card__footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 0.65rem;
  padding-top: 0.5rem;
  border-top: 1px solid var(--color-border);
}

/* Stock info */
.stock-info {
  margin: 0.5rem 0 0;
  font-size: 0.78rem;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 0.3rem;
}

.stock-info .stock-icon {
  font-size: 16px;
  vertical-align: middle;
}

.stock-available {
  color: var(--color-muted);
  display: flex;
  align-items: center;
  gap: 0.3rem;
}

.stock-max {
  color: var(--color-earth);
  display: flex;
  align-items: center;
  gap: 0.3rem;
}
</style>
