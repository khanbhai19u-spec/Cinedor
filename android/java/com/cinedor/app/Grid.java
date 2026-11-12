package com.cinedor.app;

import android.content.Context;
import android.util.DisplayMetrics;

/** Screen-size aware sizes, so the same layouts work on small phones, big phones, landscape and tablets. */
final class Grid {

    private Grid() {}

    private static DisplayMetrics m(Context c) { return c.getResources().getDisplayMetrics(); }
    static float widthDp(Context c) { return m(c).widthPixels / m(c).density; }
    static float heightDp(Context c) { return m(c).heightPixels / m(c).density; }
    static boolean wide(Context c) { return widthDp(c) >= 600 || widthDp(c) > heightDp(c); }

    /** 3 columns on phones, more on landscape / tablets. */
    static int columns(Context c) {
        int n = (int) (widthDp(c) / 125f);
        return Math.max(3, Math.min(n, 8));
    }

    /** Card width for a grid inside a RecyclerView with 12dp side padding and 6dp item padding. */
    static int cardWidth(Context c) {
        int cols = columns(c);
        int usable = m(c).widthPixels - Ui.dp(c, 24) - Ui.dp(c, 12) * cols;
        return usable / cols;
    }

    static int cardHeight(Context c) { return Math.round(cardWidth(c) * 1.45f); }

    /** Horizontal row poster width: ~30% of a phone screen, capped on big screens. */
    static int rowCardWidth(Context c) {
        float dp = Math.max(104f, Math.min(widthDp(c) * 0.3f, 160f));
        return Ui.dp(c, dp);
    }

    static int rowCardHeight(Context c) { return Math.round(rowCardWidth(c) * 1.44f); }

    /** Hero card width (the carousel shows neighbours peeking in at the sides). */
    static int heroCardWidth(Context c) {
        float w = widthDp(c);
        float dp = wide(c) ? Math.min(w * 0.42f, 420f) : Math.min(w * 0.8f, 400f);
        return Ui.dp(c, dp);
    }

    /** Hero height follows the card's 2:3 shape but never takes more than ~62% of the screen. */
    static int heroHeight(Context c) {
        int byShape = Math.round(heroCardWidth(c) * 1.5f);
        int maxH = Math.round(m(c).heightPixels * (wide(c) ? 0.78f : 0.62f));
        return Math.max(Ui.dp(c, 300), Math.min(byShape, maxH));
    }

    /** Floating nav bar width. */
    static int navWidth(Context c) {
        return Math.min(m(c).widthPixels - Ui.dp(c, 48), Ui.dp(c, 360));
    }
}
