<script setup>
const props = defineProps({
  variant: {
    type: String,
    default: 'primary',
    validator: (v) => ['primary', 'secondary', 'ghost', 'danger', 'success'].includes(v),
  },
  size: {
    type: String,
    default: 'md',
    validator: (v) => ['sm', 'md', 'lg'].includes(v),
  },
  disabled: {
    type: Boolean,
    default: false,
  },
  type: {
    type: String,
    default: 'button',
  },
  isLoading: {
    type: Boolean,
    default: false,
  },
  icon: {
    type: String,
    default: '',
  },
  iconPosition: {
    type: String,
    default: 'left',
    validator: (v) => ['left', 'right'].includes(v),
  },
});

const emit = defineEmits(['click']);
</script>

<template>
  <button
    :type="type"
    :disabled="disabled || isLoading"
    :class="[
      'base-button',
      `base-button--${variant}`,
      `base-button--${size}`,
      {
        'base-button--loading': isLoading,
        'base-button--disabled': disabled,
      },
    ]"
    @click="emit('click')"
  >
    <span v-if="icon && iconPosition === 'left'" class="base-button__icon">
      <span class="material-icons">{{ icon }}</span>
    </span>
    <span class="base-button__text">
      {{ isLoading ? 'Processing...' : $slots.default?.()[0]?.children }}
    </span>
    <span v-if="icon && iconPosition === 'right'" class="base-button__icon">
      <span class="material-icons">{{ icon }}</span>
    </span>
  </button>
</template>

<style scoped>
.base-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  border: none;
  border-radius: var(--radius-md);
  font-family: var(--font-ui);
  font-weight: 600;
  cursor: pointer;
  transition: all var(--transition-fast);
  white-space: nowrap;
  text-decoration: none;
}

/* ==================== SIZES ==================== */
.base-button--sm {
  padding: 0.4rem 0.8rem;
  font-size: 0.85rem;
}

.base-button--md {
  padding: 0.6rem 1.2rem;
  font-size: 0.95rem;
}

.base-button--lg {
  padding: 0.8rem 1.6rem;
  font-size: 1.05rem;
}

/* ==================== VARIANTS ==================== */
.base-button--primary {
  background: var(--color-canopy);
  color: white;
  border: 1px solid var(--color-canopy);
}

.base-button--primary:hover:not(:disabled) {
  background: var(--color-canopy-mid);
  border-color: var(--color-canopy-mid);
  box-shadow: 0 4px 12px rgba(26, 60, 52, 0.2);
}

.base-button--primary:active:not(:disabled) {
  transform: scale(0.98);
}

.base-button--secondary {
  background: var(--color-sage-soft);
  color: var(--color-canopy);
  border: 1px solid var(--color-sage);
}

.base-button--secondary:hover:not(:disabled) {
  background: var(--color-sage);
  color: white;
}

.base-button--ghost {
  background: transparent;
  color: var(--color-canopy);
  border: 1px solid var(--color-border);
}

.base-button--ghost:hover:not(:disabled) {
  background: var(--color-surface);
  border-color: var(--color-canopy);
}

.base-button--danger {
  background: #8b2c1f;
  color: white;
  border: 1px solid #8b2c1f;
}

.base-button--danger:hover:not(:disabled) {
  background: #a63b2a;
  border-color: #a63b2a;
  box-shadow: 0 4px 12px rgba(139, 44, 31, 0.2);
}

.base-button--success {
  background: #1a4d2e;
  color: white;
  border: 1px solid #1a4d2e;
}

.base-button--success:hover:not(:disabled) {
  background: #1e5a36;
  border-color: #1e5a36;
  box-shadow: 0 4px 12px rgba(26, 77, 46, 0.2);
}

/* ==================== STATES ==================== */
.base-button:disabled,
.base-button--disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.base-button--loading {
  opacity: 0.7;
  cursor: wait;
}

/* ==================== ICON ==================== */
.base-button__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.base-button__icon .material-icons {
  font-size: 1.2rem;
}

.base-button--sm .base-button__icon .material-icons {
  font-size: 1rem;
}
</style>
