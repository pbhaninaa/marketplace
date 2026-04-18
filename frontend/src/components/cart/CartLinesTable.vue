<script setup>
import DataTableShell from '../ui/DataTableShell.vue';
import { formatRentalInclusiveRange } from '../../utils/rentalPricing';

defineProps({
  lines: { type: Array, default: () => [] },
});

/* events for parent */
const emit = defineEmits(['update-quantity', 'remove-line', 'show-limit-warning']);

function rentalWindow(line) {
  if (!line.rentalStart || !line.rentalEnd) return '—';
  return formatRentalInclusiveRange(line.rentalStart, line.rentalEnd);
}

function handleDecrement(line) {
  if (line.quantity <= 1) {
    emit('show-limit-warning', {
      type: 'min',
      message: 'Quantity cannot be less than 1. Use the delete button to remove this item.',
    });
    return;
  }
  emit('update-quantity', { id: line.lineId, quantity: line.quantity - 1 });
}

function handleIncrement(line) {
  const atMaxStock = line.listingType === 'SALE' &&
                     line.availableStock !== null &&
                     line.quantity >= line.availableStock;

  if (atMaxStock) {
    emit('show-limit-warning', {
      type: 'max',
      message: `Maximum available stock is ${line.availableStock} units. You cannot add more than what's in stock.`,
    });
    return;
  }
  emit('update-quantity', { id: line.lineId, quantity: line.quantity + 1 });
}
</script>

<template>
  <DataTableShell caption="Shopping cart lines">
    <thead>
      <tr>
        <th>Item</th>
        <th>Type</th>
        <th>Qty</th>
        <th>Rental window</th>
        <th class="col-num">Line total</th>
        <th></th>
      </tr>
    </thead>

    <tbody>
      <tr v-for="line in lines" :key="line.lineId">

        <!-- ITEM -->
        <td><strong>{{ line.title }}</strong></td>

        <!-- TYPE -->
        <td>{{ line.listingType }}</td>

        <!-- QUANTITY WITH ICONS -->
        <td>
          <div class="qty-box">

            <!-- minus -->
            <button
              class="icon-btn"
              @click="handleDecrement(line)"
              :class="{ 'at-limit': line.quantity <= 1 }"
              title="Decrease quantity"
            >
              <span class="material-icons">remove</span>
            </button>

            <!-- value -->
            <span class="qty-value">{{ line.quantity }}</span>

            <!-- plus -->
            <button
              class="icon-btn"
              @click="handleIncrement(line)"
              :class="{ 'at-limit': line.listingType === 'SALE' && line.availableStock !== null && line.quantity >= line.availableStock }"
              :title="line.availableStock !== null && line.quantity >= line.availableStock ? 'Maximum stock reached' : 'Increase quantity'"
            >
              <span class="material-icons">add</span>
            </button>

          </div>
          <!-- Stock indicator for SALE items -->
          <div v-if="line.listingType === 'SALE' && line.availableStock !== null" class="stock-hint">
            <span v-if="line.quantity >= line.availableStock" class="stock-max">Max stock</span>
            <span v-else class="stock-available">{{ line.availableStock }} available</span>
          </div>
        </td>

        <!-- RENTAL WINDOW -->
        <td>
          <span v-if="line.rentalStart" class="muted small">
            {{ rentalWindow(line) }}
          </span>
          <span v-else class="muted small">—</span>
        </td>

        <!-- TOTAL -->
        <td class="col-num">
          <strong>R {{ line.lineTotal }}</strong>
        </td>

        <!-- DELETE -->
        <td>
          <button
            class="icon-btn danger"
            @click="$emit('remove-line', line.lineId)"
          >
            <span class="material-icons">delete</span>
          </button>
        </td>

      </tr>
    </tbody>
  </DataTableShell>
</template>

<style scoped>
.col-num {
  text-align: right;
  white-space: nowrap;
}

.muted {
  color: var(--muted);
}

.small {
  font-size: 0.82rem;
}

/* QTY LAYOUT */
.qty-box {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.qty-value {
  min-width: 20px;
  text-align: center;
}

/* ICON BUTTONS */
.icon-btn {
  border: none;
  background: transparent;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 4px;
}

.icon-btn .material-icons {
  font-size: 20px;
}

/* at-limit state */
.icon-btn.at-limit {
  opacity: 0.4;
  cursor: not-allowed;
}

.icon-btn.at-limit:hover {
  color: var(--color-earth);
}

/* delete style */
.icon-btn.danger {
  color: #d32f2f;
}

/* Stock hint */
.stock-hint {
  margin-top: 0.25rem;
  font-size: 0.72rem;
  font-weight: 500;
}

.stock-available {
  color: var(--color-muted);
}

.stock-max {
  color: var(--color-earth);
  font-weight: 600;
}
</style>