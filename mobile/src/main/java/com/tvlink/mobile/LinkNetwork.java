package com.tvlink.mobile;

import android.content.SharedPreferences;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.GeneralSecurityException;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class LinkNetwork implements AutoCloseable {
    private static final int PORT = 47833;
    private static final String PREFIX = "TVLink1";
    private final DatagramSocket listener;

    LinkNetwork(SharedPreferences settings) {
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket(null);
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(PORT));
        } catch (SocketException error) {
            if (socket != null) socket.close();
            socket = null;
        }
        listener = socket;
        if (listener != null) new Thread(() -> respond(settings), "TVLink discovery").start();
    }

    static String sign(String token, String operation, String nonce) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(token.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] proof = mac.doFinal((PREFIX + ":" + operation + ":" + nonce).getBytes(StandardCharsets.UTF_8));
            return android.util.Base64.encodeToString(proof, android.util.Base64.NO_WRAP);
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException(error);
        }
    }

    private void respond(SharedPreferences settings) {
        while (!listener.isClosed()) {
            try {
                DatagramPacket request = new DatagramPacket(new byte[128], 128);
                listener.receive(request);
                String[] parts = decode(request).split(" ", 3);
                String token = settings.getString("link_token", "");
                if (token.isEmpty() || parts.length != 3 || !PREFIX.equals(parts[0])
                        || !matches(sign(token, "lookup-mobile", parts[1]), parts[2])) continue;
                byte[] reply = sign(token, "found-mobile", parts[1]).getBytes(StandardCharsets.UTF_8);
                listener.send(new DatagramPacket(reply, reply.length, request.getSocketAddress()));
            } catch (IOException ignored) {
                // The socket closes when the receiver stops.
            }
        }
    }

    static java.util.Map<String, String> findPairingTvs() throws IOException {
        java.util.Map<String, String> devices = new java.util.LinkedHashMap<>();
        String marker = "TVLinkPair1 " + UUID.randomUUID();
        byte[] request = marker.getBytes(StandardCharsets.UTF_8);
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            InetAddress broadcast = InetAddress.getByName("255.255.255.255");
            long deadline = android.os.SystemClock.elapsedRealtime() + 2200;
            while (android.os.SystemClock.elapsedRealtime() < deadline) {
                socket.send(new DatagramPacket(request, request.length, broadcast, PORT));
                long nextProbe = Math.min(deadline, android.os.SystemClock.elapsedRealtime() + 550);
                while (android.os.SystemClock.elapsedRealtime() < nextProbe) {
                    socket.setSoTimeout((int) Math.max(1, nextProbe - android.os.SystemClock.elapsedRealtime()));
                    DatagramPacket reply = new DatagramPacket(new byte[256], 256);
                    try {
                        socket.receive(reply);
                    } catch (SocketTimeoutException timeout) {
                        break;
                    }
                    String message = decode(reply);
                    if (reply.getPort() == PORT && message.startsWith(marker + " ")) {
                        devices.put(reply.getAddress().getHostAddress(), message.substring(marker.length() + 1));
                    }
                }
            }
        }
        return devices;
    }

    static void sendPairingStatus(String host, String status) {
        try (DatagramSocket socket = new DatagramSocket()) {
            byte[] data = ("TVLinkPairStatus1 " + status).getBytes(StandardCharsets.UTF_8);
            socket.send(new DatagramPacket(data, data.length, InetAddress.getByName(host), PORT));
        } catch (IOException ignored) {
            // Pairing is independent of status delivery.
        }
    }

    static String resolve(String host, String token) throws IOException {
        try (TransferClient client = new TransferClient(host, "PING", token)) {
            client.confirm();
            return host;
        } catch (IOException original) {
            try {
                return find(token);
            } catch (IOException unavailable) {
                throw original;
            }
        }
    }

    private static String find(String token) throws IOException {
        String nonce = UUID.randomUUID().toString();
        byte[] request = (PREFIX + " " + nonce + " " + sign(token, "lookup-tv", nonce)).getBytes(StandardCharsets.UTF_8);
        String expected = sign(token, "found-tv", nonce);
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.send(new DatagramPacket(request, request.length, InetAddress.getByName("255.255.255.255"), PORT));
            long deadline = android.os.SystemClock.elapsedRealtime() + 2000;
            while (android.os.SystemClock.elapsedRealtime() < deadline) {
                socket.setSoTimeout((int) Math.max(1, deadline - android.os.SystemClock.elapsedRealtime()));
                DatagramPacket reply = new DatagramPacket(new byte[128], 128);
                socket.receive(reply);
                if (matches(expected, decode(reply))) return reply.getAddress().getHostAddress();
            }
        }
        throw new IOException("Linked device not found on Wi-Fi");
    }

    private static String decode(DatagramPacket packet) {
        return new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
    }

    static boolean matches(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    @Override public void close() {
        if (listener != null) listener.close();
    }
}
