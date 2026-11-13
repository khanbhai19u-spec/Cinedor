package com.cinedor.app;

import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.widget.Toast;

/**
 * Downloads MP4 files from your server into public storage:
 *   /Movies/Cinedor/<title>_<quality>.mp4
 * with a system notification (progress + "complete").
 */
public final class VideoDownloader {

    public static final int STATUS_UNKNOWN = 0, STATUS_RUNNING = 1, STATUS_DONE = 2, STATUS_FAILED = 3;

    private VideoDownloader() {}

    /** @return DownloadManager id, or -1 if it could not start. */
    public static long download(Context ctx, String url, String title, String quality) {
        if (url == null || url.isEmpty()) {
            Toast.makeText(ctx, "Invalid download URL", Toast.LENGTH_LONG).show();
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
                    .setAllowedOverMetered(!Prefs.wifiOnly(ctx))
                    .setAllowedOverRoaming(false)
                    .setDestinationInExternalPublicDir("movies".equals(Prefs.downloadFolder(ctx))
                            ? Environment.DIRECTORY_MOVIES : Environment.DIRECTORY_DOWNLOADS, "Cinedor/" + fileName);

            if (ApiConfig.needsToken(url)) req.addRequestHeader("X-App-Token", ApiConfig.token());

            DownloadManager dm = (DownloadManager) ctx.getSystemService(Context.DOWNLOAD_SERVICE);
            return dm != null ? dm.enqueue(req) : -1;
        } catch (Exception e) {
            Toast.makeText(ctx, "Download failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return -1;
        }
    }

    /** Status of a download, plus its local uri when finished. */
    public static Object[] status(Context ctx, long id) {
        DownloadManager dm = (DownloadManager) ctx.getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm == null || id < 0) return new Object[]{STATUS_UNKNOWN, null, 0};
        try (Cursor c = dm.query(new DownloadManager.Query().setFilterById(id))) {
            if (c == null || !c.moveToFirst()) return new Object[]{STATUS_FAILED, null, 0};
            int st = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            long done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
            long total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
            int pct = total > 0 ? (int) (done * 100 / total) : 0;
            if (st == DownloadManager.STATUS_SUCCESSFUL) {
                Uri uri = dm.getUriForDownloadedFile(id);
                return new Object[]{STATUS_DONE, uri, 100};
            }
            if (st == DownloadManager.STATUS_FAILED) return new Object[]{STATUS_FAILED, null, pct};
            return new Object[]{STATUS_RUNNING, null, pct};
        } catch (Exception e) {
            return new Object[]{STATUS_UNKNOWN, null, 0};
        }
    }

    public static void remove(Context ctx, long id) {
        DownloadManager dm = (DownloadManager) ctx.getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm != null && id >= 0) dm.remove(id);
    }
}
