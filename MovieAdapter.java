package com.cinedor.app;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** Poster cards used in the home rows and in the search / "See all" grid. */
public class MovieAdapter extends RecyclerView.Adapter<MovieAdapter.Holder> {

    private final List<MovieApi.Movie> items = new ArrayList<>();
    private final boolean grid;

    public MovieAdapter(boolean grid) { this.grid = grid; }

    public void set(List<MovieApi.Movie> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    public void add(List<MovieApi.Movie> list) {
        int start = items.size();
        items.addAll(list);
        notifyItemRangeInserted(start, list.size());
    }

    public List<MovieApi.Movie> items() { return items; }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_movie_card, parent, false);
        if (grid) {
            ViewGroup.LayoutParams lp = v.getLayoutParams();
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            v.setLayoutParams(lp);
        }
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        MovieApi.Movie m = items.get(position);
        h.title.setText(m.title);
        h.year.setText(m.year);
        h.year.setVisibility(m.year.isEmpty() ? View.GONE : View.VISIBLE);
        ImageLoader.load(h.poster, m.poster());
        h.itemView.setOnClickListener(v -> {
            Intent i = new Intent(v.getContext(), MovieDetailActivity.class);
            i.putExtra(MovieDetailActivity.EXTRA_VIDEO_ID, m.id);
            i.putExtra(MovieDetailActivity.EXTRA_TITLE, m.title);
            v.getContext().startActivity(i);
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView poster;
        final TextView title, year;

        Holder(View v) {
            super(v);
            poster = v.findViewById(R.id.cardPoster);
            title = v.findViewById(R.id.cardTitle);
            year = v.findViewById(R.id.cardYear);
        }
    }
}
