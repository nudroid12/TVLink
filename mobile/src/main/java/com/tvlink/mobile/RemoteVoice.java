package com.tvlink.mobile;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.SystemClock;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

final class RemoteVoice {
    private static final int SAMPLE_RATE = 8000;
    private static final int CHUNK_BYTES = 8192;
    private static final long MAX_DURATION_MS = 8000;

    static void capture(AndroidTvRemote remote, AtomicBoolean stopped, AtomicBoolean finish,
                        Runnable listening) throws IOException {
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
                listening.run();
                stream(remote, session, recorder, stopped, finish);
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
                               AtomicBoolean stopped, AtomicBoolean finish) throws IOException {
        byte[] chunk = new byte[CHUNK_BYTES];
        int used = 0;
        long until = SystemClock.elapsedRealtime() + MAX_DURATION_MS;
        while (!stopped.get() && remote.voiceActive(session) && SystemClock.elapsedRealtime() < until) {
            int count = recorder.read(chunk, used, Math.min(640, chunk.length - used));
            if (count < 0) throw new IOException("Could not read microphone audio.");
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
    }

    private RemoteVoice() {}
}
