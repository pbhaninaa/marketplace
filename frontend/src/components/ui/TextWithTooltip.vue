<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';

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

const isMobileView = ref(false);
const showDialog = ref(false);
let mq;
let mqHandler;

onMounted(() => {
  // Mobile UX: show full text, no tooltip (no hover).
  mq = window.matchMedia('(hover: none), (max-width: 640px)');
  const update = () => {
    isMobileView.value = Boolean(mq?.matches);
  };
  mqHandler = update;
  update();
  if (mq?.addEventListener) mq.addEventListener('change', mqHandler);
  else if (mq?.addListener) mq.addListener(mqHandler);
});

onBeforeUnmount(() => {
  if (!mq || !mqHandler) return;
  if (mq.removeEventListener) mq.removeEventListener('change', mqHandler);
  else if (mq.removeListener) mq.removeListener(mqHandler);
});

const isTruncated = computed(() => props.text.length > props.maxLength);
const showThemedTooltip = computed(() => !isMobileView.value && props.text.length > props.maxLength);
const displayText = computed(() => 
  isTruncated.value 
    ? props.text.substring(0, props.maxLength) + '…'
    : props.text
);

function openFullText() {
  if (!isMobileView.value) return;
  if (!isTruncated.value) return;
  showDialog.value = true;
}

function closeFullText() {
  showDialog.value = false;
}
</script>

<template>
  <span class="text-with-tooltip__wrap">
    <component
      :is="tag"
      class="text-with-tooltip"
      :class="{ 'text-with-tooltip--truncated': showThemedTooltip }"
      :data-tooltip="showThemedTooltip ? text : null"
      :aria-label="showThemedTooltip ? text : null"
      :role="isMobileView && isTruncated ? 'button' : null"
      :tabindex="isMobileView && isTruncated ? 0 : null"
      @click="openFullText"
      @keydown.enter.prevent="openFullText"
      @keydown.space.prevent="openFullText"
    >
      {{ displayText }}
    </component>

    <dialog class="twt-dialog" :open="showDialog" @cancel.prevent="closeFullText">
      <div class="twt-dialog__backdrop" @click="closeFullText" />
      <div class="twt-dialog__panel" role="document" aria-label="Full text">
        <button type="button" class="twt-dialog__close" @click="closeFullText" aria-label="Close">
          ✕
        </button>
        <div class="twt-dialog__content">{{ text }}</div>
      </div>
    </dialog>
  </span>
</template>

<style scoped>
.text-with-tooltip__wrap {
  display: inline;
}

.text-with-tooltip {
  cursor: inherit;
}

.text-with-tooltip--truncated {
  position: relative;
  display: inline-block;
  cursor: help;
}

.text-with-tooltip--truncated:hover {
  /* Keep hover tooltip behavior without underlines */
}

.text-with-tooltip--truncated::after {
  content: attr(data-tooltip);
  position: absolute;
  left: 0;
  bottom: calc(100% + 0.45rem);
  z-index: 50;
  max-width: min(28rem, 70vw);
  padding: 0.55rem 0.65rem;
  border-radius: 0.55rem;
  border: 1px solid var(--color-border);
  background: var(--color-surface-elevated);
  color: var(--color-text, #111827);
  box-shadow: var(--shadow-lg);
  font-size: 0.85rem;
  line-height: 1.35;
  white-space: normal;
  word-break: break-word;
  opacity: 0;
  transform: translateY(2px);
  pointer-events: none;
  transition:
    opacity 0.12s ease,
    transform 0.12s ease;
}

.text-with-tooltip--truncated:hover::after,
.text-with-tooltip--truncated:focus-visible::after {
  opacity: 1;
  transform: translateY(0);
}

@media (hover: none) {
  .text-with-tooltip--truncated::after {
    display: none;
  }
}

@media (hover: none) {
  .text-with-tooltip[role='button'] {
    cursor: pointer;
  }
}

.twt-dialog {
  all: revert;
  border: none;
  padding: 0;
  margin: 0;
  background: transparent;
}

.twt-dialog:not([open]) {
  display: none;
}

.twt-dialog::backdrop {
  background: rgba(0, 0, 0, 0.55);
  backdrop-filter: blur(2px);
}

.twt-dialog__backdrop {
  position: fixed;
  inset: 0;
}

.twt-dialog__panel {
  position: fixed;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  width: min(92vw, 36rem);
  max-height: min(70vh, 26rem);
  overflow: auto;
  padding: 0.9rem 0.95rem;
  border-radius: var(--radius-lg, 14px);
  border: 1px solid var(--color-border);
  background: var(--color-surface-elevated);
  color: var(--color-text, #111827);
  box-shadow: var(--shadow-lg);
}

.twt-dialog__close {
  position: sticky;
  top: 0;
  margin-left: auto;
  display: block;
  border: none;
  background: rgba(26, 60, 52, 0.08);
  color: var(--color-canopy, #1a3c34);
  width: 36px;
  height: 36px;
  border-radius: 999px;
  cursor: pointer;
  font-size: 1.1rem;
  line-height: 1;
}

.twt-dialog__content {
  margin-top: 0.6rem;
  font-size: 0.95rem;
  line-height: 1.45;
  white-space: pre-wrap;
  word-break: break-word;
}

@media (hover: hover) {
  .twt-dialog {
    display: none;
  }
}
</style>
