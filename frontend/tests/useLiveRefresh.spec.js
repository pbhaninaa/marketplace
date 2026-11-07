import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createLiveRefresher } from '../src/composables/useLiveRefresh';
import { emitLiveUpdate } from '../src/utils/liveUpdates';

describe('createLiveRefresher', () => {
  let hidden;
  let refresh;
  let r;

  beforeEach(() => {
    vi.useFakeTimers();
    hidden = false;
    refresh = vi.fn().mockResolvedValue(undefined);
    r = createLiveRefresher(refresh, {
      debounceMs: 1000,
      pollMs: 60_000,
      staleMs: 15_000,
      now: () => Date.now(),
      isHidden: () => hidden,
    });
    r.markFresh();
    r.start();
  });

  afterEach(() => {
    r.stop();
    vi.useRealTimers();
  });

  it('reloads once shortly after a burst of server pushes', async () => {
    emitLiveUpdate({ type: 'ORDER_CREATED' });
    emitLiveUpdate({ type: 'PAYMENT' });
    emitLiveUpdate({ type: 'ORDER_STATUS' });
    expect(refresh).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(1000);
    expect(refresh).toHaveBeenCalledTimes(1);
    expect(r.lastUpdated.value).toBe(Date.now());
  });

  it('waits while the tab is hidden, then catches up when it is shown', async () => {
    hidden = true;
    emitLiveUpdate({});
    await vi.advanceTimersByTimeAsync(5000);
    expect(refresh).not.toHaveBeenCalled();

    hidden = false;
    document.dispatchEvent(new Event('visibilitychange'));
    await vi.advanceTimersByTimeAsync(0);
    expect(refresh).toHaveBeenCalledTimes(1);
  });

  it('refreshes on return to the tab only when the data is stale', async () => {
    window.dispatchEvent(new Event('focus'));
    await vi.advanceTimersByTimeAsync(0);
    expect(refresh).not.toHaveBeenCalled();

    vi.setSystemTime(Date.now() + 16_000);
    window.dispatchEvent(new Event('focus'));
    await vi.advanceTimersByTimeAsync(0);
    expect(refresh).toHaveBeenCalledTimes(1);
  });

  it('falls back to a background refresh every minute while visible', async () => {
    await vi.advanceTimersByTimeAsync(60_000);
    expect(refresh).toHaveBeenCalledTimes(1);
    hidden = true;
    await vi.advanceTimersByTimeAsync(120_000);
    expect(refresh).toHaveBeenCalledTimes(1);
  });

  it('never runs two reloads at once; a push during a reload queues one more', async () => {
    let finish;
    refresh.mockImplementationOnce(() => new Promise((res) => (finish = res)));
    const first = r.run();
    emitLiveUpdate({});
    await vi.advanceTimersByTimeAsync(1000); // debounce fires while the first reload is still running
    expect(refresh).toHaveBeenCalledTimes(1);
    finish();
    await first;
    await vi.advanceTimersByTimeAsync(1000);
    expect(refresh).toHaveBeenCalledTimes(2);
  });

  it('keeps the old timestamp when a background reload fails', async () => {
    const before = r.lastUpdated.value;
    vi.setSystemTime(Date.now() + 5000);
    refresh.mockRejectedValueOnce(new Error('offline'));
    await r.run();
    expect(r.lastUpdated.value).toBe(before);
    expect(r.refreshing.value).toBe(false);
  });

  it('ignores pushes rejected by the accept filter', async () => {
    r.stop();
    const filtered = createLiveRefresher(refresh, {
      debounceMs: 1000,
      pollMs: 0,
      isHidden: () => false,
      accept: (d) => d.type !== 'SUBSCRIPTION',
    });
    filtered.start();
    emitLiveUpdate({ type: 'SUBSCRIPTION' });
    await vi.advanceTimersByTimeAsync(2000);
    expect(refresh).not.toHaveBeenCalled();
    emitLiveUpdate({ type: 'ORDER_CREATED' });
    await vi.advanceTimersByTimeAsync(1000);
    expect(refresh).toHaveBeenCalledTimes(1);
    filtered.stop();
  });

  it('stops listening after stop()', async () => {
    r.stop();
    emitLiveUpdate({});
    await vi.advanceTimersByTimeAsync(120_000);
    expect(refresh).not.toHaveBeenCalled();
  });
});
