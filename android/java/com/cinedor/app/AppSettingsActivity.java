package com.cinedor.app;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.materialswitch.MaterialSwitch;

/** Appearance, player, downloads, privacy and about — grouped cards like a modern settings page. */
public class AppSettingsActivity extends AppCompatActivity {

    private static final int ACCENT = 0xFF5E81F4;
    private static final String RELEASES = "https://github.com/khanbhai19u-spec/Cinedor/releases/latest";

    private LinearLayout content;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setBackgroundDrawable(Ui.appBackground(this));
        setContentView(R.layout.activity_app_settings);
        content = findViewById(R.id.content);

        View root = findViewById(R.id.appSettingsRoot);
        root.setPadding(0, Ui.statusBarHeight(this), 0, Ui.dp(this, 32));
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets b = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(b.left, Math.max(b.top, Ui.statusBarHeight(this)), b.right, b.bottom + Ui.dp(this, 32));
            return insets;
        });
        build();
    }

    private void build() {
        content.removeAllViews();
        topBar();

        // ----- Appearance -----
        section(R.drawable.ic_palette, "Appearance");
        LinearLayout look = card();
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout themes = new LinearLayout(this);
        themes.setPadding(dp(14), dp(14), dp(14), dp(4));
        hs.addView(themes);
        String cur = Prefs.theme(this);
        themes.addView(themeCard(Prefs.THEME_GLASS, "Glass", 0xFF101116, 0xFF1C1E26, cur));
        themes.addView(themeCard(Prefs.THEME_MATTE, "Matte", 0xFF0B0B0D, 0xFF17171A, cur));
        themes.addView(themeCard(Prefs.THEME_MATERIAL, "Material", 0xFF1A1B24, 0xFF262838, cur));
        look.addView(hs);
        TextView hint = text(themeHint(cur), 13, 0xFF9A9DA8, false);
        hint.setPadding(dp(18), dp(4), dp(18), dp(14));
        look.addView(hint);

        // ----- Player -----
        section(R.drawable.ic_touch, "Player");
        LinearLayout player = card();
        player.addView(valueRow("Double-tap skip", null, Prefs.skipSeconds(this) + " s", v -> pickSkip()));
        player.addView(switchRow("Hold to speed up", "Press and hold the video to play at 2×.",
                Prefs.holdToSpeed(this), on -> Prefs.setHoldToSpeed(this, on)));
        player.addView(switchRow("Small window when leaving the app", null,
                Prefs.pip(this), on -> Prefs.setPip(this, on)));

        // ----- Downloads -----
        section(R.drawable.ic_download, "Downloads");
        LinearLayout dl = card();
        dl.addView(switchRow("Download on Wi-Fi only", null, Prefs.wifiOnly(this), on -> Prefs.setWifiOnly(this, on)));
        dl.addView(valueRow("Download folder", Prefs.downloadFolderLabel(this), null, v -> pickFolder()));

        // ----- Privacy -----
        section(R.drawable.ic_lock, "Privacy");
        LinearLayout priv = card();
        priv.addView(valueRow("Clear search history", null, null, v -> confirm("Clear search history?", () -> {
            Prefs.clearSearches(this);
            toast("Search history cleared");
        })));
        priv.addView(valueRow("Clear watch history", "Continue watching and History.", null, v -> confirm("Clear watch history?", () -> {
            Store.clearHistory(this);
            toast("Watch history cleared");
        })));
        priv.addView(valueRow("Clear My List", null, null, v -> confirm("Clear My List?", () -> {
            Store.clearFavorites(this);
            toast("My List cleared");
        })));

        // ----- About -----
        section(R.drawable.ic_info, "About");
        LinearLayout about = card();
        TextView ver = text("Cinedor " + version(), 14, 0xFF9A9DA8, false);
        ver.setPadding(dp(18), dp(14), dp(18), dp(4));
        about.addView(ver);
        about.addView(valueRow("Share Cinedor", "Send the download link to a friend.", null, v -> share()));
        View reset = valueRow("Reset settings to default", "History and lists are kept.", null, v -> confirm("Reset all settings?", () -> {
            Prefs.reset(this);
            getWindow().setBackgroundDrawable(Ui.appBackground(this));
            build();
            toast("Settings reset");
        }));
        ((TextView) reset.findViewWithTag("title")).setTextColor(0xFFFF6B6B);
        about.addView(reset);
    }

    // ---------- pieces ----------
    private int dp(float v) { return Ui.dp(this, v); }

    private void topBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(0, dp(14), 0, dp(6));
        ImageButton back = new ImageButton(this);
        back.setImageResource(R.drawable.ic_back);
        back.setBackgroundResource(R.drawable.bg_circle_dark);
        back.setImageTintList(ColorStateList.valueOf(0xFFF4F4F6));
        back.setContentDescription(getString(R.string.back));
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView t = text("Settings", 22, 0xFFF4F4F6, true);
        t.setPadding(dp(14), 0, 0, 0);
        bar.addView(t);
        content.addView(bar);
    }

    private void section(int icon, String title) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(22), 0, dp(10));
        FrameLayout dot = new FrameLayout(this);
        dot.setBackground(Ui.round(0x335E81F4, dp(16)));
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        iv.setImageTintList(ColorStateList.valueOf(ACCENT));
        dot.addView(iv, new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER));
        row.addView(dot, new LinearLayout.LayoutParams(dp(32), dp(32)));
        TextView t = text(title, 18, 0xFFF4F4F6, true);
        t.setPadding(dp(12), 0, 0, 0);
        row.addView(t);
        content.addView(row);
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(Ui.round(0xB31A1B21, dp(22), dp(1), 0xFF2A2C33));
        c.setPadding(0, dp(4), 0, dp(4));
        content.addView(c, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return c;
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(t.getTypeface(), Typeface.BOLD);
        return t;
    }

    private LinearLayout rowBase(String title, String sub) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(56));
        row.setPadding(dp(18), dp(10), dp(14), dp(10));
        row.setBackgroundResource(android.R.drawable.list_selector_background);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView t = text(title, 16, 0xFFF4F4F6, false);
        t.setTag("title");
        texts.addView(t);
        if (sub != null && !sub.isEmpty()) {
            TextView s = text(sub, 13, 0xFF9A9DA8, false);
            s.setPadding(0, dp(2), 0, 0);
            texts.addView(s);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    private View valueRow(String title, String sub, String value, View.OnClickListener click) {
        LinearLayout row = rowBase(title, sub);
        if (value != null) {
            TextView v = text(value, 14, 0xFF9A9DA8, false);
            v.setPadding(dp(8), 0, dp(2), 0);
            row.addView(v);
            ImageView chev = new ImageView(this);
            chev.setImageResource(R.drawable.ic_chevron);
            chev.setImageTintList(ColorStateList.valueOf(0xFF9A9DA8));
            row.addView(chev, new LinearLayout.LayoutParams(dp(20), dp(20)));
        }
        row.setOnClickListener(click);
        return row;
    }

    interface Toggle { void on(boolean on); }

    private View switchRow(String title, String sub, boolean checked, Toggle toggle) {
        LinearLayout row = rowBase(title, sub);
        MaterialSwitch sw = new MaterialSwitch(this);
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener((b, on) -> toggle.on(on));
        row.addView(sw);
        row.setOnClickListener(v -> sw.toggle());
        return row;
    }

    private View themeCard(String key, String name, int bg, int tile, String current) {
        boolean on = key.equals(current);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(0, 0, dp(12), 0);

        LinearLayout prev = new LinearLayout(this);
        prev.setOrientation(LinearLayout.VERTICAL);
        prev.setPadding(dp(10), dp(10), dp(10), dp(10));
        prev.setBackground(Ui.round(bg, dp(16), dp(on ? 2 : 1), on ? ACCENT : 0xFF2E3038));
        prev.addView(bar(dp(34), dp(4), 0xFFEDEDF0, dp(2)));
        LinearLayout tiles = new LinearLayout(this);
        tiles.setPadding(0, dp(8), 0, dp(8));
        for (int i = 0; i < 3; i++) {
            View tv = new View(this);
            tv.setBackground(Ui.round(tile, dp(5)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(18), dp(26));
            if (i < 2) lp.setMarginEnd(dp(4));
            tiles.addView(tv, lp);
        }
        prev.addView(tiles);
        prev.addView(bar(dp(46), dp(3), 0xFF4A4C55, dp(2)));
        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPadding(0, dp(10), 0, 0);
        bottom.addView(bar(dp(40), dp(12), Prefs.THEME_MATERIAL.equals(key) ? 0xFF9DB2FF : ACCENT, dp(6)));
        View sp = new View(this);
        bottom.addView(sp, new LinearLayout.LayoutParams(0, 1, 1f));
        View circle = new View(this);
        circle.setBackground(Ui.round(0xFF3A3C44, dp(6)));
        bottom.addView(circle, new LinearLayout.LayoutParams(dp(12), dp(12)));
        prev.addView(bottom);
        col.addView(prev, new LinearLayout.LayoutParams(dp(86), ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView label = text((on ? "✓ " : "") + name, 14, on ? 0xFFF4F4F6 : 0xFF9A9DA8, on);
        label.setPadding(0, dp(8), 0, 0);
        col.addView(label);
        col.setOnClickListener(v -> {
            Prefs.setTheme(this, key);
            getWindow().setBackgroundDrawable(Ui.appBackground(this));
            build();
            toast("Restart the app to apply it everywhere");
        });
        return col;
    }

    private View bar(int w, int h, int color, int r) {
        View v = new View(this);
        v.setBackground(Ui.round(color, r));
        v.setLayoutParams(new LinearLayout.LayoutParams(w, h));
        return v;
    }

    private String themeHint(String t) {
        if (Prefs.THEME_MATTE.equals(t)) return "Flat, deep black — easy on the eyes and the battery";
        if (Prefs.THEME_MATERIAL.equals(t)) return "Soft blue tint across the whole app";
        return "Clear dark glass with a soft glow";
    }

    // ---------- actions ----------
    private void pickSkip() {
        int[] opts = {5, 10, 15, 30};
        String[] names = {"5 s", "10 s", "15 s", "30 s"};
        int sel = 1;
        for (int i = 0; i < opts.length; i++) if (opts[i] == Prefs.skipSeconds(this)) sel = i;
        new AlertDialog.Builder(this).setTitle("Double-tap skip")
                .setSingleChoiceItems(names, sel, (d, w) -> { Prefs.setSkipSeconds(this, opts[w]); d.dismiss(); build(); })
                .show();
    }

    private void pickFolder() {
        String[] keys = {"download", "movies"};
        String[] names = {"Download/Cinedor", "Movies/Cinedor"};
        int sel = "movies".equals(Prefs.downloadFolder(this)) ? 1 : 0;
        new AlertDialog.Builder(this).setTitle("Download folder")
                .setSingleChoiceItems(names, sel, (d, w) -> { Prefs.setDownloadFolder(this, keys[w]); d.dismiss(); build(); })
                .show();
    }

    private void share() {
        Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "Try Cinedor — browse movies & series, trailers and your own list: " + RELEASES);
        startActivity(Intent.createChooser(i, "Share Cinedor"));
    }

    private String version() {
        try {
            String v = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            return v == null ? "" : v;
        } catch (Exception e) {
            return "";
        }
    }

    private void confirm(String msg, Runnable ok) {
        new AlertDialog.Builder(this).setMessage(msg).setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", (d, w) -> ok.run()).show();
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
