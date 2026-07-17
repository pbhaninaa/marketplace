/** Sync native bottom nav with Vue router + report login state. Mobile shell only. */
export const WEB_APP_STATE_BRIDGE = `
(function () {
  if (window.__wheelHubAppStateBridgeInstalled) return;
  window.__wheelHubAppStateBridgeInstalled = true;

  function sendState() {
    if (!window.ReactNativeWebView || !window.ReactNativeWebView.postMessage) return;
    try {
      var path = window.location.pathname || '/';
      var role = localStorage.getItem('agri_role') || '';
      var token = localStorage.getItem('agri_token') || '';
      var loggedIn = !!token;
      window.ReactNativeWebView.postMessage(JSON.stringify({
        type: 'WEB_APP_STATE',
        path: path,
        role: role,
        loggedIn: loggedIn
      }));
      if (typeof window.__wheelHubApplyRoleLayout === 'function') {
        window.__wheelHubApplyRoleLayout(role, loggedIn);
      }
    } catch (e) {}
  }

  window.__wheelHubSendAppState = sendState;

  function getRouter() {
    try {
      var el = document.getElementById('app');
      var app = el && el.__vue_app__;
      if (!app) return null;
      var router = app.config && app.config.globalProperties && app.config.globalProperties.$router;
      if (router) return router;
      var provides = app._context && app._context.provides;
      if (!provides) return null;
      var symbols = Object.getOwnPropertySymbols(provides);
      for (var i = 0; i < symbols.length; i++) {
        var candidate = provides[symbols[i]];
        if (candidate && typeof candidate.push === 'function' && candidate.currentRoute) {
          return candidate;
        }
      }
    } catch (e) {}
    return null;
  }

  function hookRouter() {
    var router = getRouter();
    if (!router || router.__wheelHubHooked) return;
    router.__wheelHubHooked = true;
    router.afterEach(function () { sendState(); });
    sendState();
  }

  sendState();
  window.addEventListener('popstate', sendState);
  window.addEventListener('hashchange', sendState);
  setInterval(function () {
    hookRouter();
    sendState();
  }, 800);
})();
true;
`;

/** SPA-first navigation — avoids full WebView reload. */
export function buildWebNavigateScript(path: string, baseUrl: string): string {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  const url = `${baseUrl.replace(/\\/+$/, '')}${normalizedPath}`;
  const safeUrl = JSON.stringify(url);
  const safePath = JSON.stringify(normalizedPath);
  return `
(function () {
  var path = ${safePath};
  var target = ${safeUrl};
  var current = (window.location.pathname || '/') + (window.location.search || '');
  if (current === path) return;

  function notifyNative() {
    if (typeof window.__wheelHubSendAppState === 'function') {
      window.__wheelHubSendAppState();
    }
  }

  function getRouter() {
    try {
      var el = document.getElementById('app');
      var app = el && el.__vue_app__;
      if (!app) return null;
      var router = app.config && app.config.globalProperties && app.config.globalProperties.$router;
      if (router) return router;
      var provides = app._context && app._context.provides;
      if (!provides) return null;
      var symbols = Object.getOwnPropertySymbols(provides);
      for (var i = 0; i < symbols.length; i++) {
        var candidate = provides[symbols[i]];
        if (candidate && typeof candidate.push === 'function' && candidate.currentRoute) {
          return candidate;
        }
      }
    } catch (e) {}
    return null;
  }

  var router = getRouter();
  if (router && typeof router.push === 'function') {
    router.push(path).then(notifyNative).catch(function () {
      window.location.assign(target);
    });
    return;
  }

  try {
    window.history.pushState({ marketplaceNav: true }, '', path);
    window.dispatchEvent(new PopStateEvent('popstate', { state: { marketplaceNav: true } }));
    notifyNative();
    if ((window.location.pathname || '/') === path) return;
  } catch (e) {}

  window.location.assign(target);
})();
true;
`;
}

export type WebAppStateMessage = {
  type: 'WEB_APP_STATE';
  path: string;
  role: string;
  loggedIn: boolean;
};

export function isWebAppStateMessage(raw: unknown): raw is WebAppStateMessage {
  if (!raw || typeof raw !== 'object') return false;
  const msg = raw as WebAppStateMessage;
  return msg.type === 'WEB_APP_STATE' && typeof msg.path === 'string';
}

export function buildWebAppUrl(baseUrl: string, path: string): string {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  return `${baseUrl.replace(/\\/+$/, '')}${normalizedPath}`;
}
