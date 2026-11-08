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
  <label class="base-input-wrapper">
    <span v-if="label" class="base-input__label ctl-label">
      {{ label }}<span v-if="required" class="ctl-required" aria-hidden="true">*</span>
    </span>
    <input
      :value="modelValue"
      :type="type"
      :placeholder="placeholder"
      :disabled="disabled"
      :readonly="readonly"
      :required="required"
      :aria-invalid="hasError ? 'true' : undefined"
      :class="['base-input', `base-input--${size}`]"
      @input="emit('update:modelValue', $event.target.value)"
      @blur="emit('blur')"
      @focus="emit('focus')"
      @change="emit('change')"
    />
    <span v-if="error" class="base-input__error ctl-error" role="alert">{{ error }}</span>
  </label>
</template>

<style scoped>
/* Control visuals (border, radius, focus, error, sizes) come from src/styles/forms.css. */
.base-input-wrapper {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  min-width: 0;
}

.base-input {
  width: 100%;
}
</style>
