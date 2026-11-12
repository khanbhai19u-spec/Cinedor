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

        RecyclerView rv = v.findViewById(R.id.results);
        rv.setLayoutManager(new GridLayoutManager(requireContext(), Grid.COLUMNS));
        adapter = new PosterAdapter(Grid.cardWidth(requireContext()), Grid.cardHeight(requireContext()), true, it ->
                startActivity(new Intent(requireContext(), MovieDetailActivity.class)
                        .putExtra(MovieDetailActivity.EXTRA_VIDEO_ID, it.video.id)
                        .putExtra(MovieDetailActivity.EXTRA_VIDEO_JSON, it.video.toJson().toString())));
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
            if (action == EditorInfo.IME_ACTION_SEARCH) { hideKeyboard(); return true; }
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
        if (q.isEmpty()) {
            adapter.submit(new ArrayList<>());
            showEmpty(R.drawable.ic_film, getString(R.string.search_empty_title), getString(R.string.search_empty_sub));
            return;
        }
        List<Video> hits = new ArrayList<>();
        for (Video v : Catalog.items()) if (v.matches(q)) hits.add(v);
        adapter.submit(PosterAdapter.wrap(hits));
        if (hits.isEmpty()) showEmpty(R.drawable.ic_search, getString(R.string.search_none, q),
                Catalog.isLoaded() ? "Try another word." : "Server not reachable — check Settings.");
        else empty.setVisibility(View.GONE);
    }

    private void showEmpty(int icon, String title, String sub) {
        emptyIcon.setImageResource(icon);
        emptyTitle.setText(title);
        emptySub.setText(sub);
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
