package com.tvlink.mobile;

import java.io.*;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import javax.net.ssl.*;

// Android TV Remote v2 wire format, cross-checked against tronikos/androidtvremote2.
final class AndroidTvRemote implements AutoCloseable {
    private static final String LEGACY_ALIAS = "tvlink_android_tv_remote";
    private final SSLSocket socket;
    private final DataInputStream input;
    private final OutputStream output;
    private final X509Certificate identity;
    private volatile boolean closed;

    AndroidTvRemote(android.content.Context androidContext, File identityDirectory, String host, boolean pairing, String pin,
                    boolean legacyIdentity) throws Exception {
        this(androidContext, identityDirectory, host, pairing, pin, legacyIdentity, message -> {});
    }

    AndroidTvRemote(android.content.Context androidContext, File identityDirectory, String host, boolean pairing,
                    String pin, boolean legacyIdentity, java.util.function.Consumer<String> log) throws Exception {
        log.accept("Loading Remote identity…");
        KeyStore legacy = KeyStore.getInstance("AndroidKeyStore");
        legacy.load(null);
        String legacyAlias = LEGACY_ALIAS;
        boolean useLegacy = !pairing && legacyIdentity && legacy.containsAlias(legacyAlias);
        KeyStore keys = useLegacy ? legacy : RemoteIdentity.load(identityDirectory);
        String alias = useLegacy ? legacyAlias : RemoteIdentity.ALIAS;
        identity = (X509Certificate) keys.getCertificate(alias);
        PrivateKey key = (PrivateKey) keys.getKey(alias, useLegacy ? null : RemoteIdentity.PASSWORD);
        X509KeyManager client = new X509ExtendedKeyManager() {
            public String[] getClientAliases(String type, Principal[] issuers) { return new String[] {alias}; }
            public String chooseClientAlias(String[] types, Principal[] issuers, java.net.Socket s) { return alias; }
            public String[] getServerAliases(String type, Principal[] issuers) { return null; }
            public String chooseServerAlias(String type, Principal[] issuers, java.net.Socket s) { return null; }
            public X509Certificate[] getCertificateChain(String requestedAlias) {
                return alias.equals(requestedAlias) ? new X509Certificate[] {identity} : null;
            }
            public PrivateKey getPrivateKey(String requestedAlias) { return alias.equals(requestedAlias) ? key : null; }
        };
        X509TrustManager trust = new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            public void checkClientTrusted(X509Certificate[] chain, String type) throws CertificateException {
                throw new CertificateException("Unexpected client certificate");
            }
            public void checkServerTrusted(X509Certificate[] chain, String type) throws CertificateException {
                if (chain.length == 0 || (!pairing && !pin.equals(fingerprint(chain[0])))) {
                    throw new CertificateException("TV identity changed. Pair again.");
                }
            }
        };
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(new KeyManager[] {client}, new TrustManager[] {trust}, null);
        int port = pairing ? 6467 : 6466;
        java.net.Socket raw = RemoteDiscovery.connect(androidContext, host, port, log);
        log.accept("TCP connected. Starting TLS handshake…");
        try {
            socket = (SSLSocket) context.getSocketFactory().createSocket(raw, host, port, true);
            socket.setSoTimeout(8000);
            try {
                socket.startHandshake();
            } catch (SSLException error) {
                throw new IOException("TLS handshake with Android TV Remote Service failed: " + error.getMessage(), error);
            }
            input = new DataInputStream(socket.getInputStream());
            output = socket.getOutputStream();
        } catch (Exception error) {
            try { raw.close(); } catch (IOException closeError) { error.addSuppressed(closeError); }
            throw error;
        }
    }

    private static String fingerprint(X509Certificate certificate) throws CertificateException {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(certificate.getPublicKey().getEncoded());
            return android.util.Base64.encodeToString(digest, android.util.Base64.NO_WRAP);
        } catch (NoSuchAlgorithmException error) {
            throw new CertificateException(error);
        }
    }

    void beginPairing() throws IOException {
        String step = "pairing request";
        try {
            pairMessage(10, concat(string(1, "atvremote"), string(2, "TVLink")));
            expectPair(11);
            step = "code format negotiation";
            byte[] encoding = concat(number(1, 3), number(2, 6));
            pairMessage(20, concat(field(1, encoding), number(3, 1)));
            expectPair(20);
            step = "pairing configuration";
            pairMessage(30, concat(field(1, encoding), number(2, 1)));
            expectPair(31);
        } catch (IOException error) {
            throw new IOException("Android TV Remote Service failed at " + step + ": " + error.getMessage(), error);
        }
    }

    String finishPairing(String code) throws Exception {
        if (!code.matches("[0-9A-Fa-f]{6}")) throw new IOException("Enter the six-character TV code.");
        X509Certificate peer = (X509Certificate) socket.getSession().getPeerCertificates()[0];
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (X509Certificate cert : new X509Certificate[] {identity, peer}) {
            RSAPublicKey rsa = (RSAPublicKey) cert.getPublicKey();
            for (java.math.BigInteger value : new java.math.BigInteger[] {rsa.getModulus(), rsa.getPublicExponent()}) {
                byte[] bytes = value.toByteArray();
                digest.update(bytes, bytes[0] == 0 ? 1 : 0, bytes.length - (bytes[0] == 0 ? 1 : 0));
            }
        }
        digest.update((byte) Integer.parseInt(code.substring(2, 4), 16));
        digest.update((byte) Integer.parseInt(code.substring(4), 16));
        byte[] secret = digest.digest();
        if ((secret[0] & 255) != Integer.parseInt(code.substring(0, 2), 16)) {
            throw new IOException("Code does not match this TV. Start pairing again.");
        }
        pairMessage(40, field(1, secret));
        expectPair(41);
        return fingerprint(peer);
    }

    private void pairMessage(int type, byte[] body) throws IOException {
        write(concat(number(1, 2), number(2, 200), field(type, body)));
    }

    private void expectPair(int type) throws IOException {
        Map<Integer, Object> message = read();
        if (!Integer.valueOf(200).equals(message.get(2)) || !message.containsKey(type)) {
            throw new IOException("Android TV rejected pairing. Check the TV and try again.");
        }
    }

    void start() throws IOException {
        start(message -> {});
    }

    void start(java.util.function.Consumer<String> log) throws IOException {
        start(log, null);
    }

    private static final int IME_FEATURE = 1 << 2;
    private static final int VOICE_FEATURE = 1 << 3;
    private static final int VOICE_BEGIN = 30;
    private static final int VOICE_PAYLOAD = 31;
    private static final int VOICE_END = 32;
    private volatile boolean voiceSupported;
    private final Object voiceLock = new Object();
    private java.util.concurrent.CompletableFuture<Integer> voiceBegin;
    private volatile int voiceSession = -1;
    private volatile int voiceAcknowledged = -1;
    private java.util.function.Consumer<ImeState> imeListener;
    private ImeState imeState;

    static final class ImeState {
        final int field;
        final int counter;
        final int inputType;
        final String text;

        ImeState(int field, int counter, int inputType, String text) {
            this.field = field;
            this.counter = counter;
            this.inputType = inputType;
            this.text = text;
        }
    }

    void start(java.util.function.Consumer<String> log,
               java.util.function.Consumer<ImeState> listener) throws IOException {
        imeListener = listener;
        final int ping = 1;
        final int keys = 1 << 1;
        final int power = 1 << 5;
        final int volume = 1 << 6;
        int features = ping | keys | power | volume;
        if (listener != null) features |= IME_FEATURE | VOICE_FEATURE;
        boolean configured = false;
        int frames = 0;
        boolean activated = false;
        long deadline = android.os.SystemClock.elapsedRealtime() + 5000;
        while (!closed && frames++ < 64) {
            long remaining = deadline - android.os.SystemClock.elapsedRealtime();
            if (remaining <= 0) break;
            socket.setSoTimeout((int) remaining);
            Map<Integer, Object> message;
            try {
                message = read();
            } catch (java.net.SocketTimeoutException timeout) {
                throw new IOException(activated ? "TV did not send Remote ready after activation (5s limit)"
                        : "TV did not finish Remote negotiation (5s limit)", timeout);
            }
            if (message.containsKey(1)) {
                Map<Integer, Object> config = parse((byte[]) message.get(1));
                int offered = (Integer) config.getOrDefault(1, 0);
                features &= offered;
                voiceSupported = (features & VOICE_FEATURE) != 0;
                log.accept("TV capabilities: " + offered + "; requested: " + features + ".");
                if ((features & keys) == 0) throw new IOException("TV does not offer remote keys");
                byte[] info = concat(number(3, 1), string(4, "1"), string(5, "atvremote"), string(6, "1.0.0"));
                write(field(1, concat(number(1, features), field(2, info))));
                configured = true;
            }
            if (message.containsKey(2) && configured) {
                write(field(2, number(1, features)));
                activated = true;
                log.accept("Activation sent. Waiting for TV ready (5s negotiation limit)…");
            }
            if (message.containsKey(3)) throw new IOException("TV rejected remote connection");
            pong(message);
            handleIme(message);
            if (message.containsKey(8)) log.accept("TV keepalive received and answered.");
            if (message.containsKey(40)) {
                startKeepalive();
                log.accept("TV confirmed the Remote session.");
                return;
            }
            if (activated && (message.containsKey(50) || message.containsKey(20))) {
                startKeepalive();
                log.accept("TV sent Remote state after activation. Opening controls.");
                return;
            }
        }
        throw new IOException(activated ? "TV did not send Remote ready after activation"
                : "TV did not finish Remote negotiation");
    }

    private void startKeepalive() throws IOException {
        socket.setSoTimeout(16000);
        new Thread(() -> {
            try {
                while (!closed) {
                    Map<Integer, Object> message = read();
                    pong(message);
                    handleVoice(message);
                    handleIme(message);
                }
            } catch (IOException ignored) {
                close();
            }
        }, "TVLink remote keepalive").start();
    }

    private synchronized void handleIme(Map<Integer, Object> message) throws IOException {
        if (imeListener == null || closed) return;
        if (message.containsKey(20)) {
            Map<Integer, Object> ime = nested(message, 20);
            Map<Integer, Object> editor = nested(ime, 1);
            Map<Integer, Object> status = nested(ime, 2);
            int field = integer(editor, 1);
            int type = integer(editor, 2);
            imeState = field == 0 || type == 0 ? null
                    : new ImeState(field, integer(status, 1), type, readText(status, 2));
            imeListener.accept(imeState);
        } else if (imeState != null && message.containsKey(22)) {
            Map<Integer, Object> payload = nested(message, 22);
            Map<Integer, Object> status = nested(payload, 2);
            if (status.isEmpty()) return;
            boolean ownChange = integer(payload, 1) != 0;
            imeState = new ImeState(imeState.field, integer(status, 1), imeState.inputType,
                    ownChange ? imeState.text : readText(status, 2));
            if (!ownChange) imeListener.accept(imeState);
        }
    }

    synchronized boolean replaceImeText(int fieldId, String value) throws IOException {
        if (closed || imeState == null || imeState.field != fieldId) return false;
        if (imeState.text.equals(value)) return true;
        write(field(21, imeEdit(imeState, value)));
        imeState = new ImeState(imeState.field, imeState.counter, imeState.inputType, value);
        return true;
    }

    static byte[] imeEdit(ImeState state, String value) {
        byte[] range = concat(number(1, 0), number(2, state.text.length()), string(3, value));
        return concat(number(1, state.field), number(2, state.counter), field(3, field(2, range)));
    }

    private static Map<Integer, Object> nested(Map<Integer, Object> fields, int id) throws IOException {
        Object value = fields.get(id);
        return value instanceof byte[] ? parse((byte[]) value) : java.util.Collections.emptyMap();
    }

    private static int integer(Map<Integer, Object> fields, int id) {
        Object value = fields.get(id);
        return value instanceof Integer ? (Integer) value : 0;
    }

    private static String readText(Map<Integer, Object> fields, int id) {
        Object value = fields.get(id);
        return value instanceof byte[]
                ? new String((byte[]) value, java.nio.charset.StandardCharsets.UTF_8) : "";
    }

    private void pong(Map<Integer, Object> message) throws IOException {
        if (message.containsKey(8)) {
            Map<Integer, Object> ping = parse((byte[]) message.get(8));
            write(field(9, number(1, (Integer) ping.getOrDefault(1, 0))));
        }
    }

    boolean supportsVoice() { return voiceSupported; }

    int beginVoice() throws IOException {
        java.util.concurrent.CompletableFuture<Integer> ready = new java.util.concurrent.CompletableFuture<>();
        synchronized (voiceLock) {
            if (closed || !voiceSupported) throw new IOException("TV voice input is unavailable.");
            if (voiceBegin != null || voiceSession >= 0) throw new IOException("Voice input is already active.");
            voiceBegin = ready;
        }
        boolean opened = false;
        try {
            key(android.view.KeyEvent.KEYCODE_SEARCH);
            int session = ready.get(5, java.util.concurrent.TimeUnit.SECONDS);
            if (!voiceActive(session)) throw new IOException("The TV ended voice input.");
            write(field(VOICE_BEGIN, number(1, session)));
            opened = true;
            return session;
        } catch (java.util.concurrent.TimeoutException error) {
            throw new IOException("The TV didn’t start a voice session.", error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("Voice input was interrupted.", error);
        } catch (java.util.concurrent.ExecutionException error) {
            throw new IOException("Remote disconnected during voice input.", error);
        } finally {
            synchronized (voiceLock) {
                voiceBegin = null;
                if (!opened) voiceSession = -1;
            }
        }
    }

    private void handleVoice(Map<Integer, Object> message) throws IOException {
        if (!message.containsKey(VOICE_BEGIN) && !message.containsKey(VOICE_PAYLOAD)
                && !message.containsKey(VOICE_END)) return;
        synchronized (voiceLock) {
            if (message.containsKey(VOICE_BEGIN) && voiceBegin != null && !voiceBegin.isDone()) {
                Object id = nested(message, VOICE_BEGIN).get(1);
                if (id instanceof Integer && (Integer) id >= 0) {
                    voiceAcknowledged = -1;
                    voiceSession = (Integer) id;
                    voiceBegin.complete(voiceSession);
                }
            }
            if (message.containsKey(VOICE_PAYLOAD) && voiceSession >= 0) {
                Object id = nested(message, VOICE_PAYLOAD).get(1);
                if (Integer.valueOf(voiceSession).equals(id)) voiceAcknowledged = voiceSession;
            }
            if (message.containsKey(VOICE_END) && voiceSession >= 0) {
                Object id = nested(message, VOICE_END).get(1);
                if (Integer.valueOf(voiceSession).equals(id)) {
                    android.util.Log.d(RemoteVoice.LOG_TAG, "Session " + voiceSession + ": VOICE_END from TV");
                    voiceSession = -1;
                }
            }
        }
    }

    boolean voiceAcknowledged(int session) { return voiceAcknowledged == session; }

    boolean voiceActive(int session) { return !closed && voiceSession == session; }

    boolean sendVoice(int session, byte[] samples) throws IOException {
        if (!voiceActive(session)) return false;
        write(field(VOICE_PAYLOAD, concat(number(1, session), field(2, samples))));
        return true;
    }

    boolean endVoice(int session) throws IOException {
        synchronized (voiceLock) {
            if (!voiceActive(session)) return false;
            voiceSession = -1;
        }
        write(field(VOICE_END, number(1, session)));
        android.util.Log.d(RemoteVoice.LOG_TAG, "Session " + session + ": VOICE_END sent to TV");
        return true;
    }

    boolean isOpen() { return !closed; }

    void key(int code) throws IOException {
        write(field(10, concat(number(1, code), number(2, 3))));
    }

    private synchronized void write(byte[] message) throws IOException {
        output.write(varint(message.length));
        output.write(message);
        output.flush();
    }

    private Map<Integer, Object> read() throws IOException {
        int length = readVarint(input);
        if (length < 0 || length > 65536) throw new IOException("Invalid remote frame");
        byte[] message = new byte[length];
        input.readFully(message);
        return parse(message);
    }

    private static Map<Integer, Object> parse(byte[] bytes) throws IOException {
        ByteArrayInputStream data = new ByteArrayInputStream(bytes);
        Map<Integer, Object> fields = new HashMap<>();
        while (data.available() > 0) {
            int tag = readVarint(data);
            switch (tag & 7) {
                case 0: fields.put(tag >>> 3, readVarint(data)); break;
                case 2:
                    int size = readVarint(data);
                    if (size < 0 || size > data.available()) throw new IOException("Invalid field");
                    byte[] value = new byte[size];
                    if (size > 0 && data.read(value, 0, size) != size) throw new EOFException();
                    fields.put(tag >>> 3, value);
                    break;
                case 1: if (data.skip(8) != 8) throw new EOFException(); break;
                case 5: if (data.skip(4) != 4) throw new EOFException(); break;
                default: throw new IOException("Unsupported remote field");
            }
        }
        return fields;
    }

    private static int readVarint(InputStream data) throws IOException {
        int value = 0;
        for (int shift = 0; shift < 64; shift += 7) {
            int next = data.read();
            if (next < 0) throw new EOFException("TV closed the connection");
            if (shift < 32) value |= (next & 127) << shift;
            if ((next & 128) == 0) return value;
        }
        throw new IOException("Invalid remote encoding");
    }

    private static byte[] varint(int value) {
        byte[] bytes = new byte[5];
        int count = 0;
        while ((value & ~127) != 0) {
            bytes[count++] = (byte) ((value & 127) | 128);
            value >>>= 7;
        }
        bytes[count++] = (byte) value;
        return Arrays.copyOf(bytes, count);
    }

    private static byte[] number(int tag, int value) { return concat(varint(tag << 3), varint(value)); }
    private static byte[] field(int tag, byte[] value) {
        return concat(varint(tag << 3 | 2), varint(value.length), value);
    }
    private static byte[] string(int tag, String value) {
        return field(tag, value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private static byte[] concat(byte[]... values) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (byte[] value : values) output.write(value, 0, value.length);
        return output.toByteArray();
    }

    @Override public void close() {
        closed = true;
        synchronized (voiceLock) {
            voiceSession = -1;
            if (voiceBegin != null) voiceBegin.completeExceptionally(new IOException("Remote disconnected."));
        }
        try { socket.close(); } catch (IOException ignored) {}
        if (imeListener != null) imeListener.accept(null);
    }
}
