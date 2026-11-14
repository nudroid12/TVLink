package com.tvlink.tv;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.*;

final class TransferTls {
    static SSLContext server(File directory) throws IOException {
        try {
            KeyManagerFactory factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            factory.init(RemoteIdentity.load(directory, "tvlink-transfer.p12"), RemoteIdentity.PASSWORD);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(factory.getKeyManagers(), null, null);
            return context;
        } catch (Exception error) {
            throw new IOException("Could not initialize encrypted file transfers", error);
        }
    }

    static SSLContext client() throws IOException {
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            // The file pairing comparison or saved-link HMAC authenticates this certificate
            // before any file operation. Both bind their proofs to its fingerprint.
            context.init(null, new TrustManager[] {new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                    throw new CertificateException("Unexpected TLS client certificate");
                }
                public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                    if (chain == null || chain.length == 0) throw new CertificateException("Missing TLS certificate");
                    chain[0].checkValidity();
                }
            }}, null);
            return context;
        } catch (java.security.GeneralSecurityException error) {
            throw new IOException("Could not initialize encrypted file transfers", error);
        }
    }

    static SSLSocket wrap(SSLContext context, Socket raw, boolean client) throws IOException {
        SSLSocket socket = (SSLSocket) context.getSocketFactory().createSocket(
                raw, raw.getInetAddress().getHostAddress(), raw.getPort(), true);
        try {
            socket.setUseClientMode(client);
            java.util.List<String> protocols = new java.util.ArrayList<>();
            for (String protocol : socket.getSupportedProtocols()) {
                if ("TLSv1.2".equals(protocol) || "TLSv1.3".equals(protocol)) protocols.add(protocol);
            }
            socket.setEnabledProtocols(protocols.toArray(new String[0]));
            socket.setSoTimeout(15000);
            socket.startHandshake();
            return socket;
        } catch (IOException | RuntimeException error) {
            socket.close();
            throw error;
        }
    }

    static String binding(SSLSocket socket, boolean client) throws IOException {
        try {
            Certificate[] chain = client ? socket.getSession().getPeerCertificates()
                    : socket.getSession().getLocalCertificates();
            if (chain == null || chain.length == 0) throw new IOException("Missing transfer certificate");
            return android.util.Base64.encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(chain[0].getEncoded()), android.util.Base64.NO_WRAP);
        } catch (java.security.GeneralSecurityException error) {
            throw new IOException("Could not identify encrypted transfer connection", error);
        }
    }
}
