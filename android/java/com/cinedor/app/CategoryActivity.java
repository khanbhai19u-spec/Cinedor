package com.cinedor.app;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** A full TMDB list (K-Drama, C-Drama, Anime, "See all"...) with endless scrolling. */
public class CategoryActivity extends AppCompatActivity {

    private static final String EXTRA_KEY = "category";

    public static void open(Context c, Tmdb.Category cat) {
        c.startActivity(new Intent(c, CategoryActivity.class).putExtra(EXTRA_KEY, cat.key));
    }

    private final List<PosterAdapter.Item> items = new ArrayList<>();
    private PosterAdapter adapter;
    private Tmdb.Category category;
    private int page = 0;
    private boolean loading, end;
    private View progress;
    private TextView message;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ApiConfig.init(this);
        Tmdb.init(this);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setBackgroundDrawable(Ui.appBackground(this));
        setContentView(R.layout.activity_category);

        category = Tmdb.category(getIntent().getStringExtra(EXTRA_KEY));
        ((TextView) findViewById(R.id.catTitle)).setText(category.title);
        TextView sub = findViewById(R.id.catSub);
        sub.setText(category.subtitle);
        sub.setVisibility(category.subtitle.isEmpty() ? View.GONE : View.VISIBLE);
        progress = findViewById(R.id.progress);
        message = findViewById(R.id.catMessage);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        View bar = findViewById(R.id.catBar);
        RecyclerView rv = findViewById(R.id.catList);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.catRoot), (v, insets) -> {
            Insets b = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int p = Ui.dp(this, 16);
            bar.setPadding(p + b.left, p + b.top, p + b.right, p);
            rv.setPadding(Ui.dp(this, 12) + b.left, 0, Ui.dp(this, 12) + b.right, Ui.dp(this, 24) + b.bottom);
            return insets;
        });

        GridLayoutManager lm = new GridLayoutManager(this, Grid.columns(this));
        rv.setLayoutManager(lm);
        adapter = new PosterAdapter(Grid.cardWidth(this), Grid.cardHeight(this), true, it ->
                startActivity(new Intent(this, MovieDetailActivity.class)
                        .putExtra(MovieDetailActivity.EXTRA_VIDEO_ID, it.video.id)
                        .putExtra(MovieDetailActivity.EXTRA_VIDEO_JSON, it.video.toJson().toString())));
        rv.setAdapter(adapter);
        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView r, int dx, int dy) {
                if (lm.findLastVisibleItemPosition() > items.size() - 9) more();
            }
        });
        more();
    }

    private void more() {
        if (loading || end || page >= 30) return;
        loading = true;
        if (items.isEmpty()) progress.setVisibility(View.VISIBLE);
        Tmdb.page(category, page + 1, (list, error) -> {
            loading = false;
            progress.setVisibility(View.GONE);
            if (error != null && items.isEmpty()) {
                message.setText(error);
                message.setVisibility(View.VISIBLE);
                return;
            }
            if (list.isEmpty()) { end = true; return; }
            page++;
            items.addAll(PosterAdapter.wrap(list));
            adapter.submit(new ArrayList<>(items));
        });
    }
}
