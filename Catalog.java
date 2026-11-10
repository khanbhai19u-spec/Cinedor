package com.cinedor.app;

import android.content.Context;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** What appears where: the rows of every tab, and one loader for every kind of list. */
public final class Catalog {

    public static final String LOCAL_LIST = "local:mylist", LOCAL_HISTORY = "local:history";
    private static final String ARCHIVE = "archive|";

    private Catalog() {}

    /** Loads a page of any list: TMDB path, free public-domain films, My List or Continue Watching. */
    public static List<Item> load(Context c, String path, int page) throws Exception {
        if (LOCAL_LIST.equals(path)) return page == 1 ? Store.favorites(c) : new ArrayList<>();
        if (LOCAL_HISTORY.equals(path)) return page == 1 ? Store.continueWatching(c) : new ArrayList<>();
        if (path.startsWith(ARCHIVE)) {
            List<Item> out = new ArrayList<>();
            for (MovieApi.Movie m : MovieApi.search(path.substring(ARCHIVE.length()), MovieApi.SORT_POPULAR, 30, page)) {
                out.add(Item.fromArchive(m));
            }
            return out;
        }
        return Tmdb.list(path, page);
    }

    private static String day(int offsetDays) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, offsetDays);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.getTime());
    }

    private static String newIn(String country) {
        return "discover/movie?with_origin_country=" + country + "&sort_by=popularity.desc"
                + "&primary_release_date.gte=" + day(-90) + "&primary_release_date.lte=" + day(0);
    }

    public static final String FREE_MOVIES = ARCHIVE + MovieApi.FEATURE;

    // ---------------------------------------------------------------- Home

    public static String[][] homeChips() {
        return new String[][]{
                {"Movies", "movie/popular"}, {"Series", "tv/popular"},
                {"Free Movies", FREE_MOVIES},
                {"Action", "discover/movie?with_genres=28"}, {"Comedy", "discover/movie?with_genres=35"},
                {"Horror", "discover/movie?with_genres=27"}, {"Romance", "discover/movie?with_genres=10749"},
                {"Sci-Fi", "discover/movie?with_genres=878"}, {"Thriller", "discover/movie?with_genres=53"},
                {"Animation", "discover/movie?with_genres=16"}, {"Family", "discover/movie?with_genres=10751"},
        };
    }

    public static String[][] languages() {
        return new String[][]{
                {"Bangla", "bn"}, {"Hindi", "hi"}, {"English", "en"}, {"Tamil", "ta"}, {"Telugu", "te"},
                {"Malayalam", "ml"}, {"Korean", "ko"}, {"Japanese", "ja"}, {"Spanish", "es"}, {"Turkish", "tr"},
        };
    }

    public static String[][] home() {
        return new String[][]{
                {"Popular Movies", "movie/popular"},
                {"Popular Series", "tv/popular"},
                {"Free Full Movies · Public Domain", FREE_MOVIES},
                {"New Releases · United States", newIn("US")},
                {"New Releases · India", newIn("IN")},
                {"New Releases · Bangladesh", newIn("BD")},
                {"Coming Soon", "discover/movie?sort_by=popularity.desc&primary_release_date.gte=" + day(1)
                        + "&primary_release_date.lte=" + day(60)},
                {"In Cinemas Now", "movie/now_playing"},
                {"New Episodes", "tv/on_the_air"},
                {"Top Rated Movies", "movie/top_rated"},
                {"Top Rated Series", "tv/top_rated"},
        };
    }

    // ---------------------------------------------------------------- Anime

    private static final String ANIME_TV = "discover/tv?with_original_language=ja&with_genres=16";
    private static final String ANIME_MOVIE = "discover/movie?with_genres=16&with_original_language=ja";

    public static String animeTop() { return ANIME_TV + "&sort_by=popularity.desc&air_date.gte=" + day(-7); }

    public static String[][] animeChips() {
        return new String[][]{
                {"Series", ANIME_TV + "&sort_by=popularity.desc"}, {"Movies", ANIME_MOVIE + "&sort_by=popularity.desc"},
                {"Action", ANIME_TV + ",10759"}, {"Fantasy", ANIME_TV + ",10765"},
                {"Comedy", ANIME_TV + ",35"}, {"Mystery", ANIME_TV + ",9648"},
        };
    }

    public static String[][] anime() {
        return new String[][]{
                {"Airing This Season", ANIME_TV + "&sort_by=popularity.desc&air_date.gte=" + day(-90)
                        + "&air_date.lte=" + day(0)},
                {"All-Time Popular", ANIME_TV + "&sort_by=vote_count.desc"},
                {"Anime Movies", ANIME_MOVIE + "&sort_by=vote_count.desc"},
                {"Top Rated", ANIME_TV + "&sort_by=vote_average.desc&vote_count.gte=500"},
                {"Action", ANIME_TV + ",10759&sort_by=popularity.desc"},
                {"Fantasy & Sci-Fi", ANIME_TV + ",10765&sort_by=popularity.desc"},
                {"Comedy", ANIME_TV + ",35&sort_by=popularity.desc"},
                {"Drama", ANIME_TV + ",18&sort_by=popularity.desc"},
                {"Mystery", ANIME_TV + ",9648&sort_by=popularity.desc"},
        };
    }

    // ---------------------------------------------------------------- K-Drama

    private static final String KO_TV = "discover/tv?with_original_language=ko";

    public static String kdramaTop() { return KO_TV + "&with_genres=18&sort_by=popularity.desc"; }

    public static String[][] kdramaChips() {
        return new String[][]{
                {"Series", KO_TV + "&sort_by=popularity.desc"},
                {"Movies", "discover/movie?with_original_language=ko&sort_by=popularity.desc"},
                {"Romance", KO_TV + "&with_genres=18,35"}, {"Crime", KO_TV + "&with_genres=80"},
                {"Fantasy", KO_TV + "&with_genres=10765"},
        };
    }

    public static String[][] kdrama() {
        return new String[][]{
                {"New This Season", KO_TV + "&with_genres=18&sort_by=popularity.desc&first_air_date.gte=" + day(-120)
                        + "&first_air_date.lte=" + day(0)},
                {"Coming Soon", KO_TV + "&sort_by=popularity.desc&first_air_date.gte=" + day(1)},
                {"Top Rated K-Dramas", KO_TV + "&with_genres=18&sort_by=vote_average.desc&vote_count.gte=200"},
                {"Romance & Drama", KO_TV + "&with_genres=18,35&sort_by=popularity.desc"},
                {"Crime & Mystery", KO_TV + "&with_genres=80%7C9648&sort_by=popularity.desc"},
                {"Fantasy & Sci-Fi", KO_TV + "&with_genres=10765&sort_by=popularity.desc"},
                {"Korean Movies", "discover/movie?with_original_language=ko&sort_by=popularity.desc"},
        };
    }

    // ---------------------------------------------------------------- genres

    public static String genre(int id) {
        switch (id) {
            case 28: return "Action"; case 12: return "Adventure"; case 16: return "Animation";
            case 35: return "Comedy"; case 80: return "Crime"; case 99: return "Documentary";
            case 18: return "Drama"; case 10751: return "Family"; case 14: return "Fantasy";
            case 36: return "History"; case 27: return "Horror"; case 10402: return "Music";
            case 9648: return "Mystery"; case 10749: return "Romance"; case 878: return "Sci-Fi";
            case 53: return "Thriller"; case 10752: return "War"; case 37: return "Western";
            case 10759: return "Action & Adventure"; case 10762: return "Kids"; case 10765: return "Sci-Fi & Fantasy";
            case 10768: return "War & Politics"; case 10764: return "Reality"; default: return "";
        }
    }

    public static String genres(Item it, int max) {
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (int g : it.genres) {
            String name = genre(g);
            if (name.isEmpty()) continue;
            if (sb.length() > 0) sb.append("  •  ");
            sb.append(name);
            if (++n >= max) break;
        }
        return sb.toString();
    }
}
