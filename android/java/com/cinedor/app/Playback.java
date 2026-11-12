package com.cinedor.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.List;

/** Starting playback / downloads, including the quality picker sheet. */
public final class Playback {

    public static final int REQ_STORAGE = 41;

    interface Pick { void on(Video.Quality q); }

    private Playback() {}

    /** Hero "Play": resume if started, else first episode / movie. */
    public static void playDefault(Activity a, Video v) {
        Store.Entry last = Store.lastWatched(a, v.id);
        if (last != null && last.unfinished() && !last.url.isEmpty()) { resume(a, last); return; }
        if (v.isSeries() && !v.episodes.isEmpty()) {
            Video.Episode ep = v.episodes.get(0);
            if (last != null && !last.epId.isEmpty()) {
                // continue with the episode after the one that was finished
                for (int i = 0; i < v.episodes.size(); i++) {
                    if (v.episodes.get(i).id.equals(last.epId)) {
                        ep = v.episodes.get(Math.min(i + 1, v.episodes.size() - 1));
                        break;
                    }
                }
            }
            play(a, v, ep);
        } else {
            play(a, v, null);
        }
    }

    public static void play(Activity a, Video v, Video.Episode ep) {
        List<Video.Quality> qs = ep != null ? ep.qualities : v.qualities;
        if (qs.isEmpty()) { Toast.makeText(a, "Nothing to play", Toast.LENGTH_SHORT).show(); return; }
        if (qs.size() == 1) { start(a, v, ep, qs.get(0), 0); return; }
        sheet(a, qs, true, q -> start(a, v, ep, q, 0));
    }

    public static void resume(Activity a, Store.Entry e) {
        Video.Episode ep = e.video.episode(e.epId);
        Video.Quality q = null;
        List<Video.Quality> qs = ep != null ? ep.qualities : e.video.qualities;
        for (Video.Quality x : qs) if (x.url.equals(e.url)) q = x;
        if (q == null) {
            if (qs.isEmpty()) { Toast.makeText(a, "Nothing to play", Toast.LENGTH_SHORT).show(); return; }
            q = qs.get(0);
        }
        start(a, e.video, ep, q, e.pos);
    }

    public static void start(Activity a, Video v, Video.Episode ep, Video.Quality q, long startMs) {
        String title = v.title + (ep != null ? " • " + ep.title : "") + " • " + q.label;
        Intent i = new Intent(a, PlayerActivity.class)
                .putExtra(PlayerActivity.EXTRA_URL, q.url)
                .putExtra(PlayerActivity.EXTRA_TYPE, q.type)
                .putExtra(PlayerActivity.EXTRA_LABEL, q.label)
                .putExtra(PlayerActivity.EXTRA_TITLE, title)
                .putExtra(PlayerActivity.EXTRA_VIDEO_JSON, v.toJson().toString())
                .putExtra(PlayerActivity.EXTRA_EP_ID, ep != null ? ep.id : "")
                .putExtra(PlayerActivity.EXTRA_EP_TITLE, ep != null ? ep.title : "")
                .putExtra(PlayerActivity.EXTRA_START, startMs);
        a.startActivity(i);
    }

    public static void download(Activity a, Video v, Video.Episode ep) {
        List<Video.Quality> qs = new ArrayList<>();
        for (Video.Quality q : ep != null ? ep.qualities : v.qualities) if (q.downloadable()) qs.add(q);
        if (qs.isEmpty()) {
            Toast.makeText(a, "No downloadable file (HLS streams can't be downloaded)", Toast.LENGTH_LONG).show();
            return;
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
                && ContextCompat.checkSelfPermission(a, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(a, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
            Toast.makeText(a, "Allow storage, then tap download again", Toast.LENGTH_LONG).show();
            return;
        }
        Pick go = q -> {
            String name = v.title + (ep != null ? " " + ep.title : "");
            long id = VideoDownloader.download(a, q.url, name, q.label);
            if (id > 0) {
                Store.addDownload(a, v, ep != null ? ep.id : "", ep != null ? ep.title : "", q, id);
                Toast.makeText(a, "Download started (" + q.label + ")", Toast.LENGTH_SHORT).show();
            }
        };
        if (qs.size() == 1) go.on(qs.get(0));
        else sheet(a, qs, false, go);
    }

    static void sheet(Activity a, List<Video.Quality> options, boolean watch, Pick pick) {
        BottomSheetDialog sheet = new BottomSheetDialog(a);
        View root = LayoutInflater.from(a).inflate(R.layout.sheet_quality, null);
        ((TextView) root.findViewById(R.id.sheetTitle)).setText(watch ? R.string.select_watch : R.string.select_download);
        LinearLayout box = root.findViewById(R.id.sheetList);
        for (Video.Quality q : options) {
            View row = LayoutInflater.from(a).inflate(R.layout.item_quality, box, false);
            ((TextView) row.findViewById(R.id.qLabel)).setText(q.label);
            ((TextView) row.findViewById(R.id.qInfo)).setText(q.info());
            row.setOnClickListener(x -> { sheet.dismiss(); pick.on(q); });
            box.addView(row);
        }
        sheet.setContentView(root);
        View parent = (View) root.getParent();
        if (parent != null) parent.setBackgroundColor(0x00000000);
        sheet.show();
    }
}
