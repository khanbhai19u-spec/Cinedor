package com.cinedor.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Favorites, watch history (with resume position) and downloads — kept on the phone. */
public final class Store {

    public static class Entry {
        public final Video video;
        public final String epId, epTitle, url, type, label;
        public final long pos, dur, time, downloadId;

        Entry(JSONObject o) {
            video = new Video(o.optJSONObject("video") != null ? o.optJSONObject("video") : new JSONObject());
            epId = o.optString("ep", "");
            epTitle = o.optString("epTitle", "");
            url = o.optString("url", "");
            type = o.optString("type", "mp4");
            label = o.optString("label", "");
            pos = o.optLong("pos", 0);
            dur = o.optLong("dur", 0);
            time = o.optLong("time", 0);
            downloadId = o.optLong("dl", -1);
        }

        public float progress() { return dur > 0 ? Math.min(1f, pos / (float) dur) : 0f; }
        public boolean unfinished() { return pos > 5_000 && (dur <= 0 || pos < dur * 0.95); }
    }

    private static final int MAX_HISTORY = 100;
    private static SharedPreferences prefs;

    private Store() {}

    private static SharedPreferences p(Context c) {
        if (prefs == null) prefs = c.getApplicationContext().getSharedPreferences("library", Context.MODE_PRIVATE);
        return prefs;
    }

    private static JSONArray read(Context c, String key) {
        try { return new JSONArray(p(c).getString(key, "[]")); }
        catch (JSONException e) { return new JSONArray(); }
    }

    private static void write(Context c, String key, JSONArray a) { p(c).edit().putString(key, a.toString()).apply(); }

    private static List<Entry> entries(Context c, String key) {
        JSONArray a = read(c, key);
        List<Entry> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null) out.add(new Entry(o));
        }
        return out;
    }

    /** Removes entries matching (video id [, extra key/value]) and returns the rest. */
    private static JSONArray without(JSONArray a, String videoId, String extraKey, Object extraVal) {
        JSONArray out = new JSONArray();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            JSONObject v = o.optJSONObject("video");
            boolean same = v != null && videoId.equals(v.optString("id"));
            if (same && extraKey != null) same = String.valueOf(extraVal).equals(String.valueOf(o.opt(extraKey)));
            if (!same) out.put(o);
        }
        return out;
    }

    private static JSONArray prepend(JSONObject first, JSONArray rest, int max) {
        JSONArray out = new JSONArray();
        out.put(first);
        for (int i = 0; i < rest.length() && out.length() < max; i++) out.put(rest.opt(i));
        return out;
    }

    // ---------- favorites ----------
    public static List<Entry> favorites(Context c) { return entries(c, "fav"); }

    public static boolean isFavorite(Context c, String id) {
        for (Entry e : favorites(c)) if (e.video.id.equals(id)) return true;
        return false;
    }

    /** @return new state */
    public static boolean toggleFavorite(Context c, Video v) {
        JSONArray a = read(c, "fav");
        JSONArray rest = without(a, v.id, null, null);
        if (rest.length() != a.length()) { write(c, "fav", rest); return false; }
        try {
            write(c, "fav", prepend(new JSONObject().put("video", v.toJson()).put("time", System.currentTimeMillis()), rest, 1000));
        } catch (JSONException ignored) {}
        return true;
    }

    // ---------- history ----------
    public static List<Entry> history(Context c) { return entries(c, "hist"); }

    public static List<Entry> continueWatching(Context c) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : history(c)) if (e.unfinished() && !e.url.isEmpty()) out.add(e);
        return out;
    }

    public static Entry lastWatched(Context c, String videoId) {
        for (Entry e : history(c)) if (e.video.id.equals(videoId)) return e;
        return null;
    }

    public static void saveProgress(Context c, Video v, String epId, String epTitle, Video.Quality q, long pos, long dur) {
        if (v == null || q == null) return;
        try {
            JSONObject o = new JSONObject().put("video", v.toJson()).put("ep", epId == null ? "" : epId)
                    .put("epTitle", epTitle == null ? "" : epTitle).put("url", q.url).put("type", q.type)
                    .put("label", q.label).put("pos", pos).put("dur", dur).put("time", System.currentTimeMillis());
            write(c, "hist", prepend(o, without(read(c, "hist"), v.id, null, null), MAX_HISTORY));
        } catch (JSONException ignored) {}
    }

    public static void removeHistory(Context c, String videoId) { write(c, "hist", without(read(c, "hist"), videoId, null, null)); }
    public static void clearHistory(Context c) { write(c, "hist", new JSONArray()); }
    public static void clearFavorites(Context c) { write(c, "fav", new JSONArray()); }

    // ---------- downloads ----------
    public static List<Entry> downloads(Context c) { return entries(c, "dl"); }

    public static void addDownload(Context c, Video v, String epId, String epTitle, Video.Quality q, long dlId) {
        try {
            JSONObject o = new JSONObject().put("video", v.toJson()).put("ep", epId == null ? "" : epId)
                    .put("epTitle", epTitle == null ? "" : epTitle).put("url", q.url).put("type", q.type)
                    .put("label", q.label).put("dl", dlId).put("time", System.currentTimeMillis());
            write(c, "dl", prepend(o, read(c, "dl"), 500));
        } catch (JSONException ignored) {}
    }

    public static void removeDownload(Context c, long dlId) {
        JSONArray a = read(c, "dl"), out = new JSONArray();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && o.optLong("dl", -1) != dlId) out.put(o);
        }
        write(c, "dl", out);
    }
}
