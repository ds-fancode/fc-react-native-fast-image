package com.dylanvann.fastimage;

import android.content.Context;
import android.graphics.Bitmap;

import androidx.annotation.Nullable;

/**
 * Thin facade used by {@link SuperResolutionTransformation} and the React Native bridge.
 * All TFLite / model code lives in the root app ({@code SuperResolutionEngine});
 * this class delegates every call to {@link FastImageSrRegistry}.
 *
 * <p>The root app registers its engine at startup:
 * <pre>
 *   SuperResolutionEngine engine = new SuperResolutionEngine();
 *   engine.init(this);
 *   FastImageSrRegistry.register(engine);
 * </pre>
 */
public final class FastImageSuperResolution {

    private static final FastImageSuperResolution INSTANCE = new FastImageSuperResolution();

    private FastImageSuperResolution() {
    }

    public static FastImageSuperResolution getInstance() {
        return INSTANCE;
    }

    /**
     * No-op. Model initialisation is now owned by the root app.
     * Register the engine via {@link FastImageSrRegistry#register} in {@code MainApplication}.
     */
    @Deprecated
    public void init(Context context) {
        FastImageSrLog.w(
                "init",
                "FastImageSuperResolution.init() is a no-op — register engine via "
                        + "FastImageSrRegistry.register() in MainApplication instead");
    }

    public boolean isAvailable() {
        FastImageSrProvider p = FastImageSrRegistry.get();
        return p != null && p.isAvailable();
    }

    public String getDelegateLabel() {
        FastImageSrProvider p = FastImageSrRegistry.get();
        return p != null ? p.getDelegateLabel() : "none";
    }

    @Nullable
    public Bitmap enhanceBitmap(Bitmap source) {
        return enhanceBitmapWithDiagnostics(source).bitmap;
    }

    public InferenceResult enhanceBitmapWithDiagnostics(Bitmap source) {
        FastImageSrProvider p = FastImageSrRegistry.get();
        if (p == null || !p.isAvailable()) {
            return new InferenceResult(null, "SR provider not registered or unavailable");
        }
        try {
            Bitmap result = p.enhance(source);
            if (result != null && result != source) {
                return new InferenceResult(result, null);
            }
            if (result == source) {
                return new InferenceResult(null, "enhance() returned original bitmap unchanged");
            }
            return new InferenceResult(null, "enhance() returned null");
        } catch (Throwable t) {
            String msg = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
            FastImageSrLog.w("infer", "enhance threw: " + msg, t);
            return new InferenceResult(null, "Inference error: " + msg);
        }
    }

    /**
     * Returned by {@link #enhanceBitmapWithDiagnostics} and consumed by
     * {@link SuperResolutionTransformation} for success/failure logging and UI toasts.
     */
    public static final class InferenceResult {
        @Nullable
        public final Bitmap bitmap;
        @Nullable
        public final String failureReason;

        InferenceResult(@Nullable Bitmap bitmap, @Nullable String failureReason) {
            this.bitmap = bitmap;
            this.failureReason = failureReason;
        }

        public boolean isSuccess() {
            return bitmap != null;
        }
    }
}
