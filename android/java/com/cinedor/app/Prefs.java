package com.cinedor.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/** App preferences (App Settings) and search history. */
public final class Prefs {

    public static final String THEME_GLASS = "glass", THEME_MATTE = "matte", THEME_MATERIAL = "material";

    private static SharedPreferences p;

    private Prefs() {}

    private static SharedPreferences p(Context c) {
        if (p == null) p = c.getApplicationContext().getSharedPreferences("app", Context.MODE_PRIVATE);
        return p;
    }

    public static String theme(Context c) { return p(c).getString("theme", THEME_GLASS); }
    public static void setTheme(Context c, String t) { p(c).edit().putString("theme", t).apply(); }

    /** Seconds skipped by double-tap and by the player's skip buttons. */
    public static int skipSeconds(Context c) { return p(c).getInt("skip", 10); }
    public static void setSkipSeconds(Context c, int s) { p(c).edit().putInt("skip", s).apply(); }

    public static boolean holdToSpeed(Context c) { return p(c).getBoolean("holdSpeed", true); }
    public static void setHoldToSpeed(Context c, boolean on) { p(c).edit().putBoolean("holdSpeed", on).apply(); }

    public static boolean pip(Context c) { return p(c).getBoolean("pip", true); }
    public static void setPip(Context c, boolean on) { p(c).edit().putBoolean("pip", on).apply(); }

    public static boolean wifiOnly(Context c) { return p(c).getBoolean("wifiOnly", false); }
    public static void setWifiOnly(Context c, boolean on) { p(c).edit().putBoolean("wifiOnly", on).apply(); }

    /** "movies" → Movies/Cinedor, "download" → Download/Cinedor */
    public static String downloadFolder(Context c) { return p(c).getString("dlFolder", "download"); }
    public static void setDownloadFolder(Context c, String f) { p(c).edit().putString("dlFolder", f).apply(); }
    public static String downloadFolderLabel(Context c) { return "movies".equals(downloadFolder(c)) ? "Movies/Cinedor" : "Download/Cinedor"; }

    /** Puts every setting back to its default (history and lists are kept). */
    public static void reset(Context c) {
        List<String> history = searchHistory(c);
        p(c).edit().clear().apply();
        saveHistory(c, history);
    }

    // ---------- search history ----------
    public static List<String> searchHistory(Context c) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(p(c).getString("searches", "[]"));
            for (int i = 0; i < a.length(); i++) out.add(a.optString(i));
        } catch (Exception ignored) {}
        return out;
    }

    public static void addSearch(Context c, String q) {
        q = q == null ? "" : q.trim();
        if (q.length() < 2) return;
        List<String> list = searchHistory(c);
        for (int i = list.size() - 1; i >= 0; i--) if (list.get(i).equalsIgnoreCase(q)) list.remove(i);
        list.add(0, q);
        while (list.size() > 15) list.remove(list.size() - 1);
        saveHistory(c, list);
    }

    public static void removeSearch(Context c, String q) {
        List<String> list = searchHistory(c);
        list.remove(q);
        saveHistory(c, list);
    }

    public static void clearSearches(Context c) { saveHistory(c, new ArrayList<>()); }

    private static void saveHistory(Context c, List<String> list) {
        JSONArray a = new JSONArray();
        for (String s : list) a.put(s);
        p(c).edit().putString("searches", a.toString()).apply();
    }
}
