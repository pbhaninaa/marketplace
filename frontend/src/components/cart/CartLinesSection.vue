<script setup>
import { RouterLink } from 'vue-router';
import ResponsiveRecordShell from '../layout/ResponsiveRecordShell.vue';
import CartLinesTable from './CartLinesTable.vue';
import CartLineCard from './CartLineCard.vue';

defineProps({
  lines: { type: Array, default: () => [] },
  estimatedTotal: { type: [String, Number], default: '0.00' },
  emptyMessage: { type: String, default: 'Cart is empty.' },
});

/* ✅ UPDATED emits */
defineEmits(['clear', 'update-quantity', 'remove-line', 'show-limit-warning']);
</script>

<template>
  <div class="cart-lines-section">
    <p v-if="!lines.length" class="muted empty">{{ emptyMessage }}</p>

    <template v-else>
      <ResponsiveRecordShell desktop-label="Cart line items">
        
        <!-- ✅ DESKTOP TABLE -->
        <template #desktop>
          <CartLinesTable
            :lines="lines"
            @update-quantity="$emit('update-quantity', $event)"
            @remove-line="$emit('remove-line', $event)"
            @show-limit-warning="$emit('show-limit-warning', $event)"
          />
        </template>

        <!-- ✅ MOBILE CARDS -->
        <template #mobile>
          <div class="cards-stack">
            <CartLineCard
              v-for="line in lines"
              :key="line.lineId"
              :line="line"
              @update-quantity="$emit('update-quantity', $event)"
              @remove-line="$emit('remove-line', $event)"
              @show-limit-warning="$emit('show-limit-warning', $event)"
            />
          </div>
        </template>

      </ResponsiveRecordShell>

      <div class="cart-total">
        Estimated total: <strong>R {{ estimatedTotal }}</strong>
      </div>

      <div class="cart-actions">
        <button
          type="button"
          class="btn btn-ghost clear-btn"
          @click="$emit('clear')"
        >
          <span class="material-icons">delete_sweep</span>
          Clear cart
        </button>
        <button
  type="button"
  class="btn btn-primary checkout-btn"
  @click="$emit('toggle-checkout', true)"
>
  Proceed to Checkout
</button>
      </div>
      
    </template>
  </div>
</template>

<style scoped>
.empty {
  margin: 0 0 0.5rem;
}

.cards-stack {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.cart-total {
  margin-top: 1rem;
  font-size: 1rem;
}





.cart-actions {
  display: flex;
  gap: 1rem;
  margin-top: 1rem;
  flex-wrap: wrap;
}


.btn{
  max-width: fit-content;
  flex: 1;
  text-decoration: none;
  display: flex;
  align-items: center;
  justify-content: center;
}
.muted {
  color: var(--color-muted);
}

.cart-total strong {
  font-family: var(--font-display);
  color: var(--color-canopy);
}
</style>