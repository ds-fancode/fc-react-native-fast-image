package com.dylanvann.fastimage;

import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresApi;

/**
 * One-shot device / OS signals relevant to TFLite + NNAPI (cannot list "NPU model" by name;
 * we log OS features and NN runtime version when available).
 * TFLite-specific diagnostics (NnApiDelegate probe) are performed inside SuperResolutionEngine.
 */
public final class FastImageDeviceMlDiagnostics {

    /**
     * Official feature name for neural networks (API 31 {@code PackageManager} constant); string
     * form used so this library compiles when {@code compileSdkVersion} is below 31.
     */
    private static final String FEATURE_NEURAL_NETWORKS_NAME = "android.hardware.neuralnetworks";

    private static volatile boolean logged;

    private FastImageDeviceMlDiagnostics() {
    }

    public static void logDeviceAndMlStackOnce(Context context) {
        if (logged) {
            return;
        }
        synchronized (FastImageDeviceMlDiagnostics.class) {
            if (logged) {
                return;
            }
            logged = true;
        }
        Context app = context.getApplicationContext();
        String abis = android.text.TextUtils.join(",", Build.SUPPORTED_ABIS);
        FastImageSrLog.i(
                "device_ml",
                "snapshot sdk=" + Build.VERSION.SDK_INT
                        + " manufacturer=" + Build.MANUFACTURER
                        + " model=" + Build.MODEL
                        + " device=" + Build.DEVICE
                        + " brand=" + Build.BRAND
                        + " hardware=" + Build.HARDWARE
                        + " product=" + Build.PRODUCT
                        + " board=" + Build.BOARD
                        + " abis=[" + abis + "]");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            logNeuralNetworkFeature(app);
            logNeuralNetworksRuntimeVersion();
        } else {
            FastImageSrLog.i(
                    "device_ml",
                    "FEATURE_NEURAL_NETWORKS / NeuralNetworks.getRuntimeVersion require API 31+"
                            + " (this device API=" + Build.VERSION.SDK_INT
                            + ") — NNAPI availability will be probed when loading TFLite model");
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    private static void logNeuralNetworkFeature(Context app) {
        try {
            boolean hasFeature =
                    app.getPackageManager().hasSystemFeature(FEATURE_NEURAL_NETWORKS_NAME);
            FastImageSrLog.i(
                    "device_ml",
                    "PackageManager.hasSystemFeature(android.hardware.neuralnetworks)=" + hasFeature
                            + " — OS declares neural network / NNAPI stack support");
        } catch (Throwable t) {
            FastImageSrLog.w("device_ml", "hasSystemFeature(android.hardware.neuralnetworks) failed", t);
        }
    }

    /**
     * Public NNAPI runtime feature level when the hidden API is reachable (API 31+).
     */
    @RequiresApi(api = Build.VERSION_CODES.S)
    private static void logNeuralNetworksRuntimeVersion() {
        long ver = tryNeuralNetworksGetRuntimeVersion();
        if (ver >= 0) {
            FastImageSrLog.i(
                    "device_ml",
                    "NeuralNetworks.getRuntimeVersion=" + ver
                            + " (NNAPI runtime present; vendor drivers may still reject a given model)");
        } else {
            FastImageSrLog.i("device_ml", "NeuralNetworks.getRuntimeVersion not readable via reflection");
        }
    }

    private static long tryNeuralNetworksGetRuntimeVersion() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return -1;
        }
        try {
            Class<?> clazz = Class.forName("android.neuralnetworks.NeuralNetworks");
            java.lang.reflect.Method m = clazz.getMethod("getRuntimeVersion");
            Object v = m.invoke(null);
            if (v instanceof Long) {
                return (Long) v;
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }
}
