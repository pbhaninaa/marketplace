/**
 * Native shell layout — exactly one nav mode at a time (mobile app only).
 * bottom: hide web chrome | hamburger: show web menu | none: auth screens
 */
import type { MobileNavMode } from '../navigation/navigationMode';
import { resolveMobileNavMode } from '../navigation/navigationMode';

export const MOBILE_VIEWPORT_BRIDGE = `
(function () {
  if (window.__wheelHubViewportBridgeInstalled) return;
  window.__wheelHubViewportBridgeInstalled = true;
  window.__wheelHubNativeShell = true;

  var SHELL = 'servicehub-native-shell';
  var MODES = ['servicehub-nav-bottom', 'servicehub-nav-hamburger', 'servicehub-nav-none'];

  function markShell() {
    document.documentElement.classList.add(SHELL);
    if (document.body) document.body.classList.add(SHELL);
  }

  function readLoggedIn() {
    try {
      return !!(localStorage.getItem('agri_token') || '');
    } catch (e) {
      return false;
    }
  }

  function resolveMode(role, loggedIn) {
    var path = window.location.pathname || '/';
    var auth = ['/login','/register','/forgot-password','/reset-password','/setup'];
    if (auth.indexOf(path) >= 0) return 'none';
    var r = (role || localStorage.getItem('agri_role') || '').toUpperCase().trim();
    if (r === 'PLATFORM_ADMIN' || r === 'SUPPORT' || r.indexOf('PROVIDER_') === 0) return 'hamburger';
    return 'bottom';
  }

  function setNavMode(mode) {
    var root = document.documentElement;
    for (var i = 0; i < MODES.length; i++) {
      root.classList.remove(MODES[i]);
    }
    if (mode === 'bottom') root.classList.add('servicehub-nav-bottom');
    if (mode === 'hamburger') root.classList.add('servicehub-nav-hamburger');
    if (mode === 'none') root.classList.add('servicehub-nav-none');
    syncDomNav(mode);
  }

  function syncDomNav(mode) {
    var hideWebNav = mode === 'bottom';
    var selectors = '.top-bar, .side-nav, .side-nav-backdrop, .hamburger, .v-app-bar, .v-navigation-drawer, .v-navigation-drawer__scrim';
    var nodes = document.querySelectorAll(selectors);
    for (var i = 0; i < nodes.length; i++) {
      nodes[i].style.setProperty('display', hideWebNav ? 'none' : '', 'important');
    }
  }

  function applyRoleLayout(role, loggedIn) {
    var mode = resolveMode(role, loggedIn !== false && (loggedIn || readLoggedIn()));
    setNavMode(mode);
  }

  window.__wheelHubApplyRoleLayout = applyRoleLayout;

  function shellCss() {
    return [
      'html.' + SHELL + ', html.' + SHELL + ' body {',
      '  width: 100% !important;',
      '  min-height: 100% !important;',
      '  margin: 0 !important;',
      '  padding: 0 !important;',
      '  overflow-x: hidden !important;',
      '  -webkit-text-size-adjust: 100%;',
      '}',
      'html.' + SHELL + ' #app { min-height: 100% !important; }',
      'html.servicehub-nav-bottom .top-bar,',
      'html.servicehub-nav-bottom .side-nav,',
      'html.servicehub-nav-bottom .side-nav-backdrop,',
      'html.servicehub-nav-bottom .hamburger,',
      'html.' + SHELL + '.servicehub-nav-bottom .top-bar,',
      'html.' + SHELL + '.servicehub-nav-bottom .side-nav,',
      'html.' + SHELL + '.servicehub-nav-bottom .side-nav-backdrop,',
      'html.' + SHELL + '.servicehub-nav-bottom .hamburger {',
      '  display: none !important;',
      '  visibility: hidden !important;',
      '  pointer-events: none !important;',
      '}',
      'html.' + SHELL + '.servicehub-nav-bottom .app-shell {',
      '  padding-top: 0 !important;',
      '  padding-bottom: calc(72px + env(safe-area-inset-bottom, 0px)) !important;',
      '}',
    ].join('\\n');
  }

  function applyViewport() {
    markShell();
    var meta = document.querySelector('meta[name="viewport"]');
    if (!meta) {
      meta = document.createElement('meta');
      meta.setAttribute('name', 'viewport');
      document.head.appendChild(meta);
    }
    meta.setAttribute('content', 'width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no');
    var style = document.getElementById('servicehub-mobile-shell-style');
    if (!style) {
      style = document.createElement('style');
      style.id = 'servicehub-mobile-shell-style';
      document.head.appendChild(style);
    }
    style.textContent = shellCss();
    applyRoleLayout(null, readLoggedIn());
  }

  applyViewport();
  document.addEventListener('DOMContentLoaded', applyViewport);
  if (typeof MutationObserver !== 'undefined') {
    var observer = new MutationObserver(function () {
      markShell();
      syncDomNav(resolveMode(null, readLoggedIn()));
    });
    observer.observe(document.documentElement, { childList: true, subtree: true });
  }
  setInterval(function () {
    applyRoleLayout(null, readLoggedIn());
  }, 1500);
})();
true;
`;

export function buildRoleLayoutScript(
  role: string | null | undefined,
  loggedIn: boolean,
): string {
  const safeRole = JSON.stringify(role || '');
  const safeLoggedIn = loggedIn ? 'true' : 'false';
  return `
(function () {
  if (typeof window.__wheelHubApplyRoleLayout === 'function') {
    window.__wheelHubApplyRoleLayout(${safeRole}, ${safeLoggedIn});
  }
})();
true;
`;
}

export const APPLY_ROLE_LAYOUT_FROM_STORAGE = `
(function () {
  if (typeof window.__wheelHubApplyRoleLayout === 'function') {
    try {
      var role = localStorage.getItem('agri_role') || '';
      var token = localStorage.getItem('agri_token') || '';
      window.__wheelHubApplyRoleLayout(role, !!token);
    } catch (e) {}
  }
})();
true;
`;

export function navModeFromState(
  role: string | null | undefined,
  loggedIn: boolean,
  path: string,
): MobileNavMode {
  return resolveMobileNavMode(role, loggedIn, path);
}
