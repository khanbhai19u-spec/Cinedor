package com.cinedor.app;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** TMDB data, fetched through your server (the key lives on the server). Info only, no streams. */
public final class Tmdb {

    public static class Row {
        public final String title;
        public final List<Video> items;

        Row(String title, List<Video> items) { this.title = title; this.items = items; }
    }

    public interface Callback<T> { void done(T result, String error); }

    private static final ExecutorService IO = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile List<Row> rows = Collections.emptyList();
    private static volatile long rowsAt;

    private Tmdb() {}

    public static void discover(boolean force, Callback<List<Row>> cb) {
        if (!force && !rows.isEmpty() && System.currentTimeMillis() - rowsAt < 10 * 60_000) { cb.done(rows, null); return; }
        IO.execute(() -> {
            try {
                JSONObject o = new JSONObject(ApiConfig.get("/api/discover"));
                List<Row> out = new ArrayList<>();
                JSONArray arr = o.optJSONArray("rows");
                if (arr != null) for (int i = 0; i < arr.length(); i++) {
                    JSONObject r = arr.optJSONObject(i);
                    if (r == null) continue;
                    List<Video> items = videos(r.optJSONArray("items"));
                    if (!items.isEmpty()) out.add(new Row(r.optString("title"), items));
                }
                rows = Collections.unmodifiableList(out);
                rowsAt = System.currentTimeMillis();
                MAIN.post(() -> cb.done(rows, null));
            } catch (Exception e) {
                MAIN.post(() -> cb.done(rows, e.getMessage()));
            }
        });
    }

    public static void search(String q, Callback<List<Video>> cb) {
        IO.execute(() -> {
            try {
                JSONObject o = new JSONObject(ApiConfig.get("/api/search?q=" + URLEncoder.encode(q, "UTF-8")));
                List<Video> out = videos(o.optJSONArray("tmdb"));
                MAIN.post(() -> cb.done(out, null));
            } catch (Exception e) {
                MAIN.post(() -> cb.done(Collections.emptyList(), e.getMessage()));
            }
        });
    }

    /** id looks like "tmdb:movie:603". */
    public static void detail(String id, Callback<Video> cb) {
        String[] parts = id.split(":");
        if (parts.length != 3) { cb.done(null, "bad id"); return; }
        IO.execute(() -> {
            try {
                Video v = new Video(new JSONObject(ApiConfig.get("/api/tmdb/" + parts[1] + "/" + parts[2])));
                MAIN.post(() -> cb.done(v, null));
            } catch (Exception e) {
                MAIN.post(() -> cb.done(null, e.getMessage()));
            }
        });
    }

    public static void config(String keyOrNull, String language, Callback<JSONObject> cb) {
        IO.execute(() -> {
            try {
                String body;
                if (keyOrNull == null && language == null) body = null;
                else {
                    JSONObject b = new JSONObject();
                    if (keyOrNull != null) b.put("tmdbKey", keyOrNull);
                    if (language != null) b.put("language", language);
                    body = b.toString();
                }
                JSONObject o = new JSONObject(body == null ? ApiConfig.get("/api/config") : ApiConfig.post("/api/config", body));
                if (body != null) { rows = Collections.emptyList(); rowsAt = 0; }
                MAIN.post(() -> cb.done(o, null));
            } catch (Exception e) {
                MAIN.post(() -> cb.done(null, e.getMessage()));
            }
        });
    }

    private static List<Video> videos(JSONArray arr) {
        List<Video> out = new ArrayList<>();
        if (arr != null) for (int i = 0; i < arr.length(); i++) {
            JSONObject v = arr.optJSONObject(i);
            if (v != null) out.add(new Video(v));
        }
        return out;
    }
}
