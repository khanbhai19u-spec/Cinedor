package com.cinedor.app;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** Instant search over the server catalog. */
public class SearchFragment extends Fragment implements MainActivity.Tab {

    private EditText input;
    private View empty;
    private ImageView emptyIcon;
    private TextView emptyTitle, emptySub;
    private PosterAdapter adapter;
    private View historyScroll;
    private android.widget.LinearLayout historyBox;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable pending;
    private int seq;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_search, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle state) {
        input = v.findViewById(R.id.searchInput);
        empty = v.findViewById(R.id.emptyBox);
        emptyIcon = v.findViewById(R.id.emptyIcon);
        emptyTitle = v.findViewById(R.id.emptyTitle);
        emptySub = v.findViewById(R.id.emptySub);
        historyScroll = v.findViewById(R.id.historyScroll);
        historyBox = v.findViewById(R.id.historyBox);

        RecyclerView rv = v.findViewById(R.id.results);
        rv.setLayoutManager(new GridLayoutManager(requireContext(), Grid.columns(requireContext())));
        adapter = new PosterAdapter(Grid.cardWidth(requireContext()), Grid.cardHeight(requireContext()), true, it -> {
                Prefs.addSearch(requireContext(), input.getText().toString());
                startActivity(new Intent(requireContext(), MovieDetailActivity.class)
                        .putExtra(MovieDetailActivity.EXTRA_VIDEO_ID, it.video.id)
                        .putExtra(MovieDetailActivity.EXTRA_VIDEO_JSON, it.video.toJson().toString()));
        });
        rv.setAdapter(adapter);
        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrollStateChanged(@NonNull RecyclerView r, int st) {
                if (st == RecyclerView.SCROLL_STATE_DRAGGING) hideKeyboard();
            }
        });

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { run(); }
        });
        input.setOnEditorActionListener((t, action, e) -> {
            if (action == EditorInfo.IME_ACTION_SEARCH) {
                Prefs.addSearch(requireContext(), input.getText().toString());
                hideKeyboard();
                return true;
            }
            return false;
        });
        run();
    }

    @Override
    public void onShown() {
        if (getView() == null) return;
        if (!Catalog.isLoaded()) Catalog.load(false, (items, err) -> { if (getView() != null) run(); });
        else run();
    }

    private void run() {
        String q = input.getText().toString().trim();
        if (pending != null) handler.removeCallbacks(pending);
        final int my = ++seq;
        if (q.isEmpty()) {
            adapter.submit(new ArrayList<>());
            showHistory();
            return;
        }
        historyScroll.setVisibility(View.GONE);
        List<Video> hits = new ArrayList<>();
        for (Video v : Catalog.items()) if (v.matches(q)) hits.add(v);
        List<PosterAdapter.Item> local = PosterAdapter.wrap(hits);
        for (PosterAdapter.Item it : local) it.subtitle = "Your video • " + it.subtitle;
        adapter.submit(local);
        if (hits.isEmpty()) showEmpty(R.drawable.ic_search, getString(R.string.search_none, q), "Searching…");
        else empty.setVisibility(View.GONE);

        // TMDB results (info & trailers) follow your own titles
        pending = () -> Tmdb.search(q, (online, error) -> {
            if (getView() == null || my != seq) return;
            List<PosterAdapter.Item> all = new ArrayList<>(local);
            all.addAll(PosterAdapter.wrap(online));
            adapter.submit(all);
            if (all.isEmpty()) {
                showEmpty(R.drawable.ic_search, getString(R.string.search_none, q),
                        error != null ? error : "Try another word.");
            } else {
                empty.setVisibility(View.GONE);
            }
        });
        handler.postDelayed(pending, 400);
    }

    /** Recent searches (tap to search again, × to remove). */
    private void showHistory() {
        List<String> list = Prefs.searchHistory(requireContext());
        historyBox.removeAllViews();
        if (list.isEmpty()) {
            historyScroll.setVisibility(View.GONE);
            showEmpty(R.drawable.ic_film, getString(R.string.search_empty_title), "");
            return;
        }
        empty.setVisibility(View.GONE);
        historyScroll.setVisibility(View.VISIBLE);
        android.content.Context c = requireContext();

        android.widget.LinearLayout head = new android.widget.LinearLayout(c);
        head.setGravity(android.view.Gravity.CENTER_VERTICAL);
        head.setPadding(Ui.dp(c, 22), Ui.dp(c, 4), Ui.dp(c, 12), Ui.dp(c, 6));
        TextView title = new TextView(c);
        title.setText("Recent searches");
        title.setTextColor(0xFFF4F4F6);
        title.setTextSize(16);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        head.addView(title, new android.widget.LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView clear = new TextView(c);
        clear.setText("Clear");
        clear.setTextColor(0xFF5E81F4);
        clear.setTextSize(14);
        int p8 = Ui.dp(c, 8);
        clear.setPadding(p8, p8, p8, p8);
        clear.setOnClickListener(x -> { Prefs.clearSearches(c); showHistory(); });
        head.addView(clear);
        historyBox.addView(head);

        for (String q : list) {
            android.widget.LinearLayout row = new android.widget.LinearLayout(c);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(Ui.dp(c, 50));
            row.setPadding(Ui.dp(c, 22), 0, Ui.dp(c, 8), 0);
            row.setBackgroundResource(android.R.drawable.list_selector_background);
            ImageView ic = new ImageView(c);
            ic.setImageResource(R.drawable.ic_history);
            ic.setImageTintList(android.content.res.ColorStateList.valueOf(0xFF868A96));
            row.addView(ic, new android.widget.LinearLayout.LayoutParams(Ui.dp(c, 20), Ui.dp(c, 20)));
            TextView t = new TextView(c);
            t.setText(q);
            t.setTextColor(0xFFDADBE0);
            t.setTextSize(15);
            t.setSingleLine(true);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            t.setPadding(Ui.dp(c, 16), 0, Ui.dp(c, 8), 0);
            row.addView(t, new android.widget.LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            android.widget.ImageButton x = new android.widget.ImageButton(c);
            x.setImageResource(R.drawable.ic_close);
            x.setImageTintList(android.content.res.ColorStateList.valueOf(0xFF868A96));
            x.setBackgroundResource(android.R.color.transparent);
            x.setContentDescription("Remove");
            x.setOnClickListener(v -> { Prefs.removeSearch(c, q); showHistory(); });
            row.addView(x, new android.widget.LinearLayout.LayoutParams(Ui.dp(c, 40), Ui.dp(c, 40)));
            row.setOnClickListener(v -> {
                input.setText(q);
                input.setSelection(q.length());
                Prefs.addSearch(c, q);
                hideKeyboard();
            });
            historyBox.addView(row);
        }
    }

    /** Called from the Home search button. */
    public void focusInput() {
        if (input == null) return;
        input.requestFocus();
        android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager) requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
    }

    private void showEmpty(int icon, String title, String sub) {
        emptyIcon.setImageResource(icon);
        emptyTitle.setText(title);
        emptySub.setText(sub);
        emptySub.setVisibility(sub == null || sub.isEmpty() ? View.GONE : View.VISIBLE);
        empty.setVisibility(View.VISIBLE);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        input.clearFocus();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden && input != null) hideKeyboard();
    }
}
