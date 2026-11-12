package com.cinedor.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Talks to TMDB directly: trending lists, categories, search, details, trailers.
 * TMDB gives information and artwork only — it has no video files.
 * The key comes from the GitHub secret TMDB_API_KEY (built into the APK) or App Settings.
 */
public final class Tmdb {

    public static class Row {
        public final String title;
        public final List<Video> items;

        Row(String title, List<Video> items) { this.title = title; this.items = items; }
    }

    /** A browsable list (Home rows and Settings categories). */
    public static class Category {
        public final String key, title, subtitle, path, params;

        Category(String key, String title, String subtitle, String path, String params) {
            this.key = key; this.title = title; this.subtitle = subtitle; this.path = path; this.params = params;
        }
    }

    public static final Category TRENDING = new Category("trending", "Trending this week", "", "/trending/all/week", "");
    public static final Category POPULAR_MOVIES = new Category("movies", "Popular movies", "", "/movie/popular", "");
    public static final Category POPULAR_TV = new Category("tv", "Popular series", "", "/tv/popular", "");
    public static final Category TOP_RATED = new Category("top", "Top rated movies", "", "/movie/top_rated", "");
    public static final Category NOW_PLAYING = new Category("now", "In cinemas now", "", "/movie/now_playing", "");
    public static final Category K_DRAMA = new Category("kdrama", "K-Drama", "Korean series", "/discover/tv",
            "with_origin_country=KR&with_genres=18&sort_by=popularity.desc");
    public static final Category C_DRAMA = new Category("cdrama", "C-Drama", "Chinese series", "/discover/tv",
            "with_origin_country=CN&with_genres=18&sort_by=popularity.desc");
    public static final Category ANIME = new Category("anime", "Anime", "Japanese animation", "/discover/tv",
            "with_origin_country=JP&with_genres=16&sort_by=popularity.desc");
    public static final Category BOLLYWOOD = new Category("hindi", "Hindi movies", "Bollywood", "/discover/movie",
            "with_original_language=hi&sort_by=popularity.desc&vote_count.gte=50");

    public static final Category[] HOME_ROWS = {TRENDING, POPULAR_MOVIES, POPULAR_TV, K_DRAMA, NOW_PLAYING, C_DRAMA, ANIME, BOLLYWOOD, TOP_RATED};

    public static Category category(String key) {
        for (Category c : HOME_ROWS) if (c.key.equals(key)) return c;
        return TRENDING;
    }

    public interface Callback<T> { void done(T result, String error); }

    private static final String API = "https://api.themoviedb.org/3";
    private static final String IMG = "https://image.tmdb.org/t/p/";
    private static final ExecutorService IO = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<String, CacheEntry> CACHE = Collections.synchronizedMap(new HashMap<>());
    private static final long TTL_MS = 15 * 60_000;
    private static volatile Map<Integer, String> genres;
    private static volatile List<Row> rows = Collections.emptyList();
    private static volatile long rowsAt;
    private static SharedPreferences prefs;

    private static class CacheEntry {
        final long at; final JSONObject data;
        CacheEntry(long at, JSONObject data) { this.at = at; this.data = data; }
    }

    private Tmdb() {}

    // ---------- settings ----------
    public static void init(Context c) {
        if (prefs == null) prefs = c.getApplicationContext().getSharedPreferences("tmdb", Context.MODE_PRIVATE);
    }

    /** Key typed in App Settings wins; otherwise the one built in from the GitHub secret. */
    public static String key() { return BuildConfig.TMDB_API_KEY; }

    public static boolean hasBuiltInKey() { return !BuildConfig.TMDB_API_KEY.isEmpty(); }
    public static boolean enabled() { return !key().isEmpty(); }
    public static String ownKey() { return prefs != null ? prefs.getString("key", "") : ""; }

    /** TMDB text in the phone's language (falls back to English where TMDB has none). */
    public static String language() {
        Locale l = Locale.getDefault();
        String lang = l.getLanguage();
        if (lang.isEmpty()) return "en-US";
        String country = l.getCountry();
        return country.isEmpty() ? lang : lang + "-" + country;
    }

    public static void save(String key, String lang) {
        if (prefs == null) return;
        SharedPreferences.Editor e = prefs.edit();
        if (key != null) e.putString("key", key.trim());
        if (lang != null) e.putString("lang", lang);
        e.apply();
        CACHE.clear();
        genres = null;
        rows = Collections.emptyList();
        rowsAt = 0;
    }

    // ---------- http ----------
    private static JSONObject get(String path, String params) throws Exception {
        String k = key();
        if (k.isEmpty()) throw new Exception("TMDB key missing — add the TMDB_API_KEY secret on GitHub and build again");
        StringBuilder u = new StringBuilder(API).append(path).append(path.contains("?") ? "&" : "?")
                .append("language=").append(language());
        if (params != null && !params.isEmpty()) u.append('&').append(params);
        String cacheKey = u.toString();
        CacheEntry hit = CACHE.get(cacheKey);
        if (hit != null && System.currentTimeMillis() - hit.at < TTL_MS) return hit.data;

        boolean bearer = k.length() > 40; // v4 read access token
        if (!bearer) u.append("&api_key=").append(k);
        HttpURLConnection c = (HttpURLConnection) new URL(u.toString()).openConnection();
        c.setConnectTimeout(12000);
        c.setReadTimeout(20000);
        c.setRequestProperty("Accept", "application/json");
        if (bearer) c.setRequestProperty("Authorization", "Bearer " + k);
        try {
            int code = c.getResponseCode();
            if (code == 401) throw new Exception("TMDB key is not valid");
            if (code == 429) throw new Exception("TMDB is busy — try again in a moment");
            if (code < 200 || code >= 300) throw new Exception("TMDB error " + code);
            StringBuilder sb = new StringBuilder();
            try (InputStream in = c.getInputStream();
                 BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }
            JSONObject o = new JSONObject(sb.toString());
            CACHE.put(cacheKey, new CacheEntry(System.currentTimeMillis(), o));
            return o;
        } catch (java.net.UnknownHostException e) {
            throw new Exception("No internet connection");
        } finally {
            c.disconnect();
        }
    }

    private static Map<Integer, String> genreMap() {
        if (genres != null) return genres;
        Map<Integer, String> m = new HashMap<>();
        for (String t : new String[]{"movie", "tv"}) {
            try {
                JSONArray a = get("/genre/" + t + "/list", "").optJSONArray("genres");
                if (a != null) for (int i = 0; i < a.length(); i++) {
                    JSONObject g = a.optJSONObject(i);
                    if (g != null) m.put(g.optInt("id"), g.optString("name"));
                }
            } catch (Exception ignored) {}
        }
        genres = m;
        return m;
    }

    // ---------- mapping ----------
    private static String img(String p, String size) {
        return p == null || p.isEmpty() || "null".equals(p) ? "" : IMG + size + p;
    }

    /** TMDB result → our Video shape. */
    static Video map(JSONObject r, String typeHint) {
        try {
            String type = r.optString("media_type", "");
            if (type.isEmpty()) type = typeHint;
            if (!"movie".equals(type) && !"tv".equals(type)) return null;
            String title = r.optString("movie".equals(type) ? "title" : "name", r.optString("title", r.optString("name")));
            String date = r.optString("movie".equals(type) ? "release_date" : "first_air_date", "");
            String year = date.length() >= 4 ? date.substring(0, 4) : "";

            StringBuilder genre = new StringBuilder();
            JSONArray gs = r.optJSONArray("genres");
            if (gs != null) {
                for (int i = 0; i < gs.length() && i < 2; i++) {
                    if (genre.length() > 0) genre.append(" • ");
                    genre.append(gs.optJSONObject(i).optString("name"));
                }
            } else {
                JSONArray ids = r.optJSONArray("genre_ids");
                Map<Integer, String> gm = genres;
                if (ids != null && gm != null) for (int i = 0, n = 0; i < ids.length() && n < 2; i++) {
                    String name = gm.get(ids.optInt(i));
                    if (name == null) continue;
                    if (genre.length() > 0) genre.append(" • ");
                    genre.append(name);
                    n++;
                }
            }
            long id = r.optLong("id");
            JSONObject o = new JSONObject()
                    .put("id", "tmdb:" + type + ":" + id)
                    .put("tmdbId", id)
                    .put("mediaType", type)
                    .put("type", "tv".equals(type) ? "series" : "movie")
                    .put("title", title)
                    .put("year", year)
                    .put("genre", genre.toString())
                    .put("description", r.optString("overview", ""))
                    .put("poster", img(r.optString("poster_path", ""), "w500"))
                    .put("backdrop", img(r.optString("backdrop_path", ""), "w1280"))
                    .put("rating", Math.round(r.optDouble("vote_average", 0) * 10) / 10.0)
                    .put("featured", false);
            Video local = Catalog.match(type, title, year);
            if (local != null) o.put("inLibrary", local.id);
            return new Video(o);
        } catch (Exception e) {
            return null;
        }
    }

    private static List<Video> list(JSONObject o, String typeHint) {
        List<Video> out = new ArrayList<>();
        JSONArray a = o.optJSONArray("results");
        if (a != null) for (int i = 0; i < a.length(); i++) {
            JSONObject r = a.optJSONObject(i);
            if (r == null || r.optBoolean("adult", false)) continue;
            Video v = map(r, typeHint);
            if (v != null && !v.poster.isEmpty()) out.add(v);
        }
        return out;
    }

    private static String typeOf(Category c) {
        return c.path.contains("/movie") ? "movie" : c.path.contains("/tv") ? "tv" : "";
    }

    // ---------- public calls ----------
    public static void discover(boolean force, Callback<List<Row>> cb) {
        if (!force && !rows.isEmpty() && System.currentTimeMillis() - rowsAt < TTL_MS) { cb.done(rows, null); return; }
        if (force) CACHE.clear();
        IO.execute(() -> {
            List<Row> out = new ArrayList<>();
            String error = null;
            genreMap();
            for (Category c : HOME_ROWS) {
                try {
                    List<Video> items = list(get(c.path, c.params), typeOf(c));
                    if (!items.isEmpty()) out.add(new Row(c.title, items));
                } catch (Exception e) {
                    error = e.getMessage();
                    if (error != null && (error.contains("key") || error.contains("internet"))) break;
                }
            }
            if (!out.isEmpty()) { rows = Collections.unmodifiableList(out); rowsAt = System.currentTimeMillis(); }
            final String err = out.isEmpty() ? error : null;
            final List<Row> res = out.isEmpty() ? rows : out;
            MAIN.post(() -> cb.done(res, err));
        });
    }

    public static void page(Category c, int page, Callback<List<Video>> cb) {
        IO.execute(() -> {
            try {
                genreMap();
                String p = (c.params.isEmpty() ? "" : c.params + "&") + "page=" + page;
                List<Video> out = list(get(c.path, p), typeOf(c));
                MAIN.post(() -> cb.done(out, null));
            } catch (Exception e) {
                MAIN.post(() -> cb.done(Collections.emptyList(), e.getMessage()));
            }
        });
    }

    public static void search(String q, Callback<List<Video>> cb) {
        IO.execute(() -> {
            try {
                genreMap();
                List<Video> out = list(get("/search/multi", "include_adult=false&query=" + URLEncoder.encode(q, "UTF-8")), "");
                MAIN.post(() -> cb.done(out, null));
            } catch (Exception e) {
                MAIN.post(() -> cb.done(Collections.emptyList(), e.getMessage()));
            }
        });
    }

    /** id looks like "tmdb:movie:603". Adds trailer, cast, runtime and tagline. */
    public static void detail(String id, Callback<Video> cb) {
        String[] parts = id.split(":");
        if (parts.length != 3) { cb.done(null, "bad id"); return; }
        IO.execute(() -> {
            try {
                String lang2 = language().substring(0, 2).toLowerCase(Locale.US);
                JSONObject r = get("/" + parts[1] + "/" + parts[2],
                        "append_to_response=videos,credits&include_video_language=" + lang2 + ",en,null");
                Video base = map(r, parts[1]);
                if (base == null) throw new Exception("Not found");
                JSONObject o = base.toJson();
                JSONArray vids = r.optJSONObject("videos") != null ? r.optJSONObject("videos").optJSONArray("results") : null;
                o.put("trailer", pickTrailer(vids));
                long runtime = r.optLong("runtime", 0);
                JSONArray ert = r.optJSONArray("episode_run_time");
                if (runtime == 0 && ert != null && ert.length() > 0) runtime = ert.optLong(0);
                o.put("runtime", runtime);
                o.put("seasons", r.optLong("number_of_seasons", 0));
                o.put("tagline", r.optString("tagline", ""));
                JSONArray cast = r.optJSONObject("credits") != null ? r.optJSONObject("credits").optJSONArray("cast") : null;
                o.put("cast", cast != null ? cast : new JSONArray());
                o.remove("castText");
                Video v = new Video(o);
                MAIN.post(() -> cb.done(v, null));
            } catch (Exception e) {
                MAIN.post(() -> cb.done(null, e.getMessage()));
            }
        });
    }

    /** Quick check of a key before saving it. */
    public static void test(String k, Callback<Boolean> cb) {
        IO.execute(() -> {
            String error = null;
            try {
                boolean bearer = k.length() > 40;
                HttpURLConnection c = (HttpURLConnection) new URL(API + "/configuration" + (bearer ? "" : "?api_key=" + k)).openConnection();
                c.setConnectTimeout(12000);
                if (bearer) c.setRequestProperty("Authorization", "Bearer " + k);
                int code = c.getResponseCode();
                c.disconnect();
                if (code == 401) error = "TMDB says this key is not valid";
                else if (code < 200 || code >= 300) error = "TMDB error " + code;
            } catch (Exception e) {
                error = e.getMessage();
            }
            final String err = error;
            MAIN.post(() -> cb.done(err == null, err));
        });
    }

    private static String pickTrailer(JSONArray vids) {
        if (vids == null) return "";
        String trailer = "", teaser = "", any = "";
        for (int i = 0; i < vids.length(); i++) {
            JSONObject v = vids.optJSONObject(i);
            if (v == null || !"YouTube".equals(v.optString("site"))) continue;
            String t = v.optString("type"), key = v.optString("key");
            if ("Trailer".equals(t) && v.optBoolean("official") && trailer.isEmpty()) trailer = key;
            else if ("Trailer".equals(t) && trailer.isEmpty()) trailer = key;
            else if ("Teaser".equals(t) && teaser.isEmpty()) teaser = key;
            if (any.isEmpty()) any = key;
        }
        return !trailer.isEmpty() ? trailer : !teaser.isEmpty() ? teaser : any;
    }
}
