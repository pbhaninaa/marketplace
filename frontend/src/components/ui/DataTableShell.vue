<script setup>
import { nextTick, onMounted, onUpdated } from 'vue';

const props = defineProps({
  caption: { type: String, default: '' },
  /** Max characters allowed to show in a cell before truncation + hover tooltip. */
  maxChars: { type: Number, default: 22 },
});

function isSkippableCell(td) {
  // Don't attach tooltips to action/tooling cells.
  if (!td) return true;
  if (td.classList?.contains('cell-actions')) return true;
  if (td.querySelector?.('.cell-actions')) return true;
  // Skip cells that contain interactive elements/icons; their textContent isn't meaningful.
  if (td.querySelector?.('button,a,input,select,textarea,svg,form')) return true;
  // Skip complex layout cells (e.g. stacks) — we only auto-truncate simple text cells.
  if (td.children && td.children.length > 0) return true;
  return false;
}

function applyAutoTooltips(root) {
  if (!root || !props.maxChars) return;
  const max = Number(props.maxChars);
  if (!Number.isFinite(max) || max <= 0) return;
  const cells = root.querySelectorAll('td');
  for (const td of cells) {
    if (isSkippableCell(td)) continue;
    if (td.hasAttribute('data-no-tooltip')) continue;
    const raw = (td.textContent || '').replace(/\s+/g, ' ').trim();
    if (!raw) continue;
    if (raw.length <= max) continue;
    td.setAttribute('title', raw);
    td.classList.add('cell-auto-tooltipped', 'cell-auto-truncate');
    td.textContent = raw.substring(0, max) + '…';
  }
}

onMounted(async () => {
  await nextTick();
  applyAutoTooltips(document?.activeElement?.closest?.('.data-table-wrap') || document.querySelector('.data-table-wrap'));
});

onUpdated(async () => {
  await nextTick();
  // Scope to this component instance by walking from the current script's DOM via querySelector.
  // Vue doesn't give us a ref here, so we target the nearest table wrapper.
  applyAutoTooltips(document?.activeElement?.closest?.('.data-table-wrap') || document.querySelector('.data-table-wrap'));
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
  table-layout: auto;
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
  white-space: normal;
}

/* Default cell text truncation (use with TextWithTooltip). */
.data-table :deep(.cell-text) {
  display: inline-block;
  max-width: 32ch;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: top;
}

.data-table :deep(.cell-auto-truncate) {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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
  white-space: normal;
}

.data-table :deep(.cell-actions) {
  white-space: nowrap;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 0.5rem;
  overflow: visible;
}

/* Every button has breathing room */
.data-table :deep(.btn) {
  margin: 0.1rem 0;
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
