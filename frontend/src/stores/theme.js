import { defineStore } from 'pinia';
import { computed, ref } from 'vue';

const THEME_KEY = 'agri_theme';

function systemPrefersDark() {
  try {
    return window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
  } catch {
    return false;
  }
}

function applyThemeToDom(mode) {
  const root = document.documentElement;
  root.dataset.theme = mode;
  root.style.colorScheme = mode;
}

export const useThemeStore = defineStore('theme', () => {
  const mode = ref(localStorage.getItem(THEME_KEY) || '');

  const resolvedMode = computed(() => {
    if (mode.value === 'dark' || mode.value === 'light') return mode.value;
    return systemPrefersDark() ? 'dark' : 'light';
  });

  function init() {
    applyThemeToDom(resolvedMode.value);
  }

  function setMode(next) {
    if (next !== 'dark' && next !== 'light') return;
    mode.value = next;
    localStorage.setItem(THEME_KEY, next);
    applyThemeToDom(next);
  }

  function toggle() {
    setMode(resolvedMode.value === 'dark' ? 'light' : 'dark');
  }

  return { mode, resolvedMode, init, setMode, toggle };
});

