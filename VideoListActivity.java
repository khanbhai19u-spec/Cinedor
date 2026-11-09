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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Handles movie list and passes TMDB ID to MovieDetailActivity. */
public class VideoListActivity extends AppCompatActivity {

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

        // মুভি বা সিরিজে ক্লিক করলে তার TMDB ID সহ MovieDetailActivity তে চলে যাবে
        list.setOnItemClickListener((p, v, pos, id) -> {
            Intent i = new Intent(this, MovieDetailActivity.class);
            i.putExtra(MovieDetailActivity.EXTRA_VIDEO_ID, ids.get(pos));
            startActivity(i);
        });

        findViewById(R.id.btnRefresh).setOnClickListener(v -> loadSampleMovies());
        
        // অ্যাপ চালু হওয়ার পর ডিফল্ট কিছু পপুলার TMDB ID দিয়ে লিস্ট লোড করা (অথবা তোর TMDB API লিস্ট)
        loadSampleMovies();
    }

    private void loadSampleMovies() {
        progress.setVisibility(View.VISIBLE);
        empty.setVisibility(View.GONE);

        // এখানে ডেমো হিসেবে কিছু জনপ্রিয় মুভির TMDB ID দেওয়া হলো 
        // (পরে এগুলো তুই TMDB API লিস্ট বা তোর হোম পেজ থেকে ডায়নামিক করে নিতে পারবি)
        List<String> movieIds = new ArrayList<>();
        List<String> movieTitles = new ArrayList<>();

        // Fight Club (ID: 550)
        movieIds.add("550");
        movieTitles.add("Fight Club (1999)");

        // Interstellar (ID: 157336)
        movieIds.add("157336");
        movieTitles.add("Interstellar (2014)");

        // Inception (ID: 27205)
        movieIds.add("27205");
        movieTitles.add("Inception (2010)");

        // The Dark Knight (ID: 155)
        movieIds.add("155");
        movieTitles.add("The Dark Knight (2008)");

        Handler mainHandler = new Handler(Looper.getMainLooper());
        mainHandler.post(() -> {
            ids.clear();
            ids.addAll(movieIds);
            
            titles.clear();
            titles.addAll(movieTitles);
            
            adapter.notifyDataSetChanged();
            progress.setVisibility(View.GONE);
            
            if (ids.isEmpty()) {
                empty.setText(R.string.no_videos);
                empty.setVisibility(View.VISIBLE);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
