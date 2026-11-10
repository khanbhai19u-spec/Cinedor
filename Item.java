package com.cinedor.app;

import org.json.JSONObject;

/** One title: a TMDB movie / series, or a free public-domain film from the Internet Archive. */
public class Item {

    public static final String MOVIE = "movie", TV = "tv", ARCHIVE = "archive";

    public String type = MOVIE, id = "", title = "", poster = "", backdrop = "", year = "", overview = "", date = "";
    public double rating;
    public int[] genres = new int[0];

    public String key() { return type + ":" + id; }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("type", type); o.put("id", id); o.put("title", title); o.put("poster", poster);
            o.put("backdrop", backdrop); o.put("year", year); o.put("overview", overview);
            o.put("date", date); o.put("rating", rating);
            StringBuilder g = new StringBuilder();
            for (int x : genres) g.append(g.length() > 0 ? "," : "").append(x);
            o.put("genres", g.toString());
        } catch (Exception ignored) {}
        return o;
    }

    public static Item fromJson(JSONObject o) {
        Item i = new Item();
        i.type = o.optString("type", MOVIE); i.id = o.optString("id"); i.title = o.optString("title");
        i.poster = o.optString("poster"); i.backdrop = o.optString("backdrop"); i.year = o.optString("year");
        i.overview = o.optString("overview"); i.date = o.optString("date"); i.rating = o.optDouble("rating", 0);
        String g = o.optString("genres");
        if (!g.isEmpty()) {
            String[] parts = g.split(",");
            i.genres = new int[parts.length];
            for (int k = 0; k < parts.length; k++) {
                try { i.genres[k] = Integer.parseInt(parts[k]); } catch (Exception ignored) {}
            }
        }
        return i;
    }

    public static Item fromJson(String json) {
        try { return fromJson(new JSONObject(json)); } catch (Exception e) { return null; }
    }

    /** @return null for people / entries without a title. */
    public static Item fromTmdb(JSONObject o, String defaultType) {
        String type = o.optString("media_type", defaultType);
        if (!MOVIE.equals(type) && !TV.equals(type)) return null;
        Item i = new Item();
        i.type = type;
        i.id = String.valueOf(o.optLong("id"));
        i.title = o.optString(MOVIE.equals(type) ? "title" : "name");
        if (i.title.isEmpty()) i.title = o.optString("name", o.optString("title"));
        if (i.title.isEmpty()) return null;
        String p = o.optString("poster_path", ""), b = o.optString("backdrop_path", "");
        i.poster = p.isEmpty() || "null".equals(p) ? "" : Tmdb.IMG + "w342" + p;
        i.backdrop = b.isEmpty() || "null".equals(b) ? "" : Tmdb.IMG + "w780" + b;
        i.date = o.optString(MOVIE.equals(type) ? "release_date" : "first_air_date");
        i.year = i.date.length() >= 4 ? i.date.substring(0, 4) : "";
        i.overview = o.optString("overview");
        i.rating = o.optDouble("vote_average", 0);
        org.json.JSONArray g = o.optJSONArray("genre_ids");
        if (g != null) {
            i.genres = new int[g.length()];
            for (int k = 0; k < g.length(); k++) i.genres[k] = g.optInt(k);
        }
        return i;
    }

    public static Item fromArchive(MovieApi.Movie m) {
        Item i = new Item();
        i.type = ARCHIVE; i.id = m.id; i.title = m.title; i.year = m.year;
        i.poster = m.poster(); i.backdrop = m.poster();
        return i;
    }
}
