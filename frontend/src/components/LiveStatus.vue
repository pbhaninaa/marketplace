<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';

const props = defineProps({
  /** Epoch ms of the last successful load. */
  updatedAt: { type: Number, default: null },
  refreshing: { type: Boolean, default: false },
});
defineEmits(['refresh']);

// Re-render the relative time every 20s ("just now" → "1 min ago").
const tick = ref(Date.now());
let timer = null;
onMounted(() => {
  timer = setInterval(() => (tick.value = Date.now()), 20_000);
});
onBeforeUnmount(() => clearInterval(timer));

const updatedText = computed(() => {
  if (!props.updatedAt) return '';
  const secs = Math.max(0, Math.round((tick.value - props.updatedAt) / 1000));
  if (secs < 45) return 'just now';
  const mins = Math.round(secs / 60);
  if (mins < 60) return `${mins} min ago`;
  return new Date(props.updatedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
});
</script>

<template>
  <div class="live-status" role="status" aria-live="polite">
    <span class="live-status__dot" :class="{ 'live-status__dot--busy': refreshing }" aria-hidden="true" />
    <span class="live-status__label">Live</span>
    <span v-if="updatedText" class="live-status__time">· {{ refreshing ? 'Updating…' : `Updated ${updatedText}` }}</span>
    <button
      type="button"
      class="live-status__btn"
      :disabled="refreshing"
      aria-label="Refresh now"
      title="Refresh now"
      @click="$emit('refresh')"
    >
      <span class="material-icons" aria-hidden="true">refresh</span>
    </button>
  </div>
</template>

<style scoped>
.live-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 0.78rem;
  color: var(--color-muted);
  white-space: nowrap;
}
.live-status__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-canopy, #2f7d5b);
  animation: live-pulse 2.4s ease-out infinite;
}
.live-status__dot--busy {
  background: var(--color-wheat, #c9a227);
}
.live-status__label {
  font-weight: 700;
  color: var(--color-canopy, #2f7d5b);
  letter-spacing: 0.02em;
}
.live-status__btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: 1px solid var(--color-border);
  border-radius: 50%;
  background: var(--color-surface-elevated);
  color: inherit;
  cursor: pointer;
}
.live-status__btn:disabled {
  opacity: 0.55;
  cursor: progress;
}
.live-status__btn .material-icons {
  font-size: 16px;
}
@keyframes live-pulse {
  0% {
    box-shadow: 0 0 0 0 rgba(47, 125, 91, 0.45);
  }
  70% {
    box-shadow: 0 0 0 6px rgba(47, 125, 91, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(47, 125, 91, 0);
  }
}
@media (prefers-reduced-motion: reduce) {
  .live-status__dot {
    animation: none;
  }
}
</style>
