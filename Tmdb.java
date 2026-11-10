package com.cinedor.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** TMDB client: all titles, posters, cast, trailers and "where to watch" come from here automatically. */
public final class Tmdb {

    public static final String BASE = "https://api.themoviedb.org/3/";
    public static final String IMG = "https://image.tmdb.org/t/p/";

    private Tmdb() {}

    public static boolean hasKey() { return !Keys.TMDB.isEmpty(); }

    public static JSONObject get(String path) throws Exception {
        String url = BASE + path + (path.contains("?") ? "&" : "?") + "api_key=" + Keys.TMDB + "&include_adult=false";
        return new JSONObject(MovieApi.http(url));
    }

    public static List<Item> list(String path, int page) throws Exception {
        String type = path.startsWith("tv") || path.startsWith("discover/tv") || path.startsWith("trending/tv")
                ? Item.TV : Item.MOVIE;
        JSONObject root = get(path + (path.contains("?") ? "&" : "?") + "page=" + page);
        return parse(root.optJSONArray("results"), type);
    }

    public static List<Item> parse(JSONArray arr, String type) {
        List<Item> out = new ArrayList<>();
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            Item it = Item.fromTmdb(o, type);
            if (it != null && !it.poster.isEmpty()) out.add(it);
        }
        return out;
    }
}
