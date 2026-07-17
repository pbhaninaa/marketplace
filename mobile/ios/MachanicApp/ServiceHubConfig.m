#import <React/RCTBridgeModule.h>

@interface ServiceHubConfig : NSObject <RCTBridgeModule>
@end

@implementation ServiceHubConfig

RCT_EXPORT_MODULE();

- (NSDictionary *)constantsToExport
{
  NSString *env = [[NSBundle mainBundle] objectForInfoDictionaryKey:@"SERVICEHUB_APP_ENV"];
  if (env.length == 0) {
    env = @"sit";
  }
  return @{ @"appEnv": env };
}

+ (BOOL)requiresMainQueueSetup
{
  return NO;
}

@end
