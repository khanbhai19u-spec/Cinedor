package com.cinedor.app;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Loads and caches the title list from the server. */
public final class Catalog {

    public interface Callback {
        void done(List<Video> items, String error);
    }

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile List<Video> items = Collections.emptyList();
    private static volatile boolean loaded;

    private Catalog() {}

    public static List<Video> items() { return items; }
    public static boolean isLoaded() { return loaded; }

    public static Video find(String id) {
        for (Video v : items) if (v.id.equals(id)) return v;
        return null;
    }

    public static void load(boolean force, Callback cb) {
        if (loaded && !force) { cb.done(items, null); return; }
        IO.execute(() -> {
            try {
                String body = ApiConfig.get("/api/videos");
                JSONArray arr;
                String t = body.trim();
                arr = t.startsWith("[") ? new JSONArray(t) : new JSONObject(t).optJSONArray("items");
                List<Video> list = new ArrayList<>();
                if (arr != null) for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o != null) list.add(new Video(o));
                }
                items = Collections.unmodifiableList(list);
                loaded = true;
                MAIN.post(() -> cb.done(items, null));
            } catch (Exception e) {
                String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                MAIN.post(() -> cb.done(items, msg));
            }
        });
    }
}
