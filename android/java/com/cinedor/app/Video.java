package com.cinedor.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** One title (movie or series) from the server catalog. */
public class Video {

    public static class Quality {
        public final String label, url, type;
        public final long size;

        Quality(JSONObject o) {
            label = o.optString("label", "Original");
            url = ApiConfig.abs(o.optString("url"));
            String t = o.optString("type", "");
            type = t.isEmpty() ? (url.toLowerCase(Locale.US).contains(".m3u8") ? "hls" : "mp4") : t;
            size = o.optLong("size", 0);
        }

        public boolean downloadable() { return !"hls".equals(type); }

        public String info() {
            String t = "hls".equals(type) ? "HLS stream" : type.toUpperCase(Locale.US);
            return size > 0 ? t + " • " + Ui.size(size) : t;
        }

        JSONObject toJson() throws JSONException {
            return new JSONObject().put("label", label).put("url", url).put("type", type).put("size", size);
        }
    }

    public static class Episode {
        public final String id, title;
        public final List<Quality> qualities;

        Episode(JSONObject o) {
            id = o.optString("id");
            title = o.optString("title");
            qualities = qualities(o.optJSONArray("qualities"));
        }

        JSONObject toJson() throws JSONException {
            return new JSONObject().put("id", id).put("title", title).put("qualities", toArray(qualities));
        }
    }

    public final String id, title, year, type, genre, description, poster, backdrop;
    public final boolean featured;
    public final long added;
    public final List<Quality> qualities;
    public final List<Episode> episodes = new ArrayList<>();

    public Video(JSONObject o) {
        id = o.optString("id");
        title = o.optString("title", "Untitled");
        year = o.optString("year", "");
        genre = o.optString("genre", "");
        description = o.optString("description", "");
        poster = ApiConfig.abs(o.optString("poster", ""));
        String b = ApiConfig.abs(o.optString("backdrop", ""));
        backdrop = b.isEmpty() ? poster : b;
        featured = o.optBoolean("featured", true);
        added = o.optLong("added", 0);
        qualities = qualities(o.optJSONArray("qualities"));
        JSONArray eps = o.optJSONArray("episodes");
        if (eps != null) for (int i = 0; i < eps.length(); i++) {
            JSONObject e = eps.optJSONObject(i);
            if (e != null) episodes.add(new Episode(e));
        }
        String t = o.optString("type", "");
        type = t.isEmpty() ? (episodes.isEmpty() ? "movie" : "series") : t;
    }

    public boolean isSeries() { return "series".equalsIgnoreCase(type) || !episodes.isEmpty(); }

    /** Poster for portrait cards (falls back to backdrop). */
    public String portrait() { return poster.isEmpty() ? backdrop : poster; }

    public Episode episode(String epId) {
        if (epId == null) return null;
        for (Episode e : episodes) if (e.id.equals(epId)) return e;
        return null;
    }

    /** "2026 • SERIES" */
    public String meta() {
        String kind = isSeries() ? "SERIES" : "MOVIE";
        return year.isEmpty() ? kind : year + "  •  " + kind;
    }

    public boolean matches(String q) {
        String s = (title + " " + genre + " " + year + " " + type).toLowerCase(Locale.ROOT);
        for (String part : q.toLowerCase(Locale.ROOT).trim().split("\\s+")) {
            if (!s.contains(part)) return false;
        }
        return true;
    }

    public JSONObject toJson() {
        try {
            JSONArray eps = new JSONArray();
            for (Episode e : episodes) eps.put(e.toJson());
            return new JSONObject().put("id", id).put("title", title).put("year", year).put("type", type)
                    .put("genre", genre).put("description", description).put("poster", poster)
                    .put("backdrop", backdrop).put("featured", featured).put("added", added)
                    .put("qualities", toArray(qualities)).put("episodes", eps);
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    static List<Quality> qualities(JSONArray arr) {
        List<Quality> out = new ArrayList<>();
        if (arr != null) for (int i = 0; i < arr.length(); i++) {
            JSONObject q = arr.optJSONObject(i);
            if (q != null && !q.optString("url").isEmpty()) out.add(new Quality(q));
        }
        return out;
    }

    static JSONArray toArray(List<Quality> list) throws JSONException {
        JSONArray a = new JSONArray();
        for (Quality q : list) a.put(q.toJson());
        return a;
    }
}
