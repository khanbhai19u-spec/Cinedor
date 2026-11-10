package com.mayra.assistant;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

/** Colours and small view helpers — the whole UI is built in code (no XML layouts). */
public final class Ui {

    public static final int BG = Color.parseColor("#0B0D12");
    public static final int SURFACE = Color.parseColor("#151924");
    public static final int SURFACE2 = Color.parseColor("#1E2332");
    public static final int STROKE = Color.parseColor("#2A3044");
    public static final int TEXT = Color.parseColor("#ECEEF5");
    public static final int MUTED = Color.parseColor("#8C93A8");
    public static final int ACCENT = Color.parseColor("#8B6CFF");
    public static final int ACCENT2 = Color.parseColor("#FF5CA8");
    public static final int CODE_BG = Color.parseColor("#0F121A");
    public static final int DANGER = Color.parseColor("#FF5C6C");
    public static final int OK = Color.parseColor("#3DDC97");

    private Ui() {}

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    public static GradientDrawable round(int color, float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        return d;
    }

    public static GradientDrawable roundStroke(int color, float radiusPx, int strokePx, int strokeColor) {
        GradientDrawable d = round(color, radiusPx);
        d.setStroke(strokePx, strokeColor);
        return d;
    }

    public static GradientDrawable gradient(float radiusPx) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{ACCENT, ACCENT2});
        d.setCornerRadius(radiusPx);
        return d;
    }

    public static GradientDrawable circleGradient() {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{ACCENT, ACCENT2});
        d.setShape(GradientDrawable.OVAL);
        return d;
    }

    public static Drawable ripple(Drawable content) {
        return new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), content, null);
    }

    public static TextView text(Context c, String s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    /** Round icon-style button using an emoji or symbol as the icon. */
    public static TextView iconButton(Context c, String symbol, int sizeDp, Drawable bg) {
        TextView b = text(c, symbol, 18, TEXT);
        b.setGravity(Gravity.CENTER);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        int s = dp(c, sizeDp);
        b.setMinWidth(s);
        b.setMinHeight(s);
        b.setWidth(s);
        b.setHeight(s);
        b.setBackground(ripple(bg != null ? bg : round(0x00000000, s / 2f)));
        b.setClickable(true);
        b.setFocusable(true);
        return b;
    }

    public static TextView pillButton(Context c, String label, boolean primary) {
        TextView b = text(c, label, 15, Color.WHITE);
        b.setGravity(Gravity.CENTER);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        int h = dp(c, 12), w = dp(c, 18);
        b.setPadding(w, h, w, h);
        b.setBackground(ripple(primary ? gradient(dp(c, 24)) : roundStroke(SURFACE2, dp(c, 24), dp(c, 1), STROKE)));
        b.setClickable(true);
        return b;
    }

    public static void copy(Context c, String s) {
        ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("MAYRA", s));
        Toast.makeText(c, "কপি হয়েছে", Toast.LENGTH_SHORT).show();
    }

    public static void gone(View v, boolean gone) { v.setVisibility(gone ? View.GONE : View.VISIBLE); }
}
