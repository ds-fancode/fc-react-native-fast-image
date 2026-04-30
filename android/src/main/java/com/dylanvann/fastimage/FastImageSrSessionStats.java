package com.dylanvann.fastimage;

/**
 * Running counts for super-resolution attempts this process (for {@code sr_each} log lines).
 */
public final class FastImageSrSessionStats {

    private static volatile long srAttemptCount;
    private static volatile long srOkCount;
    private static volatile long srFailCount;

    private FastImageSrSessionStats() {
    }

    public static void recordSrAttempt(boolean success) {
        srAttemptCount++;
        if (success) {
            srOkCount++;
        } else {
            srFailCount++;
        }
    }

    public static String formatSessionSuffix() {
        return "session_sr total=" + srAttemptCount + " ok=" + srOkCount + " fail=" + srFailCount;
    }
}
