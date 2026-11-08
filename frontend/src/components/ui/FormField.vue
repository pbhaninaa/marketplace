<script setup>
import { ref, onMounted, onUpdated, onUnmounted, nextTick, watch, useId } from 'vue';

const props = defineProps({
  label: { type: String, default: '' },
  forId: { type: String, default: '' },
  error: { type: String, default: '' },
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

const autoId = `ff-${useId()}`;
const errorId = `${autoId}-error`;
const labelFor = ref(props.forId || '');

/**
 * Connect the label to the first control (so clicking the label focuses it and screen readers announce
 * it) and expose the error state, which also drives the shared invalid styling in forms.css.
 */
function linkControl() {
  const root = controlRef.value;
  if (!root) return;
  // AppSelect's button comes before its hidden native <select>, so it wins in document order.
  const el = root.querySelector(
    'button[role="combobox"], input:not([type="hidden"]), select:not([aria-hidden="true"]), textarea',
  );
  if (!el) return;
  if (!props.forId) {
    if (!el.id) el.id = autoId;
    labelFor.value = el.id;
  }
  if (props.error) {
    el.setAttribute('aria-invalid', 'true');
    el.setAttribute('aria-describedby', errorId);
  } else {
    el.removeAttribute('aria-invalid');
    if (el.getAttribute('aria-describedby') === errorId) el.removeAttribute('aria-describedby');
  }
}

function scheduleBind() {
  nextTick(() => {
    bindCapitalize();
    linkControl();
  });
}
watch(() => props.error, scheduleBind);

onMounted(scheduleBind);
onUpdated(scheduleBind);
watch(
  () => props.capitalizeFirst,
  () => scheduleBind(),
);
onUnmounted(() => detach?.());
</script>

<template>
  <div class="form-field" :class="{ 'form-field--has-error': error }">
    <label v-if="label" :for="forId || labelFor || undefined" class="form-field__label ctl-label">{{ label }}</label>
    <div ref="controlRef" class="form-field__control">
      <slot />
    </div>
    <p v-if="error" :id="errorId" class="form-field__error ctl-error" role="alert">{{ error }}</p>
  </div>
</template>

<style scoped>
/* Visuals for the control itself live in src/styles/forms.css (shared by every form). */
.form-field {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  min-width: 0;
  /* Same rhythm in every form: clearly more space between fields than between a label and its field. */
  margin-bottom: 0.75rem;
}

.form-field__control :deep(:where(input:not([type='checkbox']):not([type='radio']), select, textarea)) {
  width: 100%;
}
</style>
