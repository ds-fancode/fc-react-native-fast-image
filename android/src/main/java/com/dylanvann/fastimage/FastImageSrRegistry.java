package com.dylanvann.fastimage;

import androidx.annotation.Nullable;

/**
 * Static registry that decouples fast-image from TFLite.
 * The root app creates and registers a {@link FastImageSrProvider} at startup;
 * fast-image calls through this registry without importing TFLite directly.
 *
 * <p>Usage (in {@code MainApplication.onCreate}):
 * <pre>
 *   SuperResolutionEngine engine = new SuperResolutionEngine();
 *   engine.init(this);
 *   FastImageSrRegistry.register(engine);
 * </pre>
 */
public final class FastImageSrRegistry {

    private static volatile FastImageSrProvider provider;

    private FastImageSrRegistry() {
    }

    public static void register(@Nullable FastImageSrProvider p) {
        provider = p;
    }

    @Nullable
    public static FastImageSrProvider get() {
        return provider;
    }
}
