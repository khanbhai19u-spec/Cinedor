package com.cinedor.app;

import android.content.Context;
import android.content.Intent;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** Poster cards: horizontal rows, "Top 10" rows with big numbers, and grids. */
public class PosterAdapter extends RecyclerView.Adapter<PosterAdapter.Holder> {

    public static final int ROW = 0, RANKED = 1, GRID = 2;

    private final List<Item> items = new ArrayList<>();
    private final int mode;

    public PosterAdapter(int mode) { this.mode = mode; }

    public void set(List<Item> list) {
        items.clear();
        items.addAll(mode == RANKED && list.size() > 10 ? list.subList(0, 10) : list);
        notifyDataSetChanged();
    }

    public void add(List<Item> list) {
        int start = items.size();
        items.addAll(list);
        notifyItemRangeInserted(start, list.size());
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context c = parent.getContext();
        boolean grid = mode == GRID;
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.BOTTOM);
        RecyclerView.LayoutParams rlp = new RecyclerView.LayoutParams(
                grid ? ViewGroup.LayoutParams.MATCH_PARENT : ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        int m = Ui.dp(c, 5);
        rlp.setMargins(m, m, m, m);
        root.setLayoutParams(rlp);

        TextView rank = new TextView(c);
        rank.setTextSize(92);
        rank.setTypeface(Typeface.DEFAULT_BOLD);
        rank.setTextColor(0xFF6B757D);
        rank.setIncludeFontPadding(false);
        rank.getPaint().setStyle(Paint.Style.STROKE);
        rank.getPaint().setStrokeWidth(Ui.dp(c, 1.5f));
        LinearLayout.LayoutParams rk = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rk.setMarginEnd(-Ui.dp(c, 10));
        rank.setVisibility(mode == RANKED ? View.VISIBLE : View.GONE);
        root.addView(rank, rk);

        FrameLayout card = new FrameLayout(c);
        Ui.clip(card, Ui.SURFACE, Ui.dp(c, 10));
        root.addView(card, new LinearLayout.LayoutParams(
                grid ? ViewGroup.LayoutParams.MATCH_PARENT : Ui.dp(c, mode == RANKED ? 128 : 116),
                Ui.dp(c, mode == RANKED ? 192 : 174)));

        ImageView img = new ImageView(c);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        card.addView(img, new FrameLayout.LayoutParams(-1, -1));

        TextView title = Ui.text(c, "", 12.5f, Ui.TEXT, false);
        title.setGravity(Gravity.CENTER);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setPadding(Ui.dp(c, 6), Ui.dp(c, 26), Ui.dp(c, 6), Ui.dp(c, 8));
        title.setBackground(Ui.fade(0x00000000, 0xE6000000));
        card.addView(title, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        TextView badge = Ui.text(c, "", 10.5f, Ui.TEXT, true);
        badge.setPadding(Ui.dp(c, 6), Ui.dp(c, 2), Ui.dp(c, 6), Ui.dp(c, 2));
        badge.setBackground(Ui.round(Ui.ACCENT, Ui.dp(c, 6)));
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        bl.setMargins(Ui.dp(c, 6), Ui.dp(c, 6), 0, 0);
        card.addView(badge, bl);

        return new Holder(root, rank, img, title, badge);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Item it = items.get(position);
        h.rank.setText(String.valueOf(position + 1));
        h.title.setText(it.title);
        ImageLoader.load(h.img, it.poster);
        boolean upcoming = it.date.compareTo(today()) > 0 && it.date.length() == 10;
        h.badge.setVisibility(upcoming ? View.VISIBLE : View.GONE);
        if (upcoming) h.badge.setText(shortDate(it.date));
        else if (Item.ARCHIVE.equals(it.type)) { h.badge.setVisibility(View.VISIBLE); h.badge.setText("FREE"); }
        h.itemView.setOnClickListener(v -> open(v.getContext(), it));
    }

    static void open(Context c, Item it) {
        Intent i = new Intent(c, DetailActivity.class);
        i.putExtra(DetailActivity.EXTRA_ITEM, it.toJson().toString());
        c.startActivity(i);
    }

    private static String today() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date());
    }

    private static String shortDate(String d) {
        try {
            java.util.Date date = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(d);
            return new java.text.SimpleDateFormat("d MMM", java.util.Locale.US).format(date);
        } catch (Exception e) { return d; }
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView rank, title, badge;
        final ImageView img;

        Holder(View v, TextView rank, ImageView img, TextView title, TextView badge) {
            super(v);
            this.rank = rank; this.img = img; this.title = title; this.badge = badge;
        }
    }
}
