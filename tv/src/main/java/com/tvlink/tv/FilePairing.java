package com.tvlink.tv;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Locale;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class FilePairing {
    static KeyPair keyPair() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    static String encode(byte[] value) {
        return android.util.Base64.encodeToString(value, android.util.Base64.NO_WRAP);
    }

    static String commitment(String key) throws GeneralSecurityException {
        return encode(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.US_ASCII)));
    }

    static Result derive(KeyPair own, String clientKey, String serverKey, boolean client, String binding)
            throws GeneralSecurityException, IOException {
        String peer = client ? serverKey : clientKey;
        if (peer.length() > 256) throw new IOException("Invalid file pairing key");
        PublicKey key = KeyFactory.getInstance("EC").generatePublic(
                new X509EncodedKeySpec(android.util.Base64.decode(peer, android.util.Base64.NO_WRAP)));
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(own.getPrivate());
        agreement.doPhase(key, true);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(agreement.generateSecret(), "HmacSHA256"));
        byte[] token = mac.doFinal(("TVLink file token v2:" + clientKey + ":" + serverKey + ":" + binding)
                .getBytes(StandardCharsets.US_ASCII));
        byte[] code = mac.doFinal(("TVLink file confirmation v2:" + clientKey + ":" + serverKey + ":" + binding)
                .getBytes(StandardCharsets.US_ASCII));
        int number = (java.nio.ByteBuffer.wrap(code).getInt() & Integer.MAX_VALUE) % 1000000;
        return new Result(encode(token), String.format(Locale.ROOT, "%06d", number));
    }

    static final class Result {
        final String token;
        final String code;
        Result(String token, String code) { this.token = token; this.code = code; }
    }
}
