package com.dylanvann.fastimage;

import android.app.Activity;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.model.GlideUrl;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.views.imagehelper.ImageSource;
import com.facebook.react.bridge.ReactApplicationContext;

class FastImageViewModuleImplementation {
    ReactApplicationContext reactContext;
    FastImageViewModuleImplementation(ReactApplicationContext reactContext){

    this.reactContext = reactContext;
    }

    public static final String REACT_CLASS = "FastImageViewModule";

    private Activity getCurrentActivity(){
        return reactContext.getCurrentActivity();
    }

    public void preload(final ReadableArray sources) {
        final Activity activity = getCurrentActivity();
        if (activity == null) return;
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < sources.size(); i++) {
                    final ReadableMap source = sources.getMap(i);
                    final FastImageSource imageSource = FastImageViewConverter.getImageSource(activity, source);
                    if (source == null || !source.hasKey("uri") || source.getString("uri").isEmpty()) {
                            System.out.println("Source is null or URI is empty");
                            continue;
                          }
                    RequestBuilder<?> preloadBuilder = Glide
                            .with(activity.getApplicationContext())
                            // This will make this work for remote and local images. e.g.
                            //    - file:///
                            //    - content://
                            //    - res:/
                            //    - android.resource://
                            //    - data:image/png;base64
                            .load(
                                    imageSource.isBase64Resource() ? imageSource.getSource() :
                                    imageSource.isResource() ? imageSource.getUri() : imageSource.getGlideUrl()
                            )
                            .apply(FastImageViewConverter.getOptions(activity, imageSource, source, null));
                    if (FastImageViewConverter.shouldApplySuperResolution(source)) {
                        String preloadSrUrl = preloadUrlKeyForSuperResolution(imageSource);
                        preloadBuilder = preloadBuilder.transform(new SuperResolutionTransformation(preloadSrUrl));
                    }
                    preloadBuilder.preload();
                }
            }
        });
    }

    /**
     * Same URL identity as {@link FastImageViewWithUrl} uses for {@link SuperResolutionTransformation} (cache key + logs).
     */
    private static String preloadUrlKeyForSuperResolution(FastImageSource imageSource) {
        if (imageSource == null) {
            return "";
        }
        try {
            if (!imageSource.isBase64Resource() && !imageSource.isResource()) {
                GlideUrl glideUrl = imageSource.getGlideUrl();
                if (glideUrl != null) {
                    return glideUrl.toStringUrl();
                }
            }
            if (imageSource.getUri() != null) {
                return imageSource.getUri().toString();
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    public void clearMemoryCache(final Promise promise) {
        final Activity activity = getCurrentActivity();
        if (activity == null) {
            promise.resolve(null);
            return;
        }

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Glide.get(activity.getApplicationContext()).clearMemory();
                promise.resolve(null);
            }
        });
    }
    public void clearDiskCache(Promise promise) {
        final Activity activity = getCurrentActivity();
        if (activity == null) {
            promise.resolve(null);
            return;
        }

        Glide.get(activity.getApplicationContext()).clearDiskCache();
        promise.resolve(null);
    }
}
