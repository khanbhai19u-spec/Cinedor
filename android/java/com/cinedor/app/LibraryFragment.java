package com.cinedor.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** Favorites / History / Downloads. */
public class LibraryFragment extends Fragment implements MainActivity.Tab {

    private static final String[] NAMES = {"My List", "History", "Downloads"};

    private int tab = 0;
    private LinearLayout tabs;
    private View empty;
    private ImageView emptyIcon;
    private TextView emptyTitle, emptySub;
    private PosterAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_library, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle state) {
        if (state != null) tab = state.getInt("tab", 0);
        tabs = v.findViewById(R.id.tabs);
        empty = v.findViewById(R.id.emptyBox);
        emptyIcon = v.findViewById(R.id.emptyIcon);
        emptyTitle = v.findViewById(R.id.emptyTitle);
        emptySub = v.findViewById(R.id.emptySub);

        for (int i = 0; i < NAMES.length; i++) {
            TextView t = new TextView(requireContext());
            t.setTextSize(17);
            t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
            t.setGravity(android.view.Gravity.CENTER);
            int ph = Ui.dp(requireContext(), 26);
            t.setPadding(ph, 0, ph, 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(requireContext(), 54));
            lp.setMarginEnd(Ui.dp(requireContext(), 10));
            final int idx = i;
            t.setOnClickListener(x -> { tab = idx; refresh(); });
            tabs.addView(t, lp);
        }

        RecyclerView rv = v.findViewById(R.id.list);
        rv.setLayoutManager(new GridLayoutManager(requireContext(), Grid.columns(requireContext())));
        adapter = new PosterAdapter(Grid.cardWidth(requireContext()), Grid.cardHeight(requireContext()), true,
                new PosterAdapter.Listener() {
                    @Override public void onClick(PosterAdapter.Item it) { open(it); }
                    @Override public boolean onLongClick(PosterAdapter.Item it) { askRemove(it); return true; }
                });
        rv.setAdapter(adapter);
        refresh();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle out) {
        out.putInt("tab", tab);
        super.onSaveInstanceState(out);
    }

    public void showTab(int t) {
        tab = t;
        refresh();
    }

    @Override public void onShown() { if (getView() != null) refresh(); }
    @Override public void onResume() { super.onResume(); refresh(); }

    private void refresh() {
        if (adapter == null || !isAdded()) return;
        List<Store.Entry> fav = Store.favorites(requireContext());
        List<Store.Entry> hist = Store.history(requireContext());
        List<Store.Entry> dl = Store.downloads(requireContext());
        int[] counts = {fav.size(), hist.size(), dl.size()};
        for (int i = 0; i < tabs.getChildCount(); i++) {
            TextView t = (TextView) tabs.getChildAt(i);
            boolean on = i == tab;
            t.setText(NAMES[i] + " (" + counts[i] + ")");
            t.setTextColor(on ? 0xFFFFFFFF : 0xFFDADBE0);
            t.setBackground(Ui.round(on ? 0xFF5E81F4 : 0xFF1C1D22, Ui.dp(requireContext(), 27)));
        }

        List<PosterAdapter.Item> items = new ArrayList<>();
        List<Store.Entry> src = tab == 0 ? fav : tab == 1 ? hist : dl;
        for (Store.Entry e : src) {
            PosterAdapter.Item it = new PosterAdapter.Item(e.video);
            it.tag = e;
            if (tab == 0) {
                it.subtitle = e.video.meta().replace("  •  ", " • ");
            } else if (tab == 1) {
                it.progress = e.dur > 0 ? e.progress() : -1;
                it.subtitle = (e.epTitle.isEmpty() ? "" : e.epTitle + " • ")
                        + (e.unfinished() ? Ui.time(e.pos) + " / " + Ui.time(e.dur) : "Watched");
            } else {
                Object[] st = VideoDownloader.status(requireContext(), e.downloadId);
                int s = (int) st[0];
                String state = s == VideoDownloader.STATUS_DONE ? "Ready" : s == VideoDownloader.STATUS_RUNNING
                        ? st[2] + "%" : s == VideoDownloader.STATUS_FAILED ? "Failed" : "";
                it.showPlay = s == VideoDownloader.STATUS_DONE;
                it.subtitle = (e.epTitle.isEmpty() ? "" : e.epTitle + " • ") + e.label + (state.isEmpty() ? "" : " • " + state);
            }
            items.add(it);
        }
        adapter.submit(items);

        if (items.isEmpty()) {
            int icon = tab == 0 ? R.drawable.ic_bookmark_border : tab == 1 ? R.drawable.ic_history : R.drawable.ic_download;
            emptyIcon.setImageResource(icon);
            emptyTitle.setText(tab == 0 ? R.string.fav_empty_title : tab == 1 ? R.string.hist_empty_title : R.string.dl_empty_title);
            emptySub.setText(tab == 0 ? R.string.fav_empty_sub : tab == 1 ? R.string.hist_empty_sub : R.string.dl_empty_sub);
            empty.setVisibility(View.VISIBLE);
        } else {
            empty.setVisibility(View.GONE);
        }
    }

    private void open(PosterAdapter.Item it) {
        Store.Entry e = (Store.Entry) it.tag;
        if (tab == 1 && e.unfinished()) { Playback.resume(requireActivity(), e); return; }
        if (tab == 2) {
            Object[] st = VideoDownloader.status(requireContext(), e.downloadId);
            if ((int) st[0] == VideoDownloader.STATUS_DONE && st[1] != null) {
                Video.Episode ep = e.video.episode(e.epId);
                String title = e.video.title + (ep != null ? " • " + ep.title : "") + " • " + e.label + " (offline)";
                startActivity(new Intent(requireContext(), PlayerActivity.class)
                        .putExtra(PlayerActivity.EXTRA_URL, ((Uri) st[1]).toString())
                        .putExtra(PlayerActivity.EXTRA_TYPE, "mp4")
                        .putExtra(PlayerActivity.EXTRA_LABEL, e.label)
                        .putExtra(PlayerActivity.EXTRA_TITLE, title));
                return;
            }
            if ((int) st[0] == VideoDownloader.STATUS_RUNNING) {
                Toast.makeText(requireContext(), "Still downloading (" + st[2] + "%)", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        startActivity(new Intent(requireContext(), MovieDetailActivity.class)
                .putExtra(MovieDetailActivity.EXTRA_VIDEO_ID, e.video.id)
                .putExtra(MovieDetailActivity.EXTRA_VIDEO_JSON, e.video.toJson().toString()));
    }

    private void askRemove(PosterAdapter.Item it) {
        Store.Entry e = (Store.Entry) it.tag;
        String what = tab == 0 ? "favorites" : tab == 1 ? "history" : "downloads (the file is deleted too)";
        new AlertDialog.Builder(requireContext())
                .setTitle(e.video.title)
                .setMessage("Remove from " + what + "?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove", (d, w) -> {
                    if (tab == 0) Store.toggleFavorite(requireContext(), e.video);
                    else if (tab == 1) Store.removeHistory(requireContext(), e.video.id);
                    else { VideoDownloader.remove(requireContext(), e.downloadId); Store.removeDownload(requireContext(), e.downloadId); }
                    refresh();
                })
                .show();
    }
}
