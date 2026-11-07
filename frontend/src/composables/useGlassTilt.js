/**
 * Glass display style: clickable cards tilt toward the mouse with a moving glare (3D feel).
 *
 * One delegated, rAF-throttled pointermove listener for the whole app — no per-card setup.
 * It only writes CSS variables (--g-rx, --g-ry, --g-mx, --g-my) and the `g-tilting` class;
 * glass.css decides what they do, so the Classic look is unaffected. Skipped for touch/pen,
 * when Glass is off, and when the user prefers reduced motion.
 */

export const TILT_SELECTOR = '.main-area a.tool-card, .main-area a.dash-card, .main-area [data-glass-tilt]';
const MAX_DEG = 6;
/** Big cards tilt less, so wide panels don't swing like doors. */
const COMFORT_SIZE = 320;
const MIN_SCALE = 0.3;

let installed = false;

function isGlass() {
  return document.documentElement.getAttribute('data-display') === 'glass';
}

function reset(el) {
  el.classList.remove('g-tilting');
  el.style.removeProperty('--g-rx');
  el.style.removeProperty('--g-ry');
  el.style.removeProperty('--g-mx');
  el.style.removeProperty('--g-my');
}

/** Pure helper (exported for tests): tilt angles and glare position for a pointer over a rect. */
export function computeTilt(rect, clientX, clientY) {
  if (!rect || !rect.width || !rect.height) return null;
  const px = Math.min(1, Math.max(0, (clientX - rect.left) / rect.width));
  const py = Math.min(1, Math.max(0, (clientY - rect.top) / rect.height));
  const deg = MAX_DEG * Math.max(MIN_SCALE, Math.min(1, COMFORT_SIZE / Math.max(rect.width, rect.height)));
  return {
    ry: (px - 0.5) * 2 * deg,
    rx: (0.5 - py) * 2 * deg,
    mx: px * 100,
    my: py * 100,
  };
}

export function installGlassTilt() {
  if (installed || typeof window === 'undefined' || typeof document === 'undefined') return;
  installed = true;

  let reducedMotion = null;
  try {
    reducedMotion = window.matchMedia ? window.matchMedia('(prefers-reduced-motion: reduce)') : null;
  } catch {
    reducedMotion = null;
  }
  let active = null;
  let lastEvent = null;
  let frame = 0;

  function clear() {
    if (active) reset(active);
    active = null;
  }

  function update() {
    frame = 0;
    const e = lastEvent;
    if (!e) return;
    const target = e.target instanceof Element ? e.target : null;
    const el = isGlass() && !reducedMotion?.matches && target ? target.closest(TILT_SELECTOR) : null;

    if (el !== active) {
      clear();
      active = el;
    }
    if (!el) return;

    const t = computeTilt(el.getBoundingClientRect(), e.clientX, e.clientY);
    if (!t) return;
    el.style.setProperty('--g-ry', `${t.ry.toFixed(2)}deg`);
    el.style.setProperty('--g-rx', `${t.rx.toFixed(2)}deg`);
    el.style.setProperty('--g-mx', `${t.mx.toFixed(1)}%`);
    el.style.setProperty('--g-my', `${t.my.toFixed(1)}%`);
    el.classList.add('g-tilting');
  }

  document.addEventListener(
    'pointermove',
    (e) => {
      if (e.pointerType && e.pointerType !== 'mouse') return;
      lastEvent = e;
      if (!frame) {
        // Mark as scheduled before asking, so a callback that runs immediately can still clear it.
        frame = 1;
        window.requestAnimationFrame(update);
      }
    },
    { passive: true },
  );
  // Pointer left the window or the tab lost focus: let the card settle back.
  document.addEventListener('mouseout', (e) => {
    if (!e.relatedTarget) clear();
  });
  window.addEventListener('blur', clear);
}
