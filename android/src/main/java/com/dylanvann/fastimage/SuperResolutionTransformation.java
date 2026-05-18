package com.dylanvann.fastimage;

import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Glide stage that runs {@link FastImageSuperResolution#enhanceBitmapWithDiagnostics(android.graphics.Bitmap)}.
 */
public final class SuperResolutionTransformation extends BitmapTransformation {

    /** Log once so logcat shows why SR never runs without spamming per-frame. */
    private static volatile boolean loggedUnavailable;

    /** Sample URL logged once when SR is unavailable (avoids per-image log spam). */
    private static volatile boolean loggedUnavailableSampleUrl;

    /** Toast once when the native model never loaded. */
    private static volatile boolean toastedModelUnavailable;

    private final String requestUrl;
    private final byte[] urlKeyBytes;

    private static final String ID = "com.dylanvann.fastimage.SuperResolutionTransformation.v2";
    private static final byte[] ID_BYTES = ID.getBytes(StandardCharsets.UTF_8);

    /** Running count of images passed through this transform — logged per image. */
    private static volatile long sTransformCount = 0;

    /** One-time pointer from Glide to the numbered infer steps. */
    private static volatile boolean sLoggedFirstSrImagePipeline;

    public SuperResolutionTransformation(@Nullable String requestUrl) {
        this.requestUrl = requestUrl != null ? requestUrl : "";
        this.urlKeyBytes = this.requestUrl.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Glide 4.12+ {@link BitmapTransformation} uses this signature (no {@link android.content.Context}).
     */
    @Override
    protected Bitmap transform(
            @NonNull BitmapPool pool,
            @NonNull Bitmap toTransform,
            int outWidth,
            int outHeight
    ) {
        String urlLog = FastImageSrUiFeedback.truncateForUi(requestUrl, 96);
        // Skip SR for images smaller than 80×80 — too small to benefit from upscaling.
        if (toTransform.getWidth() < 80 || toTransform.getHeight() < 80) {
            FastImageSrLog.i("transform", "skip SR — image too small "
                    + toTransform.getWidth() + "x" + toTransform.getHeight()
                    + " (min 80x80) url=" + urlLog);
            return toTransform;
        }

        // Skip SR when the long edge exceeds the model's OUTPUT size (1024px).
        // The engine scales the input so its long edge fits 512, then outputs at 2× = 1024 max.
        // If the input long edge is already > 1024, the SR output would be SMALLER than the
        // original (e.g. 1080×1637 → scale to 338×512 → output 676×1024 < 1080×1637).
        // But inputs with long edge ≤ 1024 always receive a net upscale
        // (e.g. 380×569 → scale to 342×512 → output 684×1024 > 380×569 ✓).
        if (Math.max(toTransform.getWidth(), toTransform.getHeight()) > 1024) {
            FastImageSrLog.i("transform", "skip SR — image long edge exceeds model output "
                    + toTransform.getWidth() + "x" + toTransform.getHeight()
                    + " (max 1024px long edge) url=" + urlLog);
            return toTransform;
        }

        if (!FastImageSuperResolution.getInstance().isAvailable()) {
            if (!loggedUnavailable) {
                loggedUnavailable = true;
                FastImageSrLog.w("transform", "skip SR model/inference not available (once per process)");
            }
            if (!loggedUnavailableSampleUrl) {
                loggedUnavailableSampleUrl = true;
                FastImageSrLog.w("transform_sample", "example url skipped (sr unavailable) url=" + urlLog);
            }
            if (!toastedModelUnavailable) {
                toastedModelUnavailable = true;
                FastImageSrUiFeedback.toastSrResult(
                        false,
                        requestUrl,
                        "SR model not loaded",
                        0,
                        0
                );
            }
            return toTransform;
        }

        if (!sLoggedFirstSrImagePipeline) {
            sLoggedFirstSrImagePipeline = true;
            FastImageSrLog.i(
                    "pipeline",
                    "First SR image this session: Glide decoded Bitmap → this transform() → "
                            + "enhanceBitmapWithDiagnostics() → numbered steps in stage infer [1/5]…[5/5]");
        }

        String backend = FastImageSuperResolution.getInstance().getDelegateLabel();
        long imgIndex = ++sTransformCount;
        // Per-image entry log — shows every image so you can confirm the model fires each time.
        FastImageSrLog.i(
                "transform",
                "#" + imgIndex
                        + " enter backend=" + renderBackendShort(backend)
                        + " bitmapIn=" + toTransform.getWidth() + "x" + toTransform.getHeight()
                        + " url=" + urlLog
        );

        FastImageSuperResolution.InferenceResult result =
                FastImageSuperResolution.getInstance().enhanceBitmapWithDiagnostics(toTransform);

        if (result.bitmap != null && result.bitmap != toTransform) {
            FastImageSrSessionStats.recordSrAttempt(true);
            FastImageSrLog.i(
                    "sr_each",
                    "OK model=xlsr_2x in=" + toTransform.getWidth() + "x" + toTransform.getHeight()
                            + " out=" + result.bitmap.getWidth() + "x" + result.bitmap.getHeight()
                            + " backend=" + backend + " (" + renderBackendShort(backend) + ") "
                            + FastImageSrSessionStats.formatSessionSuffix()
                            + " url=" + urlLog
            );
            FastImageSrUiFeedback.toastSrResult(
                    true,
                    requestUrl,
                    null,
                    result.bitmap.getWidth(),
                    result.bitmap.getHeight()
            );
            // Do NOT call pool.put(toTransform) here. Glide owns the lifecycle of toTransform
            // and will return it to the pool after the transformation chain completes.
            // Calling pool.put() ourselves caused "Cannot pool recycled bitmap" → onLoadFailed.
            return result.bitmap;
        }

        String reason = result.failureReason != null ? result.failureReason : "unknown";
        FastImageSrSessionStats.recordSrAttempt(false);
        FastImageSrLog.i(
                "sr_each",
                "FAIL model=xlsr_2x in=" + toTransform.getWidth() + "x" + toTransform.getHeight()
                        + " backend=" + backend + " reason=" + reason + " "
                        + FastImageSrSessionStats.formatSessionSuffix()
                        + " url=" + urlLog
        );
        FastImageSrLog.w("transform", "FAIL url=" + urlLog + " reason=" + reason);
        FastImageSrUiFeedback.toastSrResult(false, requestUrl, reason, 0, 0);
        return toTransform;
    }

    private static String renderBackendShort(String backend) {
        if ("nnapi".equals(backend)) {
            return "NNAPI-HW";
        }
        if ("nnapi_partial".equals(backend)) {
            return "NNAPI-partial";
        }
        if ("gpu".equals(backend)) {
            return "GPU";
        }
        if ("cpu".equals(backend)) {
            return "CPU+XNNPACK";
        }
        return backend;
    }

    @Override
    public void updateDiskCacheKey(@NonNull MessageDigest messageDigest) {
        messageDigest.update(ID_BYTES);
        messageDigest.update(urlKeyBytes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SuperResolutionTransformation that = (SuperResolutionTransformation) o;
        return requestUrl.equals(that.requestUrl);
    }

    @Override
    public int hashCode() {
        return ID.hashCode() * 31 + requestUrl.hashCode();
    }
}
