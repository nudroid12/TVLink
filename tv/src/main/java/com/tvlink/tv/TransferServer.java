package com.tvlink.tv;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

final class TransferServer implements AutoCloseable {
    static final int PORT = 47832;
    private static final long LIMIT = 4L * 1024 * 1024 * 1024;
    private final File folder;
    private final ServerSocket server;
    private final LinkNetwork discovery;
    private Socket active;
    private volatile boolean closed;
    private final android.content.SharedPreferences settings;

    TransferServer(File folder, android.content.SharedPreferences settings) throws IOException {
        this.folder = folder;
        this.settings = settings;
        if (!folder.isDirectory() && !folder.mkdirs()) throw new IOException("Cannot create file folder");
        server = new ServerSocket();
        try {
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(PORT));
        } catch (IOException error) {
            server.close();
            throw error;
        }
        discovery = new LinkNetwork(settings);
        new Thread(this::serve, "TVLink receiver").start();
    }

    static File file(File folder, String name) throws IOException {
        if (name.isEmpty() || name.startsWith(".") || name.contains("/") || name.contains("\\")
                || name.getBytes(StandardCharsets.UTF_8).length > 240) throw new IOException("Invalid file name");
        for (int index = 0; index < name.length(); index++) {
            if (Character.isISOControl(name.charAt(index))) throw new IOException("Invalid file name");
        }
        return new File(folder, name);
    }

    private void serve() {
        while (!closed) {
            try (Socket socket = server.accept()) {
                synchronized (this) {
                    if (closed) return;
                    active = socket;
                }
                socket.setSoTimeout(15000);
                handle(socket);
            } catch (IOException ignored) {
                // A dropped connection does not stop the listener.
            } finally {
                synchronized (this) { active = null; }
            }
        }
    }

    private static boolean equal(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private boolean pairingEnabled;
    private FileApproval approval;

    synchronized void enableLinking(boolean enabled) {
        pairingEnabled = enabled;
        if (!enabled) {
            if (approval != null && !Boolean.TRUE.equals(approval.allowed) && active != null) {
                try { active.close(); } catch (IOException ignored) {}
            }
            approval = null;
            notifyAll();
        }
        discovery.enablePairingDiscovery(enabled);
    }

    String pairingStatus() { return discovery.pairingStatus(); }

    static final class FileApproval {
        final String code;
        Boolean allowed;
        FileApproval(String code) { this.code = code; }
    }

    synchronized FileApproval fileApproval() { return approval; }

    synchronized void answerFileApproval(FileApproval request, boolean allowed) {
        if (approval != request || request.allowed != null) return;
        request.allowed = allowed;
        notifyAll();
    }

    private void finishLink(String commitment, String host, DataInputStream in, DataOutputStream out, Socket socket)
            throws IOException {
        synchronized (this) {
            if (!pairingEnabled || !settings.getString("link_token", "").isEmpty()) {
                throw new IOException("Open Pair or Receive on the TV to connect file transfer");
            }
        }
        if (!commitment.matches("[A-Za-z0-9+/]{43}=")) throw new IOException("Invalid pairing request");
        FilePairing.Result link;
        try {
            java.security.KeyPair key = FilePairing.keyPair();
            String serverKey = FilePairing.encode(key.getPublic().getEncoded());
            out.writeUTF("PAIR_KEY");
            out.writeUTF(serverKey);
            out.flush();
            String clientKey = in.readUTF();
            if (clientKey.length() > 256 || !equal(commitment, FilePairing.commitment(clientKey))) {
                throw new IOException("File pairing key changed");
            }
            link = FilePairing.derive(key, clientKey, serverKey, false);
        } catch (java.security.GeneralSecurityException | IllegalArgumentException error) {
            throw new IOException("Could not verify file pairing keys", error);
        }

        FileApproval request = new FileApproval(link.code);
        long end = android.os.SystemClock.elapsedRealtime() + 55000;
        synchronized (this) {
            if (!pairingEnabled || closed) throw new IOException("File pairing cancelled on TV");
            approval = request;
        }
        try {
            socket.setSoTimeout(55000);
            if (!"ALLOW".equals(in.readUTF())) throw new IOException("File pairing declined on phone");
            synchronized (this) {
                while (approval == request && request.allowed == null) {
                    long remaining = end - android.os.SystemClock.elapsedRealtime();
                    if (remaining <= 0) throw new IOException("File pairing timed out");
                    wait(remaining);
                }
                if (approval != request || !Boolean.TRUE.equals(request.allowed)
                        || android.os.SystemClock.elapsedRealtime() >= end) {
                    throw new IOException("File pairing declined, cancelled or expired on TV");
                }
                if (!settings.edit().putString("peer_host", host).putString("link_token", link.token).commit()) {
                    throw new IOException("Could not save the link on the TV");
                }
                enableLinking(false);
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("File pairing interrupted", error);
        } finally {
            synchronized (this) { if (approval == request) approval = null; }
        }
        out.writeUTF("OK");
    }

    private void handle(Socket socket) throws IOException {
        DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        try {
            String operation = in.readUTF();
            if ("PAIR_FILES".equals(operation)) {
                finishLink(in.readUTF(), socket.getInetAddress().getHostAddress(), in, out, socket);
            } else {
                String token = settings.getString("link_token", "");
                authorize(operation, token, in, out);
                String peer = socket.getInetAddress().getHostAddress();
                if (!peer.equals(settings.getString("peer_host", ""))) {
                    settings.edit().putString("peer_host", peer).apply();
                }
                switch (operation) {
                    case "LIST":
                        File[] files = folder.listFiles(f -> f.isFile() && !f.getName().startsWith("."));
                        if (files == null) throw new IOException("Cannot list files");
                        out.writeUTF("OK");
                        out.writeInt(files.length);
                        for (File f : files) out.writeUTF(f.getName());
                        break;
                    case "PING":
                        out.writeUTF("OK");
                        break;
                    case "SEND":
                        if (!settings.getBoolean("receive", true)) throw new IOException("Receive Transfers is disabled");
                        receive(in, out);
                        break;
                    case "DELETE":
                        if (!file(folder, in.readUTF()).delete()) throw new IOException("Cannot delete file");
                        out.writeUTF("OK");
                        break;
                    case "RENAME":
                        File source = file(folder, in.readUTF());
                        File target = file(folder, in.readUTF());
                        synchronized (folder.getAbsolutePath().intern()) {
                            if (target.exists() || !source.renameTo(target)) throw new IOException("Cannot rename file");
                        }
                        out.writeUTF("OK");
                        break;
                    default: throw new IOException("Unknown request");
                }
            }
        } catch (IOException error) {
            out.writeUTF("ERROR");
            out.writeUTF(error.getMessage() == null ? "Request failed" : error.getMessage());
        }
        out.flush();
    }

    private void authorize(String operation, String token, DataInputStream in, DataOutputStream out)
            throws IOException {
        if (token.isEmpty()) throw new IOException("Link TVLink first");
        String clientNonce = in.readUTF();
        String nonce = java.util.UUID.randomUUID().toString();
        out.writeUTF("AUTH");
        out.writeUTF(LinkNetwork.sign(token, "server", clientNonce));
        out.writeUTF(nonce);
        out.flush();
        if (!equal(LinkNetwork.sign(token, operation, nonce), in.readUTF())) {
            throw new IOException("Device link did not match");
        }
    }

    private void receive(DataInputStream in, DataOutputStream out) throws IOException {
        File target = file(folder, in.readUTF());
        long remaining = in.readLong();
        if (remaining < 0 || remaining > LIMIT || remaining > folder.getUsableSpace()) {
            throw new IOException("File size unavailable, too large or insufficient storage");
        }
        boolean overwrite = settings.getBoolean("overwrite", false);
        if (target.exists() && (!overwrite || !target.isFile())) {
            throw new IOException("A file with this name already exists");
        }
        File temporary = File.createTempFile(".upload-", ".part", folder);
        try {
            out.writeUTF("READY");
            out.flush();
            try (FileOutputStream data = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[65536];
                while (remaining > 0) {
                    int count = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                    if (count < 0) throw new EOFException("Transfer ended early");
                    data.write(buffer, 0, count);
                    remaining -= count;
                }
            }
            synchronized (folder.getAbsolutePath().intern()) {
                if (target.exists() && (!overwrite || !target.isFile())) throw new IOException("Cannot save file");
                if (!settings.getBoolean("modify_date", true) && target.isFile()) {
                    temporary.setLastModified(target.lastModified());
                }
                if (!temporary.renameTo(target)) throw new IOException("Cannot save file");
            }
            out.writeUTF("OK");
        } finally {
            temporary.delete();
        }
    }

    @Override public synchronized void close() {
        closed = true;
        enableLinking(false);
        discovery.close();
        try { server.close(); } catch (IOException ignored) {}
        try { if (active != null) active.close(); } catch (IOException ignored) {}
    }
}
