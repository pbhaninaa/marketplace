<script setup>
/**
 * Change over time: thin lines (kind="line") or stacked columns (kind="columns").
 * One y-axis only. Hover/focus: crosshair snaps to the nearest bucket, one tooltip lists every series.
 * Legend for 2+ series; a table view carries every value without hovering.
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import {
  SERIES_VARS,
  formatBucketLabel,
  formatTick,
  formatValue,
  niceScale,
  tickIndices,
} from './chartUtils';

const props = defineProps({
  title: { type: String, required: true },
  subtitle: { type: String, default: '' },
  /** ISO dates (bucket starts), oldest first. */
  labels: { type: Array, default: () => [] },
  granularity: { type: String, default: 'day' },
  /** [{ key, label, values: number[], slot: 0-4 }] — slot fixes the colour to the entity. */
  series: { type: Array, default: () => [] },
  kind: { type: String, default: 'line' },
  money: { type: Boolean, default: false },
  busy: { type: Boolean, default: false },
  emptyText: { type: String, default: 'Nothing in this period yet.' },
  height: { type: Number, default: 220 },
});

const root = ref(null);
const width = ref(600);
let observer = null;

onMounted(() => {
  if (!root.value) return;
  width.value = root.value.clientWidth || 600;
  if (typeof ResizeObserver !== 'undefined') {
    observer = new ResizeObserver((entries) => {
      const w = entries[0]?.contentRect?.width;
      if (w) width.value = w;
    });
    observer.observe(root.value);
  }
});
onBeforeUnmount(() => observer?.disconnect());

const n = computed(() => props.labels.length);
const stacked = computed(() => props.kind === 'columns');

const totals = computed(() =>
  props.labels.map((_, i) => props.series.reduce((sum, s) => sum + (Number(s.values[i]) || 0), 0)),
);

const isEmpty = computed(() => n.value === 0 || props.series.every((s) => s.values.every((v) => !Number(v))));

const dataMax = computed(() => {
  if (stacked.value) return Math.max(0, ...totals.value);
  return Math.max(0, ...props.series.flatMap((s) => s.values.map((v) => Number(v) || 0)));
});
const scale = computed(() => niceScale(dataMax.value, 4));

const margin = computed(() => {
  const longest = Math.max(...scale.value.ticks.map((t) => formatTick(t, scale.value.max, { money: props.money }).length));
  return { top: 10, right: 12, bottom: 24, left: Math.max(28, longest * 6.6 + 10) };
});
const plotW = computed(() => Math.max(40, width.value - margin.value.left - margin.value.right));
const plotH = computed(() => props.height - margin.value.top - margin.value.bottom);

const band = computed(() => plotW.value / Math.max(1, n.value));
/** Line points sit at band centres so columns and lines share the same x for a bucket. */
const xAt = (i) => margin.value.left + band.value * i + band.value / 2;
const yAt = (v) => margin.value.top + plotH.value - (Math.max(0, Number(v) || 0) / scale.value.max) * plotH.value;

const colour = (s) => SERIES_VARS[s.slot ?? 0] || SERIES_VARS[0];

const linePaths = computed(() =>
  props.series.map((s) => ({
    key: s.key,
    colour: colour(s),
    d: s.values.map((v, i) => `${i ? 'L' : 'M'}${xAt(i).toFixed(1)},${yAt(v).toFixed(1)}`).join(' '),
    lastX: xAt(s.values.length - 1),
    lastY: yAt(s.values[s.values.length - 1]),
  })),
);

const barWidth = computed(() => Math.max(2, Math.min(24, band.value * 0.7)));
const GAP = 2;
const RADIUS = 4;

/** Stacked segments bottom-up in series order; 2px surface gap between segments; only the top one is rounded. */
const columns = computed(() =>
  props.labels.map((_, i) => {
    let base = 0;
    const visible = props.series.filter((s) => Number(s.values[i]) > 0);
    const topKey = visible.length ? visible[visible.length - 1].key : null;
    const segs = [];
    for (const s of props.series) {
      const v = Number(s.values[i]) || 0;
      if (v <= 0) continue;
      const y0 = yAt(base);
      const y1 = yAt(base + v);
      base += v;
      const isTop = s.key === topKey;
      const bottom = y0 - (segs.length ? GAP / 2 : 0);
      const top = y1 + (isTop ? 0 : GAP / 2);
      const h = Math.max(0, bottom - top);
      if (h <= 0) continue;
      segs.push({ key: s.key, colour: colour(s), d: segPath(xAt(i) - barWidth.value / 2, top, barWidth.value, h, isTop) });
    }
    return segs;
  }),
);

function segPath(x, y, w, h, roundTop) {
  const r = roundTop ? Math.min(RADIUS, w / 2, h) : 0;
  return [
    `M${x},${y + h}`,
    `L${x},${y + r}`,
    r ? `Q${x},${y} ${x + r},${y}` : '',
    `L${x + w - r},${y}`,
    r ? `Q${x + w},${y} ${x + w},${y + r}` : '',
    `L${x + w},${y + h}`,
    'Z',
  ].join(' ');
}

const xTicks = computed(() => tickIndices(n.value, Math.max(2, Math.min(7, Math.floor(plotW.value / 70)))));

// ---------------------------------------------------------------- hover / focus
const active = ref(null);

function onPointer(e) {
  if (isEmpty.value) return;
  const rect = e.currentTarget.getBoundingClientRect();
  const x = e.clientX - rect.left - margin.value.left;
  active.value = Math.min(n.value - 1, Math.max(0, Math.floor(x / band.value)));
}

function onKey(e) {
  if (isEmpty.value) return;
  if (e.key === 'ArrowRight' || e.key === 'ArrowLeft') {
    e.preventDefault();
    const cur = active.value ?? (e.key === 'ArrowRight' ? -1 : n.value);
    active.value = Math.min(n.value - 1, Math.max(0, cur + (e.key === 'ArrowRight' ? 1 : -1)));
  } else if (e.key === 'Escape') {
    active.value = null;
  }
}

const tooltip = computed(() => {
  const i = active.value;
  if (i == null) return null;
  const rows = props.series.map((s) => ({ key: s.key, label: s.label, colour: colour(s), value: Number(s.values[i]) || 0 }));
  const flip = xAt(i) > width.value * 0.6;
  return {
    title: formatBucketLabel(props.labels[i], props.granularity, { long: true }),
    rows,
    total: stacked.value && props.series.length > 1 ? totals.value[i] : null,
    style: flip
      ? { right: `${width.value - xAt(i) + 12}px`, top: `${margin.value.top}px` }
      : { left: `${xAt(i) + 12}px`, top: `${margin.value.top}px` },
  };
});

const ariaSummary = computed(() => {
  if (isEmpty.value) return `${props.title}: ${props.emptyText}`;
  const parts = props.series.map((s) => {
    const sum = s.values.reduce((a, v) => a + (Number(v) || 0), 0);
    return `${s.label} ${formatValue(sum, { money: props.money })} in total`;
  });
  return `${props.title}. ${parts.join('; ')}. Use left and right arrow keys to read each ${props.granularity}.`;
});
</script>

<template>
  <section class="viz-card">
    <div class="viz-card__head">
      <div>
        <h3 class="viz-card__title">{{ title }}</h3>
        <p v-if="subtitle" class="viz-card__sub">{{ subtitle }}</p>
      </div>
      <ul v-if="series.length > 1" class="viz-legend">
        <li v-for="s in series" :key="s.key">
          <span
            :class="kind === 'line' ? 'viz-key-line' : 'viz-key-rect'"
            :style="{ background: colour(s) }"
            aria-hidden="true"
          ></span>
          {{ s.label }}
        </li>
      </ul>
    </div>

    <div ref="root" class="viz-plot" :class="{ 'viz-plot--busy': busy }">
      <p v-if="isEmpty" class="viz-empty">{{ emptyText }}</p>
      <template v-else>
        <svg
          :width="width"
          :height="height"
          role="img"
          :aria-label="ariaSummary"
          tabindex="0"
          @pointermove="onPointer"
          @pointerleave="active = null"
          @keydown="onKey"
          @blur="active = null"
        >
          <!-- recessive grid + y ticks -->
          <g>
            <template v-for="t in scale.ticks" :key="`g${t}`">
              <line
                :x1="margin.left"
                :x2="margin.left + plotW"
                :y1="yAt(t)"
                :y2="yAt(t)"
                :stroke="t === 0 ? 'var(--viz-axis)' : 'var(--viz-grid)'"
                stroke-width="1"
                shape-rendering="crispEdges"
              />
              <text class="viz-tick" :x="margin.left - 6" :y="yAt(t)" text-anchor="end" dominant-baseline="middle">
                {{ formatTick(t, scale.max, { money }) }}
              </text>
            </template>
          </g>
          <!-- x ticks -->
          <g>
            <text
              v-for="i in xTicks"
              :key="`x${i}`"
              class="viz-tick"
              :x="xAt(i)"
              :y="margin.top + plotH + 16"
              :text-anchor="i === 0 && n > 1 ? 'start' : i === n - 1 && n > 1 ? 'end' : 'middle'"
            >
              {{ formatBucketLabel(labels[i], granularity) }}
            </text>
          </g>

          <!-- crosshair -->
          <line
            v-if="active != null"
            :x1="xAt(active)"
            :x2="xAt(active)"
            :y1="margin.top"
            :y2="margin.top + plotH"
            stroke="var(--viz-axis)"
            stroke-width="1"
          />

          <!-- marks -->
          <g v-if="kind === 'columns'">
            <g v-for="(segs, i) in columns" :key="`c${i}`" :opacity="active == null || active === i ? 1 : 0.55">
              <path v-for="seg in segs" :key="seg.key" :d="seg.d" :fill="seg.colour" />
            </g>
          </g>
          <g v-else>
            <path
              v-if="series.length === 1"
              :d="`${linePaths[0].d} L${xAt(n - 1)},${yAt(0)} L${xAt(0)},${yAt(0)} Z`"
              :fill="linePaths[0].colour"
              opacity="0.1"
            />
            <path
              v-for="p in linePaths"
              :key="p.key"
              :d="p.d"
              fill="none"
              :stroke="p.colour"
              stroke-width="2"
              stroke-linejoin="round"
              stroke-linecap="round"
            />
            <!-- end markers (8px, 2px surface ring) -->
            <circle
              v-for="p in linePaths"
              :key="`end-${p.key}`"
              :cx="p.lastX"
              :cy="p.lastY"
              r="4"
              :fill="p.colour"
              stroke="var(--viz-surface)"
              stroke-width="2"
            />
            <!-- hovered points -->
            <template v-if="active != null">
              <circle
                v-for="s in series"
                :key="`h-${s.key}`"
                :cx="xAt(active)"
                :cy="yAt(s.values[active])"
                r="4.5"
                :fill="colour(s)"
                stroke="var(--viz-surface)"
                stroke-width="2"
              />
            </template>
          </g>

          <!-- hit area covers the whole plot so the reader aims at a date, not a 2px line -->
          <rect :x="margin.left" :y="margin.top" :width="plotW" :height="plotH" fill="transparent" />
        </svg>

        <div v-if="tooltip" class="viz-tooltip" :style="tooltip.style" role="status">
          <p class="viz-tooltip__title">{{ tooltip.title }}</p>
          <div v-for="r in tooltip.rows" :key="r.key" class="viz-tooltip__row">
            <span
              :class="kind === 'line' ? 'viz-key-line' : 'viz-key-rect'"
              :style="{ background: r.colour }"
              aria-hidden="true"
            ></span>
            <strong>{{ formatValue(r.value, { money }) }}</strong>
            <span>{{ r.label }}</span>
          </div>
          <div v-if="tooltip.total != null" class="viz-tooltip__row">
            <strong>{{ formatValue(tooltip.total, { money }) }}</strong>
            <span>Total</span>
          </div>
        </div>
      </template>
    </div>

    <details v-if="!isEmpty" class="viz-table-toggle">
      <summary>Show as table</summary>
      <table class="viz-table">
        <thead>
          <tr>
            <th scope="col">{{ granularity === 'week' ? 'Week of' : 'Day' }}</th>
            <th v-for="s in series" :key="s.key" scope="col">{{ s.label }}</th>
            <th v-if="series.length > 1 && kind === 'columns'" scope="col">Total</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(d, i) in labels" :key="d">
            <th scope="row">{{ formatBucketLabel(d, granularity, { long: true }) }}</th>
            <td v-for="s in series" :key="s.key">{{ formatValue(s.values[i], { money }) }}</td>
            <td v-if="series.length > 1 && kind === 'columns'">{{ formatValue(totals[i], { money }) }}</td>
          </tr>
        </tbody>
      </table>
    </details>
  </section>
</template>
