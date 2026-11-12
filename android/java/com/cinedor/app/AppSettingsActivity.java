package com.cinedor.app;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

/** Library housekeeping (watch history, My List). */
public class AppSettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setBackgroundDrawable(Ui.appBackground(this));
        setContentView(R.layout.activity_app_settings);

        // Keep the header below the status bar
        android.view.View root = findViewById(R.id.appSettingsRoot);
        root.setPadding(0, Ui.statusBarHeight(this), 0, Ui.dp(this, 32));
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets b = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(b.left, Math.max(b.top, Ui.statusBarHeight(this)), b.right, b.bottom + Ui.dp(this, 32));
            return insets;
        });
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        findViewById(R.id.btnClearHistory).setOnClickListener(b -> confirm("Clear watch history?", () -> {
            Store.clearHistory(this);
            Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show();
        }));
        findViewById(R.id.btnClearFavorites).setOnClickListener(b -> confirm("Clear My List?", () -> {
            Store.clearFavorites(this);
            Toast.makeText(this, "My List cleared", Toast.LENGTH_SHORT).show();
        }));
        ((TextView) findViewById(R.id.about)).setText(getString(R.string.long_press_remove));
    }

    private void confirm(String msg, Runnable ok) {
        new AlertDialog.Builder(this)
                .setMessage(msg)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", (d, w) -> ok.run())
                .show();
    }
}
