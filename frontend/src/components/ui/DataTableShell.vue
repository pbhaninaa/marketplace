<script setup>
defineProps({
  caption: { type: String, default: '' },
});
</script>

<template>
  <div class="data-table-wrap">
    <table class="data-table">
      <caption v-if="caption" class="sr-only">{{ caption }}</caption>
      <slot />
    </table>
  </div>
</template>

<style scoped>
.data-table-wrap {
  width: 100%;
  overflow-x: auto;
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border);
  background: var(--color-surface-elevated);
  box-shadow: var(--shadow-md);
}

.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.9rem;
}

.data-table :deep(th) {
  text-align: left;
  padding: 0.75rem 1rem;
  background: linear-gradient(180deg, #f0f5f2 0%, #e4ebe6 100%);
  font-family: var(--font-ui);
  font-size: 0.68rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--color-canopy);
  border-bottom: 1px solid var(--color-border);
  white-space: nowrap;
}

.data-table :deep(td) {
  padding: 0.75rem 1rem;
  border-bottom: 1px solid rgba(217, 211, 199, 0.65);
  vertical-align: top;
}

.data-table :deep(tr:last-child td) {
  border-bottom: none;
}

.data-table :deep(tr.is-greyed) {
  opacity: 0.42;
  filter: grayscale(0.15);
}

.data-table :deep(tr.is-greyed td) {
  pointer-events: none;
}

.data-table :deep(.cell-stack) {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.data-table :deep(.cell-actions) {
  white-space: nowrap;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}
</style>
