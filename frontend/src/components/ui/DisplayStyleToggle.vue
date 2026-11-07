<script setup>
import { useDisplayStyleStore } from '../../stores/displayStyle';

defineProps({
  /** 'panel' = settings card with heading; 'compact' = single row (side menu). */
  variant: { type: String, default: 'panel' },
});

const display = useDisplayStyleStore();
const inputId = `display-style-${Math.random().toString(36).slice(2, 8)}`;
</script>

<template>
  <section v-if="variant === 'panel'" class="surface-panel display-style display-style--panel">
    <h2>Appearance</h2>
    <label class="display-style__row" :for="inputId">
      <span class="display-style__text">
        <span class="display-style__title">Glass display style</span>
        <span class="display-style__hint muted">
          Frosted, translucent panels with soft depth. Works with light and dark mode. Saved for your account on this device.
        </span>
      </span>
      <input
        :id="inputId"
        type="checkbox"
        role="switch"
        class="display-style__switch"
        :checked="display.isGlass"
        :aria-checked="display.isGlass"
        @change="display.setStyle($event.target.checked ? 'glass' : 'classic')"
      />
    </label>
  </section>
  <label v-else class="display-style__row display-style--compact" :for="inputId">
    <span class="display-style__title">Glass style</span>
    <input
      :id="inputId"
      type="checkbox"
      role="switch"
      class="display-style__switch"
      :checked="display.isGlass"
      :aria-checked="display.isGlass"
      @change="display.setStyle($event.target.checked ? 'glass' : 'classic')"
    />
  </label>
</template>

<style scoped>
.display-style__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  cursor: pointer;
}

.display-style__text {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.display-style__title {
  font-weight: 600;
  color: var(--color-text);
}

.display-style--compact .display-style__title {
  font-size: 0.85rem;
  font-weight: 500;
  color: var(--color-text-secondary);
}

.display-style__hint {
  font-size: 0.85rem;
  line-height: 1.4;
}

/* Native checkbox restyled as a switch; keeps keyboard + screen-reader behaviour. */
.display-style__switch {
  appearance: none;
  -webkit-appearance: none;
  flex-shrink: 0;
  position: relative;
  width: 2.6rem;
  height: 1.5rem;
  margin: 0;
  border-radius: var(--radius-pill);
  border: 1px solid var(--color-border-strong);
  background: var(--color-bg-warm);
  cursor: pointer;
  transition: background var(--transition-fast), border-color var(--transition-fast);
}

.display-style__switch::after {
  content: '';
  position: absolute;
  top: 2px;
  left: 2px;
  width: calc(1.5rem - 6px);
  height: calc(1.5rem - 6px);
  border-radius: 50%;
  background: var(--color-surface-elevated);
  box-shadow: var(--shadow-sm);
  transition: transform var(--transition-fast);
}

.display-style__switch:checked {
  background: var(--color-sage);
  border-color: var(--color-sage);
}

.display-style__switch:checked::after {
  transform: translateX(1.1rem);
}

@media (prefers-reduced-motion: reduce) {
  .display-style__switch,
  .display-style__switch::after {
    transition: none;
  }
}
</style>
