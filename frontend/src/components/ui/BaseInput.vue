<script setup>
import { computed } from 'vue';

const props = defineProps({
  modelValue: {
    type: [String, Number],
    default: '',
  },
  type: {
    type: String,
    default: 'text',
  },
  placeholder: {
    type: String,
    default: '',
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
  readonly: {
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

const emit = defineEmits(['update:modelValue', 'blur', 'focus', 'change']);

const hasError = computed(() => !!props.error);
</script>

<template>
  <div class="base-input-wrapper">
    <label v-if="label" class="base-input__label">
      {{ label }}
      <span v-if="required" class="base-input__required">*</span>
    </label>
    <input
      :value="modelValue"
      :type="type"
      :placeholder="placeholder"
      :disabled="disabled"
      :readonly="readonly"
      :required="required"
      :class="[
        'base-input',
        `base-input--${size}`,
        {
          'base-input--error': hasError,
          'base-input--disabled': disabled,
        },
      ]"
      @input="emit('update:modelValue', $event.target.value)"
      @blur="emit('blur')"
      @focus="emit('focus')"
      @change="emit('change')"
    />
    <span v-if="error" class="base-input__error">{{ error }}</span>
  </div>
</template>

<style scoped>
.base-input-wrapper {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.base-input__label {
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--color-text);
}

.base-input__required {
  color: var(--color-danger-text);
  margin-left: 0.2rem;
}

.base-input {
  padding: 0.6rem 0.8rem;
  font-size: 0.95rem;
  font-family: var(--font-ui);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface-elevated);
  color: var(--color-text);
  transition: all var(--transition-fast);
}

/* ==================== SIZES ==================== */
.base-input--sm {
  padding: 0.4rem 0.6rem;
  font-size: 0.85rem;
}

.base-input--lg {
  padding: 0.8rem 1rem;
  font-size: 1.05rem;
}

/* ==================== STATES ==================== */
.base-input:focus {
  outline: none;
  border-color: var(--color-canopy);
  box-shadow: 0 0 0 3px rgba(26, 60, 52, 0.1);
  background: var(--color-surface-elevated);
}

.base-input--error {
  border-color: var(--color-danger-text);
}

.base-input--error:focus {
  box-shadow: 0 0 0 3px rgba(139, 44, 31, 0.1);
}

.base-input:disabled,
.base-input--disabled {
  background: var(--color-bg);
  color: var(--color-muted);
  cursor: not-allowed;
}

.base-input__error {
  font-size: 0.8rem;
  color: var(--color-danger-text);
  font-weight: 500;
}

/* Placeholder styles */
.base-input::placeholder {
  color: var(--color-muted);
}
</style>
