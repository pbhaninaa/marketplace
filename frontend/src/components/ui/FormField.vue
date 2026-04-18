<script setup>
import { ref, onMounted, onUpdated, onUnmounted, nextTick, watch } from 'vue';

const props = defineProps({
  label: { type: String, default: '' },
  forId: { type: String, default: '' },
  /** When true, first character of text / textarea is uppercased while typing (not for email, password, file, etc.). */
  capitalizeFirst: { type: Boolean, default: false },
});

const controlRef = ref(null);
let detach = null;

function isTextLikeInput(el) {
  if (!el) return false;
  if (el.tagName === 'TEXTAREA') return true;
  if (el.tagName !== 'INPUT') return false;
  const t = (el.getAttribute('type') || 'text').toLowerCase();
  if (['file', 'password', 'email', 'number', 'tel', 'url', 'search', 'hidden', 'date', 'datetime-local', 'checkbox', 'radio', 'range', 'color'].includes(t)) {
    return false;
  }
  return true;
}

/** Prefer textarea, then first text-like input (skips file inputs etc.). */
function findTextField(root) {
  if (!root) return null;
  const ta = root.querySelector('textarea');
  if (ta) return ta;
  for (const inp of root.querySelectorAll('input')) {
    if (isTextLikeInput(inp)) return inp;
  }
  return null;
}

function bindCapitalize() {
  detach?.();
  detach = null;
  if (!props.capitalizeFirst || !controlRef.value) return;
  const el = findTextField(controlRef.value);
  if (!el || !isTextLikeInput(el)) return;

  let composing = false;
  const onCompositionStart = () => {
    composing = true;
  };
  const onCompositionEnd = () => {
    composing = false;
  };

  const handler = (e) => {
    if (composing || e.isComposing) return;
    const node = e.target;
    if (!isTextLikeInput(node)) return;
    const v = node.value;
    if (!v) return;
    const nv = v.charAt(0).toUpperCase() + v.slice(1);
    if (nv === v) return;
    const len = nv.length;
    const a = node.selectionStart;
    const b = node.selectionEnd;
    node.value = nv;
    node.dispatchEvent(new InputEvent('input', { bubbles: true, inputType: e.inputType || 'insertText' }));
    nextTick(() => {
      try {
        let start = a;
        let end = b;
        if (start == null || end == null) {
          node.setSelectionRange(len, len);
          return;
        }
        if (start > len) start = len;
        if (end > len) end = len;
        node.setSelectionRange(start, end);
      } catch {
        /* ignore */
      }
    });
  };

  el.addEventListener('compositionstart', onCompositionStart);
  el.addEventListener('compositionend', onCompositionEnd);
  el.addEventListener('input', handler);
  detach = () => {
    el.removeEventListener('compositionstart', onCompositionStart);
    el.removeEventListener('compositionend', onCompositionEnd);
    el.removeEventListener('input', handler);
  };
}

function scheduleBind() {
  nextTick(() => bindCapitalize());
}

onMounted(scheduleBind);
onUpdated(scheduleBind);
watch(
  () => props.capitalizeFirst,
  () => scheduleBind(),
);
onUnmounted(() => detach?.());
</script>

<template>
  <div class="form-field">
    <label v-if="label" :for="forId || undefined" class="form-field__label">{{ label }}</label>
    <div ref="controlRef" class="form-field__control">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.form-field {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.form-field__label {
  font-size: 0.72rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--color-text-secondary);
}

.form-field__control :deep(input),
.form-field__control :deep(select),
.form-field__control :deep(textarea) {
  width: 100%;
  padding: 0.55rem 0.75rem;
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-border);
  font: inherit;
  font-size: 0.95rem;
  color: var(--color-text);
  background: var(--color-surface-elevated);
  transition:
    border-color 0.15s ease,
    box-shadow 0.15s ease;
}

.form-field__control :deep(input:hover),
.form-field__control :deep(select:hover),
.form-field__control :deep(textarea:hover) {
  border-color: var(--color-border-strong);
}

.form-field__control :deep(input:focus),
.form-field__control :deep(select:focus),
.form-field__control :deep(textarea:focus) {
  outline: none;
  border-color: var(--color-sage);
  box-shadow: 0 0 0 3px rgba(61, 122, 102, 0.18);
}

.form-field__control :deep(textarea) {
  resize: vertical;
  min-height: 5rem;
}
</style>
