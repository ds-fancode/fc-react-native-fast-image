#import "FFFastImageUpscaleTransformer.h"

@class FCImageUpscaler;

static id _Nullable FCGetUpscalerShared(void) {
    Class upscalerClass = NSClassFromString(@"SportsGuru.FCImageUpscaler");
    if (!upscalerClass) {
        upscalerClass = NSClassFromString(@"FCImageUpscaler");
    }
    if (upscalerClass) {
        SEL sel = NSSelectorFromString(@"shared");
        if ([upscalerClass respondsToSelector:sel]) {
            #pragma clang diagnostic push
            #pragma clang diagnostic ignored "-Warc-performSelector-leaks"
            return [upscalerClass performSelector:sel];
            #pragma clang diagnostic pop
        }
    }
    return nil;
}

static const NSTimeInterval kUpscaleTimeoutSeconds = 5.0;

@implementation FFFastImageUpscaleTransformer

+ (instancetype)sharedTransformer {
    static FFFastImageUpscaleTransformer *instance = nil;
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        instance = [[self alloc] init];
    });
    return instance;
}

- (NSString *)transformerKey {
    return @"FCUpscale_v1";
}

- (UIImage *)transformedImageWithImage:(UIImage *)image forKey:(NSString *)key {
    if (image == nil) {
        return nil;
    }

    id upscaler = FCGetUpscalerShared();
    SEL upscaleSelector = NSSelectorFromString(@"upscaleImage:completion:");
    if (!upscaler || ![upscaler respondsToSelector:upscaleSelector]) {
        return image;
    }

    CGSize originalSize = image.size;
    __block UIImage *result = nil;
    dispatch_semaphore_t sem = dispatch_semaphore_create(0);

    void (^completion)(UIImage * _Nullable) = ^(UIImage * _Nullable upscaled) {
        result = upscaled;
        dispatch_semaphore_signal(sem);
    };

    NSMethodSignature *signature = [upscaler methodSignatureForSelector:upscaleSelector];
    NSInvocation *invocation = [NSInvocation invocationWithMethodSignature:signature];
    [invocation setTarget:upscaler];
    [invocation setSelector:upscaleSelector];
    [invocation setArgument:&image atIndex:2];
    [invocation setArgument:&completion atIndex:3];
    [invocation invoke];

    long timedOut = dispatch_semaphore_wait(sem, dispatch_time(DISPATCH_TIME_NOW, (int64_t)(kUpscaleTimeoutSeconds * NSEC_PER_SEC)));
    if (timedOut != 0) {
        NSLog(@"[FCImageMetrics] UPSCALE: TIMEOUT | Original: %.0fx%.0f | Key: %@",
              originalSize.width, originalSize.height, key);
        return image;
    }

    UIImage *finalImage = result ?: image;
    BOOL succeeded = (result != nil && !CGSizeEqualToSize(result.size, originalSize));
    NSLog(@"[FCImageMetrics] UPSCALE: %@ | Original: %.0fx%.0f -> Final: %.0fx%.0f | Key: %@",
          succeeded ? @"SUCCESS" : @"SKIPPED",
          originalSize.width, originalSize.height,
          finalImage.size.width, finalImage.size.height,
          key);
    return finalImage;
}

@end
