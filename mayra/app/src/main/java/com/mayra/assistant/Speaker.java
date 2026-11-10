package com.mayra.assistant;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.widget.Toast;

import java.util.Locale;

/** Text-to-speech. Picks a Bangla voice for Bangla text and English for English text. */
public final class Speaker {

    private final Context ctx;
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextToSpeech tts;
    private boolean ready;
    private boolean warnedBangla;
    private Runnable onDone;
    private String pending;
    private float rate = 1f;

    public Speaker(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        tts = new TextToSpeech(this.ctx, status -> {
            ready = status == TextToSpeech.SUCCESS;
            if (ready) {
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) { }
                    @Override public void onDone(String id) { if ("last".equals(id)) finish(); }
                    @Override public void onError(String id) { finish(); }
                    @Override public void onStop(String id, boolean interrupted) { }
                });
                if (pending != null) { String p = pending; pending = null; speak(p, rate, onDone); }
            }
        });
    }

    private void finish() {
        Runnable r = onDone;
        onDone = null;
        if (r != null) main.post(r);
    }

    public boolean isSpeaking() { return ready && tts.isSpeaking(); }

    public void speak(String text, float rate, Runnable done) {
        this.rate = rate;
        this.onDone = done;
        if (!ready) { pending = text; return; }
        String clean = Markdown.toSpeech(text);
        if (clean.isEmpty()) { finish(); return; }
        boolean bangla = clean.codePoints().filter(cp -> cp >= 0x0980 && cp <= 0x09FF).count() > clean.length() / 10;
        Locale loc = bangla ? new Locale("bn", "BD") : Locale.US;
        int r = tts.setLanguage(loc);
        if (bangla && (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED)) {
            r = tts.setLanguage(new Locale("bn", "IN"));
            if ((r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) && !warnedBangla) {
                warnedBangla = true;
                main.post(() -> Toast.makeText(ctx, "বাংলা ভয়েস নেই: Settings → Text-to-speech → Google → বাংলা ভয়েস ডাউনলোড করুন", Toast.LENGTH_LONG).show());
            }
        }
        tts.setSpeechRate(rate);
        tts.stop();
        // TTS engines limit utterance length — split into sentences.
        String[] parts = clean.split("(?<=[।.!?\\n])\\s+");
        Bundle params = new Bundle();
        for (int i = 0; i < parts.length; i++) {
            String id = i == parts.length - 1 ? "last" : "p" + i;
            String chunk = parts[i];
            while (chunk.length() > 3000) {
                tts.speak(chunk.substring(0, 3000), TextToSpeech.QUEUE_ADD, params, "c" + i);
                chunk = chunk.substring(3000);
            }
            tts.speak(chunk, TextToSpeech.QUEUE_ADD, params, id);
        }
    }

    public void stop() {
        onDone = null;
        pending = null;
        if (ready) tts.stop();
    }

    public void shutdown() {
        stop();
        if (tts != null) tts.shutdown();
    }
}
