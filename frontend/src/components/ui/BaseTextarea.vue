<script setup>
import { computed } from 'vue';

const props = defineProps({
  modelValue: {
    type: String,
    default: '',
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
  rows: {
    type: Number,
    default: 4,
  },
});

const emit = defineEmits(['update:modelValue', 'blur', 'focus']);

const hasError = computed(() => !!props.error);
</script>

<template>
  <div class="base-textarea-wrapper">
    <label v-if="label" class="base-textarea__label">
      {{ label }}
      <span v-if="required" class="base-textarea__required">*</span>
    </label>
    <textarea
      :value="modelValue"
      :placeholder="placeholder"
      :disabled="disabled"
      :readonly="readonly"
      :required="required"
      :rows="rows"
      :class="[
        'base-textarea',
        {
          'base-textarea--error': hasError,
          'base-textarea--disabled': disabled,
        },
      ]"
      @input="emit('update:modelValue', $event.target.value)"
      @blur="emit('blur')"
      @focus="emit('focus')"
    />
    <span v-if="error" class="base-textarea__error">{{ error }}</span>
  </div>
</template>

<style scoped>
.base-textarea-wrapper {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.base-textarea__label {
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--color-text);
}

.base-textarea__required {
  color: var(--color-danger-text);
  margin-left: 0.2rem;
}

.base-textarea {
  padding: 0.8rem;
  font-size: 0.95rem;
  font-family: var(--font-ui);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface-elevated);
  color: var(--color-text);
  resize: vertical;
  transition: all var(--transition-fast);
}

.base-textarea:focus {
  outline: none;
  border-color: var(--color-canopy);
  box-shadow: 0 0 0 3px rgba(26, 60, 52, 0.1);
  background: var(--color-surface-elevated);
}

.base-textarea--error {
  border-color: var(--color-danger-text);
}

.base-textarea--error:focus {
  box-shadow: 0 0 0 3px rgba(139, 44, 31, 0.1);
}

.base-textarea:disabled,
.base-textarea--disabled {
  background: var(--color-bg);
  color: var(--color-muted);
  cursor: not-allowed;
}

.base-textarea__error {
  font-size: 0.8rem;
  color: var(--color-danger-text);
  font-weight: 500;
}

.base-textarea::placeholder {
  color: var(--color-muted);
}
</style>
