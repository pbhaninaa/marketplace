<script setup>
/**
 * AppSelect: a dropdown that looks like every other input (src/styles/forms.css) with an option list
 * styled to match, instead of the browser's grey system menu.
 *
 * Drop-in for <select>: write <option> children exactly as before (v-for, :value objects/booleans,
 * disabled). A real, visually hidden <select> renders those options, so `required` form validation,
 * v-model (incl. .number) and `@change="fn($event.target.value)"` all keep working.
 *
 * - Desktop: custom button + listbox, teleported to <body> so no container can clip it.
 * - Touch devices: the native picker (better for fingers), styled by forms.css.
 * - Keyboard: Enter/Space/ArrowDown open; arrows/Home/End move; type to jump; Enter picks; Esc closes.
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, useAttrs, useId, watch } from 'vue';

defineOptions({ inheritAttrs: false });

const props = defineProps({
  modelValue: { type: [String, Number, Boolean, Array, Object], default: undefined },
  /** Native-select style binding (`:value` + `@change`) for pages that don't use v-model. */
  value: { type: [String, Number, Boolean, Array, Object], default: undefined },
  /** Shown on the button when nothing is selected (and no empty-value option exists). */
  placeholder: { type: String, default: 'Select an option' },
  multiple: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
  required: { type: Boolean, default: false },
  name: { type: String, default: undefined },
});

const emit = defineEmits(['update:modelValue', 'change', 'focus', 'blur']);
const attrs = useAttrs();

const uid = useId();
const listId = `app-select-list-${uid}`;
const nativeRef = ref(null);
const buttonRef = ref(null);
const listRef = ref(null);
const open = ref(false);
const activeIndex = ref(-1);
const options = ref([]);
const coarse = ref(false);
const pos = ref({ top: 0, left: 0, width: 0, maxHeight: 280, above: false });

/** Attributes a page puts on <AppSelect>: class/style go on the wrapper, the rest (id, aria-*) on the control. */
const wrapperAttrs = computed(() => ({ class: attrs.class, style: attrs.style }));
const controlAttrs = computed(() => {
  const { class: _c, style: _s, onChange: _ch, onFocus: _f, onBlur: _b, ...rest } = attrs;
  return rest;
});

/** Vue stores non-string option bindings (:value="true", :value="5") on el._value. */
const valueOf = (opt) => (opt && '_value' in opt ? opt._value : opt?.value);
const same = (a, b) => a === b || (a != null && b != null && String(a) === String(b));

function readOptions() {
  const el = nativeRef.value;
  if (!el) return;
  options.value = Array.from(el.options).map((o, i) => ({
    index: i,
    value: valueOf(o),
    label: o.label || o.textContent.trim(),
    disabled: o.disabled,
  }));
  syncNative();
}

/** v-model wins; otherwise a plain :value binding (native-select style). */
const current = computed(() => (props.modelValue !== undefined ? props.modelValue : props.value));

const selectedValues = computed(() => {
  if (props.multiple) return Array.isArray(current.value) ? current.value : [];
  return current.value === undefined ? [] : [current.value];
});
const isSelected = (opt) => selectedValues.value.some((v) => same(v, opt.value));

const displayLabel = computed(() => {
  const chosen = options.value.filter((o) => isSelected(o));
  if (props.multiple) return chosen.map((o) => o.label).join(', ');
  if (chosen[0]) return chosen[0].label;
  // Like a native select: with no value set, show the "empty" option (e.g. "All categories").
  const empty = options.value.find((o) => o.value === '' || o.value == null);
  return empty?.label ?? '';
});
const showPlaceholder = computed(() => !displayLabel.value);

/** Keep the hidden native select in step with the model (drives `required` validation). */
function syncNative() {
  const el = nativeRef.value;
  if (!el) return;
  Array.from(el.options).forEach((o) => {
    o.selected = selectedValues.value.some((v) => same(v, valueOf(o)));
  });
}

let observer = null;
let mq = null;
const onMq = () => {
  coarse.value = !!mq?.matches;
};

onMounted(() => {
  if (typeof window !== 'undefined' && window.matchMedia) {
    mq = window.matchMedia('(pointer: coarse)');
    onMq();
    mq.addEventListener?.('change', onMq);
  }
  readOptions();
  if (typeof MutationObserver !== 'undefined' && nativeRef.value) {
    observer = new MutationObserver(() => readOptions());
    observer.observe(nativeRef.value, { childList: true, subtree: true, characterData: true, attributes: true });
  }
});

onBeforeUnmount(() => {
  observer?.disconnect();
  mq?.removeEventListener?.('change', onMq);
  detachGlobal();
});

watch(current, () => nextTick(syncNative), { deep: true });

// ---------------------------------------------------------------- choosing

function commit(value) {
  emit('update:modelValue', value);
  // Pages written for native selects read $event.target.value.
  emit('change', { target: { value }, value });
}

function choose(opt) {
  if (!opt || opt.disabled) return;
  if (props.multiple) {
    const next = isSelected(opt)
      ? selectedValues.value.filter((v) => !same(v, opt.value))
      : [...selectedValues.value, opt.value];
    commit(next);
    return;
  }
  commit(opt.value);
  close(true);
}

/** Native picker path (touch devices). */
function onNativeChange(e) {
  const el = e.target;
  if (props.multiple) {
    commit(Array.from(el.selectedOptions).map(valueOf));
  } else {
    commit(valueOf(el.options[el.selectedIndex]));
  }
}

// ---------------------------------------------------------------- open / close / position

function place() {
  const btn = buttonRef.value;
  if (!btn) return;
  const r = btn.getBoundingClientRect();
  const gap = 6;
  const below = window.innerHeight - r.bottom - gap - 8;
  const aboveSpace = r.top - gap - 8;
  const wanted = Math.min(288, 44 * Math.max(1, options.value.length) + 12);
  const above = below < Math.min(wanted, 180) && aboveSpace > below;
  const maxHeight = Math.max(120, Math.min(288, above ? aboveSpace : below));
  pos.value = {
    left: Math.round(r.left),
    width: Math.round(r.width),
    top: above ? Math.round(r.top - gap) : Math.round(r.bottom + gap),
    maxHeight,
    above,
  };
}

function openList() {
  if (props.disabled || open.value) return;
  readOptions();
  place();
  open.value = true;
  const first = options.value.findIndex((o) => isSelected(o) && !o.disabled);
  activeIndex.value = first >= 0 ? first : nextEnabled(-1, 1);
  attachGlobal();
  nextTick(scrollActiveIntoView);
}

function close(refocus = false) {
  if (!open.value) return;
  open.value = false;
  detachGlobal();
  if (refocus) nextTick(() => buttonRef.value?.focus());
}

function toggle() {
  open.value ? close(true) : openList();
}

function onOutside(e) {
  const t = e.target;
  if (buttonRef.value?.contains(t) || listRef.value?.contains(t)) return;
  close(false);
}

function onViewportChange(e) {
  // Scrolling inside the list itself must not close it.
  if (e?.type === 'scroll' && listRef.value?.contains(e.target)) return;
  place();
}

function attachGlobal() {
  document.addEventListener('pointerdown', onOutside, true);
  window.addEventListener('resize', onViewportChange);
  window.addEventListener('scroll', onViewportChange, true);
}

function detachGlobal() {
  if (typeof document === 'undefined') return;
  document.removeEventListener('pointerdown', onOutside, true);
  window.removeEventListener('resize', onViewportChange);
  window.removeEventListener('scroll', onViewportChange, true);
}

// ---------------------------------------------------------------- keyboard

function nextEnabled(from, step) {
  const n = options.value.length;
  for (let i = 1; i <= n; i++) {
    const idx = from + step * i;
    if (idx < 0 || idx >= n) break;
    if (!options.value[idx].disabled) return idx;
  }
  return from;
}

function scrollActiveIntoView() {
  const el = listRef.value?.querySelector(`[data-index="${activeIndex.value}"]`);
  el?.scrollIntoView?.({ block: 'nearest' });
}

let typed = '';
let typedTimer = null;
function typeahead(ch) {
  typed += ch.toLowerCase();
  clearTimeout(typedTimer);
  typedTimer = setTimeout(() => (typed = ''), 600);
  const start = open.value ? activeIndex.value : options.value.findIndex((o) => isSelected(o));
  const n = options.value.length;
  for (let i = 1; i <= n; i++) {
    const o = options.value[(Math.max(start, 0) + i) % n];
    if (!o.disabled && o.label.toLowerCase().startsWith(typed)) {
      if (open.value) {
        activeIndex.value = o.index;
        nextTick(scrollActiveIntoView);
      } else if (!props.multiple) {
        commit(o.value);
      }
      return;
    }
  }
}

function onKeydown(e) {
  const k = e.key;
  if (!open.value) {
    if (['Enter', ' ', 'ArrowDown', 'ArrowUp', 'Alt'].includes(k) || (k === 'ArrowDown' && e.altKey)) {
      e.preventDefault();
      openList();
    } else if (k.length === 1 && !e.ctrlKey && !e.metaKey && !e.altKey) {
      typeahead(k);
    }
    return;
  }
  switch (k) {
    case 'ArrowDown':
      e.preventDefault();
      activeIndex.value = nextEnabled(activeIndex.value, 1);
      nextTick(scrollActiveIntoView);
      break;
    case 'ArrowUp':
      e.preventDefault();
      activeIndex.value = nextEnabled(activeIndex.value, -1);
      nextTick(scrollActiveIntoView);
      break;
    case 'Home':
      e.preventDefault();
      activeIndex.value = nextEnabled(-1, 1);
      nextTick(scrollActiveIntoView);
      break;
    case 'End':
      e.preventDefault();
      activeIndex.value = nextEnabled(options.value.length, -1);
      nextTick(scrollActiveIntoView);
      break;
    case 'Enter':
    case ' ':
      e.preventDefault();
      choose(options.value[activeIndex.value]);
      break;
    case 'Escape':
      e.preventDefault();
      close(true);
      break;
    case 'Tab':
      close(false);
      break;
    default:
      if (k.length === 1 && !e.ctrlKey && !e.metaKey) typeahead(k);
  }
}

const activeId = computed(() => (open.value && activeIndex.value >= 0 ? `${listId}-opt-${activeIndex.value}` : undefined));
const invalid = computed(() => attrs['aria-invalid'] === 'true' || attrs['aria-invalid'] === true);

const listStyle = computed(() => ({
  left: `${pos.value.left}px`,
  width: `${pos.value.width}px`,
  maxHeight: `${pos.value.maxHeight}px`,
  ...(pos.value.above
    ? { bottom: `${window.innerHeight - pos.value.top}px` }
    : { top: `${pos.value.top}px` }),
}));
</script>

<template>
  <div class="app-select" :class="[wrapperAttrs.class, { 'app-select--open': open, 'app-select--native': coarse }]" :style="wrapperAttrs.style">
    <!-- Desktop: custom control. Comes first in the DOM so FormField links its label to it. -->
    <button
      v-if="!coarse"
      ref="buttonRef"
      type="button"
      class="app-select__button"
      role="combobox"
      aria-haspopup="listbox"
      :aria-expanded="open ? 'true' : 'false'"
      :aria-controls="listId"
      :aria-activedescendant="activeId"
      :aria-invalid="invalid ? 'true' : undefined"
      :aria-required="required ? 'true' : undefined"
      :disabled="disabled"
      v-bind="controlAttrs"
      @click="toggle"
      @keydown="onKeydown"
      @focus="emit('focus')"
      @blur="emit('blur')"
    >
      <span class="app-select__value" :class="{ 'app-select__value--placeholder': showPlaceholder }">
        {{ showPlaceholder ? placeholder : displayLabel }}
      </span>
      <svg class="app-select__chevron" viewBox="0 0 20 20" aria-hidden="true">
        <path d="M5.5 7.5 10 12l4.5-4.5" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
      </svg>
    </button>

    <!--
      The real select: renders the <option> children, keeps `required` validation and is the control
      on touch devices. On desktop it sits invisibly under the button (not display:none, so the
      browser can still anchor its "please select" bubble).
    -->
    <select
      ref="nativeRef"
      class="app-select__native"
      :class="{ 'app-select__native--hidden': !coarse }"
      :multiple="multiple"
      :disabled="disabled"
      :required="required"
      :name="name"
      :tabindex="coarse ? undefined : -1"
      :aria-hidden="coarse ? undefined : 'true'"
      v-bind="coarse ? controlAttrs : {}"
      @change="onNativeChange"
      @focus="coarse && emit('focus')"
      @blur="coarse && emit('blur')"
    >
      <slot />
    </select>

    <Teleport to="body">
      <ul
        v-if="open"
        :id="listId"
        ref="listRef"
        class="app-select__list"
        :class="{ 'app-select__list--above': pos.above }"
        role="listbox"
        :aria-multiselectable="multiple ? 'true' : undefined"
        :style="listStyle"
        tabindex="-1"
      >
        <li
          v-for="opt in options"
          :id="`${listId}-opt-${opt.index}`"
          :key="opt.index"
          :data-index="opt.index"
          class="app-select__option"
          :class="{
            'app-select__option--active': opt.index === activeIndex,
            'app-select__option--selected': isSelected(opt),
            'app-select__option--disabled': opt.disabled,
          }"
          role="option"
          :aria-selected="isSelected(opt) ? 'true' : 'false'"
          :aria-disabled="opt.disabled ? 'true' : undefined"
          @pointerenter="!opt.disabled && (activeIndex = opt.index)"
          @pointerdown.prevent
          @click="choose(opt)"
        >
          <span class="app-select__label">{{ opt.label }}</span>
          <svg v-if="isSelected(opt)" class="app-select__check" viewBox="0 0 20 20" aria-hidden="true">
            <path d="m5 10.5 3.2 3.2L15 7" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        </li>
        <li v-if="!options.length" class="app-select__empty" role="presentation">No options</li>
      </ul>
    </Teleport>
  </div>
</template>

<style>
/* Not scoped: the list is teleported to <body>. Classes are namespaced with app-select__. */
.app-select {
  position: relative;
  display: block;
  min-width: 0;
}

/* The closed control borrows every visual from forms.css by being styled like an input. */
.app-select__button {
  box-sizing: border-box;
  width: 100%;
  min-height: var(--ctl-height, 2.75rem);
  padding: 0.55rem 0.75rem 0.55rem var(--ctl-pad-x, 0.875rem);
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font: inherit;
  font-family: var(--font-ui, inherit);
  font-size: var(--ctl-font-size, 0.95rem);
  line-height: 1.4;
  text-align: left;
  color: var(--color-text);
  background-color: var(--ctl-bg, #fff);
  border: 1px solid var(--ctl-border, #c4bcb0);
  border-radius: var(--ctl-radius, 12px);
  box-shadow: var(--ctl-shadow, none);
  cursor: pointer;
  transition:
    border-color 0.15s ease,
    box-shadow 0.15s ease,
    background-color 0.15s ease;
}

.app-select__button:hover:not(:disabled) {
  border-color: var(--ctl-border-hover, #a39a8c);
}

.app-select__button:focus {
  outline: none;
}

.app-select__button:focus-visible,
.app-select--open .app-select__button {
  border-color: var(--ctl-border-focus, #3d7a66);
  box-shadow: 0 0 0 3px var(--ctl-ring, rgba(61, 122, 102, 0.2));
}

.app-select__button[aria-invalid='true'],
.form-field--has-error .app-select__button {
  border-color: var(--ctl-danger, #8b2c1f);
}

.app-select__button[aria-invalid='true']:focus-visible,
.form-field--has-error .app-select__button:focus-visible {
  box-shadow: 0 0 0 3px var(--ctl-danger-ring, rgba(139, 44, 31, 0.16));
}

.app-select__button:disabled {
  background-color: var(--ctl-bg-disabled, #f6f3ec);
  color: var(--color-muted);
  cursor: not-allowed;
  box-shadow: none;
}

.app-select__value {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-select__value--placeholder {
  color: var(--ctl-placeholder, #6b7568);
}

.app-select__chevron {
  flex: none;
  width: 1.15rem;
  height: 1.15rem;
  color: var(--color-muted);
  transition: transform 0.15s ease;
}

.app-select--open .app-select__chevron {
  transform: rotate(180deg);
  color: var(--color-sage);
}

/* Hidden native select (desktop): invisible, unclickable, but still validates and anchors bubbles. */
.app-select__native--hidden {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  opacity: 0;
  pointer-events: none;
  margin: 0;
}

/* Touch devices: the native select is the control and uses forms.css styling. */
.app-select--native .app-select__native {
  width: 100%;
}

/* ---------------- option list (teleported to <body>) ---------------- */
.app-select__list {
  position: fixed;
  z-index: 2000;
  box-sizing: border-box;
  margin: 0;
  padding: 0.35rem;
  list-style: none;
  overflow-y: auto;
  overscroll-behavior: contain;
  background: var(--color-surface-elevated, #fff);
  border: 1px solid var(--ctl-border, #c4bcb0);
  border-radius: var(--ctl-radius, 12px);
  box-shadow:
    0 12px 32px rgba(28, 36, 24, 0.14),
    0 2px 6px rgba(28, 36, 24, 0.08);
  font-family: var(--font-ui, inherit);
  font-size: var(--ctl-font-size, 0.95rem);
  color: var(--color-text);
  animation: app-select-in 0.12s ease-out;
}

.app-select__list--above {
  animation-name: app-select-in-above;
}

@keyframes app-select-in {
  from {
    opacity: 0;
    transform: translateY(-4px);
  }
}

@keyframes app-select-in-above {
  from {
    opacity: 0;
    transform: translateY(4px);
  }
}

@media (prefers-reduced-motion: reduce) {
  .app-select__list {
    animation: none;
  }
}

.app-select__option {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  min-height: 2.5rem;
  padding: 0.5rem 0.65rem;
  border-radius: calc(var(--ctl-radius, 12px) - 4px);
  cursor: pointer;
  line-height: 1.35;
}

.app-select__label {
  flex: 1;
  min-width: 0;
}

.app-select__option--active {
  background: var(--color-sage-soft, #e4f0eb);
}

.app-select__option--selected {
  font-weight: 600;
  color: var(--color-canopy, #1a3c34);
}

.app-select__check {
  flex: none;
  width: 1.1rem;
  height: 1.1rem;
  color: var(--color-sage, #3d7a66);
}

.app-select__option--disabled {
  color: var(--color-muted);
  cursor: not-allowed;
  opacity: 0.6;
}

.app-select__empty {
  padding: 0.6rem 0.65rem;
  color: var(--color-muted);
}

:root[data-theme='dark'] .app-select__list {
  box-shadow:
    0 12px 32px rgba(0, 0, 0, 0.5),
    0 2px 6px rgba(0, 0, 0, 0.35);
}

/* Glass display style: recessed control like other inputs, solid readable list. */
:root[data-display='glass'] .app-select__button {
  background-color: var(--g-fill-soft) !important;
  box-shadow: var(--g-sunken);
}

:root[data-display='glass'] .app-select__button:focus-visible,
:root[data-display='glass'] .app-select--open .app-select__button {
  background-color: var(--g-fill-strong) !important;
  box-shadow: 0 0 0 4px rgba(var(--g-accent-rgb), 0.18), var(--g-sunken);
}

@media (forced-colors: active) {
  .app-select__option--active {
    outline: 2px solid Highlight;
  }
}
</style>
