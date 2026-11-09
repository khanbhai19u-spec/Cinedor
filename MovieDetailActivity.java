package com.cinedor.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Shows TMDB video options fetched dynamically from Python Server; Watch / Download open quality sheet. */
public class MovieDetailActivity extends AppCompatActivity {

    public static final String EXTRA_VIDEO_ID = "video_id";
    private static final int REQ_STORAGE = 41;

    /** One playable file format returned by Python Server. */
    static class Quality {
        final String label, url, type;
        final long size;
        final boolean downloadable;

        Quality(JSONObject o) {
            label = o.optString("quality", "HD / Auto");
            url = o.optString("url");
            type = o.optString("ext", "mp4");
            size = 0; // External streams don't return raw byte size
            downloadable = true; // All mp4 formats can be downloaded
        }
    }

    private final ExecutorService io = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Quality> qualities = new ArrayList<>();
    private String title = "";
    private Quality pendingDownload;

    private ProgressBar progress;
    private TextView tvTitle, tvMeta, tvDesc;
    private ImageView poster;
    private Button btnWatch, btnDownload;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_movie_detail);
        progress = findViewById(R.id.progress);
        tvTitle = findViewById(R.id.tvTitle);
        tvMeta = findViewById(R.id.tvMeta);
        tvDesc = findViewById(R.id.tvDesc);
        poster = findViewById(R.id.poster);
        btnWatch = findViewById(R.id.btnWatch);
        btnDownload = findViewById(R.id.btnDownload);

        btnWatch.setOnClickListener(v -> showQualitySheet(true));
        btnDownload.setOnClickListener(v -> showQualitySheet(false));

        String tmdbId = getIntent().getStringExtra(EXTRA_VIDEO_ID);
        if (tmdbId == null || tmdbId.isEmpty()) { 
            finish(); 
            return; 
        }
        loadVideo(tmdbId);
    }

    private void loadVideo(String tmdbId) {
        setLoading(true);
        io.execute(() -> {
            try {
                // Python Server-এর API endpoint: /movie?id=TMDB_ID
                String jsonStr = ApiConfig.get("/movie?id=" + tmdbId);
                JSONObject json = new JSONObject(jsonStr);

                if (!"success".equalsIgnoreCase(json.optString("status"))) {
                    throw new Exception("Movie stream links not found");
                }

                title = json.optString("title", "Movie " + tmdbId);
                String posterUrl = json.optString("thumbnail", "");
                JSONArray arr = json.getJSONArray("formats");

                List<Quality> list = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    list.add(new Quality(arr.getJSONObject(i)));
                }

                main.post(() -> {
                    qualities.clear();
                    qualities.addAll(list);
                    tvTitle.setText(title);
                    tvMeta.setText("TMDB ID: " + tmdbId + "  •  " + list.size() + " stream options");
                    tvDesc.setText("Ready to stream in high quality");
                    tvDesc.setVisibility(View.VISIBLE);
                    setLoading(false);
                });

                if (!posterUrl.isEmpty() && !"null".equals(posterUrl)) {
                    loadPoster(posterUrl);
                }

            } catch (Exception e) {
                main.post(() -> {
                    setLoading(false);
                    Toast.makeText(this, "Error fetching links: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void loadPoster(String url) {
        io.execute(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                if (!ApiConfig.APP_TOKEN.isEmpty()) c.setRequestProperty("X-App-Token", ApiConfig.APP_TOKEN);
                try (InputStream in = c.getInputStream()) {
                    Bitmap b = BitmapFactory.decodeStream(in);
                    if (b != null) main.post(() -> poster.setImageBitmap(b));
                }
            } catch (Exception ignored) {}
        });
    }

    private void setLoading(boolean on) {
        progress.setVisibility(on ? View.VISIBLE : View.GONE);
        btnWatch.setEnabled(!on);
        btnDownload.setEnabled(!on);
    }

    /** Bottom sheet listing stream/download qualities. */
    private void showQualitySheet(boolean watchMode) {
        List<Quality> options = new ArrayList<>();
        for (Quality q : qualities) {
            if (watchMode || q.downloadable) options.add(q);
        }
        
        if (options.isEmpty()) {
            Toast.makeText(this, watchMode ? "No playable stream" : "No download options available", Toast.LENGTH_LONG).show();
            return;
        }

        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View root = LayoutInflater.from(this).inflate(R.layout.sheet_quality, null);
        ((TextView) root.findViewById(R.id.sheetTitle)).setText(watchMode ? R.string.select_watch : R.string.select_download);
        
        LinearLayout box = root.findViewById(R.id.sheetList);
        for (Quality q : options) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_quality, box, false);
            ((TextView) row.findViewById(R.id.qLabel)).setText(q.label);
            ((TextView) row.findViewById(R.id.qInfo)).setText(q.type.toUpperCase(Locale.US) + " • Direct Link");
            
            row.setOnClickListener(v -> {
                sheet.dismiss();
                if (watchMode) play(q); else startDownload(q);
            });
            box.addView(row);
        }
        sheet.setContentView(root);
        sheet.show();
    }

    private void play(Quality q) {
        Intent i = new Intent(this, PlayerActivity.class);
        i.putExtra(PlayerActivity.EXTRA_URL, q.url);
        i.putExtra(PlayerActivity.EXTRA_TYPE, q.type);
        i.putExtra(PlayerActivity.EXTRA_TITLE, title + " • " + q.label);
        startActivity(i);
    }

    private void startDownload(Quality q) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
                && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingDownload = q;
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
            return;
        }
        long id = VideoDownloader.download(this, q.url, title, q.label);
        if (id > 0) Toast.makeText(this, "Download started (" + q.label + ")", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_STORAGE && pendingDownload != null) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startDownload(pendingDownload);
            else Toast.makeText(this, "Storage permission needed to download", Toast.LENGTH_LONG).show();
            pendingDownload = null;
        }
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }
}
