package com.mayra.assistant;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.util.ArrayList;

/** Speech-to-text using Android's recognizer (Bangla: bn-BD, English: en-US). Use on the UI thread. */
public final class VoiceInput {

    public interface Callback {
        void onListening();
        void onPartial(String text);
        void onLevel(float rmsDb);
        void onResult(String text);
        void onError(String message);
    }

    private final Context ctx;
    private SpeechRecognizer rec;
    private boolean listening;

    public VoiceInput(Context ctx) { this.ctx = ctx; }

    public boolean available() { return SpeechRecognizer.isRecognitionAvailable(ctx); }
    public boolean isListening() { return listening; }

    public void start(String lang, Callback cb) {
        stop();
        rec = SpeechRecognizer.createSpeechRecognizer(ctx);
        rec.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { cb.onListening(); }
            @Override public void onBeginningOfSpeech() { }
            @Override public void onRmsChanged(float rmsdB) { cb.onLevel(rmsdB); }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() { }
            @Override public void onEvent(int eventType, Bundle params) { }

            @Override public void onPartialResults(Bundle b) {
                String s = first(b);
                if (s != null) cb.onPartial(s);
            }

            @Override public void onResults(Bundle b) {
                listening = false;
                String s = first(b);
                if (s == null || s.trim().isEmpty()) cb.onError("কিছু শুনতে পাইনি");
                else cb.onResult(s);
            }

            @Override public void onError(int error) {
                listening = false;
                cb.onError(message(error));
            }
        });
        String tag = "en".equals(lang) ? "en-US" : "bn-BD";
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, tag)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L);
        listening = true;
        rec.startListening(i);
    }

    public void stop() {
        listening = false;
        if (rec != null) {
            try { rec.cancel(); rec.destroy(); } catch (Exception ignored) { }
            rec = null;
        }
    }

    private static String first(Bundle b) {
        ArrayList<String> r = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        return r != null && !r.isEmpty() ? r.get(0) : null;
    }

    private static String message(int e) {
        switch (e) {
            case SpeechRecognizer.ERROR_NO_MATCH:
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return "কিছু শুনতে পাইনি";
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: return "ভয়েসের জন্য ইন্টারনেট দরকার";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "মাইক্রোফোনের অনুমতি দিন";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: return "মাইক ব্যস্ত, আবার চেষ্টা করুন";
            case SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED:
            case SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE: return "এই ভাষার ভয়েস প্যাক নেই (Google অ্যাপ আপডেট করুন)";
            default: return "ভয়েস সমস্যা (" + e + ")";
        }
    }
}
