package com.cinedor.app;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Home screen. Everything loads automatically:
 *  - a random "Today's Pick" banner
 *  - Continue Watching + My List (local)
 *  - Popular / Top Rated / New / genre rows (online, cached for instant + offline start)
 *  - rows refresh by themselves every hour
 * Plus search and an endless "See all" grid.
 */
public class VideoListActivity extends AppCompatActivity {

    private static final int ROW_SIZE = 24, PAGE_SIZE = 36;
    private static final long AUTO_REFRESH_MS = 60L * 60 * 1000;

    private final ExecutorService io = Executors.newFixedThreadPool(3);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    private final List<Row> onlineRows = new ArrayList<>();
    private Row continueRow, favRow;
    private int generation = 0;
    private long lastLoad = 0;

    private View homeScroll, hero, btnBack;
    private ImageView heroImage;
    private TextView heroTitle, tvHeader, empty;
    private ProgressBar progress;
    private LinearLayout sectionsBox;
    private EditText search;

    // "See all" / search grid
    private RecyclerView grid;
    private MovieAdapter gridAdapter;
    private String gridQuery, gridSort;
    private int gridPage, gridGen;
    private boolean gridLoading, gridEnd;

    static class Row {
        MovieApi.Section section;
        View view;
        MovieAdapter adapter;
        ProgressBar progress;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_list);

        homeScroll = findViewById(R.id.homeScroll);
        hero = findViewById(R.id.hero);
        heroImage = findViewById(R.id.heroImage);
        heroTitle = findViewById(R.id.heroTitle);
        tvHeader = findViewById(R.id.tvHeader);
        btnBack = findViewById(R.id.btnBack);
        empty = findViewById(R.id.empty);
        progress = findViewById(R.id.progress);
        sectionsBox = findViewById(R.id.sections);
        search = findViewById(R.id.search);
        grid = findViewById(R.id.grid);

        findViewById(R.id.btnRefresh).setOnClickListener(v -> {
            if (grid.getVisibility() == View.VISIBLE) openGrid(tvHeader.getText().toString(), gridQuery, gridSort);
            else loadHome(true);
        });
        btnBack.setOnClickListener(v -> closeGrid());

        search.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_SEARCH || enter) {
                String text = search.getText().toString().trim();
                if (!text.isEmpty()) {
                    hideKeyboard();
                    openGrid("🔍 " + text, MovieApi.searchQuery(text), MovieApi.SORT_POPULAR);
                }
                return true;
            }
            return false;
        });

        setupGrid();

        continueRow = addRow(getString(R.string.continue_watching), null);
        favRow = addRow(getString(R.string.my_list), null);
        for (MovieApi.Section s : MovieApi.homeSections()) onlineRows.add(addRow(s.title, s));

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (grid.getVisibility() == View.VISIBLE) closeGrid();
                else { setEnabled(false); getOnBackPressedDispatcher().onBackPressed(); }
            }
        });

        loadHome(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshLocalRows();
        if (lastLoad > 0 && System.currentTimeMillis() - lastLoad > AUTO_REFRESH_MS) loadHome(true);
    }

    // ------------------------------------------------------------ home rows

    private Row addRow(String title, MovieApi.Section section) {
        Row r = new Row();
        r.section = section;
        r.view = LayoutInflater.from(this).inflate(R.layout.item_section, sectionsBox, false);
        r.progress = r.view.findViewById(R.id.sectionProgress);
        ((TextView) r.view.findViewById(R.id.sectionTitle)).setText(title);
        TextView more = r.view.findViewById(R.id.sectionMore);
        if (section == null) {
            more.setVisibility(View.GONE);
            r.progress.setVisibility(View.GONE);
            r.view.setVisibility(View.GONE);
        } else {
            more.setOnClickListener(v -> openGrid(title, section.query, section.sort));
        }
        RecyclerView list = r.view.findViewById(R.id.sectionList);
        list.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        r.adapter = new MovieAdapter(false);
        list.setAdapter(r.adapter);
        sectionsBox.addView(r.view);
        return r;
    }

    private void refreshLocalRows() {
        showLocal(continueRow, Store.continueWatching(this));
        showLocal(favRow, Store.favorites(this));
    }

    private void showLocal(Row r, List<MovieApi.Movie> list) {
        r.adapter.set(list);
        r.view.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void loadHome(boolean force) {
        final int gen = ++generation;
        lastLoad = System.currentTimeMillis();
        empty.setVisibility(View.GONE);
        AtomicInteger failed = new AtomicInteger();
        boolean anyCached = false;

        for (int i = 0; i < onlineRows.size(); i++) {
            Row r = onlineRows.get(i);
            String key = "row" + i + "_" + r.section.title.hashCode();
            List<MovieApi.Movie> cached = fromJson(Store.getCache(this, key));
            if (!cached.isEmpty()) {
                anyCached = true;
                r.adapter.set(cached);
                r.progress.setVisibility(View.GONE);
                if (i == 0 && heroImage.getTag() == null) showHero(cached);
            } else {
                r.progress.setVisibility(View.VISIBLE);
            }
            if (!force && !cached.isEmpty() && Store.cacheAge(this, key) < AUTO_REFRESH_MS) continue;

            final int index = i;
            io.execute(() -> {
                try {
                    List<MovieApi.Movie> list = MovieApi.search(r.section.query, r.section.sort, ROW_SIZE, 1);
                    Store.putCache(this, key, toJson(list));
                    main.post(() -> {
                        if (gen != generation || isFinishing()) return;
                        r.adapter.set(list);
                        r.progress.setVisibility(View.GONE);
                        r.view.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
                        if (index == 0 && (force || heroImage.getTag() == null)) showHero(list);
                    });
                } catch (Exception e) {
                    int f = failed.incrementAndGet();
                    main.post(() -> {
                        if (gen != generation || isFinishing()) return;
                        r.progress.setVisibility(View.GONE);
                        if (r.adapter.getItemCount() == 0) r.view.setVisibility(View.GONE);
                        if (f == onlineRows.size() && !hasAnyOnlineItems()) {
                            empty.setText(R.string.no_internet);
                            empty.setVisibility(View.VISIBLE);
                        }
                    });
                }
            });
        }
        if (!anyCached) hero.setVisibility(View.GONE);
    }

    private boolean hasAnyOnlineItems() {
        for (Row r : onlineRows) if (r.adapter.getItemCount() > 0) return true;
        return false;
    }

    /** Random "Today's Pick" from the popular row — changes automatically. */
    private void showHero(List<MovieApi.Movie> list) {
        if (list.isEmpty()) return;
        MovieApi.Movie m = list.get(random.nextInt(Math.min(list.size(), 12)));
        hero.setVisibility(View.VISIBLE);
        heroTitle.setText(m.year.isEmpty() ? m.title : m.title + "  (" + m.year + ")");
        ImageLoader.load(heroImage, m.poster());
        hero.setOnClickListener(v -> {
            Intent i = new Intent(this, MovieDetailActivity.class);
            i.putExtra(MovieDetailActivity.EXTRA_VIDEO_ID, m.id);
            i.putExtra(MovieDetailActivity.EXTRA_TITLE, m.title);
            startActivity(i);
        });
    }

    // ------------------------------------------------------------ grid (search / see all)

    private void setupGrid() {
        int widthDp = (int) (getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density);
        GridLayoutManager lm = new GridLayoutManager(this, Math.max(3, widthDp / 130));
        grid.setLayoutManager(lm);
        gridAdapter = new MovieAdapter(true);
        grid.setAdapter(gridAdapter);
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy > 0 && lm.findLastVisibleItemPosition() >= gridAdapter.getItemCount() - 9) loadGridPage();
            }
        });
    }

    private void openGrid(String title, String query, String sort) {
        gridQuery = query;
        gridSort = sort;
        gridPage = 0;
        gridEnd = false;
        gridLoading = false;
        gridGen++;
        gridAdapter.set(new ArrayList<>());
        tvHeader.setText(title);
        btnBack.setVisibility(View.VISIBLE);
        homeScroll.setVisibility(View.GONE);
        empty.setVisibility(View.GONE);
        grid.setVisibility(View.VISIBLE);
        loadGridPage();
    }

    private void closeGrid() {
        gridGen++;
        grid.setVisibility(View.GONE);
        progress.setVisibility(View.GONE);
        empty.setVisibility(View.GONE);
        homeScroll.setVisibility(View.VISIBLE);
        btnBack.setVisibility(View.GONE);
        tvHeader.setText(R.string.app_name);
        search.setText("");
    }

    private void loadGridPage() {
        if (gridLoading || gridEnd) return;
        gridLoading = true;
        final int gen = gridGen;
        final int page = gridPage + 1;
        progress.setVisibility(View.VISIBLE);
        io.execute(() -> {
            try {
                List<MovieApi.Movie> list = MovieApi.search(gridQuery, gridSort, PAGE_SIZE, page);
                main.post(() -> {
                    if (gen != gridGen || isFinishing()) return;
                    gridLoading = false;
                    gridPage = page;
                    gridEnd = list.size() < PAGE_SIZE;
                    gridAdapter.add(list);
                    progress.setVisibility(View.GONE);
                    if (gridAdapter.getItemCount() == 0) {
                        empty.setText(R.string.nothing_found);
                        empty.setVisibility(View.VISIBLE);
                    }
                });
            } catch (Exception e) {
                main.post(() -> {
                    if (gen != gridGen || isFinishing()) return;
                    gridLoading = false;
                    progress.setVisibility(View.GONE);
                    if (gridAdapter.getItemCount() == 0) {
                        empty.setText(R.string.no_internet);
                        empty.setVisibility(View.VISIBLE);
                    }
                });
            }
        });
    }

    // ------------------------------------------------------------ helpers

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
        search.clearFocus();
    }

    private static String toJson(List<MovieApi.Movie> list) {
        JSONArray a = new JSONArray();
        for (MovieApi.Movie m : list) a.put(m.toJson());
        return a.toString();
    }

    private static List<MovieApi.Movie> fromJson(String json) {
        List<MovieApi.Movie> out = new ArrayList<>();
        if (json == null) return out;
        try {
            JSONArray a = new JSONArray(json);
            for (int i = 0; i < a.length(); i++) out.add(MovieApi.Movie.fromJson(a.getJSONObject(i)));
        } catch (Exception ignored) {}
        return out;
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }
}
