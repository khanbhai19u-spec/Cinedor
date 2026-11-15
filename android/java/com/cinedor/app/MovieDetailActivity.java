package com.cinedor.app;

import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Title page: artwork, "2026 · ★ 8.3 · 8 Episodes · TV-14", Play + My List + Download,
 * description, genres, and Episodes / For you / Cast tabs.
 */
public class MovieDetailActivity extends AppCompatActivity {

    public static final String EXTRA_VIDEO_ID = "video_id";
    public static final String EXTRA_VIDEO_JSON = "video_json";

    private static final int TAB_EPISODES = 0, TAB_FOR_YOU = 1, TAB_CAST = 2;

    private Video video;                 // what the header shows (TMDB or your own title)
    private Video own;                   // your own playable title, if any
    private Tmdb.Detail detail;          // TMDB extras (cast, recommendations, seasons)
    private List<Tmdb.Episode> seasonEpisodes = new ArrayList<>();
    private int season = 1;
    private int tab = -1;

    private View progress;
    private TextView tvTitle, tvMeta, tvDesc, tvGenres, tvNote, seasonPicker;
    private ImageView poster;
    private ImageButton btnFavorite, btnSecond;
    private MaterialButton btnWatch;
    private LinearLayout tabs, tabContent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ApiConfig.init(this);
        Tmdb.init(this);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setBackgroundDrawable(Ui.appBackground(this));
        setContentView(R.layout.activity_movie_detail);

        progress = findViewById(R.id.progress);
        tvTitle = findViewById(R.id.tvTitle);
        tvMeta = findViewById(R.id.tvMeta);
        tvDesc = findViewById(R.id.tvDesc);
        tvGenres = findViewById(R.id.tvGenres);
        tvNote = findViewById(R.id.tvNote);
        seasonPicker = findViewById(R.id.seasonPicker);
        poster = findViewById(R.id.poster);
        btnFavorite = findViewById(R.id.btnFavorite);
        btnSecond = findViewById(R.id.btnSecond);
        btnWatch = findViewById(R.id.btnWatch);
        tabs = findViewById(R.id.tabs);
        tabContent = findViewById(R.id.tabContent);

        View posterBox = findViewById(R.id.posterBox);
        posterBox.getLayoutParams().height = Math.round(getResources().getDisplayMetrics().heightPixels * (Grid.wide(this) ? 0.7f : 0.42f));
        posterBox.setLayoutParams(posterBox.getLayoutParams());

        View back = findViewById(R.id.btnBack);
        ViewGroup.MarginLayoutParams blp = (ViewGroup.MarginLayoutParams) back.getLayoutParams();
        blp.topMargin = Ui.dp(this, 12) + Ui.statusBarHeight(this);
        back.setLayoutParams(blp);
        ViewCompat.setOnApplyWindowInsetsListener(back, (v, insets) -> {
            Insets b = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            lp.topMargin = Ui.dp(this, 12) + Math.max(b.top, Ui.statusBarHeight(this));
            v.setLayoutParams(lp);
            return insets;
        });
        back.setOnClickListener(v -> finish());

        btnFavorite.setOnClickListener(v -> {
            if (video == null) return;
            boolean on = Store.toggleFavorite(this, video);
            updateFavorite();
            Toast.makeText(this, on ? "Added to My List" : "Removed from My List", Toast.LENGTH_SHORT).show();
        });
        btnWatch.setOnClickListener(v -> watch());
        seasonPicker.setOnClickListener(v -> pickSeason());

        String id = getIntent().getStringExtra(EXTRA_VIDEO_ID);
        Video found = id != null ? Catalog.find(id) : null;
        if (found == null) {
            try {
                String json = getIntent().getStringExtra(EXTRA_VIDEO_JSON);
                if (json != null) found = new Video(new JSONObject(json));
            } catch (Exception ignored) {}
        }
        final Video cached = found;
        if (cached == null && id == null) { finish(); return; }
        if (cached != null) {
            if (!cached.isTmdb()) own = cached;
            show(cached);
        }

        progress.setVisibility(cached == null ? View.VISIBLE : View.GONE);
        final String tmdbId = id != null && id.startsWith("tmdb:") ? id
                : cached != null && cached.tmdbId > 0 ? "tmdb:" + (cached.isSeries() ? "tv" : "movie") + ":" + cached.tmdbId : null;
        Catalog.load(false, (items, e1) -> {
            if (own == null && cached != null && cached.isTmdb() && !cached.inLibrary.isEmpty()) own = Catalog.find(cached.inLibrary);
            if (tmdbId == null) {
                progress.setVisibility(View.GONE);
                if (video != null) show(video);
                return;
            }
            Tmdb.detail(tmdbId, (d, error) -> {
                progress.setVisibility(View.GONE);
                if (d == null) {
                    if (video == null) Toast.makeText(this, error != null ? error : "Not found", Toast.LENGTH_LONG).show();
                    else show(video);
                    return;
                }
                detail = d;
                if (own == null && !d.video.inLibrary.isEmpty()) own = Catalog.find(d.video.inLibrary);
                show(own != null && video != null && !video.isTmdb() ? video : d.video);
                if (!d.seasonNumbers.isEmpty() && (own == null || own.episodes.isEmpty())) loadSeason(d.seasonNumbers.get(0));
            });
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (video != null) { updateWatchButton(); updateFavorite(); }
    }

    // ---------- header ----------
    private void show(Video v) {
        video = v;
        String art = v.backdrop.isEmpty() ? v.portrait() : v.backdrop;
        Ui.load(poster, art);
        tvTitle.setText(v.title);

        List<String> meta = new ArrayList<>();
        if (!v.year.isEmpty()) meta.add(v.year);
        if (v.rating > 0) meta.add(v.ratingText());
        if (v.isSeries()) {
            long eps = own != null && !own.episodes.isEmpty() ? own.episodes.size() : v.episodeCount;
            if (eps > 0) meta.add(eps + (eps == 1 ? " Episode" : " Episodes"));
            else if (v.seasons > 0) meta.add(v.seasons + (v.seasons == 1 ? " Season" : " Seasons"));
        } else if (v.runtime > 0) {
            meta.add((v.runtime / 60 > 0 ? (v.runtime / 60) + "h " : "") + (v.runtime % 60) + "m");
        }
        if (detail != null && !detail.certification.isEmpty()) meta.add(detail.certification);
        tvMeta.setText(android.text.TextUtils.join("  ·  ", meta));

        tvDesc.setText(v.description);
        tvDesc.setVisibility(v.description.isEmpty() ? View.GONE : View.VISIBLE);
        String genres = v.genre.replace(" • ", ", ");
        tvGenres.setText(genres.isEmpty() ? "" : "Genres: " + genres);
        tvGenres.setVisibility(genres.isEmpty() ? View.GONE : View.VISIBLE);

        updateWatchButton();
        updateFavorite();
        buildTabs();
    }

    /** Play your own file when there is one; otherwise the trailer (TMDB has no video files). */
    private void updateWatchButton() {
        boolean canPlay = own != null && (!own.qualities.isEmpty() || !own.episodes.isEmpty());
        if (canPlay) {
            Store.Entry last = Store.lastWatched(this, own.id);
            btnWatch.setText(last != null && last.unfinished() ? "Resume" : getString(R.string.play));
            btnWatch.setEnabled(true);
            btnWatch.setAlpha(1f);
            tvNote.setVisibility(View.GONE);
        } else {
            btnWatch.setText(video.trailer.isEmpty() ? getString(R.string.play) : "Trailer");
            boolean nothing = video.trailer.isEmpty();
            btnWatch.setEnabled(!nothing || detail == null);
            btnWatch.setAlpha(nothing && detail != null ? 0.5f : 1f);
        }

        boolean canDownload = false;
        if (own != null) for (Video.Quality q : own.qualities) if (q.downloadable()) canDownload = true;
        if (canDownload) {
            btnSecond.setImageResource(R.drawable.ic_download);
            btnSecond.setContentDescription(getString(R.string.download));
            btnSecond.setOnClickListener(x -> Playback.download(this, own, null));
        } else {
            btnSecond.setImageResource(R.drawable.ic_send);
            btnSecond.setContentDescription("Share");
            btnSecond.setOnClickListener(x -> share());
        }
    }

    private void watch() {
        if (video == null) return;
        if (own != null && (!own.qualities.isEmpty() || !own.episodes.isEmpty())) {
            Playback.playDefault(this, own);
        } else if (!video.trailer.isEmpty()) {
            openTrailer(video.trailer);
        } else {
            Toast.makeText(this, "No trailer for this title yet", Toast.LENGTH_SHORT).show();
        }
    }

    private void openTrailer(String key) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=" + key))); }
        catch (Exception e) { Toast.makeText(this, "No app can open the trailer", Toast.LENGTH_SHORT).show(); }
    }

    private void share() {
        String url = video.tmdbId > 0 ? "https://www.themoviedb.org/" + (video.isSeries() ? "tv/" : "movie/") + video.tmdbId : "";
        Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, video.title + (video.year.isEmpty() ? "" : " (" + video.year + ")") + (url.isEmpty() ? "" : "\n" + url));
        startActivity(Intent.createChooser(i, "Share"));
    }

    private void updateFavorite() {
        boolean fav = video != null && Store.isFavorite(this, video.id);
        btnFavorite.setImageResource(fav ? R.drawable.ic_check : R.drawable.ic_add);
    }

    // ---------- tabs ----------
    private boolean hasEpisodes() {
        return (own != null && !own.episodes.isEmpty()) || (detail != null && !detail.seasonNumbers.isEmpty());
    }

    private boolean hasForYou() { return detail != null && !detail.recommendations.isEmpty(); }
    private boolean hasCast() { return detail != null && !detail.cast.isEmpty(); }

    private void buildTabs() {
        tabs.removeAllViews();
        int[] ids = {TAB_EPISODES, TAB_FOR_YOU, TAB_CAST};
        String[] names = {getString(R.string.episodes), "For you", "Cast"};
        boolean[] on = {hasEpisodes(), hasForYou(), hasCast()};
        if (tab < 0 || !on[tab]) {
            tab = -1;
            for (int i = 0; i < 3; i++) if (on[i]) { tab = ids[i]; break; }
        }
        for (int i = 0; i < 3; i++) {
            if (!on[i]) continue;
            final int t = ids[i];
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            int ph = Ui.dp(this, 16);
            col.setPadding(ph, Ui.dp(this, 6), ph, 0);
            TextView tv = new TextView(this);
            tv.setText(names[i]);
            tv.setTextSize(17);
            tv.setTextColor(t == tab ? 0xFFFFFFFF : 0xFF9A9DA8);
            if (t == tab) tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
            col.addView(tv);
            View line = new View(this);
            line.setBackground(Ui.round(t == tab ? 0xFFFFFFFF : 0x00000000, Ui.dp(this, 2)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 3));
            lp.topMargin = Ui.dp(this, 8);
            col.addView(line, lp);
            col.setOnClickListener(v -> { tab = t; buildTabs(); });
            tabs.addView(col);
        }
        tabs.setVisibility(tab < 0 ? View.GONE : View.VISIBLE);
        renderTab();
    }

    private void renderTab() {
        tabContent.removeAllViews();
        seasonPicker.setVisibility(View.GONE);
        if (tab == TAB_EPISODES) renderEpisodes();
        else if (tab == TAB_FOR_YOU) renderForYou();
        else if (tab == TAB_CAST) renderCast();
    }

    // ----- Episodes -----
    private void renderEpisodes() {
        LayoutInflater inf = LayoutInflater.from(this);
        if (own != null && !own.episodes.isEmpty()) {
            Store.Entry last = Store.lastWatched(this, own.id);
            int n = 1;
            for (Video.Episode ep : own.episodes) {
                View row = inf.inflate(R.layout.item_episode, tabContent, false);
                Ui.load(row.findViewById(R.id.epThumb), own.backdrop.isEmpty() ? own.portrait() : own.backdrop);
                ((TextView) row.findViewById(R.id.epTitle)).setText(n++ + ". " + ep.title);
                String info = ep.qualities.isEmpty() ? "" : ep.qualities.get(0).label;
                if (last != null && ep.id.equals(last.epId)) info += last.unfinished() ? "  ·  at " + Ui.time(last.pos) : "  ·  watched";
                ((TextView) row.findViewById(R.id.epInfo)).setText(info);
                row.findViewById(R.id.epOverview).setVisibility(View.GONE);
                boolean dl = false;
                for (Video.Quality q : ep.qualities) if (q.downloadable()) dl = true;
                View dlBtn = row.findViewById(R.id.epDownload);
                dlBtn.setVisibility(dl ? View.VISIBLE : View.INVISIBLE);
                dlBtn.setOnClickListener(x -> Playback.download(this, own, ep));
                row.findViewById(R.id.epRow).setOnClickListener(x -> {
                    if (last != null && ep.id.equals(last.epId) && last.unfinished()) Playback.resume(this, last);
                    else Playback.play(this, own, ep);
                });
                tabContent.addView(row);
            }
            return;
        }
        if (detail == null || detail.seasonNumbers.isEmpty()) return;
        if (detail.seasonNumbers.size() > 1) {
            seasonPicker.setText("Season " + season + "  ▾");
            seasonPicker.setVisibility(View.VISIBLE);
        }
        if (seasonEpisodes.isEmpty()) {
            TextView loading = new TextView(this);
            loading.setText("Loading episodes…");
            loading.setTextColor(0xFF9A9DA8);
            loading.setPadding(0, Ui.dp(this, 16), 0, 0);
            tabContent.addView(loading);
            return;
        }
        for (Tmdb.Episode ep : seasonEpisodes) {
            View row = inf.inflate(R.layout.item_episode, tabContent, false);
            Ui.load(row.findViewById(R.id.epThumb), ep.still);
            row.findViewById(R.id.epPlay).setVisibility(View.GONE);
            row.findViewById(R.id.epDownload).setVisibility(View.GONE);
            ((TextView) row.findViewById(R.id.epTitle)).setText(ep.number + ". " + ep.name);
            ((TextView) row.findViewById(R.id.epInfo)).setText(ep.runtime > 0 ? ep.runtime + "m" : "");
            TextView ov = row.findViewById(R.id.epOverview);
            ov.setText(ep.overview);
            ov.setVisibility(ep.overview.isEmpty() ? View.GONE : View.VISIBLE);
            tabContent.addView(row);
        }
    }

    private void loadSeason(int number) {
        season = number;
        seasonEpisodes = new ArrayList<>();
        if (tab == TAB_EPISODES) renderTab();
        Tmdb.season(detail.video.tmdbId, number, (list, error) -> {
            if (season != number) return;
            seasonEpisodes = new ArrayList<>(list);
            if (tab == TAB_EPISODES) renderTab();
        });
    }

    private void pickSeason() {
        if (detail == null) return;
        String[] names = new String[detail.seasonNumbers.size()];
        int sel = 0;
        for (int i = 0; i < names.length; i++) {
            names[i] = "Season " + detail.seasonNumbers.get(i);
            if (detail.seasonNumbers.get(i) == season) sel = i;
        }
        new AlertDialog.Builder(this).setTitle("Season")
                .setSingleChoiceItems(names, sel, (d, w) -> { d.dismiss(); loadSeason(detail.seasonNumbers.get(w)); })
                .show();
    }

    // ----- For you -----
    private void renderForYou() {
        RecyclerView rv = new RecyclerView(this);
        rv.setNestedScrollingEnabled(false);
        int cols = Grid.columns(this);
        rv.setLayoutManager(new GridLayoutManager(this, cols));
        int cardW = (Ui.screenWidth(this) - Ui.dp(this, 40) - Ui.dp(this, 12) * cols) / cols;
        PosterAdapter a = new PosterAdapter(cardW, Math.round(cardW * 1.45f), true, it ->
                startActivity(new Intent(this, MovieDetailActivity.class)
                        .putExtra(EXTRA_VIDEO_ID, it.video.id)
                        .putExtra(EXTRA_VIDEO_JSON, it.video.toJson().toString())));
        a.submit(PosterAdapter.wrap(detail.recommendations));
        rv.setAdapter(a);
        rv.setPadding(0, Ui.dp(this, 8), 0, 0);
        tabContent.addView(rv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    // ----- Cast -----
    private void renderCast() {
        for (Tmdb.Person p : detail.cast) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
            com.google.android.material.imageview.ShapeableImageView img = new com.google.android.material.imageview.ShapeableImageView(this);
            img.setShapeAppearanceModel(img.getShapeAppearanceModel().withCornerSize(Ui.dp(this, 28)));
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setBackground(Ui.round(0xFF1E1F25, Ui.dp(this, 28)));
            Ui.load(img, p.photo);
            row.addView(img, new LinearLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 56)));
            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setPadding(Ui.dp(this, 14), 0, 0, 0);
            TextView name = new TextView(this);
            name.setText(p.name);
            name.setTextColor(0xFFF4F4F6);
            name.setTextSize(16);
            name.setTypeface(name.getTypeface(), Typeface.BOLD);
            texts.addView(name);
            if (!p.role.isEmpty()) {
                TextView role = new TextView(this);
                role.setText(p.role);
                role.setTextColor(0xFF9A9DA8);
                role.setTextSize(13);
                texts.addView(role);
            }
            row.addView(texts);
            tabContent.addView(row);
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
