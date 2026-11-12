package com.cinedor.app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;

import org.json.JSONObject;

/** Title page: artwork, Play / Resume, Download, favorite, episodes. */
public class MovieDetailActivity extends AppCompatActivity {

    public static final String EXTRA_VIDEO_ID = "video_id";
    public static final String EXTRA_VIDEO_JSON = "video_json";

    private Video video;
    private View progress;
    private TextView tvTitle, tvMeta, tvGenre, tvDesc, tvEpisodes, tvNote, tvTagline, tvCast;
    private ImageView poster;
    private ImageButton btnFavorite;
    private MaterialButton btnWatch, btnDownload, btnTrailer;
    private LinearLayout episodes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ApiConfig.init(this);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setBackgroundDrawable(Ui.appBackground(this));
        setContentView(R.layout.activity_movie_detail);

        progress = findViewById(R.id.progress);
        tvTitle = findViewById(R.id.tvTitle);
        tvMeta = findViewById(R.id.tvMeta);
        tvGenre = findViewById(R.id.tvGenre);
        tvDesc = findViewById(R.id.tvDesc);
        tvEpisodes = findViewById(R.id.tvEpisodes);
        poster = findViewById(R.id.poster);
        btnFavorite = findViewById(R.id.btnFavorite);
        btnWatch = findViewById(R.id.btnWatch);
        btnDownload = findViewById(R.id.btnDownload);
        episodes = findViewById(R.id.episodes);
        btnTrailer = findViewById(R.id.btnTrailer);
        tvNote = findViewById(R.id.tvNote);
        tvTagline = findViewById(R.id.tvTagline);
        tvCast = findViewById(R.id.tvCast);

        // Artwork height adapts to the screen (shorter in landscape)
        View posterBox = findViewById(R.id.posterBox);
        posterBox.getLayoutParams().height = Math.round(getResources().getDisplayMetrics().heightPixels * (Grid.wide(this) ? 0.8f : 0.6f));
        posterBox.setLayoutParams(posterBox.getLayoutParams());

        btnTrailer.setOnClickListener(v -> {
            if (video == null || video.trailer.isEmpty()) return;
            android.net.Uri uri = android.net.Uri.parse("https://www.youtube.com/watch?v=" + video.trailer);
            try { startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, uri)); }
            catch (Exception e) { Toast.makeText(this, "No app can open the trailer", Toast.LENGTH_SHORT).show(); }
        });

        View top = findViewById(R.id.topBar);
        ViewCompat.setOnApplyWindowInsetsListener(top, (v, insets) -> {
            Insets b = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int p = Ui.dp(this, 16);
            v.setPadding(p + b.left, p + b.top, p + b.right, p);
            return insets;
        });

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        btnFavorite.setOnClickListener(v -> {
            if (video == null) return;
            boolean on = Store.toggleFavorite(this, video);
            updateFavorite();
            Toast.makeText(this, on ? "Added to favorites" : "Removed from favorites", Toast.LENGTH_SHORT).show();
        });
        btnWatch.setOnClickListener(v -> {
            if (video == null) return;
            if (video.isTmdb()) {
                Video own = Catalog.find(video.inLibrary);
                if (own != null) Playback.playDefault(this, own);
            } else {
                Playback.playDefault(this, video);
            }
        });
        btnDownload.setOnClickListener(v -> {
            if (video == null) return;
            if (video.isSeries() && video.qualities.isEmpty()) {
                Toast.makeText(this, "Tap the download icon next to an episode", Toast.LENGTH_SHORT).show();
            } else {
                Playback.download(this, video, null);
            }
        });

        String id = getIntent().getStringExtra(EXTRA_VIDEO_ID);
        Video cached = id != null ? Catalog.find(id) : null;
        if (cached == null) {
            try {
                String json = getIntent().getStringExtra(EXTRA_VIDEO_JSON);
                if (json != null) cached = new Video(new JSONObject(json));
            } catch (Exception ignored) {}
        }
        if (cached != null) show(cached);
        if (id == null) { if (cached == null) finish(); return; }

        if (id.startsWith("tmdb:")) {
            progress.setVisibility(View.VISIBLE);
            Catalog.load(false, (items, e1) -> Tmdb.detail(id, (v, error) -> {
                progress.setVisibility(View.GONE);
                if (v != null) show(v);
                else Toast.makeText(this, error != null ? error : "Not found", Toast.LENGTH_LONG).show();
            }));
            return;
        }

        // Refresh from the server so new episodes / qualities show up
        progress.setVisibility(cached == null ? View.VISIBLE : View.GONE);
        final boolean hadCache = cached != null;
        Catalog.load(!hadCache, (items, error) -> {
            progress.setVisibility(View.GONE);
            Video fresh = Catalog.find(id);
            if (fresh != null) show(fresh);
            else if (!hadCache) {
                Toast.makeText(this, error != null ? getString(R.string.server_error, error) : "Title not found", Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (video != null) show(video);
    }

    private void show(Video v) {
        video = v;
        Ui.load(poster, v.portrait());
        tvGenre.setText(v.genre);
        tvGenre.setVisibility(v.genre.isEmpty() ? View.GONE : View.VISIBLE);
        tvTitle.setText(v.title);
        String meta = v.meta();
        if (v.isSeries() && !v.episodes.isEmpty()) meta += "  •  " + v.episodes.size() + " episodes";
        if (v.runtime > 0) meta += "  •  " + (v.isSeries() ? v.runtime + " min/ep" : (v.runtime / 60) + "h " + (v.runtime % 60) + "m");
        if (v.seasons > 0) meta += "  •  " + v.seasons + (v.seasons == 1 ? " season" : " seasons");
        if (v.rating > 0) meta += "  •  " + v.ratingText();
        tvMeta.setText(meta);
        btnTrailer.setVisibility(v.trailer.isEmpty() ? View.GONE : View.VISIBLE);
        tvTagline.setText(v.tagline.isEmpty() ? "" : "“" + v.tagline + "”");
        tvTagline.setVisibility(v.tagline.isEmpty() ? View.GONE : View.VISIBLE);
        tvCast.setText(v.cast.isEmpty() ? "" : "Cast: " + v.cast);
        tvCast.setVisibility(v.cast.isEmpty() ? View.GONE : View.VISIBLE);

        if (v.isTmdb()) {
            Video own = v.inLibrary.isEmpty() ? null : Catalog.find(v.inLibrary);
            btnWatch.setVisibility(own != null ? View.VISIBLE : View.GONE);
            btnWatch.setText("Play from your library");
            btnDownload.setVisibility(View.GONE);
            tvNote.setVisibility(own != null ? View.GONE : View.VISIBLE);
            updateFavorite();
            episodes.removeAllViews();
            tvEpisodes.setVisibility(View.GONE);
            return;
        }
        btnWatch.setVisibility(View.VISIBLE);
        tvNote.setVisibility(View.GONE);
        tvDesc.setText(v.description);
        tvDesc.setVisibility(v.description.isEmpty() ? View.GONE : View.VISIBLE);

        Store.Entry last = Store.lastWatched(this, v.id);
        if (last != null && last.unfinished()) {
            String which = last.epTitle.isEmpty() ? "" : last.epTitle + " • ";
            btnWatch.setText("Resume  " + which + Ui.time(last.pos));
        } else {
            btnWatch.setText(R.string.play);
        }
        boolean canDownload = false;
        for (Video.Quality q : v.qualities) if (q.downloadable()) canDownload = true;
        btnDownload.setVisibility(canDownload ? View.VISIBLE : View.GONE);
        updateFavorite();
        buildEpisodes(last);
    }

    private void updateFavorite() {
        boolean fav = video != null && Store.isFavorite(this, video.id);
        btnFavorite.setImageResource(fav ? R.drawable.ic_bookmark : R.drawable.ic_bookmark_border);
    }

    private void buildEpisodes(Store.Entry last) {
        episodes.removeAllViews();
        tvEpisodes.setVisibility(video.episodes.isEmpty() ? View.GONE : View.VISIBLE);
        LayoutInflater inf = LayoutInflater.from(this);
        int n = 1;
        for (Video.Episode ep : video.episodes) {
            View row = inf.inflate(R.layout.item_episode, episodes, false);
            ((TextView) row.findViewById(R.id.epNumber)).setText(String.valueOf(n++));
            ((TextView) row.findViewById(R.id.epTitle)).setText(ep.title);
            StringBuilder info = new StringBuilder();
            for (Video.Quality q : ep.qualities) {
                if (info.length() > 0) info.append(" · ");
                info.append(q.label);
            }
            if (last != null && ep.id.equals(last.epId)) {
                info.append(last.unfinished() ? "  •  at " + Ui.time(last.pos) : "  •  watched");
            }
            ((TextView) row.findViewById(R.id.epInfo)).setText(info);
            boolean dl = false;
            for (Video.Quality q : ep.qualities) if (q.downloadable()) dl = true;
            View dlBtn = row.findViewById(R.id.epDownload);
            dlBtn.setVisibility(dl ? View.VISIBLE : View.INVISIBLE);
            dlBtn.setOnClickListener(x -> Playback.download(this, video, ep));
            row.setOnClickListener(x -> {
                if (last != null && ep.id.equals(last.epId) && last.unfinished()) Playback.resume(this, last);
                else Playback.play(this, video, ep);
            });
            episodes.addView(row);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == Playback.REQ_STORAGE && (results.length == 0 || results[0] != 0)) {
            Toast.makeText(this, "Storage permission needed to download", Toast.LENGTH_LONG).show();
        }
    }
}
