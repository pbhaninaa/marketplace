/**
 * Keeps a page's data fresh without the user reloading:
 *  - reloads shortly after a real-time push from the server (see utils/liveUpdates.js),
 *    debounced so a burst of pushes causes one reload;
 *  - reloads when the user comes back to the tab and the data is older than `staleMs`;
 *  - falls back to a background refresh every `pollMs` (60s) for when the push channel is down.
 *
 * Paused while the tab is hidden or the page is cached by <keep-alive> but not shown. Never runs
 * two reloads at once. The page still does its own first load on mount.
 */
import { onActivated, onBeforeUnmount, onDeactivated, onMounted, ref } from 'vue';
import { onLiveUpdate } from '../utils/liveUpdates';

export const LIVE_REFRESH_DEFAULTS = {
  debounceMs: 1200,
  pollMs: 60_000,
  staleMs: 15_000,
};

export function createLiveRefresher(refresh, options = {}) {
  const { debounceMs, pollMs, staleMs } = { ...LIVE_REFRESH_DEFAULTS, ...options };
  const now = options.now || (() => Date.now());
  const isHidden = options.isHidden || (() => typeof document !== 'undefined' && document.hidden);
  /** Optional predicate on the push detail, e.g. to ignore unrelated event types. */
  const accept = options.accept || (() => true);

  const lastUpdated = ref(null);
  const refreshing = ref(false);

  let active = false;
  let dirty = false;
  let pending = false;
  let debounceTimer = null;
  let pollTimer = null;
  let unsubscribe = null;

  async function run() {
    clearTimeout(debounceTimer);
    debounceTimer = null;
    if (refreshing.value) {
      pending = true;
      return;
    }
    refreshing.value = true;
    dirty = false;
    try {
      await refresh();
      lastUpdated.value = now();
    } catch {
      /* the page shows its own error state; keep the old data on screen */
    } finally {
      refreshing.value = false;
      if (pending && active) {
        pending = false;
        schedule();
      }
    }
  }

  function schedule() {
    clearTimeout(debounceTimer);
    debounceTimer = setTimeout(run, debounceMs);
  }

  function onPush(detail) {
    if (!active || !accept(detail || {})) return;
    if (isHidden()) {
      dirty = true;
      return;
    }
    schedule();
  }

  function onVisible() {
    if (!active || isHidden()) return;
    const age = lastUpdated.value == null ? Infinity : now() - lastUpdated.value;
    if (dirty || age > staleMs) void run();
  }

  function onPoll() {
    if (!active || isHidden() || refreshing.value) return;
    const age = lastUpdated.value == null ? Infinity : now() - lastUpdated.value;
    if (age >= pollMs - 500) void run();
  }

  function start() {
    if (active) return;
    active = true;
    unsubscribe = onLiveUpdate(onPush);
    if (typeof document !== 'undefined') document.addEventListener('visibilitychange', onVisible);
    if (typeof window !== 'undefined') window.addEventListener('focus', onVisible);
    if (pollMs > 0) pollTimer = setInterval(onPoll, pollMs);
  }

  function stop() {
    active = false;
    clearTimeout(debounceTimer);
    clearInterval(pollTimer);
    debounceTimer = null;
    pollTimer = null;
    unsubscribe?.();
    unsubscribe = null;
    if (typeof document !== 'undefined') document.removeEventListener('visibilitychange', onVisible);
    if (typeof window !== 'undefined') window.removeEventListener('focus', onVisible);
  }

  /** The page just loaded its data itself (first mount). */
  function markFresh() {
    lastUpdated.value = now();
    dirty = false;
  }

  return { start, stop, run, markFresh, onVisible, lastUpdated, refreshing };
}

export function useLiveRefresh(refresh, options = {}) {
  const r = createLiveRefresher(refresh, options);

  onMounted(() => {
    r.markFresh();
    r.start();
  });
  // Inside <keep-alive>: pause when the page is hidden, catch up when it is shown again.
  onActivated(() => {
    r.start();
    r.onVisible();
  });
  onDeactivated(r.stop);
  onBeforeUnmount(r.stop);

  return { refreshNow: r.run, lastUpdated: r.lastUpdated, refreshing: r.refreshing, markFresh: r.markFresh };
}
