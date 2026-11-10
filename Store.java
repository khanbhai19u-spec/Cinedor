package com.cinedor.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Local storage: My List (favorites), Continue Watching, and an offline cache of home rows. */
public final class Store {

    private static final String PREFS = "cinedor";
    private static final String KEY_FAV = "favorites", KEY_HISTORY = "history";
    private static final int MAX_HISTORY = 30;

    private Store() {}

    private static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static JSONArray array(Context c, String key) {
        try { return new JSONArray(p(c).getString(key, "[]")); } catch (Exception e) { return new JSONArray(); }
    }

    // ------------------------------------------------------------ My List

    public static List<MovieApi.Movie> favorites(Context c) {
        JSONArray a = array(c, KEY_FAV);
        List<MovieApi.Movie> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null) out.add(MovieApi.Movie.fromJson(o));
        }
        return out;
    }

    public static boolean isFavorite(Context c, String id) {
        for (MovieApi.Movie m : favorites(c)) if (m.id.equals(id)) return true;
        return false;
    }

    /** @return true if the movie is now in My List. */
    public static boolean toggleFavorite(Context c, MovieApi.Movie movie) {
        JSONArray a = array(c, KEY_FAV), out = new JSONArray();
        boolean removed = false;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            if (movie.id.equals(o.optString("id"))) removed = true; else out.put(o);
        }
        JSONArray result = new JSONArray();
        if (!removed) result.put(movie.toJson());
        for (int i = 0; i < out.length(); i++) result.put(out.opt(i));
        p(c).edit().putString(KEY_FAV, result.toString()).apply();
        return !removed;
    }

    // ------------------------------------------------------------ Continue Watching

    public static void saveProgress(Context c, MovieApi.Movie movie, String url, long pos, long dur) {
        if (movie == null || url == null || dur <= 0) return;
        JSONArray a = array(c, KEY_HISTORY), out = new JSONArray();
        boolean finished = pos > dur * 0.95;
        if (!finished) {
            JSONObject o = movie.toJson();
            try {
                o.put("url", url); o.put("pos", pos); o.put("dur", dur); o.put("t", System.currentTimeMillis());
            } catch (Exception ignored) {}
            out.put(o);
        }
        for (int i = 0; i < a.length() && out.length() < MAX_HISTORY; i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && !movie.id.equals(o.optString("id"))) out.put(o);
        }
        p(c).edit().putString(KEY_HISTORY, out.toString()).apply();
    }

    public static List<MovieApi.Movie> continueWatching(Context c) {
        JSONArray a = array(c, KEY_HISTORY);
        List<MovieApi.Movie> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null) out.add(MovieApi.Movie.fromJson(o));
        }
        return out;
    }

    /** Where to resume this stream automatically (0 = from the start). */
    public static long resumePosition(Context c, String url) {
        JSONArray a = array(c, KEY_HISTORY);
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && url.equals(o.optString("url"))) {
                long pos = o.optLong("pos"), dur = o.optLong("dur");
                return pos > 5000 && pos < dur - 30000 ? pos : 0;
            }
        }
        return 0;
    }

    // ------------------------------------------------------------ offline cache

    public static void putCache(Context c, String key, String json) {
        p(c).edit().putString("cache_" + key, json).putLong("cache_t_" + key, System.currentTimeMillis()).apply();
    }

    public static String getCache(Context c, String key) {
        return p(c).getString("cache_" + key, null);
    }

    public static long cacheAge(Context c, String key) {
        return System.currentTimeMillis() - p(c).getLong("cache_t_" + key, 0);
    }
}
