package com.tvlink.tv;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.UUID;

final class TransferServer implements AutoCloseable {
    static final int PORT = 47832;
    private static final long LIMIT = 4L * 1024 * 1024 * 1024;
    interface RemoteCommands {
        boolean send(String key) throws IOException;
    }

    private final RemoteCommands remote;
    private final File folder;
    private final ServerSocket server;
    private Socket active;
    private volatile boolean closed;
    private volatile String code;
    private long codeExpiry;
    private long nextAttempt;
    private final android.content.SharedPreferences settings;

    TransferServer(File folder, android.content.SharedPreferences settings) throws IOException {
        this(folder, settings, null);
    }

    TransferServer(File folder, android.content.SharedPreferences settings, RemoteCommands remote) throws IOException {
        this.remote = remote;
        this.folder = folder;
        this.settings = settings;
        if (!folder.isDirectory() && !folder.mkdirs()) throw new IOException("Cannot create file folder");
        server = new ServerSocket();
        try {
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(remote == null ? PORT : 47833));
        } catch (IOException error) {
            server.close();
            throw error;
        }
        if (remote == null && settings.getString("transfer_token", "").isEmpty()) renewCode();
        else code = "";
        new Thread(this::serve, "TVLink receiver").start();
    }

    synchronized String renewCode() {
        code = String.format(Locale.ROOT, "%08d", new SecureRandom().nextInt(100000000));
        codeExpiry = android.os.SystemClock.elapsedRealtime() + 300000;
        return code;
    }

    synchronized String currentCode() {
        return code;
    }

    synchronized void forgetPhone() {
        settings.edit().remove("transfer_token").apply();
        renewCode();
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

    private synchronized void pair(String supplied, DataOutputStream out) throws IOException {
        long now = android.os.SystemClock.elapsedRealtime();
        if (now < nextAttempt || now >= codeExpiry || code.isEmpty() || !equal(code, supplied)) {
            nextAttempt = now + 1500;
            throw new IOException("Invalid or expired pairing code. Try again in a moment.");
        }
        String token = UUID.randomUUID().toString() + UUID.randomUUID();
        if (!settings.edit().putString("transfer_token", token).commit()) {
            throw new IOException("Could not save pairing");
        }
        code = "";
        out.writeUTF("OK");
        out.writeUTF(token);
    }

    private void handle(Socket socket) throws IOException {
        DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        try {
            String operation = in.readUTF();
            if (remote != null) {
                if (!"REMOTE".equals(operation)) throw new IOException("Unknown remote request");
                String token = settings.getString("transfer_token", "");
                if (token.isEmpty() || !equal(token, in.readUTF())) throw new IOException("Pair with TV again");
                if (!remote.send(in.readUTF())) throw new IOException("TV could not perform this key on the current screen");
                out.writeUTF("OK");
            } else if ("PAIR".equals(operation)) {
                pair(in.readUTF(), out);
            } else {
                String token = settings.getString("transfer_token", "");
                if (token.isEmpty() || !equal(token, in.readUTF())) throw new IOException("Pair with TV again");
                switch (operation) {
                    case "LIST":
                        File[] files = folder.listFiles(f -> f.isFile() && !f.getName().startsWith("."));
                        if (files == null) throw new IOException("Cannot list files");
                        out.writeUTF("OK");
                        out.writeInt(files.length);
                        for (File f : files) out.writeUTF(f.getName());
                        break;
                    case "SEND":
                        receive(in, out);
                        break;
                    case "DELETE":
                        if (!file(folder, in.readUTF()).delete()) throw new IOException("Cannot delete file");
                        out.writeUTF("OK");
                        break;
                    case "RENAME":
                        File source = file(folder, in.readUTF());
                        File target = file(folder, in.readUTF());
                        synchronized (folder.getParentFile().getAbsolutePath().intern()) {
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

    private void receive(DataInputStream in, DataOutputStream out) throws IOException {
        File target = file(folder, in.readUTF());
        long remaining = in.readLong();
        if (remaining < 0 || remaining > LIMIT || remaining > folder.getUsableSpace()) {
            throw new IOException("File size unavailable, too large or insufficient TV storage");
        }
        if (target.exists()) throw new IOException("A file with this name already exists");
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
            synchronized (folder.getParentFile().getAbsolutePath().intern()) {
                if (target.exists() || !temporary.renameTo(target)) throw new IOException("Cannot save file");
            }
            out.writeUTF("OK");
        } finally {
            temporary.delete();
        }
    }

    @Override public synchronized void close() {
        closed = true;
        try { server.close(); } catch (IOException ignored) {}
        try { if (active != null) active.close(); } catch (IOException ignored) {}
    }
}
