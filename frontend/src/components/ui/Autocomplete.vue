<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue';

const props = defineProps({
  modelValue: { type: [String, Number], default: '' },
  options: { type: Array, default: () => [] },
  placeholder: { type: String, default: 'Search...' },
  /** Option display key (default: 'name') */
  labelKey: { type: String, default: 'name' },
  /** Option value key (default: 'id') */
  valueKey: { type: String, default: 'id' },
  /** Label to show when no option selected */
  emptyLabel: { type: String, default: 'All' },
});

const emit = defineEmits(['update:modelValue']);

const searchQuery = ref('');
const isOpen = ref(false);
const highlightIndex = ref(-1);
const inputRef = ref(null);
const dropdownRef = ref(null);
const dropdownPosition = ref({ top: 0, left: 0, width: 0 });

const selectedOption = computed(() => {
  if (!props.modelValue) return null;
  return props.options.find(opt => String(opt[props.valueKey]) === String(props.modelValue));
});

const displayValue = computed(() => {
  if (selectedOption.value) {
    return selectedOption.value[props.labelKey];
  }
  return '';
});

const filteredOptions = computed(() => {
  if (!searchQuery.value.trim()) {
    return props.options;
  }
  const query = searchQuery.value.toLowerCase();
  return props.options.filter(opt =>
    opt[props.labelKey]?.toLowerCase().includes(query)
  );
});

function openDropdown() {
  if (inputRef.value) {
    const rect = inputRef.value.getBoundingClientRect();
    dropdownPosition.value = {
      top: rect.bottom + window.scrollY,
      left: rect.left + window.scrollX,
      width: rect.width,
    };
  }
  isOpen.value = true;
  highlightIndex.value = -1;
  searchQuery.value = '';
  // Focus input after opening
  setTimeout(() => {
    const input = inputRef.value?.querySelector('input');
    if (input) input.focus();
  }, 0);
}

function closeDropdown() {
  isOpen.value = false;
  searchQuery.value = '';
  highlightIndex.value = -1;
}

function selectOption(option) {
  emit('update:modelValue', option ? String(option[props.valueKey]) : '');
  closeDropdown();
}

function clearSelection() {
  emit('update:modelValue', '');
  searchQuery.value = '';
  inputRef.value?.focus();
}

function handleKeydown(e) {
  if (!isOpen.value) {
    if (e.key === 'ArrowDown' || e.key === 'Enter') {
      e.preventDefault();
      openDropdown();
    }
    return;
  }

  switch (e.key) {
    case 'ArrowDown':
      e.preventDefault();
      highlightIndex.value = Math.min(highlightIndex.value + 1, filteredOptions.value.length - 1);
      scrollToHighlighted();
      break;
    case 'ArrowUp':
      e.preventDefault();
      highlightIndex.value = Math.max(highlightIndex.value - 1, -1);
      scrollToHighlighted();
      break;
    case 'Enter':
      e.preventDefault();
      if (highlightIndex.value >= 0 && filteredOptions.value[highlightIndex.value]) {
        selectOption(filteredOptions.value[highlightIndex.value]);
      } else if (highlightIndex.value === -1) {
        selectOption(null);
      }
      break;
    case 'Escape':
      e.preventDefault();
      closeDropdown();
      break;
  }
}

function scrollToHighlighted() {
  if (!dropdownRef.value || highlightIndex.value < 0) return;
  const highlightedEl = dropdownRef.value.querySelector(`[data-index="${highlightIndex.value}"]`);
  if (highlightedEl) {
    highlightedEl.scrollIntoView({ block: 'nearest' });
  }
}

function handleClickOutside(e) {
  if (!inputRef.value?.contains(e.target) && !dropdownRef.value?.contains(e.target)) {
    closeDropdown();
  }
}

watch(() => props.modelValue, () => {
  searchQuery.value = '';
});

onMounted(() => {
  document.addEventListener('click', handleClickOutside);
});

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside);
});
</script>

<template>
  <div class="autocomplete">
    <div
      ref="inputRef"
      class="autocomplete__input"
      :class="{ 'autocomplete__input--open': isOpen }"
    >
      <input
        v-if="isOpen"
        v-model="searchQuery"
        type="text"
        :placeholder="placeholder"
        @keydown="handleKeydown"
        @focus="openDropdown"
      />
      <div
        v-else
        class="autocomplete__display"
        tabindex="0"
        @click="openDropdown"
        @keydown="handleKeydown"
      >
        <span v-if="displayValue" class="autocomplete__selected">{{ displayValue }}</span>
        <span v-else class="autocomplete__placeholder">{{ emptyLabel }}</span>
      </div>
      <button
        v-if="modelValue && !isOpen"
        type="button"
        class="autocomplete__clear"
        aria-label="Clear selection"
        @click.stop="clearSelection"
      >
        ✕
      </button>
      <span class="autocomplete__arrow" :class="{ 'autocomplete__arrow--up': isOpen }" aria-hidden="true">
        ▼
      </span>
    </div>

    <Teleport to="body">
      <div
        v-if="isOpen"
        ref="dropdownRef"
        class="autocomplete__dropdown"
        :style="{
          position: 'absolute',
          top: `${dropdownPosition.top + 2}px`,
          left: `${dropdownPosition.left}px`,
          width: `${dropdownPosition.width}px`,
        }"
      >
        <div
          class="autocomplete__option"
          :class="{ 'autocomplete__option--highlighted': highlightIndex === -1 }"
          :data-index="-1"
          @click="selectOption(null)"
          @mouseenter="highlightIndex = -1"
        >
          {{ emptyLabel }}
        </div>
        <div
          v-for="(option, idx) in filteredOptions"
          :key="option[valueKey]"
          class="autocomplete__option"
          :class="{ 'autocomplete__option--highlighted': highlightIndex === idx }"
          :data-index="idx"
          @click="selectOption(option)"
          @mouseenter="highlightIndex = idx"
        >
          {{ option[labelKey] }}
        </div>
        <div v-if="searchQuery && filteredOptions.length === 0" class="autocomplete__empty">
          No results found
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.autocomplete {
  position: relative;
}

.autocomplete__input {
  position: relative;
  width: 100%;
}

.autocomplete__input input {
  width: 100%;
  padding: 0.55rem 2.5rem 0.55rem 0.75rem;
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

.autocomplete__input input:hover {
  border-color: var(--color-border-strong);
}

.autocomplete__input input:focus {
  outline: none;
  border-color: var(--color-sage);
  box-shadow: 0 0 0 3px rgba(61, 122, 102, 0.18);
}

.autocomplete__display {
  width: 100%;
  padding: 0.55rem 2.5rem 0.55rem 0.75rem;
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-border);
  font: inherit;
  font-size: 0.95rem;
  color: var(--color-text);
  background: var(--color-surface-elevated);
  cursor: pointer;
  transition:
    border-color 0.15s ease,
    box-shadow 0.15s ease;
  display: flex;
  align-items: center;
  min-height: 2.4rem;
  box-sizing: border-box;
}

.autocomplete__display:hover {
  border-color: var(--color-border-strong);
}

.autocomplete__display:focus {
  outline: none;
  border-color: var(--color-sage);
  box-shadow: 0 0 0 3px rgba(61, 122, 102, 0.18);
}

.autocomplete__input--open .autocomplete__display {
  border-color: var(--color-sage);
  box-shadow: 0 0 0 3px rgba(61, 122, 102, 0.18);
}

.autocomplete__selected {
  color: var(--color-text);
}

.autocomplete__placeholder {
  color: var(--color-muted);
}

.autocomplete__clear {
  position: absolute;
  right: 2rem;
  top: 50%;
  transform: translateY(-50%);
  width: 1.2rem;
  height: 1.2rem;
  border: none;
  background: transparent;
  color: var(--color-muted);
  cursor: pointer;
  font-size: 0.75rem;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  transition: all 0.15s ease;
  padding: 0;
  line-height: 1;
}

.autocomplete__clear:hover {
  background: rgba(0, 0, 0, 0.1);
  color: var(--color-text);
}

.autocomplete__arrow {
  position: absolute;
  right: 0.75rem;
  top: 50%;
  transform: translateY(-50%);
  color: var(--color-muted);
  font-size: 0.65rem;
  pointer-events: none;
  transition: transform 0.2s ease;
}

.autocomplete__arrow--up {
  transform: translateY(-50%) rotate(180deg);
}

.autocomplete__dropdown {
  max-height: 16rem;
  overflow-y: auto;
  background: var(--color-surface-elevated);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  box-shadow: var(--shadow-md);
  z-index: 9999;
  padding: 0.25rem;
}

.autocomplete__option {
  padding: 0.5rem 0.75rem;
  cursor: pointer;
  border-radius: var(--radius-sm);
  font-size: 0.95rem;
  color: var(--color-text);
  transition: background-color 0.1s ease;
}

.autocomplete__option:hover,
.autocomplete__option--highlighted {
  background: var(--color-sage-soft);
}

.autocomplete__empty {
  padding: 0.75rem;
  text-align: center;
  color: var(--color-muted);
  font-size: 0.9rem;
}

/* Scrollbar styling */
.autocomplete__dropdown::-webkit-scrollbar {
  width: 8px;
}

.autocomplete__dropdown::-webkit-scrollbar-track {
  background: transparent;
}

.autocomplete__dropdown::-webkit-scrollbar-thumb {
  background: var(--color-border);
  border-radius: 4px;
}

.autocomplete__dropdown::-webkit-scrollbar-thumb:hover {
  background: var(--color-border-strong);
}
</style>
