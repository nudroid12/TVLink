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
        if (!enabled || digit < -1 || digit > 3) return false;
        if (digit == -1) {
            token.setLength(0);
            deadline = android.os.SystemClock.elapsedRealtime() + 12000;
            notifyAll();
        } else {
            if (android.os.SystemClock.elapsedRealtime() >= deadline || token.length() >= LENGTH) return false;
            token.append(digit);
            if (token.length() == LENGTH) notifyAll();
        }
        return true;
    }

    // Allow the final Android key events to reach the UI before checking the proof.
    synchronized String verify(String proof, String linkToken) throws java.io.IOException {
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
        String expected = LinkNetwork.sign(candidate, "remote-pair-phone", linkToken);
        if (!java.security.MessageDigest.isEqual(expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                proof.getBytes(java.nio.charset.StandardCharsets.UTF_8))) return null;
        enable(false);
        return candidate;
    }
}
