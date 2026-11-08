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
  <label class="base-select-wrapper">
    <span v-if="label" class="base-select__label ctl-label">
      {{ label }}<span v-if="required" class="ctl-required" aria-hidden="true">*</span>
    </span>
    <AppSelect
      :value="modelValue"
      :disabled="disabled"
      :required="required"
      :aria-invalid="hasError ? 'true' : undefined"
      :class="['base-select', `base-select--${size}`]"
      @change="emit('update:modelValue', $event.target.value); emit('change')"
    >
      <option value="">Select an option</option>
      <option v-for="opt in options" :key="opt.value" :value="opt.value">
        {{ opt.label }}
      </option>
    </AppSelect>
    <span v-if="error" class="base-select__error ctl-error" role="alert">{{ error }}</span>
  </label>
</template>

<style scoped>
/* Control visuals (border, radius, focus, error, sizes) come from src/styles/forms.css. */
.base-select-wrapper {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  min-width: 0;
}

.base-select {
  width: 100%;
}
</style>
