/**
 * App-wide "something changed on the server" signal.
 *
 * The single live-updates stream (services/liveStream.js, started in App.vue) re-broadcasts every
 * server push here, so open pages such as the dashboards can reload without each opening their own
 * connection.
 */
export const LIVE_UPDATE_EVENT = 'agrimarket:live-update';

export function emitLiveUpdate(detail = {}) {
  if (typeof window === 'undefined') return;
  try {
    window.dispatchEvent(new CustomEvent(LIVE_UPDATE_EVENT, { detail }));
  } catch {
    /* very old browsers without the CustomEvent constructor: pages fall back to polling */
  }
}

export function onLiveUpdate(handler) {
  if (typeof window === 'undefined') return () => {};
  const listener = (e) => handler(e?.detail || {});
  window.addEventListener(LIVE_UPDATE_EVENT, listener);
  return () => window.removeEventListener(LIVE_UPDATE_EVENT, listener);
}
