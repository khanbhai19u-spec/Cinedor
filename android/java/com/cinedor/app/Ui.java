package com.cinedor.app;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.util.DisplayMetrics;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

import java.util.Locale;

/** Small view helpers shared by all screens. */
public final class Ui {

    private Ui() {}

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static int screenWidth(Context c) {
        DisplayMetrics m = c.getResources().getDisplayMetrics();
        return m.widthPixels;
    }

    public static GradientDrawable round(int color, float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        return d;
    }

    public static GradientDrawable round(int color, float radiusPx, int strokePx, int strokeColor) {
        GradientDrawable d = round(color, radiusPx);
        d.setStroke(strokePx, strokeColor);
        return d;
    }

    private static GradientDrawable glow(int color, float cx, float cy, float radiusPx) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{color, 0x00000000});
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setGradientCenter(cx, cy);
        g.setGradientRadius(radiusPx);
        return g;
    }

    /** Dark background with soft blue (top-right) and plum (bottom-left) glows. */
    public static Drawable appBackground(Context c) {
        float w = screenWidth(c);
        return new LayerDrawable(new Drawable[]{
                new ColorDrawable(0xFF0F1014),
                glow(0x552B4C9E, 0.75f, 0.02f, w * 0.85f),
                glow(0x403E2C42, 0.0f, 0.72f, w * 0.9f),
        });
    }

    public static void load(ImageView iv, String url) {
        if (url == null || url.isEmpty()) {
            Glide.with(iv).clear(iv);
            iv.setImageDrawable(null);
            return;
        }
        Object model = url;
        if (ApiConfig.needsToken(url)) {
            model = new GlideUrl(url, new LazyHeaders.Builder().addHeader("X-App-Token", ApiConfig.token()).build());
        }
        Glide.with(iv).load(model)
                .transition(DrawableTransitionOptions.withCrossFade(250))
                .into(iv);
    }

    public static String time(long ms) {
        long s = Math.max(0, ms / 1000);
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h > 0 ? String.format(Locale.US, "%d:%02d:%02d", h, m, sec) : String.format(Locale.US, "%d:%02d", m, sec);
    }

    public static String size(long bytes) {
        if (bytes >= 1L << 30) return String.format(Locale.US, "%.1f GB", bytes / (double) (1L << 30));
        if (bytes >= 1L << 20) return String.format(Locale.US, "%.0f MB", bytes / (double) (1L << 20));
        return String.format(Locale.US, "%.0f KB", bytes / 1024.0);
    }
}
