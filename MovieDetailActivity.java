package com.cinedor.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Movie page: poster, year, runtime, genres and description load automatically.
 * Watch plays the best quality right away (and resumes where you left off);
 * long-press Watch or tap Download to choose a quality. ❤ adds it to My List.
 */
public class MovieDetailActivity extends AppCompatActivity {

    public static final String EXTRA_VIDEO_ID = "video_id";
    public static final String EXTRA_TITLE = "video_title";
    private static final int REQ_STORAGE = 41;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private String movieId;
    private MovieApi.Details details;
    private MovieApi.Stream pendingDownload;

    private ProgressBar progress;
    private TextView tvTitle, tvMeta, tvGenres, tvDesc;
    private ImageView poster;
    private Button btnWatch, btnDownload, btnFav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_movie_detail);
        progress = findViewById(R.id.progress);
        tvTitle = findViewById(R.id.tvTitle);
        tvMeta = findViewById(R.id.tvMeta);
        tvGenres = findViewById(R.id.tvGenres);
        tvDesc = findViewById(R.id.tvDesc);
        poster = findViewById(R.id.poster);
        btnWatch = findViewById(R.id.btnWatch);
        btnDownload = findViewById(R.id.btnDownload);
        btnFav = findViewById(R.id.btnFav);

        movieId = getIntent().getStringExtra(EXTRA_VIDEO_ID);
        if (movieId == null || movieId.isEmpty()) { finish(); return; }

        String title = getIntent().getStringExtra(EXTRA_TITLE);
        tvTitle.setText(title != null ? title : "");
        ImageLoader.load(poster, new MovieApi.Movie(movieId, title, "").poster());

        btnWatch.setOnClickListener(v -> autoPlay());
        btnWatch.setOnLongClickListener(v -> { showQualitySheet(true); return true; });
        btnDownload.setOnClickListener(v -> showQualitySheet(false));
        btnFav.setOnClickListener(v -> toggleFav());
        updateFavButton();

        loadDetails();
    }

    private void loadDetails() {
        setLoading(true);
        io.execute(() -> {
            try {
                MovieApi.Details d = MovieApi.details(movieId);
                main.post(() -> {
                    if (isFinishing()) return;
                    details = d;
                    tvTitle.setText(d.title);
                    StringBuilder meta = new StringBuilder();
                    if (!d.year.isEmpty()) meta.append(d.year);
                    if (!d.runtime.isEmpty()) meta.append(meta.length() > 0 ? "  •  " : "").append(d.runtime);
                    meta.append(meta.length() > 0 ? "  •  " : "")
                            .append(d.streams.isEmpty() ? getString(R.string.no_stream_short)
                                    : getResources().getQuantityString(R.plurals.qualities, d.streams.size(), d.streams.size()));
                    tvMeta.setText(meta);
                    tvGenres.setText(d.genres);
                    tvGenres.setVisibility(d.genres.isEmpty() ? View.GONE : View.VISIBLE);
                    tvDesc.setText(d.description.isEmpty() ? getString(R.string.public_domain_note) : d.description);
                    setLoading(false);
                    btnWatch.setEnabled(!d.streams.isEmpty());
                    btnDownload.setEnabled(!d.streams.isEmpty());
                });
            } catch (Exception e) {
                main.post(() -> {
                    if (isFinishing()) return;
                    setLoading(false);
                    btnWatch.setText(R.string.retry);
                    btnWatch.setOnClickListener(v -> {
                        btnWatch.setText(R.string.watch);
                        btnWatch.setOnClickListener(x -> autoPlay());
                        loadDetails();
                    });
                    Toast.makeText(this, getString(R.string.load_error, e.getMessage()), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void setLoading(boolean on) {
        progress.setVisibility(on ? View.VISIBLE : View.GONE);
        btnWatch.setEnabled(!on);
        btnDownload.setEnabled(!on);
    }

    private void toggleFav() {
        String title = details != null ? details.title : tvTitle.getText().toString();
        String year = details != null ? details.year : "";
        boolean added = Store.toggleFavorite(this, new MovieApi.Movie(movieId, title, year));
        Toast.makeText(this, added ? R.string.added_to_list : R.string.removed_from_list, Toast.LENGTH_SHORT).show();
        updateFavButton();
    }

    private void updateFavButton() {
        btnFav.setText(Store.isFavorite(this, movieId) ? R.string.in_my_list : R.string.add_to_list);
    }

    /** One tap = best quality, no questions asked. */
    private void autoPlay() {
        if (details == null) return;
        MovieApi.Stream best = details.best();
        if (best == null) { Toast.makeText(this, R.string.no_stream, Toast.LENGTH_LONG).show(); return; }
        play(best);
    }

    private void showQualitySheet(boolean watchMode) {
        if (details == null || details.streams.isEmpty()) {
            Toast.makeText(this, R.string.no_stream, Toast.LENGTH_LONG).show();
            return;
        }
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View root = LayoutInflater.from(this).inflate(R.layout.sheet_quality, null);
        ((TextView) root.findViewById(R.id.sheetTitle)).setText(watchMode ? R.string.select_watch : R.string.select_download);
        LinearLayout box = root.findViewById(R.id.sheetList);
        for (MovieApi.Stream s : details.streams) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_quality, box, false);
            ((TextView) row.findViewById(R.id.qLabel)).setText(s.label);
            ((TextView) row.findViewById(R.id.qInfo)).setText(
                    s.type.toUpperCase(Locale.US) + (s.size > 0 ? " • " + formatSize(s.size) : ""));
            row.setOnClickListener(v -> {
                sheet.dismiss();
                if (watchMode) play(s); else startDownload(s);
            });
            box.addView(row);
        }
        sheet.setContentView(root);
        sheet.show();
    }

    private void play(MovieApi.Stream s) {
        Intent i = new Intent(this, PlayerActivity.class);
        i.putExtra(PlayerActivity.EXTRA_URL, s.url);
        i.putExtra(PlayerActivity.EXTRA_TYPE, s.type);
        i.putExtra(PlayerActivity.EXTRA_TITLE, details.title + " • " + s.label);
        i.putExtra(PlayerActivity.EXTRA_MOVIE_ID, details.id);
        i.putExtra(PlayerActivity.EXTRA_MOVIE_TITLE, details.title);
        i.putExtra(PlayerActivity.EXTRA_MOVIE_YEAR, details.year);
        startActivity(i);
    }

    private void startDownload(MovieApi.Stream s) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
                && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingDownload = s;
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
            return;
        }
        long id = VideoDownloader.download(this, s.url, details.title, s.label);
        if (id > 0) Toast.makeText(this, getString(R.string.download_started, s.label), Toast.LENGTH_SHORT).show();
    }

    private static String formatSize(long bytes) {
        if (bytes >= 1024L * 1024 * 1024) return String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024 * 1024));
        return String.format(Locale.US, "%d MB", bytes / (1024 * 1024));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_STORAGE && pendingDownload != null) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startDownload(pendingDownload);
            else Toast.makeText(this, R.string.storage_needed, Toast.LENGTH_LONG).show();
            pendingDownload = null;
        }
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }
}
