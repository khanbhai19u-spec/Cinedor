package com.cinedor.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.List;

/** Poster cards used for rows, grids and "Continue watching". */
public class PosterAdapter extends RecyclerView.Adapter<PosterAdapter.Holder> {

    public static class Item {
        public final Video video;
        public String subtitle = "";
        public float progress = -1f;
        public boolean showPlay;
        public Object tag;

        public Item(Video v) { video = v; }
    }

    public interface Listener {
        void onClick(Item item);
        default boolean onLongClick(Item item) { return false; }
    }

    private final List<Item> items = new ArrayList<>();
    private final Listener listener;
    private final int widthPx, heightPx;
    private final boolean showTitle;

    public PosterAdapter(int widthPx, int heightPx, boolean showTitle, Listener listener) {
        this.widthPx = widthPx;
        this.heightPx = heightPx;
        this.showTitle = showTitle;
        this.listener = listener;
    }

    public void submit(List<Item> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    public static List<Item> wrap(List<Video> videos) {
        List<Item> out = new ArrayList<>();
        for (Video v : videos) {
            Item it = new Item(v);
            it.subtitle = v.meta().replace("  •  ", " • ");
            out.add(it);
        }
        return out;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_poster, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Item it = items.get(position);
        ViewGroup.LayoutParams lp = h.card.getLayoutParams();
        lp.width = widthPx;
        lp.height = heightPx;
        h.card.setLayoutParams(lp);
        h.title.getLayoutParams().width = widthPx;
        h.sub.getLayoutParams().width = widthPx;

        String img = it.video.portrait();
        h.fallback.setText(img.isEmpty() ? it.video.title : "");
        Ui.load(h.image, img);
        h.play.setVisibility(it.showPlay ? View.VISIBLE : View.GONE);
        if (it.progress >= 0) {
            h.progress.setVisibility(View.VISIBLE);
            h.progress.setProgressCompat(Math.max(20, Math.round(it.progress * 1000)), false);
        } else {
            h.progress.setVisibility(View.GONE);
        }
        h.title.setVisibility(showTitle ? View.VISIBLE : View.GONE);
        h.sub.setVisibility(showTitle && !it.subtitle.isEmpty() ? View.VISIBLE : View.GONE);
        h.title.setText(it.video.title);
        h.sub.setText(it.subtitle);
        h.itemView.setOnClickListener(v -> listener.onClick(it));
        h.itemView.setOnLongClickListener(v -> listener.onLongClick(it));
        h.itemView.setContentDescription(it.video.title);
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class Holder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final ImageView image, play;
        final TextView fallback, title, sub;
        final LinearProgressIndicator progress;

        Holder(View v) {
            super(v);
            card = v.findViewById(R.id.posterCard);
            image = v.findViewById(R.id.posterImage);
            play = v.findViewById(R.id.posterPlay);
            fallback = v.findViewById(R.id.posterFallback);
            title = v.findViewById(R.id.posterTitle);
            sub = v.findViewById(R.id.posterSub);
            progress = v.findViewById(R.id.posterProgress);
        }
    }
}
