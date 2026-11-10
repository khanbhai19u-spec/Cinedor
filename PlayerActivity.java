package com.cinedor.app;

import android.os.Bundle;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

import java.util.HashMap;
import java.util.Map;

/** Full-screen Media3 ExoPlayer for MP4 and HLS (.m3u8). Remembers & resumes the position automatically. */
@OptIn(markerClass = UnstableApi.class)
public class PlayerActivity extends AppCompatActivity {

    public static final String EXTRA_URL = "video_url";
    public static final String EXTRA_TYPE = "video_type";   // "mp4" or "hls"
    public static final String EXTRA_TITLE = "video_title";
    public static final String EXTRA_MOVIE_ID = "movie_id";
    public static final String EXTRA_MOVIE_TITLE = "movie_title";
    public static final String EXTRA_MOVIE_YEAR = "movie_year";

    private static final String KEY_POS = "pos", KEY_PLAY = "play";

    private PlayerView playerView;
    private ExoPlayer player;
    private String url, type;
    private long position = 0L;
    private boolean playWhenReady = true;
    private MovieApi.Movie movie;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        playerView = findViewById(R.id.playerView);
        TextView tvTitle = findViewById(R.id.tvPlayerTitle);

        url = getIntent().getStringExtra(EXTRA_URL);
        type = getIntent().getStringExtra(EXTRA_TYPE);
        tvTitle.setText(getIntent().getStringExtra(EXTRA_TITLE));
        if (url == null) { finish(); return; }

        String movieId = getIntent().getStringExtra(EXTRA_MOVIE_ID);
        if (movieId != null) {
            movie = new MovieApi.Movie(movieId, getIntent().getStringExtra(EXTRA_MOVIE_TITLE),
                    getIntent().getStringExtra(EXTRA_MOVIE_YEAR));
        }

        if (savedInstanceState != null) {
            position = savedInstanceState.getLong(KEY_POS, 0L);
            playWhenReady = savedInstanceState.getBoolean(KEY_PLAY, true);
        } else {
            // Auto-resume from where you stopped last time
            position = Store.resumePosition(this, url);
            if (position > 0) Toast.makeText(this, R.string.resuming, Toast.LENGTH_SHORT).show();
        }
        // Title bar shows/hides together with the player controls
        playerView.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener) v -> tvTitle.setVisibility(v));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemBars();
    }

    private void initializePlayer() {
        if (player != null) return;

        Map<String, String> headers = new HashMap<>();
        if (!ApiConfig.APP_TOKEN.isEmpty()) headers.put("X-App-Token", ApiConfig.APP_TOKEN);

        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent("CinedorApp/1.0")
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(20_000)
                .setDefaultRequestProperties(headers);
        DefaultDataSource.Factory dataSource = new DefaultDataSource.Factory(this, http);

        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(dataSource))
                .setSeekBackIncrementMs(10_000)
                .setSeekForwardIncrementMs(10_000)
                .build();
        playerView.setPlayer(player);

        MediaItem.Builder item = new MediaItem.Builder().setUri(url);
        if ("hls".equals(type) || url.toLowerCase().contains(".m3u8")) item.setMimeType(MimeTypes.APPLICATION_M3U8);
        else item.setMimeType(MimeTypes.VIDEO_MP4);

        player.setMediaItem(item.build());
        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                Toast.makeText(PlayerActivity.this, "Playback error: " + error.getErrorCodeName(), Toast.LENGTH_LONG).show();
            }

            @Override
            public void onPlaybackStateChanged(int state) {
                // Movie finished → remove from Continue Watching and close automatically
                if (state == Player.STATE_ENDED && player != null) {
                    Store.saveProgress(PlayerActivity.this, movie, url, player.getDuration(), player.getDuration());
                    finish();
                }
            }
        });
        player.seekTo(position);
        player.setPlayWhenReady(playWhenReady);
        player.prepare();
    }

    private void releasePlayer() {
        if (player == null) return;
        position = player.getCurrentPosition();
        playWhenReady = player.getPlayWhenReady();
        Store.saveProgress(this, movie, url, position, player.getDuration());   // for "Continue Watching"
        playerView.setPlayer(null);
        player.release();
        player = null;
    }

    private void hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat c = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        c.hide(WindowInsetsCompat.Type.systemBars());
        c.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    // minSdk 24+: start/stop is the right place (multi-window safe)
    @Override protected void onStart() { super.onStart(); initializePlayer(); playerView.onResume(); }
    @Override protected void onResume() { super.onResume(); hideSystemBars(); }
    @Override protected void onStop() { playerView.onPause(); releasePlayer(); super.onStop(); }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        if (player != null) {
            position = player.getCurrentPosition();
            playWhenReady = player.getPlayWhenReady();
        }
        out.putLong(KEY_POS, position);
        out.putBoolean(KEY_PLAY, playWhenReady);
        super.onSaveInstanceState(out);
    }
}
