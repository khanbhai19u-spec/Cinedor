package com.cinedor.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import androidx.appcompat.app.AlertDialog;

import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Shows the announcement you wrote in Cinedor Console (only when a personal server is set), once per message. */
public final class Announcement {

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    private Announcement() {}

    public static void check(Activity a) {
        if (!ApiConfig.hasServer()) return;
        IO.execute(() -> {
            try {
                JSONObject o = new JSONObject(ApiConfig.get("/api/announcement"));
                if (!o.optBoolean("active")) return;
                String id = o.optString("id");
                if (id.isEmpty() || id.equals(Prefs.seenAnnouncement(a))) return;
                a.runOnUiThread(() -> show(a, o, id));
            } catch (Exception ignored) {
                // server offline or no announcement — stay quiet
            }
        });
    }

    private static void show(Activity a, JSONObject o, String id) {
        if (a.isFinishing() || a.isDestroyed()) return;
        String link = o.optString("link");
        String label = o.optString("linkLabel");
        AlertDialog.Builder b = new AlertDialog.Builder(a)
                .setTitle(o.optString("title", "Announcement"))
                .setMessage(o.optString("text"))
                .setNegativeButton("Close", null)
                .setOnDismissListener(d -> Prefs.setSeenAnnouncement(a, id));
        if (link.startsWith("http://") || link.startsWith("https://")) {
            b.setPositiveButton(label.isEmpty() ? "Open" : label, (d, w) -> {
                try { a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(link))); } catch (Exception ignored) {}
            });
        }
        b.show();
    }
}
