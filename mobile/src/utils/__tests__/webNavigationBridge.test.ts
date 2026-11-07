import { buildWebAppUrl, buildWebNavigateScript } from '../webNavigationBridge';

describe('webNavigationBridge', () => {
  it('normalizes the base URL when building app URLs', () => {
    expect(buildWebAppUrl('https://marketplace.sphilagroup.com/', '/checkout')).toBe(
      'https://marketplace.sphilagroup.com/checkout',
    );
    expect(buildWebAppUrl('https://marketplace.sphilagroup.com///', 'login')).toBe(
      'https://marketplace.sphilagroup.com/login',
    );
  });

  it('creates a navigation script with a normalized target URL', () => {
    const script = buildWebNavigateScript('client/orders', 'https://marketplace.sphilagroup.com///');

    expect(script).toContain('https://marketplace.sphilagroup.com/client/orders');
    expect(script).toContain('var path = "/client/orders";');
  });
});
