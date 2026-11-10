package com.cinedor.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Tiny poster loader: background download, downscale, in-memory cache. No extra library needed. */
public final class ImageLoader {

    private static final int MAX_SIDE = 480;
    private static final ExecutorService POOL = Executors.newFixedThreadPool(4);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final LruCache<String, Bitmap> CACHE =
            new LruCache<String, Bitmap>((int) Math.min(Runtime.getRuntime().maxMemory() / 8, 48L * 1024 * 1024)) {
                @Override protected int sizeOf(String key, Bitmap b) { return b.getByteCount(); }
            };

    private ImageLoader() {}

    public static void load(ImageView view, String url) {
        view.setTag(url);
        Bitmap cached = url == null ? null : CACHE.get(url);
        if (cached != null) { view.setImageBitmap(cached); return; }
        view.setImageDrawable(null);
        if (url == null || url.isEmpty()) return;
        POOL.execute(() -> {
            Bitmap b = fetch(url);
            if (b == null) return;
            CACHE.put(url, b);
            MAIN.post(() -> { if (url.equals(view.getTag())) view.setImageBitmap(b); });
        });
    }

    private static Bitmap fetch(String url) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(20000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", "CinedorApp/2.0 (Android)");
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            try (InputStream in = c.getInputStream()) {
                byte[] chunk = new byte[16 * 1024];
                int n;
                while ((n = in.read(chunk)) != -1) buf.write(chunk, 0, n);
            }
            byte[] data = buf.toByteArray();
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= MAX_SIDE && o.outHeight / (sample * 2) >= MAX_SIDE) sample *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            return BitmapFactory.decodeByteArray(data, 0, data.length, o);
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }
}
