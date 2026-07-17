import { Platform } from 'react-native';
import { SIT_DEV_HOST } from './devWebHost';

export type MarketplaceAppEnv = 'sit' | 'uat' | 'prod';

export type EnvironmentEndpoints = {
  env: MarketplaceAppEnv;
  webAppUrl: string;
  apiBaseUrl: string;
};

/**
 * Set these to your deployed Vercel frontend + Railway backend.
 * UAT/PROD APKs load the hosted web app (same UX as mobile browser).
 */
const HOSTED_WEB_APP_URL = 'https://YOUR-MARKETPLACE-FRONTEND.vercel.app';
const HOSTED_API_BASE_URL = 'https://YOUR-MARKETPLACE-BACKEND.up.railway.app';

const UAT_WEB_APP_URL = HOSTED_WEB_APP_URL;
const UAT_API_BASE_URL = HOSTED_API_BASE_URL;
const PROD_WEB_APP_URL = HOSTED_WEB_APP_URL;
const PROD_API_BASE_URL = HOSTED_API_BASE_URL;

function sitWebHost(): string {
  if (Platform.OS === 'android') {
    return SIT_DEV_HOST;
  }
  return 'localhost';
}

export function resolveEnvironmentEndpoints(env: MarketplaceAppEnv): EnvironmentEndpoints {
  switch (env) {
    case 'sit': {
      const host = sitWebHost();
      return {
        env: 'sit',
        webAppUrl: `http://${host}:5173`,
        apiBaseUrl: `http://${host}:8080`,
      };
    }
    case 'uat':
      return { env: 'uat', webAppUrl: UAT_WEB_APP_URL, apiBaseUrl: UAT_API_BASE_URL };
    case 'prod':
      return { env: 'prod', webAppUrl: PROD_WEB_APP_URL, apiBaseUrl: PROD_API_BASE_URL };
    default: {
      const host = sitWebHost();
      return {
        env: 'sit',
        webAppUrl: `http://${host}:5173`,
        apiBaseUrl: `http://${host}:8080`,
      };
    }
  }
}
