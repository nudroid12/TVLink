package com.tvlink.tv;

final class LinkKeys {
    private static final int LENGTH = 64;
    private boolean enabled;
    private final StringBuilder token = new StringBuilder(LENGTH);
    private long deadline;

    synchronized void enable(boolean enabled) {
        this.enabled = enabled;
        token.setLength(0);
        deadline = 0;
        notifyAll();
    }

    synchronized boolean input(int digit) {
        if (!enabled) return false;
        if (digit == -1) {
            token.setLength(0);
            deadline = android.os.SystemClock.elapsedRealtime() + 12000;
            notifyAll();
        } else if (android.os.SystemClock.elapsedRealtime() < deadline && token.length() < LENGTH) {
            token.append(digit);
            if (token.length() == LENGTH) notifyAll();
        }
        return true;
    }

    // Allow the final Android key events to reach the UI before checking the proof.
    synchronized String verify(String proof) throws java.io.IOException {
        long limit = android.os.SystemClock.elapsedRealtime() + 2500;
        while (enabled && token.length() < LENGTH && android.os.SystemClock.elapsedRealtime() < limit) {
            try {
                wait(Math.max(1, limit - android.os.SystemClock.elapsedRealtime()));
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new java.io.IOException("Link interrupted", error);
            }
        }
        if (!enabled || token.length() != LENGTH || android.os.SystemClock.elapsedRealtime() >= deadline) return null;
        String candidate = token.toString();
        if (!java.security.MessageDigest.isEqual(proof(candidate).getBytes(), proof.getBytes())) return null;
        return candidate;
    }

    static String proof(String token) {
        try {
            byte[] result = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(("TVLink setup v1:" + token).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return android.util.Base64.encodeToString(result, android.util.Base64.NO_WRAP);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
