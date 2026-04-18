<script setup>
import { computed } from 'vue';

const props = defineProps({
  modelValue: {
    type: Object,
    required: true,
  },
});

const emit = defineEmits(['update:modelValue']);

const preset = computed({
  get: () => props.modelValue.preset || 'LAST_7',
  set: (v) => emit('update:modelValue', { ...props.modelValue, preset: v }),
});

const from = computed({
  get: () => props.modelValue.from || '',
  set: (v) => emit('update:modelValue', { ...props.modelValue, from: v }),
});

const to = computed({
  get: () => props.modelValue.to || '',
  set: (v) => emit('update:modelValue', { ...props.modelValue, to: v }),
});
</script>

<template>
  <div class="drf">
    <div class="drf__row">
      <label class="drf__label">Date range</label>
      <select v-model="preset" class="drf__select">
        <option value="TODAY">Today</option>
        <option value="LAST_7">Last 7 days</option>
        <option value="LAST_30">Last 30 days</option>
        <option value="CUSTOM">Custom</option>
      </select>
    </div>

    <div v-if="preset === 'CUSTOM'" class="drf__row drf__row--custom">
      <div class="drf__field">
        <label class="drf__label">From</label>
        <input v-model="from" type="date" class="drf__input" />
      </div>
      <div class="drf__field">
        <label class="drf__label">To</label>
        <input v-model="to" type="date" class="drf__input" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.drf {
  display: flex;
  flex-direction: column;
  gap: 0.65rem;
  padding: 0.75rem 0.85rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface-elevated);
  box-shadow: var(--shadow-sm);
}
.drf__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  flex-wrap: wrap;
}
.drf__row--custom {
  justify-content: flex-start;
}
.drf__field {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}
.drf__label {
  font-size: 0.85rem;
  color: var(--color-muted);
}
.drf__select,
.drf__input {
  min-height: 38px;
}
</style>

