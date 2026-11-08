<script setup>
/**
 * One whole split into parts, read beside its figures: a thin segmented bar above a list that
 * gives each part's name, count and share (the list is the legend and the table at once).
 * Colours come from each part's fixed slot, so they never change when a part is zero.
 */
import { computed } from 'vue';
import { SERIES_VARS, formatShare, formatValue } from './chartUtils';

const props = defineProps({
  title: { type: String, required: true },
  subtitle: { type: String, default: '' },
  /** [{ key, label, value, slot }] in a fixed order. */
  parts: { type: Array, default: () => [] },
  unit: { type: String, default: '' },
  busy: { type: Boolean, default: false },
  emptyText: { type: String, default: 'Nothing in this period yet.' },
});

const total = computed(() => props.parts.reduce((s, p) => s + (Number(p.value) || 0), 0));
const colour = (p) => SERIES_VARS[p.slot ?? 0] || SERIES_VARS[0];
const visible = computed(() => props.parts.filter((p) => Number(p.value) > 0));
const describe = (p) =>
  `${p.label}: ${formatValue(p.value)}${props.unit ? ` ${props.unit}` : ''} (${formatShare(p.value, total.value)})`;
</script>

<template>
  <section class="viz-card">
    <div class="viz-card__head">
      <div>
        <h3 class="viz-card__title">{{ title }}</h3>
        <p v-if="subtitle" class="viz-card__sub">{{ subtitle }}</p>
      </div>
      <p v-if="total" class="viz-card__sub">
        <strong class="seg__total">{{ formatValue(total) }}</strong> {{ unit }} in total
      </p>
    </div>
    <div class="viz-plot" :class="{ 'viz-plot--busy': busy }">
      <p v-if="!total" class="viz-empty">{{ emptyText }}</p>
      <template v-else>
        <div class="seg" role="img" :aria-label="`${title}: ${parts.map(describe).join('; ')}`">
          <span
            v-for="p in visible"
            :key="p.key"
            class="seg__part"
            :style="{ flexGrow: Number(p.value), background: colour(p) }"
            :title="describe(p)"
          ></span>
        </div>
        <ul class="seg__list">
          <li v-for="p in parts" :key="p.key" :class="{ 'seg__item--zero': !Number(p.value) }">
            <span class="viz-key-rect" :style="{ background: colour(p) }" aria-hidden="true"></span>
            <span class="seg__name">{{ p.label }}</span>
            <strong class="seg__count">{{ formatValue(p.value) }}</strong>
            <span class="seg__share">{{ formatShare(p.value, total) }}</span>
          </li>
        </ul>
      </template>
    </div>
  </section>
</template>

<style scoped>
.seg {
  display: flex;
  gap: 2px;
  height: 8px;
  width: 100%;
}

.seg__part {
  flex-basis: 0;
  min-width: 3px;
  border-radius: 999px;
}

.seg__list {
  list-style: none;
  margin: 0.7rem 0 0;
  padding: 0;
  display: grid;
  gap: 0.35rem;
  font-size: 0.82rem;
}

.seg__list li {
  display: grid;
  grid-template-columns: auto 1fr auto 3rem;
  align-items: center;
  gap: 0.5rem;
  color: var(--viz-text-secondary);
}

.seg__item--zero {
  opacity: 0.7;
}

.seg__count {
  color: var(--viz-text);
  font-variant-numeric: tabular-nums;
}

.seg__share {
  text-align: right;
  color: var(--viz-muted);
  font-variant-numeric: tabular-nums;
}

.seg__total {
  color: var(--viz-text);
}
</style>
