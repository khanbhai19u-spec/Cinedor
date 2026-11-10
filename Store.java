package com.cinedor.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Local storage: My List, Continue Watching, and an offline cache of rows. */
public final class Store {

    private static final String PREFS = "cinedor2";
    private static final String KEY_FAV = "favorites", KEY_HISTORY = "history";
    private static final int MAX_HISTORY = 30;

    private Store() {}

    private static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static JSONArray array(Context c, String key) {
        try { return new JSONArray(p(c).getString(key, "[]")); } catch (Exception e) { return new JSONArray(); }
    }

    private static List<Item> items(JSONArray a) {
        List<Item> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null) out.add(Item.fromJson(o));
        }
        return out;
    }

    public static String toJson(List<Item> list) {
        JSONArray a = new JSONArray();
        for (Item it : list) a.put(it.toJson());
        return a.toString();
    }

    public static List<Item> fromJson(String json) {
        if (json == null) return new ArrayList<>();
        try { return items(new JSONArray(json)); } catch (Exception e) { return new ArrayList<>(); }
    }

    // ------------------------------------------------------------ My List

    public static List<Item> favorites(Context c) { return items(array(c, KEY_FAV)); }

    public static boolean isFavorite(Context c, Item item) {
        for (Item m : favorites(c)) if (m.key().equals(item.key())) return true;
        return false;
    }

    /** @return true if the title is now in My List. */
    public static boolean toggleFavorite(Context c, Item item) {
        List<Item> out = new ArrayList<>();
        boolean removed = false;
        for (Item m : favorites(c)) {
            if (m.key().equals(item.key())) removed = true; else out.add(m);
        }
        if (!removed) out.add(0, item);
        p(c).edit().putString(KEY_FAV, toJson(out)).apply();
        return !removed;
    }

    // ------------------------------------------------------------ Continue Watching

    public static void saveProgress(Context c, Item item, String url, long pos, long dur) {
        if (item == null || url == null || dur <= 0) return;
        JSONArray a = array(c, KEY_HISTORY), out = new JSONArray();
        if (pos <= dur * 0.95) {
            JSONObject o = item.toJson();
            try { o.put("url", url); o.put("pos", pos); o.put("dur", dur); } catch (Exception ignored) {}
            out.put(o);
        }
        for (int i = 0; i < a.length() && out.length() < MAX_HISTORY; i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && !item.key().equals(o.optString("type") + ":" + o.optString("id"))) out.put(o);
        }
        p(c).edit().putString(KEY_HISTORY, out.toString()).apply();
    }

    public static List<Item> continueWatching(Context c) { return items(array(c, KEY_HISTORY)); }

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

    public static String getCache(Context c, String key) { return p(c).getString("cache_" + key, null); }

    public static long cacheAge(Context c, String key) {
        return System.currentTimeMillis() - p(c).getLong("cache_t_" + key, 0);
    }
}
