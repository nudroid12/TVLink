package com.tvlink.tv;

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
    private volatile boolean pairing;
    private volatile String pairingStatus = "Ready to pair";
    private String pairingPeer;

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

    synchronized void enablePairingDiscovery(boolean enabled) {
        pairing = enabled;
        pairingPeer = null;
        pairingStatus = "Ready to pair";
    }

    String pairingStatus() {
        return pairingStatus;
    }

    private synchronized boolean updatePairingStatus(DatagramPacket request, String message) {
        String prefix = "TVLinkPairStatus1 ";
        if (!message.startsWith(prefix)) return false;
        if (!pairing) return true;
        String peer = request.getAddress().getHostAddress();
        String stage = message.substring(prefix.length());
        if ("CONNECTING".equals(stage) || "CODE".equals(stage)) pairingPeer = peer;
        if (!peer.equals(pairingPeer)) return true;
        switch (stage) {
            case "CONNECTING": pairingStatus = "Phone connecting…"; break;
            case "CODE": pairingStatus = "Enter the code shown by Android TV on your phone."; break;
            case "VERIFYING": pairingStatus = "Pairing in progress…"; break;
            case "PAIRED": pairingStatus = "Phone reports Remote paired · File transfer not connected"; break;
            case "FAILED": pairingStatus = "Pairing failed. Tap Find TV on your phone to try again."; break;
            case "TIMED_OUT": pairingStatus = "Pairing code timed out. Try again from your phone."; break;
            case "CANCELLED": pairingStatus = "Pairing cancelled. Ready to pair again."; break;
            default: break;
        }
        return true;
    }

    private void respond(SharedPreferences settings) {
        while (!listener.isClosed()) {
            try {
                DatagramPacket request = new DatagramPacket(new byte[128], 128);
                listener.receive(request);
                String message = decode(request);
                if (updatePairingStatus(request, message)) continue;
                String prefix = "TVLinkPair1 ";
                if (pairing && message.startsWith(prefix)) {
                    String nonce = message.substring(prefix.length());
                    if (!nonce.matches("[0-9a-f-]{36}")) continue;
                    String name = settings.getString("device_name", android.os.Build.MODEL);
                    byte[] response = (message + " " + name).getBytes(StandardCharsets.UTF_8);
                    listener.send(new DatagramPacket(response, response.length, request.getSocketAddress()));
                    continue;
                }
                String[] parts = message.split(" ", 3);
                String token = settings.getString("link_token", "");
                if (token.isEmpty() || parts.length != 3 || !PREFIX.equals(parts[0])
                        || !matches(sign(token, "lookup-tv", parts[1]), parts[2])) continue;
                byte[] reply = sign(token, "found-tv", parts[1]).getBytes(StandardCharsets.UTF_8);
                listener.send(new DatagramPacket(reply, reply.length, request.getSocketAddress()));
            } catch (IOException ignored) {
                // The socket closes when the receiver stops.
            }
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
                throw new IOException("Cannot reach the linked device. Open TVLink on both devices and check the Wi-Fi connection. "
                        + original.getMessage(), original);
            }
        }
    }

    private static String find(String token) throws IOException {
        String nonce = UUID.randomUUID().toString();
        byte[] request = (PREFIX + " " + nonce + " " + sign(token, "lookup-mobile", nonce)).getBytes(StandardCharsets.UTF_8);
        String expected = sign(token, "found-mobile", nonce);
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            long deadline = android.os.SystemClock.elapsedRealtime() + 2400;
            while (android.os.SystemClock.elapsedRealtime() < deadline) {
                broadcast(socket, request);
                long nextProbe = Math.min(deadline, android.os.SystemClock.elapsedRealtime() + 600);
                while (android.os.SystemClock.elapsedRealtime() < nextProbe) {
                    socket.setSoTimeout((int) Math.max(1, nextProbe - android.os.SystemClock.elapsedRealtime()));
                    DatagramPacket reply = new DatagramPacket(new byte[128], 128);
                    try {
                        socket.receive(reply);
                    } catch (SocketTimeoutException timeout) {
                        break;
                    }
                    if (reply.getPort() == PORT && matches(expected, decode(reply))) {
                        return reply.getAddress().getHostAddress();
                    }
                }
            }
        }
        throw new IOException("Linked device not found on Wi-Fi");
    }

    private static void broadcast(DatagramSocket socket, byte[] request) throws IOException {
        java.util.Set<InetAddress> targets = new java.util.LinkedHashSet<>();
        targets.add(InetAddress.getByName("255.255.255.255"));
        java.util.Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces != null && interfaces.hasMoreElements()) {
            NetworkInterface network = interfaces.nextElement();
            if (!network.isUp() || network.isLoopback()) continue;
            for (InterfaceAddress address : network.getInterfaceAddresses()) {
                if (address.getBroadcast() != null) targets.add(address.getBroadcast());
            }
        }
        boolean sent = false;
        IOException failure = null;
        for (InetAddress target : targets) {
            try {
                socket.send(new DatagramPacket(request, request.length, target, PORT));
                sent = true;
            } catch (IOException error) {
                failure = error;
            }
        }
        if (!sent && failure != null) throw failure;
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
