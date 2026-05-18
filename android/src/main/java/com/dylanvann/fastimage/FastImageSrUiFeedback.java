package com.dylanvann.fastimage;

import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.Nullable;

/**
 * Main-thread toasts for SR success/failure (debuggable builds by default).
 */
public final class FastImageSrUiFeedback {

    private static Application app;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /**
     * When true, shows toasts even if the app is not debuggable (e.g. QA on release build).
     */
    private static volatile boolean forceDebugToasts;

    private FastImageSrUiFeedback() {
    }

    public static void init(Context context) {
        app = (Application) context.getApplicationContext();
    }

    public static void setForceDebugToastsEnabled(boolean enabled) {
        forceDebugToasts = enabled;
    }

    static boolean shouldShowDebugToasts() {
        if (app == null) {
            return false;
        }
        if (forceDebugToasts) {
            return true;
        }
        return (app.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    /**
     * @param requestUrl may be long; shown truncated in the toast.
     */
    public static void toastSrResult(
            boolean success,
            @Nullable String requestUrl,
            @Nullable String failureReason,
            int outW,
            int outH
    ) {
        if (!shouldShowDebugToasts()) {
            return;
        }
        if (app == null) {
            return;
        }
        String urlPart = truncateForUi(requestUrl, 72);
        String text;
        if (success) {
            text = "SR OK — model ran\n" + outW + "×" + outH + "\n" + urlPart;
        } else {
            String reason = failureReason != null ? failureReason : "unknown error";
            text = "SR failed\n" + reason + "\n" + urlPart;
        }
        int length = success ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG;
        MAIN.post(() -> Toast.makeText(app, text, length).show());
    }

    public static String truncateForUi(@Nullable String url, int maxChars) {
        if (url == null || url.isEmpty()) {
            return "(no url)";
        }
        String u = url.trim().replace('\n', ' ');
        if (u.length() <= maxChars) {
            return u;
        }
        return u.substring(0, maxChars - 1) + "…";
    }
}
