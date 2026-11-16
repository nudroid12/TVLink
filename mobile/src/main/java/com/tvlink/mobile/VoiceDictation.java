package com.tvlink.mobile;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import java.util.ArrayList;
import java.util.function.BiConsumer;

final class VoiceDictation implements RecognitionListener {
    private final Context context;
    private final VoiceMeter meter;
    private final BiConsumer<String, String> complete;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable timeout = () -> complete(null, "Speech recognition timed out.");
    private SpeechRecognizer recognizer;
    private boolean finished;

    VoiceDictation(Context context, VoiceMeter meter, BiConsumer<String, String> complete) {
        this.context = context;
        this.meter = meter;
        this.complete = complete;
    }

    void start() {
        try {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context);
            recognizer.setRecognitionListener(this);
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            handler.postDelayed(timeout, 15000);
            recognizer.startListening(intent);
        } catch (RuntimeException error) {
            complete(null, "Could not start speech recognition.");
        }
    }

    void finish() {
        if (finished || recognizer == null) return;
        meter.processing("Recognizing speech…");
        try {
            recognizer.stopListening();
        } catch (RuntimeException error) {
            complete(null, "Could not finish speech recognition.");
        }
    }

    void cancel() {
        complete(null, null);
    }

    private void complete(String words, String error) {
        if (finished) return;
        finished = true;
        handler.removeCallbacks(timeout);
        if (recognizer != null) {
            recognizer.destroy();
            recognizer = null;
        }
        complete.accept(words, error);
    }

    @Override public void onReadyForSpeech(Bundle params) {
        if (!finished) meter.label("Listening · release to finish");
    }

    @Override public void onRmsChanged(float rmsDb) {
        if (!finished) meter.level(Math.round((rmsDb + 2) * 8));
    }

    @Override public void onEndOfSpeech() {
        if (finished) return;
        meter.label("Recognizing speech…");
        meter.level(-1);
    }

    @Override public void onResults(Bundle results) {
        ArrayList<String> values = results == null ? null
                : results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        String words = values == null || values.isEmpty() || values.get(0) == null ? "" : values.get(0).trim();
        complete(words.isEmpty() ? null : words, words.isEmpty() ? "No speech recognized." : null);
    }

    @Override public void onError(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_NO_MATCH:
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                complete(null, "No speech recognized.");
                break;
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                complete(null, "Speech service could not connect.");
                break;
            default:
                complete(null, "Speech recognition failed. Try again.");
        }
    }

    @Override public void onBeginningOfSpeech() {}
    @Override public void onBufferReceived(byte[] buffer) {}
    @Override public void onPartialResults(Bundle results) {}
    @Override public void onEvent(int event, Bundle params) {}
}
