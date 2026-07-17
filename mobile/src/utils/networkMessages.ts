import { AxiosError } from 'axios';

export const OFFLINE_CONNECTION_MESSAGE =
  'No internet connection. Please check your connection and try again.';

export const SERVER_UNREACHABLE_MESSAGE =
  'We could not reach the server. Please check your internet connection and try again.';

export const REQUEST_TIMEOUT_MESSAGE =
  'Request timed out. Please check your connection and try again.';

export const WEB_APP_LOAD_ERROR_MESSAGE =
  'Could not load the app. Please check your internet connection and try again.';

export function isNetworkFailure(error: unknown): boolean {
  if (!axiosLikeNetworkError(error)) {
    return false;
  }
  const axiosError = error as AxiosError;
  const msg = String(axiosError.message || '').toLowerCase();
  return (
    !axiosError.response &&
    (axiosError.code === 'ERR_NETWORK' ||
      axiosError.code === 'ECONNABORTED' ||
      msg === 'network error' ||
      msg.includes('network'))
  );
}

export function networkErrorMessage(error: unknown, fallback: string): string | null {
  if (!axiosLikeNetworkError(error)) {
    return null;
  }
  const axiosError = error as AxiosError;
  if (axiosError.code === 'ECONNABORTED') {
    return REQUEST_TIMEOUT_MESSAGE;
  }
  if (isNetworkFailure(axiosError)) {
    return OFFLINE_CONNECTION_MESSAGE;
  }
  return null;
}

function axiosLikeNetworkError(error: unknown): error is AxiosError {
  return (
    typeof error === 'object' &&
    error != null &&
    ('isAxiosError' in error || 'response' in error || 'code' in error)
  );
}

export function isLikelyConnectionLoadError(description?: string | null): boolean {
  const text = String(description || '').toLowerCase();
  if (!text) return true;
  return (
    text.includes('net::') ||
    text.includes('network') ||
    text.includes('internet') ||
    text.includes('connection') ||
    text.includes('offline') ||
    text.includes('host lookup') ||
    text.includes('timed out') ||
    text.includes('could not connect') ||
    text.includes('failed to connect')
  );
}

export function webViewLoadErrorMessage(description?: string | null): string {
  if (isLikelyConnectionLoadError(description)) {
    return WEB_APP_LOAD_ERROR_MESSAGE;
  }
  return description?.trim() || WEB_APP_LOAD_ERROR_MESSAGE;
}
