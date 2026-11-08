<script setup>
import { computed } from 'vue';
import {
  DATE_PRESETS,
  ORDER_STATUS_OPTIONS,
  PAYMENT_KIND_OPTIONS,
  PAYMENT_METHOD_OPTIONS,
  PAYMENT_STATUS_OPTIONS,
} from '../../utils/historyFilters';

const props = defineProps({
  modelValue: { type: Object, required: true },
  tab: { type: String, default: 'orders' },
  /** [{ id, name }] — shows the provider filter (admin). */
  providers: { type: Array, default: null },
  activeCount: { type: Number, default: 0 },
});
const emit = defineEmits(['update:modelValue', 'reset']);

function set(key, value) {
  emit('update:modelValue', { ...props.modelValue, [key]: value });
}

const showCustom = computed(() => props.modelValue.preset === 'CUSTOM');
</script>

<template>
  <div class="hfb" role="search">
    <label class="hfb__field hfb__field--search">
      <span class="hfb__label">Search</span>
      <input
        type="search"
        :value="modelValue.q"
        placeholder="Customer, email, phone or #id"
        @input="set('q', $event.target.value)"
      />
    </label>

    <label v-if="tab === 'orders'" class="hfb__field">
      <span class="hfb__label">Order status</span>
      <select :value="modelValue.status" @change="set('status', $event.target.value)">
        <option value="">All statuses</option>
        <option v-for="o in ORDER_STATUS_OPTIONS" :key="o.value" :value="o.value">{{ o.label }}</option>
      </select>
    </label>

    <label class="hfb__field">
      <span class="hfb__label">Payment</span>
      <select :value="modelValue.paymentStatus" @change="set('paymentStatus', $event.target.value)">
        <option value="">Paid &amp; pending</option>
        <option v-for="o in PAYMENT_STATUS_OPTIONS" :key="o.value" :value="o.value">{{ o.label }}</option>
      </select>
    </label>

    <label class="hfb__field">
      <span class="hfb__label">Method</span>
      <select :value="modelValue.method" @change="set('method', $event.target.value)">
        <option value="">All methods</option>
        <option v-for="o in PAYMENT_METHOD_OPTIONS" :key="o.value" :value="o.value">{{ o.label }}</option>
      </select>
    </label>

    <label v-if="tab === 'payments'" class="hfb__field">
      <span class="hfb__label">Type</span>
      <select :value="modelValue.kind" @change="set('kind', $event.target.value)">
        <option value="">Purchases &amp; rentals</option>
        <option v-for="o in PAYMENT_KIND_OPTIONS" :key="o.value" :value="o.value">{{ o.label }}</option>
      </select>
    </label>

    <label v-if="providers" class="hfb__field">
      <span class="hfb__label">Provider</span>
      <select :value="modelValue.providerId" @change="set('providerId', $event.target.value)">
        <option value="">All providers</option>
        <option v-for="p in providers" :key="p.id" :value="String(p.id)">{{ p.name }}</option>
      </select>
    </label>

    <label class="hfb__field">
      <span class="hfb__label">Date</span>
      <select :value="modelValue.preset" @change="set('preset', $event.target.value)">
        <option v-for="o in DATE_PRESETS" :key="o.value" :value="o.value">{{ o.label }}</option>
      </select>
    </label>

    <template v-if="showCustom">
      <label class="hfb__field">
        <span class="hfb__label">From</span>
        <input type="date" :value="modelValue.from" @change="set('from', $event.target.value)" />
      </label>
      <label class="hfb__field">
        <span class="hfb__label">To</span>
        <input type="date" :value="modelValue.to" @change="set('to', $event.target.value)" />
      </label>
    </template>

    <div class="hfb__actions">
      <button type="button" class="btn btn-ghost btn-xs" :disabled="activeCount === 0" @click="emit('reset')">
        Clear filters<span v-if="activeCount"> ({{ activeCount }})</span>
      </button>
    </div>
  </div>
</template>

<style scoped>
.hfb {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 0.65rem 0.85rem;
  padding: 0.85rem 0.95rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface-elevated);
  box-shadow: var(--shadow-sm);
  margin-bottom: 1rem;
}
.hfb__field {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  min-width: 140px;
  flex: 0 1 auto;
}
.hfb__field--search {
  flex: 1 1 220px;
}
/* Same label as every other form (.ctl-label in src/styles/forms.css); controls use the shared style. */
.hfb__label {
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--color-text);
}
.hfb select,
.hfb input {
  width: 100%;
}
.hfb__actions {
  display: flex;
  align-items: center;
  margin-left: auto;
}
@media (max-width: 640px) {
  .hfb__field {
    flex: 1 1 100%;
  }
  .hfb__actions {
    margin-left: 0;
  }
}
</style>
