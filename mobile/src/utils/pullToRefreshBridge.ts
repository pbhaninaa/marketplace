/** Detect pull-down at scroll top and ask native shell to reload the page. */
export const PULL_TO_REFRESH_BRIDGE = `
(function () {
  if (window.__wheelHubPullRefreshInstalled) return;
  window.__wheelHubPullRefreshInstalled = true;

  var startY = 0;
  var tracking = false;
  var threshold = 72;

  function scrollTop() {
    var main = document.querySelector('.v-main');
    if (main && main.scrollTop > 0) return main.scrollTop;
    return window.scrollY
      || document.documentElement.scrollTop
      || document.body.scrollTop
      || 0;
  }

  function notifyRefresh() {
    if (!window.ReactNativeWebView || !window.ReactNativeWebView.postMessage) return;
    window.ReactNativeWebView.postMessage(JSON.stringify({ type: 'PULL_REFRESH' }));
  }

  document.addEventListener(
    'touchstart',
    function (e) {
      if (scrollTop() > 2) {
        tracking = false;
        return;
      }
      startY = e.touches[0].clientY;
      tracking = true;
    },
    { passive: true },
  );

  document.addEventListener(
    'touchmove',
    function (e) {
      if (!tracking) return;
      var delta = e.touches[0].clientY - startY;
      if (delta > threshold && scrollTop() <= 2) {
        tracking = false;
        notifyRefresh();
      }
    },
    { passive: true },
  );

  document.addEventListener(
    'touchend',
    function () {
      tracking = false;
    },
    { passive: true },
  );
})();
true;
`;

export type PullRefreshMessage = {
  type: 'PULL_REFRESH';
};

export function isPullRefreshMessage(raw: unknown): raw is PullRefreshMessage {
  if (!raw || typeof raw !== 'object') return false;
  return (raw as PullRefreshMessage).type === 'PULL_REFRESH';
}
