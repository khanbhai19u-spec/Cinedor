package com.mayra.assistant;

import android.content.Context;
import android.content.SharedPreferences;

/** All user settings. Stored in app-private storage (other apps cannot read it). */
public final class Prefs {

    public static final String[] MODEL_IDS = {"claude-opus-5-5", "claude-sonnet-5-5", "claude-haiku-5-5"};
    public static final String[] MODEL_LABELS = {
            "Claude Opus 5.5 — সবচেয়ে স্মার্ট (ডিফল্ট)",
            "Claude Sonnet 5.5 — দ্রুত, খরচ কম",
            "Claude Haiku 5.5 — সবচেয়ে দ্রুত ও সস্তা"
    };

    private final SharedPreferences sp;

    public Prefs(Context c) {
        sp = c.getApplicationContext().getSharedPreferences("mayra", Context.MODE_PRIVATE);
    }

    public String apiKey() { return sp.getString("api_key", "").trim(); }
    public void setApiKey(String v) { sp.edit().putString("api_key", v == null ? "" : v.trim()).apply(); }

    public String model() { return sp.getString("model", MODEL_IDS[0]); }
    public void setModel(String v) { sp.edit().putString("model", v).apply(); }

    public String userName() { return sp.getString("user_name", ""); }
    public void setUserName(String v) { sp.edit().putString("user_name", v == null ? "" : v.trim()).apply(); }

    /** "bn" or "en" — language used for speech recognition. */
    public String voiceLang() { return sp.getString("voice_lang", "bn"); }
    public void setVoiceLang(String v) { sp.edit().putString("voice_lang", v).apply(); }

    /** Speak every reply, even typed ones. Voice questions always get a spoken reply. */
    public boolean alwaysSpeak() { return sp.getBoolean("always_speak", false); }
    public void setAlwaysSpeak(boolean v) { sp.edit().putBoolean("always_speak", v).apply(); }

    /** After a spoken reply, start listening again automatically (hands-free conversation). */
    public boolean handsFree() { return sp.getBoolean("hands_free", false); }
    public void setHandsFree(boolean v) { sp.edit().putBoolean("hands_free", v).apply(); }

    public float speechRate() { return sp.getFloat("speech_rate", 1.0f); }
    public void setSpeechRate(float v) { sp.edit().putFloat("speech_rate", v).apply(); }

    public boolean clapEnabled() { return sp.getBoolean("clap", false); }
    public void setClapEnabled(boolean v) { sp.edit().putBoolean("clap", v).apply(); }

    /** 0 = low, 1 = medium, 2 = high. */
    public int clapSensitivity() { return sp.getInt("clap_sens", 1); }
    public void setClapSensitivity(int v) { sp.edit().putInt("clap_sens", v).apply(); }

    public boolean webSearch() { return sp.getBoolean("web_search", true); }
    public void setWebSearch(boolean v) { sp.edit().putBoolean("web_search", v).apply(); }

    public boolean codeMode() { return sp.getBoolean("code_mode", false); }
    public void setCodeMode(boolean v) { sp.edit().putBoolean("code_mode", v).apply(); }
}
