package com.cinedor.app;

import android.app.DownloadManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * CineDor main screen with bottom tabs: Home, Anime, K-Drama, Profile.
 * Every row loads by itself from TMDB, is cached for instant start, and refreshes hourly.
 */
public class MainActivity extends AppCompatActivity {

    private static final long REFRESH_MS = 60L * 60 * 1000;
    private static final String[] TABS = {"Home", "Anime", "K-Drama", "Profile"};
    private static final int[] TAB_ICONS = {R.drawable.ic_home, R.drawable.ic_star, R.drawable.ic_heart, R.drawable.ic_person};
    private static final int[] TAB_TINT = {0xFF17454B, 0xFF3B2468, 0xFF4A1F33};

    private final ExecutorService io = Executors.newFixedThreadPool(4);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final View[] pages = new View[4];
    private final long[] built = new long[4];
    private FrameLayout content;
    private LinearLayout nav;
    private int tab = 0;

    interface OnItems { void got(List<Item> items); }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        content = findViewById(R.id.content);
        nav = findViewById(R.id.nav);
        buildNav();
        select(savedInstanceState != null ? savedInstanceState.getInt("tab", 0) : 0);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        out.putInt("tab", tab);
        super.onSaveInstanceState(out);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // auto refresh: rebuild a tab when it is older than an hour; Profile always (My List count)
        if (tab == 3 || (built[tab] > 0 && System.currentTimeMillis() - built[tab] > REFRESH_MS)) {
            pages[tab] = null;
            select(tab);
        }
    }

    // ------------------------------------------------------------ bottom navigation

    private void buildNav() {
        for (int i = 0; i < TABS.length; i++) {
            final int index = i;
            LinearLayout b = new LinearLayout(this);
            b.setOrientation(LinearLayout.VERTICAL);
            b.setGravity(Gravity.CENTER);
            b.setClickable(true);
            ImageView icon = Ui.icon(this, TAB_ICONS[i], Ui.SUB);
            b.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 24), Ui.dp(this, 24)));
            TextView label = Ui.text(this, TABS[i], 11.5f, Ui.SUB, false);
            label.setPadding(0, Ui.dp(this, 3), 0, 0);
            b.addView(label);
            b.setOnClickListener(v -> select(index));
            nav.addView(b, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        }
    }

    private void select(int index) {
        tab = index;
        if (index == 3) pages[3] = buildProfile();
        else if (pages[index] == null) { pages[index] = buildFeed(index); built[index] = System.currentTimeMillis(); }
        content.removeAllViews();
        content.addView(pages[index]);
        for (int i = 0; i < nav.getChildCount(); i++) {
            LinearLayout b = (LinearLayout) nav.getChildAt(i);
            int color = i == index ? Ui.ACCENT : Ui.SUB;
            ((ImageView) b.getChildAt(0)).setColorFilter(color);
            ((TextView) b.getChildAt(1)).setTextColor(color);
        }
    }

    // ------------------------------------------------------------ feeds

    private View buildFeed(int index) {
        NestedScrollView scroll = new NestedScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, 0, 0, Ui.dp(this, 28));
        scroll.addView(box, new ViewGroup.LayoutParams(-1, -2));

        if (!Tmdb.hasKey()) {
            TextView t = Ui.text(this, "TMDB API key is missing in this build.\nAdd the TMDB_API_KEY secret and build again.",
                    15, Ui.SUB, false);
            t.setPadding(Ui.dp(this, 24), Ui.dp(this, 80), Ui.dp(this, 24), 0);
            box.addView(t);
            return scroll;
        }

        // coloured top area: header + chips + hero
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setBackground(Ui.fade(TAB_TINT[index], Ui.BG));
        box.addView(top);
        top.addView(header(index == 0 ? "CineDor" : TABS[index], index == 0));

        String[][] chips = index == 0 ? Catalog.homeChips() : index == 1 ? Catalog.animeChips() : Catalog.kdramaChips();
        LinearLayout chipRow = chipRow(top);
        chipRow.addView(Ui.chip(this, "All", true));
        for (String[] c : chips) {
            TextView chip = Ui.chip(this, c[0], false);
            chip.setOnClickListener(v -> browse((index == 0 ? "" : TABS[index] + " · ") + c[0], c[1]));
            chipRow.addView(chip);
        }

        FrameLayout hero = new FrameLayout(this);
        Ui.clip(hero, Ui.SURFACE, Ui.dp(this, 18));
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-1, Ui.dp(this, 460));
        hl.setMargins(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), 0);
        top.addView(hero, hl);

        if (index == 0) {
            box.addView(sectionTitle("Browse by Language", null));
            LinearLayout langs = chipRow(box);
            for (String[] l : Catalog.languages()) {
                TextView chip = Ui.chip(this, l[0], false);
                chip.setOnClickListener(v -> browse(l[0] + " Movies",
                        "discover/movie?with_original_language=" + l[1] + "&sort_by=popularity.desc"));
                langs.addView(chip);
            }
            List<Item> history = Store.continueWatching(this);
            if (!history.isEmpty()) addSection(box, "Continue Watching", Catalog.LOCAL_HISTORY, PosterAdapter.ROW, null);
        }

        String topPath = index == 0 ? "trending/all/day" : index == 1 ? Catalog.animeTop() : Catalog.kdramaTop();
        String topTitle = index == 0 ? "Top 10 Today" : index == 1 ? "Top 10 Anime This Week" : "Top 10 K-Dramas";
        addSection(box, topTitle, topPath, PosterAdapter.RANKED, items -> showHero(hero, items));

        String[][] rows = index == 0 ? Catalog.home() : index == 1 ? Catalog.anime() : Catalog.kdrama();
        for (String[] r : rows) addSection(box, r[0], r[1], PosterAdapter.ROW, null);
        return scroll;
    }

    private View header(String title, boolean logo) {
        LinearLayout h = new LinearLayout(this);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 10));
        if (logo) {
            ImageView mark = Ui.roundButton(this, R.drawable.ic_play, 34, Ui.ACCENT, Ui.TEXT);
            mark.setClickable(false);
            h.addView(mark);
        }
        TextView t = Ui.text(this, title, 25, Ui.TEXT, true);
        t.setPadding(logo ? Ui.dp(this, 10) : 0, 0, 0, 0);
        h.addView(t, new LinearLayout.LayoutParams(0, -2, 1));

        ImageView dl = Ui.roundButton(this, R.drawable.ic_download, 46, 0xFF1A2126, Ui.TEXT);
        dl.setOnClickListener(v -> openDownloads());
        h.addView(dl);
        ImageView search = Ui.roundButton(this, R.drawable.ic_search, 46, 0xFF1A2126, Ui.TEXT);
        ((LinearLayout.LayoutParams) search.getLayoutParams()).setMarginStart(Ui.dp(this, 8));
        search.setOnClickListener(v -> startActivity(new Intent(this, BrowseActivity.class)
                .putExtra(BrowseActivity.EXTRA_SEARCH, true)));
        h.addView(search);
        return h;
    }

    private LinearLayout chipRow(LinearLayout parent) {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setPadding(Ui.dp(this, 16), Ui.dp(this, 4), Ui.dp(this, 6), Ui.dp(this, 4));
        hs.addView(row);
        parent.addView(hs);
        return row;
    }

    private View sectionTitle(String title, View.OnClickListener seeAll) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 16), Ui.dp(this, 24), Ui.dp(this, 16), Ui.dp(this, 10));
        row.addView(Ui.text(this, title, 19, Ui.TEXT, true), new LinearLayout.LayoutParams(0, -2, 1));
        if (seeAll != null) {
            TextView more = Ui.text(this, "See All ›", 15, Ui.ACCENT, false);
            more.setPadding(Ui.dp(this, 8), Ui.dp(this, 4), 0, Ui.dp(this, 4));
            more.setOnClickListener(seeAll);
            row.addView(more);
        }
        return row;
    }

    /** A titled horizontal row that fills itself: cache first, then fresh data in the background. */
    private void addSection(LinearLayout box, String title, String path, int mode, OnItems first) {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.addView(sectionTitle(title, mode == PosterAdapter.RANKED ? null : v -> browse(title, path)));
        RecyclerView list = new RecyclerView(this);
        list.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        list.setClipToPadding(false);
        list.setPadding(Ui.dp(this, 11), 0, Ui.dp(this, 11), 0);
        list.setNestedScrollingEnabled(false);
        list.setMinimumHeight(Ui.dp(this, 150));
        PosterAdapter adapter = new PosterAdapter(mode);
        list.setAdapter(adapter);
        section.addView(list, new LinearLayout.LayoutParams(-1, -2));
        box.addView(section);

        boolean local = path.startsWith("local:");
        String key = "v3_" + path.replaceAll("[0-9]{4}-[0-9]{2}-[0-9]{2}", "D").hashCode();
        List<Item> cached = local ? Store.continueWatching(this) : Store.fromJson(Store.getCache(this, key));
        if (!cached.isEmpty()) {
            adapter.set(cached);
            if (first != null) first.got(cached);
        }
        if (local || (!cached.isEmpty() && Store.cacheAge(this, key) < REFRESH_MS)) return;

        io.execute(() -> {
            try {
                List<Item> items = Catalog.load(this, path, 1);
                if (!items.isEmpty()) Store.putCache(this, key, Store.toJson(items));
                main.post(() -> {
                    if (isFinishing()) return;
                    if (items.isEmpty() && cached.isEmpty()) { section.setVisibility(View.GONE); return; }
                    if (!items.isEmpty()) adapter.set(items);
                    if (first != null && cached.isEmpty()) first.got(items);
                });
            } catch (Exception e) {
                main.post(() -> {
                    if (isFinishing() || !cached.isEmpty()) return;
                    section.setVisibility(View.GONE);
                    if (first != null) Toast.makeText(this, "Can't load. Check your internet connection.", Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /** Big banner on top: a random pick from today's top titles. */
    private void showHero(FrameLayout hero, List<Item> items) {
        if (items.isEmpty() || hero.getChildCount() > 0) return;
        Item it = items.get((int) (Math.random() * Math.min(items.size(), 8)));

        ImageView img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        hero.addView(img, new FrameLayout.LayoutParams(-1, -1));
        ImageLoader.load(img, it.poster.replace("/w342", "/w780"));

        View shade = new View(this);
        shade.setBackground(Ui.fade(0x00000000, 0xF2101316));
        hero.addView(shade, new FrameLayout.LayoutParams(-1, Ui.dp(this, 280), Gravity.BOTTOM));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setGravity(Gravity.CENTER_HORIZONTAL);
        info.setPadding(Ui.dp(this, 20), 0, Ui.dp(this, 20), Ui.dp(this, 20));
        hero.addView(info, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        TextView title = Ui.text(this, it.title, 30, Ui.TEXT, true);
        title.setGravity(Gravity.CENTER);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        info.addView(title);
        TextView genres = Ui.text(this, Catalog.genres(it, 3), 14, 0xFFC3C9CE, false);
        genres.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 14));
        info.addView(genres);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.CENTER);
        info.addView(buttons);
        LinearLayout play = new LinearLayout(this);
        play.setGravity(Gravity.CENTER);
        play.setPadding(Ui.dp(this, 22), 0, Ui.dp(this, 26), 0);
        play.setBackground(Ui.round(Ui.ACCENT, Ui.dp(this, 26)));
        play.addView(Ui.icon(this, R.drawable.ic_play, Ui.TEXT), new LinearLayout.LayoutParams(Ui.dp(this, 24), Ui.dp(this, 24)));
        TextView pt = Ui.text(this, "Play Now", 16, Ui.TEXT, true);
        pt.setPadding(Ui.dp(this, 8), 0, 0, 0);
        play.addView(pt);
        play.setOnClickListener(v -> PosterAdapter.open(this, it));
        buttons.addView(play, new LinearLayout.LayoutParams(-2, Ui.dp(this, 52)));

        ImageView add = Ui.roundButton(this, Store.isFavorite(this, it) ? R.drawable.ic_check : R.drawable.ic_add,
                52, 0xFF1A2126, Ui.TEXT);
        ((LinearLayout.LayoutParams) add.getLayoutParams()).setMarginStart(Ui.dp(this, 12));
        add.setOnClickListener(v -> {
            boolean added = Store.toggleFavorite(this, it);
            add.setImageResource(added ? R.drawable.ic_check : R.drawable.ic_add);
            Toast.makeText(this, added ? "Added to My List" : "Removed from My List", Toast.LENGTH_SHORT).show();
        });
        buttons.addView(add);
        hero.setOnClickListener(v -> PosterAdapter.open(this, it));
    }

    // ------------------------------------------------------------ profile

    private View buildProfile() {
        NestedScrollView scroll = new NestedScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(box, new ViewGroup.LayoutParams(-1, -2));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Ui.dp(this, 16), Ui.dp(this, 28), Ui.dp(this, 16), Ui.dp(this, 20));
        ImageView avatar = Ui.icon(this, R.drawable.ic_person, Ui.TEXT);
        avatar.setPadding(Ui.dp(this, 18), Ui.dp(this, 18), Ui.dp(this, 18), Ui.dp(this, 18));
        avatar.setBackground(Ui.round(Ui.ACCENT, Ui.dp(this, 12)));
        head.addView(avatar, new LinearLayout.LayoutParams(Ui.dp(this, 72), Ui.dp(this, 72)));
        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.setPadding(Ui.dp(this, 16), 0, 0, 0);
        names.addView(Ui.text(this, "Your CineDor", 24, Ui.TEXT, true));
        names.addView(Ui.text(this, Store.favorites(this).size() + " in My List", 14, Ui.SUB, false));
        head.addView(names);
        box.addView(head);

        box.addView(profileRow(R.drawable.ic_add, "My List", v -> browse("My List", Catalog.LOCAL_LIST)));
        box.addView(profileRow(R.drawable.ic_play, "Continue Watching", v -> browse("Continue Watching", Catalog.LOCAL_HISTORY)));
        box.addView(profileRow(R.drawable.ic_download, "Downloads", v -> openDownloads()));
        box.addView(profileRow(R.drawable.ic_movie, "Free Full Movies", v -> browse("Free Full Movies", Catalog.FREE_MOVIES)));
        View line = new View(this);
        line.setBackgroundColor(Ui.LINE);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-1, Ui.dp(this, 1));
        ll.setMargins(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
        box.addView(line, ll);
        box.addView(profileRow(R.drawable.ic_star, "About", v -> new AlertDialog.Builder(this)
                .setTitle("CineDor")
                .setMessage("Movie, series, anime and K-drama information is loaded automatically from TMDB. "
                        + "This product uses the TMDB API but is not endorsed or certified by TMDB.\n\n"
                        + "Free full movies are public-domain films from the Internet Archive.\n\nNo ads.")
                .setPositiveButton("OK", null).show()));
        return scroll;
    }

    private View profileRow(int icon, String label, View.OnClickListener click) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 20), 0, Ui.dp(this, 16), 0);
        row.setClickable(true);
        row.setOnClickListener(click);
        row.addView(Ui.icon(this, icon, 0xFFC3C9CE), new LinearLayout.LayoutParams(Ui.dp(this, 26), Ui.dp(this, 26)));
        TextView t = Ui.text(this, label, 17, Ui.TEXT, false);
        t.setPadding(Ui.dp(this, 22), 0, 0, 0);
        row.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(Ui.icon(this, R.drawable.ic_chevron, Ui.SUB), new LinearLayout.LayoutParams(Ui.dp(this, 24), Ui.dp(this, 24)));
        row.setLayoutParams(new LinearLayout.LayoutParams(-1, Ui.dp(this, 60)));
        return row;
    }

    // ------------------------------------------------------------ helpers

    private void browse(String title, String path) {
        startActivity(new Intent(this, BrowseActivity.class)
                .putExtra(BrowseActivity.EXTRA_TITLE, title)
                .putExtra(BrowseActivity.EXTRA_PATH, path));
    }

    private void openDownloads() {
        try {
            startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS));
        } catch (Exception e) {
            Toast.makeText(this, "Downloads are saved in Movies/Cinedor", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }
}
