package com.cinedor.app;

import android.content.Context;

/** Poster sizes for 3-column grids. */
final class Grid {
    static final int COLUMNS = 3;

    private Grid() {}

    /** Card width for a grid inside a RecyclerView with 12dp side padding and 6dp item padding. */
    static int cardWidth(Context c) {
        int usable = Ui.screenWidth(c) - Ui.dp(c, 24) - Ui.dp(c, 12) * COLUMNS;
        return usable / COLUMNS;
    }

    static int cardHeight(Context c) { return Math.round(cardWidth(c) * 1.45f); }
}
