<script setup>
import DataTableShell from '../ui/DataTableShell.vue';
import { formatRentalInclusiveRange } from '../../utils/rentalPricing';

defineProps({
  lines: { type: Array, default: () => [] },
});

/* events for parent */
defineEmits(['update-quantity', 'remove-line']);

function rentalWindow(line) {
  if (!line.rentalStart || !line.rentalEnd) return '—';
  return formatRentalInclusiveRange(line.rentalStart, line.rentalEnd);
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
              @click="$emit('update-quantity', { id: line.lineId, quantity: line.quantity - 1 })"
              :disabled="line.quantity <= 1"
            >
              <span class="material-icons">remove</span>
            </button>

            <!-- value -->
            <span class="qty-value">{{ line.quantity }}</span>

            <!-- plus -->
            <button
              class="icon-btn"
              @click="$emit('update-quantity', { id: line.lineId, quantity: line.quantity + 1 })"
            >
              <span class="material-icons">add</span>
            </button>

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

/* disabled state */
.icon-btn:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

/* delete style */
.icon-btn.danger {
  color: #d32f2f;
}
</style>