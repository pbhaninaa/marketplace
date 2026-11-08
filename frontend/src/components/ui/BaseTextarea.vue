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
  <label class="base-textarea-wrapper">
    <span v-if="label" class="base-textarea__label ctl-label">
      {{ label }}<span v-if="required" class="ctl-required" aria-hidden="true">*</span>
    </span>
    <textarea
      :value="modelValue"
      :placeholder="placeholder"
      :disabled="disabled"
      :readonly="readonly"
      :required="required"
      :rows="rows"
      :aria-invalid="hasError ? 'true' : undefined"
      class="base-textarea"
      @input="emit('update:modelValue', $event.target.value)"
      @blur="emit('blur')"
      @focus="emit('focus')"
    />
    <span v-if="error" class="base-textarea__error ctl-error" role="alert">{{ error }}</span>
  </label>
</template>

<style scoped>
/* Control visuals (border, radius, focus, error, sizes) come from src/styles/forms.css. */
.base-textarea-wrapper {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  min-width: 0;
}

.base-textarea {
  width: 100%;
}
</style>
