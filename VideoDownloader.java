package com.cinedor.app;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.widget.Toast;

/**
 * Downloads an MP4 from YOUR personal server into public storage:
 *   /Movies/Cinedor/<title>_<quality>.mp4
 * with a system notification (progress + "complete").
 */
public final class VideoDownloader {

    private VideoDownloader() {}

    /** @return DownloadManager id, or -1 if it could not start. */
    public static long download(Context ctx, String url, String title, String quality) {
        if (url == null || !url.startsWith(ApiConfig.BASE_URL)) {
            Toast.makeText(ctx, "Only files from your own server can be downloaded", Toast.LENGTH_LONG).show();
            return -1;
        }
        if (url.toLowerCase().contains(".m3u8")) {
            Toast.makeText(ctx, "HLS streams can't be downloaded — choose an MP4 quality", Toast.LENGTH_LONG).show();
            return -1;
        }
        try {
            String safe = (title + "_" + quality).replaceAll("[^\\p{L}\\p{N}._-]+", "_");
            if (safe.length() > 100) safe = safe.substring(0, 100);
            String fileName = safe + ".mp4";

            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url))
                    .setTitle(title + " (" + quality + ")")
                    .setDescription("Cinedor • downloading")
                    .setMimeType("video/mp4")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(false)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_MOVIES, "Cinedor/" + fileName);
            if (!ApiConfig.APP_TOKEN.isEmpty()) req.addRequestHeader("X-App-Token", ApiConfig.APP_TOKEN);

            DownloadManager dm = (DownloadManager) ctx.getSystemService(Context.DOWNLOAD_SERVICE);
            return dm != null ? dm.enqueue(req) : -1;
        } catch (Exception e) {
            Toast.makeText(ctx, "Download failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return -1;
        }
    }
}
