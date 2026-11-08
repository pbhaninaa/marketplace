import { describe, expect, it, vi } from 'vitest';
import {
  buildPayFastForm,
  goToOnlinePayment,
  isPayFastProcessUrl,
  submitPayFastForm,
} from '../src/utils/payFastCheckout';

const fields = {
  merchant_id: '10000100',
  merchant_key: '46f0cd694581a',
  return_url: 'https://shop.example/payment/return?ref=abc',
  m_payment_id: 'abc',
  amount: '100.00',
  item_name: 'Green Acres order',
  payment_method: 'cc',
  signature: 'f103e22c0418655fb03991538c51bfd5',
};

describe('PayFast checkout form', () => {
  it('only accepts the PayFast sandbox and live process pages', () => {
    expect(isPayFastProcessUrl('https://sandbox.payfast.co.za/eng/process')).toBe(true);
    expect(isPayFastProcessUrl('https://www.payfast.co.za/eng/process')).toBe(true);
    expect(isPayFastProcessUrl('https://evil.example/eng/process')).toBe(false);
    expect(isPayFastProcessUrl(undefined)).toBe(false);
    expect(() => buildPayFastForm('https://evil.example/eng/process', fields)).toThrow();
  });

  it('builds a hidden POST form with every field in the signed order', () => {
    const form = buildPayFastForm('https://sandbox.payfast.co.za/eng/process', fields);
    expect(form.method.toUpperCase()).toBe('POST');
    expect(form.getAttribute('action')).toBe('https://sandbox.payfast.co.za/eng/process');
    const inputs = [...form.querySelectorAll('input')];
    expect(inputs.map((i) => i.name)).toEqual(Object.keys(fields));
    expect(inputs.every((i) => i.type === 'hidden')).toBe(true);
    expect(inputs.find((i) => i.name === 'return_url').value).toBe(fields.return_url);
  });

  it('refuses fields without a signature', () => {
    const { signature, ...unsigned } = fields;
    expect(signature).toBeTruthy();
    expect(() => buildPayFastForm('https://www.payfast.co.za/eng/process', unsigned)).toThrow();
  });

  it('adds the form to the page and submits it', () => {
    const submit = vi.fn();
    const appended = [];
    const doc = {
      createElement: (tag) => {
        const el = document.createElement(tag);
        if (tag === 'form') el.submit = submit;
        return el;
      },
      body: { appendChild: (el) => appended.push(el) },
    };
    const form = submitPayFastForm('https://www.payfast.co.za/eng/process', fields, doc);
    expect(appended).toEqual([form]);
    expect(submit).toHaveBeenCalledTimes(1);
  });

  it('goes to Peach by redirect and to PayFast by form post', () => {
    const location = { assign: vi.fn() };
    const submit = vi.fn();
    const doc = {
      createElement: (tag) => {
        const el = document.createElement(tag);
        if (tag === 'form') el.submit = submit;
        return el;
      },
      body: { appendChild: () => {} },
    };
    expect(goToOnlinePayment({ redirectUrl: 'https://peach.example/pay' }, { doc, location })).toBe(true);
    expect(location.assign).toHaveBeenCalledWith('https://peach.example/pay');
    expect(
      goToOnlinePayment(
        { payfast: { processUrl: 'https://sandbox.payfast.co.za/eng/process', fields } },
        { doc, location },
      ),
    ).toBe(true);
    expect(submit).toHaveBeenCalledTimes(1);
    expect(goToOnlinePayment({}, { doc, location })).toBe(false);
  });
});
