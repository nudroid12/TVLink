package com.tvlink.mobile;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

final class TransferServer implements AutoCloseable {
    static final int PORT = 47832;
    private static final long LIMIT = 4L * 1024 * 1024 * 1024;
    private final File folder;
    private final File identityDirectory;
    private final ServerSocket server;
    private final LinkNetwork discovery;
    private Socket active;
    private volatile boolean closed;
    private final android.content.SharedPreferences settings;

    TransferServer(File folder, File identityDirectory, android.content.SharedPreferences settings) throws IOException {
        this.folder = folder;
        this.identityDirectory = identityDirectory;
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
        javax.net.ssl.SSLContext tls = null;
        while (!closed) {
            try (Socket socket = server.accept()) {
                synchronized (this) {
                    if (closed) return;
                    active = socket;
                }
                if (tls == null) tls = TransferTls.server(identityDirectory);
                try (javax.net.ssl.SSLSocket encrypted = TransferTls.wrap(tls, socket, false)) {
                    handle(encrypted, TransferTls.binding(encrypted, false));
                }
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

    private void handle(Socket socket, String binding) throws IOException {
        DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        try {
            String operation = in.readUTF();
            String token = settings.getString("link_token", "");
            authorize(operation, token, in, out, binding);
            switch (operation) {
                case "PING":
                    out.writeUTF("OK");
                    break;
                case "SEND":
                    if (!settings.getBoolean("receive", true)) throw new IOException("Receive Transfers is disabled");
                    receive(in, out);
                    break;
                default: throw new IOException("Unknown request");
            }
        } catch (IOException error) {
            out.writeUTF("ERROR");
            out.writeUTF(error.getMessage() == null ? "Request failed" : error.getMessage());
        }
        out.flush();
    }

    private void authorize(String operation, String token, DataInputStream in, DataOutputStream out, String binding)
            throws IOException {
        if (token.isEmpty()) throw new IOException("Link TVLink first");
        String clientNonce = in.readUTF();
        String nonce = java.util.UUID.randomUUID().toString();
        out.writeUTF("AUTH");
        out.writeUTF(LinkNetwork.sign(token, "tls-server:" + operation, binding + ":" + clientNonce));
        out.writeUTF(nonce);
        out.flush();
        if (!equal(LinkNetwork.sign(token, "tls-client:" + operation, binding + ":" + nonce), in.readUTF())) {
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
        discovery.close();
        try { server.close(); } catch (IOException ignored) {}
        try { if (active != null) active.close(); } catch (IOException ignored) {}
    }
}
