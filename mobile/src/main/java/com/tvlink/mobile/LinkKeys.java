package com.tvlink.mobile;

final class LinkKeys {
    static final int LENGTH = 64;
    private static final int[] DIRECTION = {19, 20, 21, 22};

    static String newToken() {
        byte[] random = new byte[LENGTH];
        new java.security.SecureRandom().nextBytes(random);
        StringBuilder token = new StringBuilder(LENGTH);
        for (byte digit : random) token.append(digit & 3);
        return token.toString();
    }

    static void send(AndroidTvRemote remote, String token) throws java.io.IOException, InterruptedException {
        remote.key(23); // Start marker.
        for (int index = 0; index < token.length(); index++) {
            Thread.sleep(35);
            remote.key(DIRECTION[token.charAt(index) - '0']);
        }
    }

}
