package com.dylanvann.fastimage;

import android.util.Log;

/**
 * Single tag + stage prefix for logcat filtering.
 * <p>
 * <b>Tag:</b> {@link #TAG} — use {@code adb logcat -s FC_FastImageSR:*} or a Logcat filter on <b>Tag</b>.
 * <p>
 * <b>Android Studio search box:</b> often matches <b>message text only</b>, not the Tag column. Every line
 * therefore repeats {@code [FC_FastImageSR]} at the start of the message so typing {@code FC_FastImageSR}
 * or {@code [FC_FastImageSR]} in the search field still finds these lines.
 * <p>
 * Message shape: {@code [FC_FastImageSR] STAGE | detail...}
 * <p>
 * Stages include: {@code device_ml}, {@code init} (and nnapi/gpu/cpu substages), {@code pipeline},
 * {@code load}, {@code sr_each} (every SR image: OK/FAIL + session totals), {@code transform},
 * {@code infer}, {@code js}, {@code bridge}.
 */
public final class FastImageSrLog {

    /**
     * Use this tag exclusively for SR / TFLite diagnostics (easy to filter; avoids mixing with FastImageView cache logs).
     */
    public static final String TAG = "FC_FastImageSR";

    private static final String MSG_PREFIX = "[" + TAG + "] ";

    private FastImageSrLog() {
    }

    public static void d(String stage, String message) {
        Log.d(TAG, MSG_PREFIX + stage + " | " + message);
    }

    public static void i(String stage, String message) {
        Log.i(TAG, MSG_PREFIX + stage + " | " + message);
    }

    public static void w(String stage, String message) {
        Log.w(TAG, MSG_PREFIX + stage + " | " + message);
    }

    public static void w(String stage, String message, Throwable t) {
        Log.w(TAG, MSG_PREFIX + stage + " | " + message, t);
    }

    public static void e(String stage, String message) {
        Log.e(TAG, MSG_PREFIX + stage + " | " + message);
    }

    public static void e(String stage, String message, Throwable t) {
        Log.e(TAG, MSG_PREFIX + stage + " | " + message, t);
    }
}
