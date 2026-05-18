package com.dylanvann.fastimage;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.module.annotations.ReactModule;

import java.util.HashMap;
import java.util.Map;

/**
 * Exposes on-device super-resolution availability to JavaScript.
 *
 * <p>{@code @ReactModule(hasConstants = true)} is required so React Native (0.71+) eagerly
 * calls {@link #getConstants()} during bridge initialisation instead of deferring it.
 */
@ReactModule(name = FastImageSuperResolutionModule.NAME, hasConstants = true)
public class FastImageSuperResolutionModule extends ReactContextBaseJavaModule {

    public static final String NAME = "FastImageSuperResolution";

    /** When true, every FastImage load uses SR regardless of per-image source flag. */
    private static volatile boolean globalSuperResolutionEnabled = false;

    public static boolean isGlobalSuperResolutionEnabled() {
        return globalSuperResolutionEnabled;
    }

    FastImageSuperResolutionModule(ReactApplicationContext reactContext) {
        super(reactContext);
        // ERROR level — never filtered, confirms class was compiled and instantiated
        android.util.Log.e("FC_FastImageSR", "[FC_FastImageSR] CONSTRUCTOR FastImageSuperResolutionModule");
    }

    @Override
    public void initialize() {
        super.initialize();
        boolean available = FastImageSuperResolution.getInstance().isAvailable();
        String delegate = FastImageSuperResolution.getInstance().getDelegateLabel();
        FastImageSrLog.i("bridge", "module created isAvailable=" + available + " delegate=" + delegate);
    }

    @NonNull
    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Map<String, Object> getConstants() {
        FastImageSuperResolution sr = FastImageSuperResolution.getInstance();
        boolean available = sr.isAvailable();
        String delegate = sr.getDelegateLabel();
        FastImageSrLog.i(
                "bridge",
                "getConstants called isAvailable=" + available
                        + " delegate=" + delegate);
        Map<String, Object> constants = new HashMap<>();
        constants.put("isImageSuperResolutionAvailable", available);
        constants.put("superResolutionDelegate", delegate);
        return constants;
    }

    /** Called from JS to get current SR availability at any point (not just module load). */
    @ReactMethod
    public void checkAvailability(Promise promise) {
        FastImageSuperResolution sr = FastImageSuperResolution.getInstance();
        boolean available = sr.isAvailable();
        FastImageSrLog.i("bridge", "checkAvailability called → " + available);
        promise.resolve(available);
    }

    @ReactMethod
    public void isImageSuperResolutionAvailable(Promise promise) {
        promise.resolve(FastImageSuperResolution.getInstance().isAvailable());
    }

    /**
     * When true, SR success/failure toasts show even on non-debuggable (e.g. release) builds.
     */
    @ReactMethod
    public void setSuperResolutionDebugToastsEnabled(boolean enabled) {
        FastImageSrUiFeedback.setForceDebugToastsEnabled(enabled);
    }

    /**
     * Globally enables or disables super-resolution for every FastImage load.
     * When enabled, all images are processed through the SR model regardless of
     * the per-image {@code superResolution} source flag.
     */
    @ReactMethod
    public void setGlobalSuperResolution(boolean enabled) {
        globalSuperResolutionEnabled = enabled;
        FastImageSrLog.i("bridge", "globalSuperResolution=" + enabled);
    }
}
