/**
 * Friendly message for a failed API call, including the auth hardening responses:
 * 429 RATE_LIMITED / ACCOUNT_LOCKED / OTP_LOCKED (with Retry-After), WEAK_PASSWORD, EMAIL_CODE_INVALID.
 */

function retryAfterMinutes(response) {
  const raw = response?.headers?.['retry-after'] ?? response?.headers?.['Retry-After'];
  const seconds = Number(raw);
  if (!Number.isFinite(seconds) || seconds <= 0) return null;
  return Math.max(1, Math.ceil(seconds / 60));
}

/**
 * @param {unknown} error axios error (or anything thrown)
 * @param {string} fallback message when nothing better is available
 */
export function apiErrorMessage(error, fallback = 'Something went wrong. Please try again.') {
  const response = error?.response;
  if (!response) {
    // No HTTP response: a network problem (axios), or a plain error thrown by our own code.
    const networkish = !error?.message || error.isAxiosError || /network error|request failed/i.test(error.message);
    return networkish ? 'Network error. Check your connection.' : error.message;
  }
  const data = response.data || {};
  if (response.status === 429) {
    if (data.message) return data.message;
    const minutes = retryAfterMinutes(response);
    return minutes
      ? `Too many attempts. Please wait ${minutes} ${minutes === 1 ? 'minute' : 'minutes'} and try again.`
      : 'Too many attempts. Please wait a few minutes and try again.';
  }
  if (data.fieldErrors && typeof data.fieldErrors === 'object') {
    const first = Object.entries(data.fieldErrors)[0];
    if (first) return `${first[0]}: ${first[1]}`;
  }
  return data.message || fallback;
}

/** Error code from our API error body ({ code, errorCode, message }), if any. */
export function apiErrorCode(error) {
  const data = error?.response?.data;
  return data?.errorCode || data?.code || '';
}
