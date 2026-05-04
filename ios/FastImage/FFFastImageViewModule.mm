#import "FFFastImageViewModule.h"
#import "FFFastImageSource.h"

#import <SDWebImage/SDImageCache.h>
#import <SDWebImage/SDWebImagePrefetcher.h>
#import <SDWebImage/SDWebImageDownloader.h>

// Forward declare the Swift upscaler class for runtime access
@class FCImageUpscaler;

@implementation FFFastImageViewModule

RCT_EXPORT_MODULE(FastImageViewModule)

RCT_EXPORT_METHOD(preload:(nonnull NSArray<FFFastImageSource *> *)sources)
{
    NSMutableArray *urls = [NSMutableArray arrayWithCapacity:sources.count];

    [sources enumerateObjectsUsingBlock:^(FFFastImageSource * _Nonnull source, NSUInteger idx, BOOL * _Nonnull stop) {
        [source.headers enumerateKeysAndObjectsUsingBlock:^(NSString *key, NSString* header, BOOL *stop) {
            [[SDWebImageDownloader sharedDownloader] setValue:header forHTTPHeaderField:key];
        }];
        [urls setObject:source.url atIndexedSubscript:idx];
    }];

    [[SDWebImagePrefetcher sharedImagePrefetcher] prefetchURLs:urls];
}

RCT_EXPORT_METHOD(clearMemoryCache:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)
{
    [SDImageCache.sharedImageCache clearMemory];
    resolve(NULL);
}

RCT_EXPORT_METHOD(clearDiskCache:(RCTPromiseResolveBlock)resolve reject:(RCTPromiseRejectBlock)reject)
{
    [SDImageCache.sharedImageCache clearDiskOnCompletion:^(){
        resolve(NULL);
    }];
}

// Global upscaler configuration method (call from JS to enable/disable via feature flag)
RCT_EXPORT_METHOD(setUpscalingEnabled:(BOOL)enabled)
{
    // Use runtime check to access FCImageUpscaler
    // Swift classes are namespaced with module name: "ModuleName.ClassName"
    Class upscalerClass = NSClassFromString(@"SportsGuru.FCImageUpscaler");

    // Fallback to just class name (in case of @objc(FCImageUpscaler) override)
    if (!upscalerClass) {
        upscalerClass = NSClassFromString(@"FCImageUpscaler");
    }

    if (upscalerClass) {
        SEL sharedSelector = NSSelectorFromString(@"shared");
        if ([upscalerClass respondsToSelector:sharedSelector]) {
            #pragma clang diagnostic push
            #pragma clang diagnostic ignored "-Warc-performSelector-leaks"
            id upscaler = [upscalerClass performSelector:sharedSelector];
            #pragma clang diagnostic pop

            if (upscaler) {
                [upscaler setValue:@(enabled) forKey:@"isEnabled"];
                NSLog(@"[FCImageUpscaler] Upscaling %@", enabled ? @"ENABLED" : @"DISABLED");
            }
        }
    } else {
        NSLog(@"[FCImageUpscaler] FCImageUpscaler class not found");
    }
}

#ifdef RCT_NEW_ARCH_ENABLED
- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:
    (const facebook::react::ObjCTurboModule::InitParams &)params
{
    return std::make_shared<facebook::react::NativeFastImageViewModuleSpecJSI>(params);
}
#endif

@end
