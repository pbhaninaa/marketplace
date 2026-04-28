<script setup>
import { computed } from 'vue';

const props = defineProps({
  text: {
    type: String,
    required: true,
  },
  maxLength: {
    type: Number,
    default: 50,
  },
  tag: {
    type: String,
    default: 'span',
  },
});

const isTruncated = computed(() => props.text.length > props.maxLength);
const displayText = computed(() => 
  isTruncated.value 
    ? props.text.substring(0, props.maxLength) + '…'
    : props.text
);
</script>

<template>
  <component
    :is="tag"
    :title="isTruncated ? text : ''"
    class="text-with-tooltip"
    :class="{ 'text-with-tooltip--truncated': isTruncated }"
  >
    {{ displayText }}
  </component>
</template>

<style scoped>
.text-with-tooltip {
  cursor: inherit;
}

.text-with-tooltip--truncated {
  cursor: help;
  border-bottom: 1px dotted rgba(0, 0, 0, 0.2);
}

.text-with-tooltip--truncated:hover {
  border-bottom-color: var(--color-sage);
}
</style>
