#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>
#import <SDWebImage/SDImageTransformer.h>

NS_ASSUME_NONNULL_BEGIN

@interface FFFastImageUpscaleTransformer : NSObject <SDImageTransformer>

+ (instancetype)sharedTransformer;

@end

NS_ASSUME_NONNULL_END
