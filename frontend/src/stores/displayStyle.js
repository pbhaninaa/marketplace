/**
 * Display style — how surfaces look (depth, translucency), independent of the light/dark theme.
 * Applied as `data-display="glass"` on <html>; styles live in src/styles/glass.css.
 * The choice is saved per signed-in user (keyed by email) so shared browsers keep each
 * person's preference. Classic (the default look) removes the attribute entirely.
 */
import { defineStore } from 'pinia';
import { computed, ref, watch } from 'vue';
import { useAuthStore } from './auth';

export const DISPLAY_STYLES = Object.freeze({
  CLASSIC: 'classic',
  GLASS: 'glass',
});

const KEY_PREFIX = 'agri_display_style';

export function displayStyleKey(email) {
  const who = String(email || '').trim().toLowerCase();
  return `${KEY_PREFIX}:${who || 'guest'}`;
}

export function normalizeDisplayStyle(value) {
  return value === DISPLAY_STYLES.GLASS ? DISPLAY_STYLES.GLASS : DISPLAY_STYLES.CLASSIC;
}

export function readDisplayStyle(email) {
  try {
    return normalizeDisplayStyle(localStorage.getItem(displayStyleKey(email)));
  } catch {
    return DISPLAY_STYLES.CLASSIC;
  }
}

export function writeDisplayStyle(email, style) {
  try {
    localStorage.setItem(displayStyleKey(email), normalizeDisplayStyle(style));
  } catch {
    /* storage unavailable (private mode / WebView) — style still applies for this session */
  }
}

export function applyDisplayStyle(style) {
  if (typeof document === 'undefined') return;
  const root = document.documentElement;
  if (normalizeDisplayStyle(style) === DISPLAY_STYLES.GLASS) root.setAttribute('data-display', DISPLAY_STYLES.GLASS);
  else root.removeAttribute('data-display');
}

export const useDisplayStyleStore = defineStore('displayStyle', () => {
  const auth = useAuthStore();
  const style = ref(readDisplayStyle(auth.email));
  const isGlass = computed(() => style.value === DISPLAY_STYLES.GLASS);
  let watching = false;

  function loadForUser(email) {
    style.value = readDisplayStyle(email);
    applyDisplayStyle(style.value);
  }

  /** Call once at startup: applies the saved style and follows sign-in / sign-out. */
  function init() {
    loadForUser(auth.email);
    if (watching) return;
    watching = true;
    watch(
      () => auth.email,
      (email) => loadForUser(email),
    );
  }

  function setStyle(next) {
    style.value = normalizeDisplayStyle(next);
    applyDisplayStyle(style.value);
    writeDisplayStyle(auth.email, style.value);
  }

  function toggle() {
    setStyle(isGlass.value ? DISPLAY_STYLES.CLASSIC : DISPLAY_STYLES.GLASS);
  }

  return { style, isGlass, init, loadForUser, setStyle, toggle };
});
