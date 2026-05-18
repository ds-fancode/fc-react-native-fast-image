package com.dylanvann.fastimage;

import android.app.Activity;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.views.imagehelper.ImageSource;
import com.facebook.react.bridge.ReactApplicationContext;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class FastImageViewModuleImplementation {
    ReactApplicationContext reactContext;

    /** Serial executor for SR preload — runs one inference at a time to avoid thrashing the model. */
    private static final ExecutorService SR_PRELOAD_EXECUTOR = Executors.newSingleThreadExecutor();

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

        boolean srEnabled = FastImageSuperResolutionModule.isGlobalSuperResolutionEnabled();

        for (int i = 0; i < sources.size(); i++) {
            final ReadableMap source = sources.getMap(i);
            if (source == null || !source.hasKey("uri") || source.getString("uri").isEmpty()) {
                continue;
            }
            final FastImageSource imageSource = FastImageViewConverter.getImageSource(activity, source);
            if (imageSource == null) continue;

            if (srEnabled) {
                // SR inference is heavy — run each preload on a serial background executor
                // so we don't block the UI thread or thrash the TFLite model with parallel calls.
                SR_PRELOAD_EXECUTOR.execute(() -> {
                    try {
                        Glide.with(activity.getApplicationContext())
                                .load(imageSource.isBase64Resource() ? imageSource.getSource() :
                                        imageSource.isResource() ? imageSource.getUri() : imageSource.getGlideUrl())
                                .apply(FastImageViewConverter.getOptions(activity, imageSource, source, null))
                                .transform(new SuperResolutionTransformation(preloadUrlKeyForSuperResolution(imageSource)))
                                .preload();
                        FastImageSrLog.i("preload", "SR preload done url=" +
                                FastImageSrUiFeedback.truncateForUi(preloadUrlKeyForSuperResolution(imageSource), 80));
                    } catch (Exception e) {
                        FastImageSrLog.w("preload", "SR preload failed: " + e.getMessage());
                    }
                });
            } else {
                // Non-SR preloads are lightweight — keep on UI thread as before.
                activity.runOnUiThread(() -> {
                    try {
                        Glide.with(activity.getApplicationContext())
                                .load(imageSource.isBase64Resource() ? imageSource.getSource() :
                                        imageSource.isResource() ? imageSource.getUri() : imageSource.getGlideUrl())
                                .apply(FastImageViewConverter.getOptions(activity, imageSource, source, null))
                                .preload();
                    } catch (Exception e) {
                        FastImageSrLog.w("preload", "preload failed: " + e.getMessage());
                    }
                });
            }
        }
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
