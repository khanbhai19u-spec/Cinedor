package com.cinedor.app;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewpager2.widget.CompositePageTransformer;
import androidx.viewpager2.widget.ViewPager2;

import java.util.ArrayList;
import java.util.List;

/** Hero carousel + Continue watching + rows. */
public class HomeFragment extends Fragment implements MainActivity.Tab {

    private static final long AUTO_SCROLL_MS = 6000;

    private SwipeRefreshLayout swipe;
    private ViewPager2 pager;
    private View progress;
    private TextView message, continueCount;
    private LinearLayout dots, rows, tmdbRows, continueBox;
    private boolean heroIsTmdb;
    private PosterAdapter continueAdapter;
    private final HeroAdapter heroAdapter = new HeroAdapter();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable autoScroll = new Runnable() {
        @Override public void run() {
            int n = heroAdapter.getItemCount();
            if (pager != null && n > 1 && isResumed() && !isHidden()) {
                pager.setCurrentItem((pager.getCurrentItem() + 1) % n, true);
            }
            handler.postDelayed(this, AUTO_SCROLL_MS);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle state) {
        swipe = v.findViewById(R.id.swipe);
        pager = v.findViewById(R.id.heroPager);
        progress = v.findViewById(R.id.progress);
        message = v.findViewById(R.id.message);
        dots = v.findViewById(R.id.dots);
        rows = v.findViewById(R.id.rows);
        tmdbRows = v.findViewById(R.id.tmdbRows);
        continueBox = v.findViewById(R.id.continueBox);
        continueCount = v.findViewById(R.id.continueCount);

        swipe.setColorSchemeResources(R.color.accent);
        swipe.setProgressBackgroundColorSchemeColor(0xFF1E1F25);
        swipe.setOnRefreshListener(() -> reload(true));
        v.findViewById(R.id.btnRefresh).setOnClickListener(b -> {
            b.animate().rotationBy(360f).setDuration(600).start();
            reload(true);
        });

        // Hero carousel: neighbours peek in from both sides and are slightly smaller
        pager.setAdapter(heroAdapter);
        pager.setOffscreenPageLimit(2);
        View heroBox = v.findViewById(R.id.heroBox);
        heroBox.getLayoutParams().height = Grid.heroHeight(requireContext());
        heroBox.setLayoutParams(heroBox.getLayoutParams());
        RecyclerView inner = (RecyclerView) pager.getChildAt(0);
        int side = Math.max(Ui.dp(requireContext(), 24), (Ui.screenWidth(requireContext()) - Grid.heroCardWidth(requireContext())) / 2);
        inner.setPadding(side, 0, side, 0);
        inner.setClipToPadding(false);
        inner.setOverScrollMode(View.OVER_SCROLL_NEVER);
        CompositePageTransformer t = new CompositePageTransformer();
        t.addTransformer((page, pos) -> {
            float s = 1f - 0.1f * Math.min(1f, Math.abs(pos));
            page.setScaleY(s);
            page.setAlpha(1f - 0.25f * Math.min(1f, Math.abs(pos)));
        });
        pager.setPageTransformer(t);
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int position) { updateDots(position); }
            @Override public void onPageScrollStateChanged(int st) {
                if (st == ViewPager2.SCROLL_STATE_DRAGGING) restartAutoScroll();
            }
        });

        int w = Math.round(Grid.rowCardWidth(requireContext()) * 1.15f), h = Math.round(w * 1.43f);
        continueAdapter = new PosterAdapter(w, h, false, new PosterAdapter.Listener() {
            @Override public void onClick(PosterAdapter.Item it) { Playback.resume(requireActivity(), (Store.Entry) it.tag); }
            @Override public boolean onLongClick(PosterAdapter.Item it) { openDetail(it.video); return true; }
        });
        RecyclerView cl = v.findViewById(R.id.continueList);
        cl.setLayoutManager(new LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false));
        cl.setAdapter(continueAdapter);

        reload(false);
    }

    @Override
    public void onShown() {
        if (getView() != null) bindContinue();
    }

    @Override
    public void onResume() {
        super.onResume();
        bindContinue();
        heroAdapter.notifyDataSetChanged();
        restartAutoScroll();
    }

    @Override
    public void onPause() {
        handler.removeCallbacks(autoScroll);
        super.onPause();
    }

    private void restartAutoScroll() {
        handler.removeCallbacks(autoScroll);
        handler.postDelayed(autoScroll, AUTO_SCROLL_MS);
    }

    public void reload(boolean force) {
        if (getView() == null) return;
        if (!Catalog.isLoaded() || force) {
            if (heroAdapter.getItemCount() == 0) progress.setVisibility(View.VISIBLE);
        }
        message.setVisibility(View.GONE);
        Catalog.load(force, (items, error) -> {
            if (getView() == null) return;
            swipe.setRefreshing(false);
            progress.setVisibility(View.GONE);
            bind(items);
            loadTmdb(force);
            if (error != null && items.isEmpty()) {
                message.setText(getString(R.string.server_error, error) + "\n\nServer: " + ApiConfig.baseUrl()
                        + "\nChange it in Settings.");
                message.setVisibility(View.VISIBLE);
            } else if (items.isEmpty()) {
                message.setText(R.string.no_videos);
                message.setVisibility(View.VISIBLE);
            }
        });
    }

    private void bind(List<Video> items) {
        List<Video> hero = new ArrayList<>();
        for (Video v : items) if (v.featured && hero.size() < 10) hero.add(v);
        if (hero.isEmpty()) for (Video v : items) if (hero.size() < 10) hero.add(v);
        heroIsTmdb = false;
        heroAdapter.submit(hero);
        buildDots(hero.size());
        if (!hero.isEmpty()) updateDots(Math.min(pager.getCurrentItem(), hero.size() - 1));
        bindContinue();
        buildRows(items);
    }

    private void loadTmdb(boolean force) {
        Tmdb.discover(force, (list, error) -> {
            if (getView() == null) return;
            tmdbRows.removeAllViews();
            for (Tmdb.Row r : list) addRow(tmdbRows, r.title, r.items, "TMDB");
            if (!list.isEmpty()) {
                TextView note = new TextView(requireContext());
                note.setText("Movie & TV info and images from TMDB. This product uses the TMDB API but is not endorsed or certified by TMDB.");
                note.setTextColor(0xFF868A96);
                note.setTextSize(12);
                note.setGravity(android.view.Gravity.CENTER);
                int p = Ui.dp(requireContext(), 28);
                note.setPadding(p, p, p, 0);
                tmdbRows.addView(note);
            }
            // Nothing of your own yet: show trending in the big carousel
            if (Catalog.items().isEmpty() && !list.isEmpty()) {
                List<Video> hero = list.get(0).items.subList(0, Math.min(10, list.get(0).items.size()));
                heroIsTmdb = true;
                heroAdapter.submit(new ArrayList<>(hero));
                buildDots(hero.size());
                updateDots(0);
                message.setVisibility(View.GONE);
            }
        });
    }

    private void bindContinue() {
        if (continueAdapter == null || !isAdded()) return;
        List<Store.Entry> list = Store.continueWatching(requireContext());
        List<PosterAdapter.Item> items = new ArrayList<>();
        for (Store.Entry e : list) {
            PosterAdapter.Item it = new PosterAdapter.Item(e.video);
            it.showPlay = true;
            it.progress = e.progress();
            it.tag = e;
            items.add(it);
        }
        continueAdapter.submit(items);
        continueCount.setText(String.valueOf(items.size()));
        continueBox.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void buildRows(List<Video> items) {
        rows.removeAllViews();
        if (items.isEmpty()) return;
        List<Video> movies = new ArrayList<>(), series = new ArrayList<>();
        for (Video v : items) (v.isSeries() ? series : movies).add(v);
        addRow(rows, "Recently added", items.subList(0, Math.min(20, items.size())), "YOUR LIBRARY");
        if (!movies.isEmpty() && !series.isEmpty()) {
            addRow(rows, "Your movies", movies, null);
            addRow(rows, "Your series", series, null);
        }
    }

    private void addRow(LinearLayout parent, String title, List<Video> list, String tag) {
        TextView tv = new TextView(requireContext());
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder(title);
        if (tag != null) {
            int start = sb.length();
            sb.append("   ").append(tag);
            sb.setSpan(new android.text.style.RelativeSizeSpan(0.6f), start, sb.length(), 0);
            sb.setSpan(new android.text.style.ForegroundColorSpan(0xFF868A96), start, sb.length(), 0);
        }
        tv.setText(sb);
        tv.setTextColor(0xFFF4F4F6);
        tv.setTextSize(19);
        tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
        int pad = Ui.dp(requireContext(), 24);
        tv.setPadding(pad, Ui.dp(requireContext(), 28), pad, Ui.dp(requireContext(), 6));
        parent.addView(tv);

        RecyclerView rv = new RecyclerView(requireContext());
        rv.setLayoutManager(new LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false));
        rv.setClipToPadding(false);
        rv.setNestedScrollingEnabled(false);
        int p = Ui.dp(requireContext(), 18);
        rv.setPadding(p, 0, p, 0);
        PosterAdapter a = new PosterAdapter(Grid.rowCardWidth(requireContext()), Grid.rowCardHeight(requireContext()), true,
                it -> openDetail(it.video));
        a.submit(PosterAdapter.wrap(list));
        rv.setAdapter(a);
        parent.addView(rv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void buildDots(int n) {
        dots.removeAllViews();
        if (n < 2) return;
        for (int i = 0; i < n; i++) {
            View d = new View(requireContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(requireContext(), 8), Ui.dp(requireContext(), 8));
            lp.setMargins(Ui.dp(requireContext(), 4), 0, Ui.dp(requireContext(), 4), 0);
            dots.addView(d, lp);
        }
    }

    private void updateDots(int sel) {
        for (int i = 0; i < dots.getChildCount(); i++) {
            View d = dots.getChildAt(i);
            boolean on = i == sel;
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) d.getLayoutParams();
            lp.width = Ui.dp(requireContext(), on ? 26 : 8);
            d.setLayoutParams(lp);
            d.setBackground(Ui.round(on ? 0xFF5E81F4 : 0xFF3A3C44, Ui.dp(requireContext(), 4)));
        }
    }

    private void openDetail(Video v) {
        startActivity(new Intent(requireContext(), MovieDetailActivity.class)
                .putExtra(MovieDetailActivity.EXTRA_VIDEO_ID, v.id)
                .putExtra(MovieDetailActivity.EXTRA_VIDEO_JSON, v.toJson().toString()));
    }

    // ---------- hero cards ----------
    private class HeroAdapter extends RecyclerView.Adapter<HeroAdapter.H> {
        private final List<Video> list = new ArrayList<>();

        void submit(List<Video> l) { list.clear(); list.addAll(l); notifyDataSetChanged(); }

        @NonNull
        @Override
        public H onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new H(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_hero, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull H h, int position) {
            Video v = list.get(position);
            Ui.load(h.image, v.portrait());
            h.genre.setText(v.genre);
            h.genre.setVisibility(v.genre.isEmpty() ? View.GONE : View.VISIBLE);
            h.title.setText(v.title);
            String r = v.ratingText();
            h.meta.setText(r.isEmpty() ? v.meta() : v.meta() + "  •  " + r);
            Store.Entry last = Store.lastWatched(requireContext(), v.id);
            if (v.isTmdb()) {
                h.play.setText("Details");
                h.play.setOnClickListener(b -> openDetail(v));
            } else {
                h.play.setText(last != null && last.unfinished() ? "Resume" : getString(R.string.play));
                h.play.setOnClickListener(b -> Playback.playDefault(requireActivity(), v));
            }
            h.itemView.setOnClickListener(b -> openDetail(v));
        }

        @Override
        public int getItemCount() { return list.size(); }

        class H extends RecyclerView.ViewHolder {
            final ImageView image;
            final TextView genre, title, meta;
            final com.google.android.material.button.MaterialButton play;

            H(View v) {
                super(v);
                image = v.findViewById(R.id.heroImage);
                genre = v.findViewById(R.id.heroGenre);
                title = v.findViewById(R.id.heroTitle);
                meta = v.findViewById(R.id.heroMeta);
                play = v.findViewById(R.id.heroPlay);
            }
        }
    }
}
