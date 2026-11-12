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
import androidx.media3.common.C;
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

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/** Full-screen Media3 ExoPlayer for MP4 and HLS (.m3u8); remembers where you stopped. */
@OptIn(markerClass = UnstableApi.class)
public class PlayerActivity extends AppCompatActivity {

    public static final String EXTRA_URL = "video_url";
    public static final String EXTRA_TYPE = "video_type";   // "mp4" or "hls"
    public static final String EXTRA_TITLE = "video_title";
    public static final String EXTRA_LABEL = "video_label";
    public static final String EXTRA_VIDEO_JSON = "video_json";
    public static final String EXTRA_EP_ID = "ep_id";
    public static final String EXTRA_EP_TITLE = "ep_title";
    public static final String EXTRA_START = "start_ms";

    private static final String KEY_POS = "pos", KEY_PLAY = "play";

    private PlayerView playerView;
    private ExoPlayer player;
    private String url, type;
    private long position = 0L;
    private boolean playWhenReady = true;

    private Video video;
    private Video.Quality quality;
    private String epId, epTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ApiConfig.init(this);
        setContentView(R.layout.activity_player);
        playerView = findViewById(R.id.playerView);
        TextView tvTitle = findViewById(R.id.tvPlayerTitle);

        url = getIntent().getStringExtra(EXTRA_URL);
        type = getIntent().getStringExtra(EXTRA_TYPE);
        tvTitle.setText(getIntent().getStringExtra(EXTRA_TITLE));
        if (url == null) { finish(); return; }

        epId = getIntent().getStringExtra(EXTRA_EP_ID);
        epTitle = getIntent().getStringExtra(EXTRA_EP_TITLE);
        try {
            String vj = getIntent().getStringExtra(EXTRA_VIDEO_JSON);
            if (vj != null) video = new Video(new JSONObject(vj));
            quality = new Video.Quality(new JSONObject()
                    .put("label", getIntent().getStringExtra(EXTRA_LABEL))
                    .put("url", url).put("type", type == null ? "" : type));
        } catch (Exception ignored) {}

        position = getIntent().getLongExtra(EXTRA_START, 0L);
        if (savedInstanceState != null) {
            position = savedInstanceState.getLong(KEY_POS, position);
            playWhenReady = savedInstanceState.getBoolean(KEY_PLAY, true);
        }
        // Title bar shows/hides together with the player controls
        playerView.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener) v -> tvTitle.setVisibility(v));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemBars();
        if (position > 0) Toast.makeText(this, "Resuming from " + Ui.time(position), Toast.LENGTH_SHORT).show();
    }

    private void initializePlayer() {
        if (player != null) return;

        Map<String, String> headers = new HashMap<>();
        if (ApiConfig.needsToken(url)) headers.put("X-App-Token", ApiConfig.token());

        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent("CinedorApp/2.0")
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(20_000)
                .setAllowCrossProtocolRedirects(true)
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

        player.setMediaItem(item.build());
        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                Toast.makeText(PlayerActivity.this, "Playback error: " + error.getErrorCodeName(), Toast.LENGTH_LONG).show();
            }

            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_ENDED) saveProgress();
            }
        });
        player.seekTo(position);
        player.setPlayWhenReady(playWhenReady);
        player.prepare();
    }

    private void saveProgress() {
        if (player == null || video == null || quality == null) return;
        long dur = player.getDuration();
        long pos = player.getCurrentPosition();
        if (dur == C.TIME_UNSET || dur < 0) dur = 0;
        if (player.getPlaybackState() == Player.STATE_ENDED) pos = dur;
        Store.saveProgress(this, video, epId, epTitle, quality, pos, dur);
    }

    private void releasePlayer() {
        if (player == null) return;
        saveProgress();
        position = player.getCurrentPosition();
        playWhenReady = player.getPlayWhenReady();
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
