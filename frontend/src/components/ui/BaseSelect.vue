<script setup>
import { computed } from 'vue';

const props = defineProps({
  modelValue: {
    type: [String, Number],
    default: '',
  },
  options: {
    type: Array,
    required: true,
    // Each option: { value: 'SALE', label: 'For Sale' }
  },
  label: {
    type: String,
    default: '',
  },
  error: {
    type: String,
    default: '',
  },
  disabled: {
    type: Boolean,
    default: false,
  },
  required: {
    type: Boolean,
    default: false,
  },
  size: {
    type: String,
    default: 'md',
    validator: (v) => ['sm', 'md', 'lg'].includes(v),
  },
});

const emit = defineEmits(['update:modelValue', 'change']);

const hasError = computed(() => !!props.error);
</script>

<template>
  <div class="base-select-wrapper">
    <label v-if="label" class="base-select__label">
      {{ label }}
      <span v-if="required" class="base-select__required">*</span>
    </label>
    <select
      :value="modelValue"
      :disabled="disabled"
      :required="required"
      :class="[
        'base-select',
        `base-select--${size}`,
        {
          'base-select--error': hasError,
          'base-select--disabled': disabled,
        },
      ]"
      @change="emit('update:modelValue', $event.target.value); emit('change')"
    >
      <option value="">— Select an option —</option>
      <option v-for="opt in options" :key="opt.value" :value="opt.value">
        {{ opt.label }}
      </option>
    </select>
    <span v-if="error" class="base-select__error">{{ error }}</span>
  </div>
</template>

<style scoped>
.base-select-wrapper {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.base-select__label {
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--color-text);
}

.base-select__required {
  color: var(--color-danger-text);
  margin-left: 0.2rem;
}

.base-select {
  padding: 0.6rem 0.8rem;
  font-size: 0.95rem;
  font-family: var(--font-ui);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface-elevated);
  color: var(--color-text);
  cursor: pointer;
  transition: all var(--transition-fast);
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 12 12'%3E%3Cpath fill='%231a3c34' d='M6 9L1 4h10z'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 0.6rem center;
  padding-right: 2rem;
}

/* ==================== SIZES ==================== */
.base-select--sm {
  padding: 0.4rem 0.6rem;
  font-size: 0.85rem;
  padding-right: 1.8rem;
}

.base-select--lg {
  padding: 0.8rem 1rem;
  font-size: 1.05rem;
  padding-right: 2.4rem;
}

/* ==================== STATES ==================== */
.base-select:focus {
  outline: none;
  border-color: var(--color-canopy);
  box-shadow: 0 0 0 3px rgba(26, 60, 52, 0.1);
}

.base-select--error {
  border-color: var(--color-danger-text);
}

.base-select--error:focus {
  box-shadow: 0 0 0 3px rgba(139, 44, 31, 0.1);
}

.base-select:disabled,
.base-select--disabled {
  background: var(--color-bg);
  color: var(--color-muted);
  cursor: not-allowed;
}

.base-select__error {
  font-size: 0.8rem;
  color: var(--color-danger-text);
  font-weight: 500;
}
</style>
