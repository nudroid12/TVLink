package com.tvlink.mobile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

final class RemoteIdentity {
    static final String ALIAS = "tvlink";
    static final char[] PASSWORD = new char[0];

    // The identity is private to the app and excluded from Android backup.
    static synchronized KeyStore load(File directory) throws Exception {
        File file = new File(directory, "android-tv-remote.p12");
        KeyStore store = KeyStore.getInstance("PKCS12");
        if (file.exists()) {
            try (FileInputStream input = new FileInputStream(file)) {
                store.load(input, PASSWORD);
            }
            return store;
        }
        store.load(null, PASSWORD);
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        store.setKeyEntry(ALIAS, pair.getPrivate(), PASSWORD, new Certificate[] {certificate(pair)});

        File temporary = File.createTempFile("remote-", ".p12", directory);
        try {
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                store.store(output, PASSWORD);
                output.getFD().sync();
            }
            if (!temporary.renameTo(file)) throw new java.io.IOException("Could not save remote identity");
        } finally {
            temporary.delete();
        }
        return store;
    }

    private static Certificate certificate(KeyPair pair) throws Exception {
        // X.509 v1, self-signed with SHA256withRSA. The remote service pairs this public key.
        byte[] algorithm = der(0x30, new byte[] {0x06, 0x09, 0x2a, (byte) 0x86, 0x48,
                (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x01, 0x0b, 0x05, 0x00});
        byte[] name = der(0x30, der(0x31, der(0x30, new byte[] {0x06, 0x03, 0x55, 0x04, 0x03},
                der(0x0c, "TVLink".getBytes(StandardCharsets.UTF_8)))));
        long now = System.currentTimeMillis();
        byte[] valid = der(0x30, time(now - 86400000L), time(now + 3650L * 86400000L));
        BigInteger serial = new BigInteger(127, new SecureRandom()).setBit(126);
        byte[] body = der(0x30, der(0x02, serial.toByteArray()), algorithm,
                name, valid, name, pair.getPublic().getEncoded());
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(pair.getPrivate());
        signature.update(body);
        byte[] encoded = der(0x30, body, algorithm, der(0x03, new byte[] {0}, signature.sign()));
        return CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(encoded));
    }

    private static byte[] time(long millis) {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmss'Z'", Locale.ROOT);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return der(0x18, format.format(new Date(millis)).getBytes(StandardCharsets.US_ASCII));
    }

    private static byte[] der(int tag, byte[]... pieces) {
        int length = 0;
        for (byte[] piece : pieces) length += piece.length;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(tag);
        if (length < 128) {
            output.write(length);
        } else {
            int size = (Integer.SIZE - Integer.numberOfLeadingZeros(length) + 7) / 8;
            output.write(0x80 | size);
            for (int shift = (size - 1) * 8; shift >= 0; shift -= 8) output.write(length >>> shift);
        }
        for (byte[] piece : pieces) output.write(piece, 0, piece.length);
        return output.toByteArray();
    }

    private RemoteIdentity() {}
}
