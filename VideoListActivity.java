package com.cinedor.app;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Lists every video on your personal server. Tap one -> MovieDetailActivity. */
public class VideoListActivity extends AppCompatActivity {

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<String> ids = new ArrayList<>();
    private final List<String> titles = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private ProgressBar progress;
    private TextView empty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_list);
        progress = findViewById(R.id.progress);
        empty = findViewById(R.id.empty);
        ListView list = findViewById(R.id.list);
        adapter = new ArrayAdapter<>(this, R.layout.item_video, R.id.itemTitle, titles);
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> {
            Intent i = new Intent(this, MovieDetailActivity.class);
            i.putExtra(MovieDetailActivity.EXTRA_VIDEO_ID, ids.get(pos));
            startActivity(i);
        });
        findViewById(R.id.btnRefresh).setOnClickListener(v -> load());
        load();
    }

    private void load() {
        progress.setVisibility(View.VISIBLE);
        empty.setVisibility(View.GONE);
        io.execute(() -> {
            try {
                JSONObject json = new JSONObject(ApiConfig.get("/api/videos"));
                JSONArray items = json.getJSONArray("items");
                List<String> newIds = new ArrayList<>(), newTitles = new ArrayList<>();
                for (int i = 0; i < items.length(); i++) {
                    JSONObject o = items.getJSONObject(i);
                    newIds.add(o.getString("id"));
                    String year = o.optString("year", "");
                    newTitles.add(o.getString("title") + (year.isEmpty() || "null".equals(year) ? "" : "  (" + year + ")"));
                }
                main.post(() -> {
                    ids.clear(); ids.addAll(newIds);
                    titles.clear(); titles.addAll(newTitles);
                    adapter.notifyDataSetChanged();
                    progress.setVisibility(View.GONE);
                    if (ids.isEmpty()) { empty.setText(R.string.no_videos); empty.setVisibility(View.VISIBLE); }
                });
            } catch (Exception e) {
                main.post(() -> {
                    progress.setVisibility(View.GONE);
                    empty.setText(getString(R.string.server_error, e.getMessage()));
                    empty.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }
}
