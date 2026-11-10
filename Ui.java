package com.cinedor.app;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared look: colours and small view builders. */
final class Ui {

    static final int BG = 0xFF0F1215, SURFACE = 0xFF1C2227, ACCENT = 0xFFF47B20;
    static final int TEXT = 0xFFFFFFFF, SUB = 0xFF9AA3AB, LINE = 0xFF262D33;

    private Ui() {}

    static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    static GradientDrawable round(int color, float radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusPx);
        return g;
    }

    /** Rounded background that also clips the children (posters, cards, avatars). */
    static void clip(View v, int color, float radiusPx) {
        v.setBackground(round(color, radiusPx));
        v.setClipToOutline(true);
    }

    static GradientDrawable fade(int top, int bottom) {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom});
    }

    static TextView text(Context c, CharSequence s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    static TextView chip(Context c, String label, boolean selected) {
        TextView t = text(c, label, 15, TEXT, selected);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 18), 0, dp(c, 18), 0);
        t.setBackground(round(selected ? ACCENT : SURFACE, dp(c, 20)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(c, 38));
        lp.setMarginEnd(dp(c, 10));
        t.setLayoutParams(lp);
        t.setClickable(true);
        return t;
    }

    static ImageView icon(Context c, int res, int tint) {
        ImageView v = new ImageView(c);
        v.setImageResource(res);
        v.setColorFilter(tint);
        return v;
    }

    /** Round icon button. */
    static ImageView roundButton(Context c, int res, int sizeDp, int bg, int tint) {
        ImageView v = icon(c, res, tint);
        int pad = dp(c, sizeDp * 0.26f);
        v.setPadding(pad, pad, pad, pad);
        v.setBackground(round(bg, dp(c, sizeDp)));
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        v.setClickable(true);
        return v;
    }
}
