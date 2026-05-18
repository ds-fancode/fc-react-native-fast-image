package com.dylanvann.fastimage;

import android.graphics.Bitmap;

import androidx.annotation.Nullable;

/**
 * Implemented by the root app's SR engine and registered via {@link FastImageSrRegistry}.
 * fast-image calls this interface; all TFLite code lives in the root app.
 */
public interface FastImageSrProvider {
    boolean isAvailable();
    String getDelegateLabel();

    /**
     * Run 2× super-resolution on {@code source}. Returns an enhanced {@link Bitmap} on success,
     * or {@code null} if inference failed (caller keeps the original).
     */
    @Nullable
    Bitmap enhance(Bitmap source);
}
