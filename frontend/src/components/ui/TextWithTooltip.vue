<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';

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
const showHoverTip = ref(false);
const tipStyle = ref({});
const triggerEl = ref(null);
let mq;
let mqHandler;

onMounted(() => {
  // Mobile UX: show full text in a dialog (no hover).
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
  isTruncated.value ? props.text.substring(0, props.maxLength) + '…' : props.text,
);

function resolveTriggerEl() {
  const el = triggerEl.value;
  if (!el) return null;
  return el instanceof HTMLElement ? el : el.$el instanceof HTMLElement ? el.$el : null;
}

function positionTip() {
  const el = resolveTriggerEl();
  if (!el) return;
  const rect = el.getBoundingClientRect();
  const maxWidth = Math.min(448, window.innerWidth * 0.7);
  let left = rect.left;
  if (left + maxWidth > window.innerWidth - 12) {
    left = Math.max(12, window.innerWidth - maxWidth - 12);
  }
  const spaceAbove = rect.top;
  const preferAbove = spaceAbove > 120;
  tipStyle.value = preferAbove
    ? {
        left: `${left}px`,
        bottom: `${window.innerHeight - rect.top + 8}px`,
        top: 'auto',
        maxWidth: `${maxWidth}px`,
      }
    : {
        left: `${left}px`,
        top: `${rect.bottom + 8}px`,
        bottom: 'auto',
        maxWidth: `${maxWidth}px`,
      };
}

async function onMouseEnter() {
  if (!showThemedTooltip.value) return;
  showHoverTip.value = true;
  await nextTick();
  positionTip();
}

function onMouseLeave() {
  showHoverTip.value = false;
}

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
      ref="triggerEl"
      class="text-with-tooltip"
      :class="{ 'text-with-tooltip--truncated': showThemedTooltip }"
      :aria-label="showThemedTooltip ? text : null"
      :role="isMobileView && isTruncated ? 'button' : null"
      :tabindex="isMobileView && isTruncated ? 0 : null"
      @mouseenter="onMouseEnter"
      @mouseleave="onMouseLeave"
      @focus="onMouseEnter"
      @blur="onMouseLeave"
      @click="openFullText"
      @keydown.enter.prevent="openFullText"
      @keydown.space.prevent="openFullText"
    >
      {{ displayText }}
    </component>

    <Teleport to="body">
      <div
        v-if="showHoverTip && showThemedTooltip"
        class="twt-hover-tip"
        role="tooltip"
        :style="tipStyle"
      >
        {{ text }}
      </div>
    </Teleport>

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
  display: inline;
  cursor: pointer;
  text-decoration: underline;
  text-decoration-style: dotted;
  text-underline-offset: 0.18em;
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

@media (hover: none) {
  .text-with-tooltip[role='button'] {
    cursor: pointer;
  }
}
</style>

<style>
/* Teleported to body — unscoped so it is not clipped by card overflow */
.twt-hover-tip {
  position: fixed;
  z-index: 10050;
  padding: 0.55rem 0.7rem;
  border-radius: 0.55rem;
  border: 1px solid var(--color-border, #d9d3c7);
  background: var(--color-surface-elevated, #fff);
  color: var(--color-text, #111827);
  box-shadow: 0 12px 28px rgba(28, 36, 24, 0.18);
  font-size: 0.85rem;
  line-height: 1.4;
  white-space: normal;
  word-break: break-word;
  pointer-events: none;
}
</style>
