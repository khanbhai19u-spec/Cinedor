package com.cinedor.app;

import android.text.Html;

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
import java.util.Map;

/**
 * Automatic movie catalog.
 *
 * Everything (lists, posters, details, stream + download links) is fetched automatically from the
 * Internet Archive's free & legal public-domain film collections. No API key, no server needed.
 */
public final class MovieApi {

    private static final String SEARCH = "https://archive.org/advancedsearch.php";
    private static final String META = "https://archive.org/metadata/";
    private static final String DOWNLOAD = "https://archive.org/download/";
    private static final String IMG = "https://archive.org/services/img/";

    /** Public-domain feature films. */
    public static final String FEATURE = "collection:(feature_films) AND mediatype:(movies)";

    public static final String SORT_POPULAR = "downloads desc";
    public static final String SORT_NEW = "addeddate desc";

    private MovieApi() {}

    // ---------------------------------------------------------------- models

    public static class Movie {
        public final String id, title, year;

        public Movie(String id, String title, String year) {
            this.id = id;
            this.title = title == null || title.isEmpty() ? id : title;
            this.year = year == null ? "" : year;
        }

        public String poster() { return IMG + id; }

        public JSONObject toJson() {
            JSONObject o = new JSONObject();
            try { o.put("id", id); o.put("title", title); o.put("year", year); } catch (Exception ignored) {}
            return o;
        }

        public static Movie fromJson(JSONObject o) {
            return new Movie(o.optString("id"), o.optString("title"), o.optString("year"));
        }
    }

    public static class Stream {
        public final String label, url, type;
        public final long size;
        public final int height;

        Stream(String label, String url, long size, int height) {
            this.label = label; this.url = url; this.size = size; this.height = height;
            this.type = url.toLowerCase().contains(".m3u8") ? "hls" : "mp4";
        }
    }

    public static class Details {
        public String id, title, year, runtime, description, genres;
        public final List<Stream> streams = new ArrayList<>();

        public Movie asMovie() { return new Movie(id, title, year); }

        /** Best quality up to 1080p, picked automatically. */
        public Stream best() {
            Stream pick = null;
            for (Stream s : streams) {
                if (s.height > 1080) continue;
                if (pick == null || s.height > pick.height || (s.height == pick.height && s.size > pick.size)) pick = s;
            }
            return pick != null ? pick : (streams.isEmpty() ? null : streams.get(0));
        }
    }

    /** One horizontal row on the home screen. */
    public static class Section {
        public final String title, query, sort;

        Section(String title, String query, String sort) {
            this.title = title; this.query = query; this.sort = sort;
        }
    }

    /** Rows that load automatically on the home screen. */
    public static List<Section> homeSections() {
        List<Section> s = new ArrayList<>();
        s.add(new Section("🔥 Most Popular", FEATURE, SORT_POPULAR));
        s.add(new Section("⭐ Top Rated", FEATURE + " AND avg_rating:[4 TO 5] AND num_reviews:[3 TO 100000]", SORT_POPULAR));
        s.add(new Section("🆕 Recently Added", FEATURE, SORT_NEW));
        s.add(new Section("👻 Horror", FEATURE + " AND subject:(horror)", SORT_POPULAR));
        s.add(new Section("🚀 Sci-Fi", FEATURE + " AND subject:(\"science fiction\" OR \"sci-fi\" OR scifi)", SORT_POPULAR));
        s.add(new Section("😂 Comedy", FEATURE + " AND subject:(comedy)", SORT_POPULAR));
        s.add(new Section("🕵️ Film Noir", FEATURE + " AND subject:(\"film noir\" OR noir)", SORT_POPULAR));
        s.add(new Section("⚔️ Action & Adventure", FEATURE + " AND subject:(action OR adventure)", SORT_POPULAR));
        s.add(new Section("🤠 Western", FEATURE + " AND subject:(western)", SORT_POPULAR));
        s.add(new Section("🎭 Drama", FEATURE + " AND subject:(drama)", SORT_POPULAR));
        s.add(new Section("💘 Romance", FEATURE + " AND subject:(romance)", SORT_POPULAR));
        s.add(new Section("🎨 Animation & Cartoons", "collection:(animationandcartoons) AND mediatype:(movies)", SORT_POPULAR));
        return s;
    }

    /** Query for what the user typed in the search box. */
    public static String searchQuery(String text) {
        String clean = text.replaceAll("[^\\p{L}\\p{N} ]+", " ").trim().replaceAll("\\s+", " ");
        if (clean.isEmpty()) return FEATURE;
        return "(" + FEATURE + " OR (collection:(animationandcartoons) AND mediatype:(movies)))"
                + " AND (title:(" + clean + ") OR subject:(" + clean + "))";
    }

    // ---------------------------------------------------------------- API calls

    public static List<Movie> search(String query, String sort, int rows, int page) throws Exception {
        String url = SEARCH + "?q=" + enc(query)
                + "&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=year"
                + "&sort%5B%5D=" + enc(sort)
                + "&rows=" + rows + "&page=" + page + "&output=json";
        return parseSearch(http(url));
    }

    public static List<Movie> parseSearch(String json) throws Exception {
        JSONArray docs = new JSONObject(json).getJSONObject("response").getJSONArray("docs");
        List<Movie> out = new ArrayList<>();
        for (int i = 0; i < docs.length(); i++) {
            JSONObject d = docs.getJSONObject(i);
            out.add(new Movie(d.optString("identifier"), first(d.opt("title")), first(d.opt("year"))));
        }
        return out;
    }

    public static Details details(String id) throws Exception {
        JSONObject root = new JSONObject(http(META + enc(id)));
        JSONObject m = root.optJSONObject("metadata");
        if (m == null) throw new Exception("Movie not found");

        Details d = new Details();
        d.id = id;
        d.title = first(m.opt("title"));
        if (d.title.isEmpty()) d.title = id;
        d.year = first(m.opt("year"));
        if (d.year.isEmpty()) {
            String date = first(m.opt("date"));
            d.year = date.length() >= 4 ? date.substring(0, 4) : "";
        }
        d.runtime = first(m.opt("runtime"));
        d.genres = joined(m.opt("subject"));
        String desc = joined(m.opt("description"));
        d.description = Html.fromHtml(desc, Html.FROM_HTML_MODE_COMPACT).toString().trim();

        JSONArray files = root.optJSONArray("files");
        if (files != null) {
            Map<String, Integer> labelCount = new HashMap<>();
            List<JSONObject> videos = new ArrayList<>();
            for (int i = 0; i < files.length(); i++) {
                JSONObject f = files.getJSONObject(i);
                String n = f.optString("name").toLowerCase();
                if (n.endsWith(".mp4") || n.endsWith(".m4v")) videos.add(f);
            }
            for (JSONObject f : videos) {
                String label = label(f);
                Integer c = labelCount.get(label);
                labelCount.put(label, c == null ? 1 : c + 1);
            }
            for (JSONObject f : videos) {
                String name = f.optString("name");
                String label = label(f);
                Integer c = labelCount.get(label);
                if (c != null && c > 1) label += " • " + shortName(name);   // multi-part movies
                d.streams.add(new Stream(label, DOWNLOAD + enc(id) + "/" + encPath(name),
                        num(f.optString("size")), (int) num(f.optString("height"))));
            }
            Collections.sort(d.streams, (a, b) -> a.height != b.height
                    ? Integer.compare(b.height, a.height) : Long.compare(b.size, a.size));
        }
        return d;
    }

    // ---------------------------------------------------------------- helpers

    private static String label(JSONObject f) {
        int h = (int) num(f.optString("height"));
        if (h > 0) return h + "p";
        String fmt = f.optString("format", "MP4");
        if (fmt.contains("512Kb")) return "360p";
        if (fmt.toLowerCase().contains("h.264")) return "HD";
        return fmt.isEmpty() ? "MP4" : fmt;
    }

    private static String shortName(String name) {
        String n = name.substring(name.lastIndexOf('/') + 1);
        int dot = n.lastIndexOf('.');
        if (dot > 0) n = n.substring(0, dot);
        return n.length() > 28 ? n.substring(0, 28) + "…" : n;
    }

    private static long num(String s) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return 0; }
    }

    private static String first(Object o) {
        if (o == null || o == JSONObject.NULL) return "";
        if (o instanceof JSONArray) return ((JSONArray) o).length() > 0 ? ((JSONArray) o).optString(0) : "";
        return String.valueOf(o).trim();
    }

    private static String joined(Object o) {
        if (o == null || o == JSONObject.NULL) return "";
        if (o instanceof JSONArray) {
            JSONArray a = (JSONArray) o;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < a.length(); i++) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(a.optString(i));
            }
            return sb.toString();
        }
        return String.valueOf(o).replace(";", ", ").trim();
    }

    static String enc(String s) {
        try { return URLEncoder.encode(s, "UTF-8").replace("+", "%20"); } catch (Exception e) { return s; }
    }

    private static String encPath(String path) {
        StringBuilder sb = new StringBuilder();
        for (String part : path.split("/")) {
            if (sb.length() > 0) sb.append('/');
            sb.append(enc(part));
        }
        return sb.toString();
    }

    static String http(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(25000);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("User-Agent", "CinedorApp/2.0 (Android)");
        int code = c.getResponseCode();
        InputStream in = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
        StringBuilder sb = new StringBuilder();
        if (in != null) {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }
        }
        c.disconnect();
        if (code < 200 || code >= 300) throw new Exception("Server error " + code);
        return sb.toString();
    }
}
