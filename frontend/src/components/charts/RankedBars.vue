<script setup>
/**
 * Ranked horizontal bars, largest first, one hue (magnitude, not identity).
 * Value at the bar tip; bars <=24px, 4px rounded data-end, square at the baseline.
 */
import { computed } from 'vue';
import { formatValue } from './chartUtils';

const props = defineProps({
  title: { type: String, required: true },
  subtitle: { type: String, default: '' },
  /** [{ key, label, value, detail? }] */
  items: { type: Array, default: () => [] },
  money: { type: Boolean, default: false },
  unit: { type: String, default: '' },
  busy: { type: Boolean, default: false },
  emptyText: { type: String, default: 'Nothing in this period yet.' },
});

const rows = computed(() =>
  [...props.items].filter((i) => Number(i.value) > 0).sort((a, b) => Number(b.value) - Number(a.value)),
);
const max = computed(() => Math.max(0, ...rows.value.map((r) => Number(r.value))));
const pct = (v) => (max.value ? Math.max(1.5, (Number(v) / max.value) * 100) : 0);
const text = (v) => `${formatValue(v, { money: props.money })}${props.unit ? ` ${props.unit}` : ''}`;
</script>

<template>
  <section class="viz-card">
    <div class="viz-card__head">
      <div>
        <h3 class="viz-card__title">{{ title }}</h3>
        <p v-if="subtitle" class="viz-card__sub">{{ subtitle }}</p>
      </div>
    </div>
    <div class="viz-plot" :class="{ 'viz-plot--busy': busy }">
      <p v-if="!rows.length" class="viz-empty">{{ emptyText }}</p>
      <ol v-else class="rbars" role="list">
        <li
          v-for="r in rows"
          :key="r.key"
          class="rbars__row"
          tabindex="0"
          :aria-label="`${r.label}: ${text(r.value)}${r.detail ? `, ${r.detail}` : ''}`"
          :title="r.detail ? `${r.label}: ${text(r.value)} (${r.detail})` : `${r.label}: ${text(r.value)}`"
        >
          <span class="rbars__label">{{ r.label }}</span>
          <span class="rbars__track">
            <span class="rbars__bar" :style="{ width: `${pct(r.value)}%` }"></span>
            <span class="rbars__value">{{ text(r.value) }}</span>
          </span>
        </li>
      </ol>
    </div>
  </section>
</template>

<style scoped>
.rbars {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 0.6rem;
}

.rbars__row {
  display: grid;
  gap: 0.2rem;
  border-radius: 6px;
  outline-offset: 3px;
}

.rbars__row:hover .rbars__bar,
.rbars__row:focus-visible .rbars__bar {
  filter: brightness(1.12);
}

.rbars__label {
  font-size: 0.8rem;
  color: var(--viz-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rbars__track {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  min-width: 0;
}

.rbars__bar {
  height: 14px;
  /* Leave room for the value text at the tip. */
  max-width: calc(100% - 6.5rem);
  border-radius: 0 4px 4px 0;
  background: var(--viz-series-1);
  flex: none;
}

.rbars__value {
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--viz-text);
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}
</style>
