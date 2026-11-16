package com.tvlink.mobile;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.SystemClock;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;

final class RemoteVoice {
    private static final int SAMPLE_RATE = 8000;
    private static final int CHUNK_BYTES = 8192;
    private static final long MAX_DURATION_MS = 8000;

    static void capture(AndroidTvRemote remote, AtomicBoolean stopped, AtomicBoolean finish,
                        IntConsumer level) throws IOException {
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
                stream(remote, session, recorder, stopped, finish, level);
            } finally {
                try {
                    if (recorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) recorder.stop();
                } finally {
                    remote.endVoice(session);
                }
            }
        } finally {
            recorder.release();
        }
    }

    private static void stream(AndroidTvRemote remote, int session, AudioRecord recorder,
                               AtomicBoolean stopped, AtomicBoolean finish, IntConsumer level) throws IOException {
        byte[] chunk = new byte[CHUNK_BYTES];
        int used = 0;
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
                if (!stopped.get() || finish.get()) remote.sendVoice(session, chunk);
                used = 0;
            }
        }
        if (used > 0 && remote.voiceActive(session) && (!stopped.get() || finish.get())) {
            Arrays.fill(chunk, used, chunk.length, (byte) 0);
            remote.sendVoice(session, chunk);
        }
        if (!signalDetected && (!stopped.get() || finish.get())) {
            throw new IOException("No microphone signal detected.");
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
