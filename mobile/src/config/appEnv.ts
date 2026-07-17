import { NativeModules } from 'react-native';
import type { MarketplaceAppEnv } from './environments';

type ServiceHubConfigNative = {
  appEnv?: string;
};

function parseAppEnv(raw: string | undefined): MarketplaceAppEnv {
  if (raw === 'uat' || raw === 'prod' || raw === 'sit') {
    return raw;
  }
  return 'sit';
}

/** Build-time environment from Android product flavor (sit | uat | prod). */
export function getAppEnvironment(): MarketplaceAppEnv {
  const native = NativeModules.ServiceHubConfig as ServiceHubConfigNative | undefined;
  return parseAppEnv(native?.appEnv);
}
