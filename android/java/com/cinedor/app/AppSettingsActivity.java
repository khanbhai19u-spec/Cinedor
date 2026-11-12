package com.cinedor.app;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

/** TMDB key / language, optional personal server, and clearing your library. */
public class AppSettingsActivity extends AppCompatActivity {

    private static final String[][] LANGS = {
            {"en-US", "English"}, {"bn-BD", "বাংলা"}, {"hi-IN", "हिन्दी"}, {"ko-KR", "한국어"},
            {"zh-CN", "中文"}, {"ja-JP", "日本語"}, {"ar-SA", "العربية"}, {"es-ES", "Español"},
    };

    private EditText server, token, tmdbKey;
    private TextView status, tmdbStatus, btnLanguage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ApiConfig.init(this);
        Tmdb.init(this);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setBackgroundDrawable(Ui.appBackground(this));
        setContentView(R.layout.activity_app_settings);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.appSettingsRoot), (v, insets) -> {
            Insets b = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(b.left, b.top, b.right, b.bottom + Ui.dp(this, 32));
            return insets;
        });
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // ----- TMDB -----
        tmdbKey = findViewById(R.id.inputTmdb);
        tmdbStatus = findViewById(R.id.tmdbStatus);
        btnLanguage = findViewById(R.id.btnLanguage);
        tmdbKey.setText(Tmdb.ownKey());
        showTmdb();
        btnLanguage.setOnClickListener(v -> pickLanguage());
        findViewById(R.id.btnTmdb).setOnClickListener(v -> {
            String k = tmdbKey.getText().toString().trim();
            if (k.isEmpty()) {
                Tmdb.save("", null);
                showTmdb();
                Toast.makeText(this, "Using the built-in key", Toast.LENGTH_SHORT).show();
                return;
            }
            tmdbStatus.setText("Checking the key with TMDB…");
            Tmdb.test(k, (ok, error) -> {
                if (!ok) { tmdbStatus.setText("✗ " + error); return; }
                Tmdb.save(k, null);
                showTmdb();
                Toast.makeText(this, "TMDB key saved", Toast.LENGTH_SHORT).show();
            });
        });

        // ----- optional personal server -----
        server = findViewById(R.id.inputServer);
        token = findViewById(R.id.inputToken);
        status = findViewById(R.id.serverStatus);
        server.setText(ApiConfig.baseUrl());
        token.setText(ApiConfig.token());
        status.setText(ApiConfig.hasServer() ? "" : "Not used");
        findViewById(R.id.btnSave).setOnClickListener(b -> {
            ApiConfig.save(this, server.getText().toString(), token.getText().toString());
            server.setText(ApiConfig.baseUrl());
            if (!ApiConfig.hasServer()) {
                Catalog.load(true, (items, e) -> {});
                status.setText("Server turned off — the app uses TMDB only");
                return;
            }
            status.setText("Connecting to " + ApiConfig.baseUrl() + " …");
            Catalog.load(true, (items, error) -> status.setText(error == null
                    ? "✓ Connected — " + items.size() + " titles" : "✗ " + error));
        });

        // ----- library -----
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

    private void showTmdb() {
        String lang = Tmdb.language();
        for (String[] l : LANGS) if (l[0].equals(lang)) lang = l[1];
        btnLanguage.setText("Language: " + lang);
        if (!Tmdb.ownKey().isEmpty()) tmdbStatus.setText("✓ Using your own key");
        else if (Tmdb.hasBuiltInKey()) tmdbStatus.setText("✓ Using the built-in key (GitHub secret)");
        else tmdbStatus.setText("No key yet — add the TMDB_API_KEY secret on GitHub, or paste a key above");
    }

    private void pickLanguage() {
        String[] names = new String[LANGS.length];
        int sel = 0;
        for (int i = 0; i < LANGS.length; i++) {
            names[i] = LANGS[i][1];
            if (LANGS[i][0].equals(Tmdb.language())) sel = i;
        }
        new AlertDialog.Builder(this)
                .setTitle("TMDB language")
                .setSingleChoiceItems(names, sel, (d, which) -> {
                    Tmdb.save(null, LANGS[which][0]);
                    showTmdb();
                    d.dismiss();
                })
                .show();
    }

    private void confirm(String msg, Runnable ok) {
        new AlertDialog.Builder(this)
                .setMessage(msg)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", (d, w) -> ok.run())
                .show();
    }
}
