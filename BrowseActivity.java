package com.cinedor.app;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** "See All" grid with endless scrolling, and the search screen. */
public class BrowseActivity extends AppCompatActivity {

    public static final String EXTRA_TITLE = "title", EXTRA_PATH = "path", EXTRA_SEARCH = "search";

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private PosterAdapter adapter;
    private ProgressBar progress;
    private TextView empty;
    private EditText search;
    private String path;
    private int page, gen;
    private boolean loading, end;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browse);
        progress = findViewById(R.id.progress);
        empty = findViewById(R.id.empty);
        search = findViewById(R.id.search);
        TextView title = findViewById(R.id.title);
        ImageView back = findViewById(R.id.back);
        back.setBackground(Ui.round(Ui.SURFACE, Ui.dp(this, 44)));
        back.setOnClickListener(v -> finish());

        RecyclerView grid = findViewById(R.id.grid);
        int widthDp = (int) (getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density);
        GridLayoutManager lm = new GridLayoutManager(this, Math.max(3, widthDp / 125));
        grid.setLayoutManager(lm);
        adapter = new PosterAdapter(PosterAdapter.GRID);
        grid.setAdapter(adapter);
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy > 0 && lm.findLastVisibleItemPosition() >= adapter.getItemCount() - 9) loadPage();
            }
        });

        if (getIntent().getBooleanExtra(EXTRA_SEARCH, false)) {
            title.setVisibility(View.GONE);
            search.setVisibility(View.VISIBLE);
            search.setBackground(Ui.round(Ui.SURFACE, Ui.dp(this, 24)));
            search.requestFocus();
            search.setOnEditorActionListener((v, actionId, event) -> {
                boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        && event.getAction() == KeyEvent.ACTION_DOWN;
                if (actionId != EditorInfo.IME_ACTION_SEARCH && !enter) return false;
                String q = search.getText().toString().trim();
                if (!q.isEmpty()) {
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
                    start("search/multi?query=" + MovieApi.enc(q));
                }
                return true;
            });
            empty.setText("Search movies, series, anime…");
            empty.setVisibility(View.VISIBLE);
        } else {
            title.setText(getIntent().getStringExtra(EXTRA_TITLE));
            start(getIntent().getStringExtra(EXTRA_PATH));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // My List / Continue Watching can change while a title is open
        if (path != null && path.startsWith("local:") && page > 0) start(path);
    }

    private void start(String newPath) {
        path = newPath;
        page = 0;
        end = false;
        loading = false;
        gen++;
        adapter.set(new ArrayList<>());
        empty.setVisibility(View.GONE);
        loadPage();
    }

    private void loadPage() {
        if (loading || end || path == null) return;
        loading = true;
        final int g = gen, next = page + 1;
        progress.setVisibility(View.VISIBLE);
        io.execute(() -> {
            List<Item> items = null;
            try { items = Catalog.load(this, path, next); } catch (Exception ignored) {}
            final List<Item> result = items;
            main.post(() -> {
                if (g != gen || isFinishing()) return;
                loading = false;
                progress.setVisibility(View.GONE);
                if (result == null) {
                    if (adapter.getItemCount() == 0) {
                        empty.setText("Can't load. Check your internet connection.");
                        empty.setVisibility(View.VISIBLE);
                    }
                    return;
                }
                page = next;
                end = result.isEmpty() || path.startsWith("local:") || next >= 500;
                adapter.add(result);
                if (adapter.getItemCount() == 0) {
                    empty.setText("Nothing here yet.");
                    empty.setVisibility(View.VISIBLE);
                }
            });
        });
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }
}
