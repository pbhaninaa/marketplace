/** Bakes the native shell API base URL into the WebView so axios hits the correct backend. */
export function buildApiBaseUrlBridgeScript(apiBaseUrl: string): string {
  const safe = JSON.stringify(apiBaseUrl);
  return `window.__wheelHubApiBaseUrl = ${safe}; true;`;
}
