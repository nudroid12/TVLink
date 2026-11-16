package com.tvlink.mobile;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.SystemClock;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;
import java.util.function.Consumer;

final class RemoteVoice {
    static final String LOG_TAG = "TVLinkVoice";
    private static final int SAMPLE_RATE = 8000;
    private static final int CHUNK_BYTES = 20 * 1024;
    private static final long MAX_DURATION_MS = 8000;
    private static final long NO_ACK_TIMEOUT_MS = 12000;
    private static final long ACK_GRACE_MS = 3000;

    static void capture(AndroidTvRemote remote, AtomicBoolean stopped, AtomicBoolean finish,
                        IntConsumer level, Consumer<String> diagnostic) throws IOException {
        int minimum = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        if (minimum <= 0) throw new IOException("This phone cannot record the Remote voice format.");
        AudioRecord recorder = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(minimum, CHUNK_BYTES * 2));
        try {
            if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                throw new IOException("Could not initialize the microphone.");
            }
            if (stopped.get()) return;
            int session = remote.beginVoice();
            try {
                if (stopped.get() || !remote.voiceActive(session)) return;
                recorder.startRecording();
                if (recorder.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                    throw new IOException("Could not start recording.");
                }
                level.accept(0);
                try {
                    stream(remote, session, recorder, stopped, finish, level, diagnostic);
                } finally {
                    if (recorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) recorder.stop();
                }
                if (!stopped.get() || finish.get()) waitForTv(remote, session, stopped, finish, diagnostic);
                if ((!stopped.get() || finish.get()) && !remote.isOpen()) {
                    throw new IOException("Remote disconnected during voice input.");
                }
            } finally {
                if (remote.endVoice(session)) {
                    diagnostic.accept("VOICE_END sent to TV");
                } else if (remote.isOpen()) {
                    diagnostic.accept("VOICE_END received from TV");
                }
            }
        } finally {
            recorder.release();
        }
    }

    private static void stream(AndroidTvRemote remote, int session, AudioRecord recorder,
                               AtomicBoolean stopped, AtomicBoolean finish, IntConsumer level, Consumer<String> diagnostic) throws IOException {
        byte[] chunk = new byte[CHUNK_BYTES];
        int used = 0;
        boolean audioSent = false;
        boolean signalDetected = false;
        long nextLevel = 0;
        long until = SystemClock.elapsedRealtime() + MAX_DURATION_MS;
        while (!stopped.get() && remote.voiceActive(session) && SystemClock.elapsedRealtime() < until) {
            int count = recorder.read(chunk, used, Math.min(640, chunk.length - used));
            if (count < 0) throw new IOException("Could not read microphone audio.");
            int strength = measureLevel(chunk, used, count);
            signalDetected |= strength > 0;
            long now = SystemClock.elapsedRealtime();
            if (now >= nextLevel) {
                level.accept(strength);
                nextLevel = now + 60;
            }
            used += count;
            if (used == chunk.length) {
                if (!stopped.get() || finish.get()) audioSent |= remote.sendVoice(session, chunk);
                used = 0;
            }
        }
        if (!stopped.get() && remote.voiceActive(session) && SystemClock.elapsedRealtime() >= until) {
            diagnostic.accept("8s recording limit reached");
            android.util.Log.d(LOG_TAG, "Session " + session + ": recording limit reached");
        }
        if (!stopped.get() || finish.get()) diagnostic.accept("Recording ended");
        if (used > 0 && remote.voiceActive(session) && (!stopped.get() || finish.get())) {
            Arrays.fill(chunk, used, chunk.length, (byte) 0);
            audioSent |= remote.sendVoice(session, chunk);
        }
        if (!signalDetected && (!stopped.get() || finish.get())) {
            throw new IOException("No microphone signal detected.");
        }
        if (!audioSent && (!stopped.get() || finish.get())) {
            throw new IOException("Voice audio could not be sent to the TV.");
        }
    }

    private static void waitForTv(AndroidTvRemote remote, int session, AtomicBoolean stopped,
                                  AtomicBoolean finish, Consumer<String> diagnostic) throws IOException {
        long deadline = SystemClock.elapsedRealtime() + NO_ACK_TIMEOUT_MS;
        if (!remote.voiceActive(session) || (stopped.get() && !finish.get())) return;
        diagnostic.accept("Audio sent · waiting for TV");

        boolean acknowledged = false;
        if (!remote.voiceAcknowledged(session)
                && remote.sendVoice(session, new byte[CHUNK_BYTES])) {
            diagnostic.accept("One silence block sent");
        }
        while (remote.voiceActive(session) && (!stopped.get() || finish.get())) {
            long now = SystemClock.elapsedRealtime();
            if (now >= deadline) {
                diagnostic.accept(acknowledged ? "3s after acknowledgement" : "12s without acknowledgement");
                break;
            }
            if (!acknowledged && remote.voiceAcknowledged(session)) {
                acknowledged = true;
                deadline = now + ACK_GRACE_MS;
                diagnostic.accept("TV acknowledged audio");
                android.util.Log.d(LOG_TAG, "Session " + session + ": audio acknowledged");
            }
            try {
                Thread.sleep(60);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IOException("Voice processing interrupted.", error);
            }
        }
        if (remote.voiceActive(session) && (!stopped.get() || finish.get())) {
            android.util.Log.d(LOG_TAG, "Session " + session + ": response window ended; acknowledged=" + acknowledged);
        }
    }

    static int measureLevel(byte[] pcm, int offset, int length) {
        if (length < 2) return 0;
        double squares = 0;
        int samples = length / 2;
        for (int i = offset; i < offset + samples * 2; i += 2) {
            int sample = (short) ((pcm[i] & 0xff) | ((pcm[i + 1] & 0xff) << 8));
            squares += (double) sample * sample;
        }
        double rms = Math.sqrt(squares / samples);
        return Math.min(100, (int) Math.round(rms / 100));
    }

    private RemoteVoice() {}
}
