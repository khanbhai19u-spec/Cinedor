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
        Tmdb.init(this);
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
        setupGestures();
    }

    // ---------- gestures: double-tap skip, hold for 2x ----------
    private android.view.GestureDetector gestures;
    private boolean speeding;

    private void setupGestures() {
        gestures = new android.view.GestureDetector(this, new android.view.GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(@NonNull android.view.MotionEvent e) {
                if (player == null) return false;
                int skip = Prefs.skipSeconds(PlayerActivity.this) * 1000;
                boolean forward = e.getX() > playerView.getWidth() / 2f;
                long target = player.getCurrentPosition() + (forward ? skip : -skip);
                long dur = player.getDuration();
                if (dur > 0) target = Math.min(target, dur);
                player.seekTo(Math.max(0, target));
                Toast.makeText(PlayerActivity.this, (forward ? "+" : "−") + Prefs.skipSeconds(PlayerActivity.this) + " s", Toast.LENGTH_SHORT).show();
                return true;
            }

            @Override
            public void onLongPress(@NonNull android.view.MotionEvent e) {
                if (player == null || !Prefs.holdToSpeed(PlayerActivity.this) || !player.isPlaying()) return;
                speeding = true;
                player.setPlaybackSpeed(2f);
                Toast.makeText(PlayerActivity.this, "2× speed", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        if (gestures != null && !isInPip()) gestures.onTouchEvent(ev);
        int a = ev.getActionMasked();
        if (speeding && (a == android.view.MotionEvent.ACTION_UP || a == android.view.MotionEvent.ACTION_CANCEL)) {
            speeding = false;
            if (player != null) player.setPlaybackSpeed(1f);
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    // ---------- small window (picture-in-picture) ----------
    private boolean isInPip() {
        return android.os.Build.VERSION.SDK_INT >= 24 && isInPictureInPictureMode();
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (android.os.Build.VERSION.SDK_INT >= 26 && Prefs.pip(this) && player != null && player.isPlaying()) {
            try {
                enterPictureInPictureMode(new android.app.PictureInPictureParams.Builder()
                        .setAspectRatio(new android.util.Rational(16, 9)).build());
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean inPip, @NonNull android.content.res.Configuration newConfig) {
        super.onPictureInPictureModeChanged(inPip, newConfig);
        playerView.setUseController(!inPip);
        findViewById(R.id.tvPlayerTitle).setVisibility(inPip ? android.view.View.GONE : android.view.View.VISIBLE);
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
                .setSeekBackIncrementMs(Prefs.skipSeconds(this) * 1000L)
                .setSeekForwardIncrementMs(Prefs.skipSeconds(this) * 1000L)
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
