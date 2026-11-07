/**
 * Server-Sent Events client for GET /api/me/live-updates.
 *
 * Uses fetch + a streaming body reader instead of EventSource so the JWT travels in the
 * Authorization header (never in the URL / proxy logs). Reconnects with backoff, restarts when the
 * server closes the stream (it does every ~10 minutes), and treats 60s of silence as a dead
 * connection (the server sends a heartbeat every 20s). If streaming is unsupported or the account
 * may not subscribe (401/403), it simply stops — pages keep their 60s polling fallback.
 */
import { emitLiveUpdate } from '../utils/liveUpdates';

const LIVE_PATH = '/api/me/live-updates';

/**
 * Splits buffered SSE text into complete events. Returns the parsed events and the unparsed rest.
 * Comment lines (":ping") are ignored.
 */
export function parseSseBuffer(buffer) {
  const events = [];
  const normalized = buffer.replace(/\r\n/g, '\n').replace(/\r/g, '\n');
  const blocks = normalized.split('\n\n');
  const rest = blocks.pop() ?? '';
  for (const block of blocks) {
    let event = 'message';
    const data = [];
    for (const line of block.split('\n')) {
      if (!line || line.startsWith(':')) continue;
      const idx = line.indexOf(':');
      const field = idx === -1 ? line : line.slice(0, idx);
      let value = idx === -1 ? '' : line.slice(idx + 1);
      if (value.startsWith(' ')) value = value.slice(1);
      if (field === 'event') event = value;
      else if (field === 'data') data.push(value);
    }
    if (data.length) events.push({ event, data: data.join('\n') });
  }
  return { events, rest };
}

export function createLiveStream(options = {}) {
  const {
    baseURL = import.meta.env?.VITE_API_BASE || '',
    getToken,
    fetchImpl = (...args) => globalThis.fetch(...args),
    onUpdate = emitLiveUpdate,
    minDelayMs = 2000,
    maxDelayMs = 60_000,
    idleTimeoutMs = 60_000,
  } = options;

  let running = false;
  let controller = null;
  let retryTimer = null;
  let idleTimer = null;
  let attempt = 0;
  let connectedOnce = false;
  const state = { connected: false, stopped: true };

  function clearTimers() {
    clearTimeout(retryTimer);
    clearTimeout(idleTimer);
    retryTimer = null;
    idleTimer = null;
  }

  function scheduleReconnect(delay) {
    if (!running) return;
    clearTimeout(retryTimer);
    retryTimer = setTimeout(connect, delay);
  }

  function backoffDelay() {
    const d = Math.min(maxDelayMs, minDelayMs * 2 ** Math.min(attempt, 6));
    attempt += 1;
    return d;
  }

  function armIdle() {
    clearTimeout(idleTimer);
    idleTimer = setTimeout(() => controller?.abort(), idleTimeoutMs);
  }

  async function connect() {
    if (!running) return;
    const token = getToken?.();
    if (!token) {
      stop();
      return;
    }
    controller = new AbortController();
    let res;
    try {
      res = await fetchImpl(`${baseURL}${LIVE_PATH}`, {
        method: 'GET',
        headers: { Authorization: `Bearer ${token}`, Accept: 'text/event-stream' },
        cache: 'no-store',
        signal: controller.signal,
      });
    } catch {
      state.connected = false;
      scheduleReconnect(backoffDelay());
      return;
    }
    if (res.status === 401 || res.status === 403) {
      stop(); // not allowed: polling only
      return;
    }
    const reader = res.ok && res.body && typeof res.body.getReader === 'function' ? res.body.getReader() : null;
    if (!reader) {
      if (res.ok) {
        stop(); // streaming unsupported in this browser/WebView: polling only
      } else {
        scheduleReconnect(backoffDelay());
      }
      return;
    }

    state.connected = true;
    const decoder = new TextDecoder();
    let buffer = '';
    armIdle();
    try {
      for (;;) {
        const { value, done } = await reader.read();
        if (done) break;
        armIdle();
        buffer += decoder.decode(value, { stream: true });
        const parsed = parseSseBuffer(buffer);
        buffer = parsed.rest;
        for (const ev of parsed.events) {
          if (ev.event === 'ready') {
            attempt = 0;
            // Anything may have changed while we were disconnected.
            if (connectedOnce) onUpdate({ type: 'RECONNECTED' });
            connectedOnce = true;
            continue;
          }
          let detail = {};
          try {
            detail = JSON.parse(ev.data);
          } catch {
            detail = { type: ev.event };
          }
          onUpdate(detail);
        }
      }
    } catch {
      /* aborted (idle / stop) or network drop */
    } finally {
      clearTimeout(idleTimer);
      state.connected = false;
    }
    // Server closed the stream normally (timeout rotation) or it dropped: come back.
    scheduleReconnect(attempt === 0 ? 1000 : backoffDelay());
  }

  function start() {
    if (running) return;
    running = true;
    state.stopped = false;
    attempt = 0;
    connect();
  }

  function stop() {
    running = false;
    state.stopped = true;
    state.connected = false;
    clearTimers();
    try {
      controller?.abort();
    } catch {
      /* ignore */
    }
    controller = null;
  }

  return { start, stop, state };
}
