import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createLiveStream, parseSseBuffer } from '../src/services/liveStream';

const enc = new TextEncoder();

/** A fetch Response-like object whose body yields the given chunks, then stays open until aborted. */
function streamingResponse(chunks, { status = 200, signal, endAfter = false } = {}) {
  let i = 0;
  return {
    ok: status >= 200 && status < 300,
    status,
    body: {
      getReader() {
        return {
          read() {
            if (i < chunks.length) return Promise.resolve({ value: enc.encode(chunks[i++]), done: false });
            if (endAfter) return Promise.resolve({ value: undefined, done: true });
            return new Promise((_, reject) => {
              signal?.addEventListener('abort', () => reject(new Error('aborted')));
            });
          },
        };
      },
    },
  };
}

describe('parseSseBuffer', () => {
  it('parses complete events, skips heartbeats and keeps the partial tail', () => {
    const { events, rest } = parseSseBuffer(
      'event:ready\ndata:{"type":"READY"}\n\n:ping\n\nevent:update\ndata:{"type":"PAYMENT","providerId":7}\r\n\r\nevent:upd',
    );
    expect(events).toEqual([
      { event: 'ready', data: '{"type":"READY"}' },
      { event: 'update', data: '{"type":"PAYMENT","providerId":7}' },
    ]);
    expect(rest).toBe('event:upd');
  });
});

describe('createLiveStream', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('sends the JWT in the Authorization header and re-broadcasts pushes', async () => {
    const onUpdate = vi.fn();
    const fetchImpl = vi.fn((url, init) =>
      Promise.resolve(
        streamingResponse(
          ['event:ready\ndata:{"type":"READY"}\n\n', 'event:update\ndata:{"type":"ORDER_CREATED","providerId":3}\n\n'],
          { signal: init.signal },
        ),
      ),
    );
    const live = createLiveStream({ baseURL: 'https://api.example.com', getToken: () => 'tok', fetchImpl, onUpdate });
    live.start();
    await vi.advanceTimersByTimeAsync(0);

    expect(fetchImpl).toHaveBeenCalledTimes(1);
    const [url, init] = fetchImpl.mock.calls[0];
    expect(url).toBe('https://api.example.com/api/me/live-updates');
    expect(init.headers.Authorization).toBe('Bearer tok');
    expect(url).not.toContain('tok');
    expect(onUpdate).toHaveBeenCalledTimes(1);
    expect(onUpdate).toHaveBeenCalledWith({ type: 'ORDER_CREATED', providerId: 3 });
    live.stop();
  });

  it('gives up (polling only) when the account may not subscribe', async () => {
    const fetchImpl = vi.fn(() => Promise.resolve({ ok: false, status: 403, body: null }));
    const live = createLiveStream({ getToken: () => 'tok', fetchImpl, onUpdate: vi.fn() });
    live.start();
    await vi.advanceTimersByTimeAsync(120_000);
    expect(fetchImpl).toHaveBeenCalledTimes(1);
    expect(live.state.stopped).toBe(true);
  });

  it('reconnects with backoff after a network error and signals a catch-up reload', async () => {
    const onUpdate = vi.fn();
    let calls = 0;
    const fetchImpl = vi.fn((url, init) => {
      calls += 1;
      if (calls === 2) return Promise.reject(new Error('offline'));
      return Promise.resolve(
        streamingResponse(['event:ready\ndata:{}\n\n'], { signal: init.signal, endAfter: calls === 1 }),
      );
    });
    const live = createLiveStream({ getToken: () => 'tok', fetchImpl, onUpdate, minDelayMs: 2000 });
    live.start();
    await vi.advanceTimersByTimeAsync(0); // 1st connects, server closes the stream
    await vi.advanceTimersByTimeAsync(1000); // quick reconnect -> network error
    expect(fetchImpl).toHaveBeenCalledTimes(2);
    await vi.advanceTimersByTimeAsync(1999);
    expect(fetchImpl).toHaveBeenCalledTimes(2);
    await vi.advanceTimersByTimeAsync(1); // backoff elapsed -> 3rd connects
    expect(fetchImpl).toHaveBeenCalledTimes(3);
    expect(onUpdate).toHaveBeenCalledWith({ type: 'RECONNECTED' });
    live.stop();
  });

  it('treats a silent connection as dead and reconnects', async () => {
    const fetchImpl = vi.fn((url, init) => Promise.resolve(streamingResponse(['event:ready\ndata:{}\n\n'], { signal: init.signal })));
    const live = createLiveStream({ getToken: () => 'tok', fetchImpl, onUpdate: vi.fn(), idleTimeoutMs: 60_000 });
    live.start();
    await vi.advanceTimersByTimeAsync(0);
    expect(fetchImpl).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(61_000 + 1000);
    expect(fetchImpl).toHaveBeenCalledTimes(2);
    live.stop();
  });

  it('does not connect without a token', async () => {
    const fetchImpl = vi.fn();
    const live = createLiveStream({ getToken: () => '', fetchImpl });
    live.start();
    await vi.advanceTimersByTimeAsync(10_000);
    expect(fetchImpl).not.toHaveBeenCalled();
  });
});
