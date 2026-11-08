/**
 * PayFast hosted checkout: the backend returns `{ processUrl, fields }` with the fields already
 * signed in PayFast's order. The browser must POST them, unchanged and in that order, to PayFast.
 */

/** Allowed PayFast process URLs (sandbox and live). Anything else is refused. */
const PAYFAST_HOSTS = ['https://sandbox.payfast.co.za', 'https://www.payfast.co.za'];

export function isPayFastProcessUrl(url) {
  if (typeof url !== 'string') return false;
  return PAYFAST_HOSTS.some((host) => url === `${host}/eng/process`);
}

/**
 * Builds (but does not submit) the hidden form. Field order follows the object's key order, which is
 * the order the backend signed.
 */
export function buildPayFastForm(processUrl, fields, doc = document) {
  if (!isPayFastProcessUrl(processUrl)) {
    throw new Error('Unexpected payment page address');
  }
  if (!fields || typeof fields !== 'object' || !fields.signature) {
    throw new Error('The payment details are incomplete');
  }
  const form = doc.createElement('form');
  form.method = 'POST';
  form.action = processUrl;
  form.style.display = 'none';
  for (const [name, value] of Object.entries(fields)) {
    if (value == null) continue;
    const input = doc.createElement('input');
    input.type = 'hidden';
    input.name = String(name);
    input.value = String(value);
    form.appendChild(input);
  }
  return form;
}

/** Builds the form, adds it to the page and submits it (the browser leaves for PayFast). */
export function submitPayFastForm(processUrl, fields, doc = document) {
  const form = buildPayFastForm(processUrl, fields, doc);
  doc.body.appendChild(form);
  form.submit();
  return form;
}

/**
 * Sends the browser to the gateway for an online checkout response:
 * Peach `{ redirectUrl }` or PayFast `{ payfast: { processUrl, fields } }`.
 * Returns true when the browser is being sent away.
 */
export function goToOnlinePayment(data, { doc = document, location = window.location } = {}) {
  if (data?.payfast?.processUrl) {
    submitPayFastForm(data.payfast.processUrl, data.payfast.fields, doc);
    return true;
  }
  if (data?.redirectUrl) {
    location.assign(data.redirectUrl);
    return true;
  }
  return false;
}
