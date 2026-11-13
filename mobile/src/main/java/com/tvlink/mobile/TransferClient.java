package com.tvlink.mobile;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;

final class TransferClient implements AutoCloseable {
    private final Socket socket = new Socket();
    private final DataInputStream in;
    private final DataOutputStream out;

    TransferClient(String host, String operation, String credential) throws IOException {
        try {
            socket.connect(new InetSocketAddress(host, "REMOTE".equals(operation) ? 47833 : 47832), 5000);
            socket.setSoTimeout(15000);
            in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            out.writeUTF(operation);
            out.writeUTF(credential);
            out.flush();
        } catch (IOException error) {
            socket.close();
            throw error;
        }
    }

    private void expect(String expected) throws IOException {
        String result = in.readUTF();
        if ("ERROR".equals(result)) throw new IOException(in.readUTF());
        if (!expected.equals(result)) throw new IOException("Unexpected response from TV");
    }

    String pair() throws IOException {
        expect("OK");
        return in.readUTF();
    }

    List<String> list() throws IOException {
        expect("OK");
        int size = in.readInt();
        if (size < 0 || size > 100000) throw new IOException("Invalid file list");
        List<String> result = new ArrayList<>();
        for (int index = 0; index < size; index++) result.add(in.readUTF());
        return result;
    }

    void change(String... names) throws IOException {
        for (String name : names) out.writeUTF(name);
        out.flush();
        expect("OK");
    }

    void send(String name, long size, InputStream source, java.util.function.IntConsumer progress) throws IOException {
        out.writeUTF(name);
        out.writeLong(size);
        out.flush();
        expect("READY");
        byte[] buffer = new byte[65536];
        long remaining = size;
        int last = -1;
        while (remaining > 0) {
            int count = source.read(buffer, 0, (int) Math.min(buffer.length, remaining));
            if (count < 0) throw new EOFException("File ended early");
            out.write(buffer, 0, count);
            remaining -= count;
            int percent = (int) ((size - remaining) * 100 / size);
            if (percent != last) {
                last = percent;
                progress.accept(percent);
            }
        }
        out.flush();
        expect("OK");
    }

    @Override public void close() throws IOException { socket.close(); }
}
