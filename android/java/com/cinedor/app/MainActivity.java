package com.cinedor.app;

import android.os.Bundle;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import android.content.res.ColorStateList;

/** Home / Search / Library / Settings with a floating pill navigation bar. */
public class MainActivity extends AppCompatActivity {

    /** Tabs refresh their content when shown again. */
    public interface Tab { void onShown(); }

    private static final String[] TAGS = {"home", "search", "library", "settings"};
    private static final int[] ICONS = {R.drawable.ic_movie, R.drawable.ic_search, R.drawable.ic_library, R.drawable.ic_settings};
    private static final int[] LABELS = {R.string.home, R.string.search, R.string.library, R.string.settings};

    private LinearLayout nav;
    private final View[] navItems = new View[4];
    private int current = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ApiConfig.init(this);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setBackgroundDrawable(Ui.appBackground(this));
        setContentView(R.layout.activity_main);

        View host = findViewById(R.id.fragmentHost);
        nav = findViewById(R.id.navBar);
        nav.setBackground(Ui.round(0xE61A1B21, Ui.dp(this, 36), Ui.dp(this, 1), 0xFF2C2E35));

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), (v, insets) -> {
            Insets b = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            host.setPadding(b.left, b.top, b.right, 0);
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) nav.getLayoutParams();
            lp.bottomMargin = b.bottom + Ui.dp(this, 14);
            nav.setLayoutParams(lp);
            return insets;
        });

        LayoutInflater inf = LayoutInflater.from(this);
        for (int i = 0; i < 4; i++) {
            View item = inf.inflate(R.layout.item_nav, nav, false);
            ((ImageView) item.findViewById(R.id.navIcon)).setImageResource(ICONS[i]);
            ((TextView) item.findViewById(R.id.navLabel)).setText(LABELS[i]);
            item.setContentDescription(getString(LABELS[i]));
            final int idx = i;
            item.setOnClickListener(v -> select(idx));
            nav.addView(item);
            navItems[i] = item;
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (current != 0) select(0);
                else { setEnabled(false); getOnBackPressedDispatcher().onBackPressed(); }
            }
        });

        select(savedInstanceState != null ? savedInstanceState.getInt("tab", 0) : 0);
    }

    private Fragment create(int i) {
        switch (i) {
            case 1: return new SearchFragment();
            case 2: return new LibraryFragment();
            case 3: return new SettingsFragment();
            default: return new HomeFragment();
        }
    }

    public void select(int idx) {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction tx = fm.beginTransaction().setReorderingAllowed(true);
        Fragment target = null;
        for (int i = 0; i < 4; i++) {
            Fragment f = fm.findFragmentByTag(TAGS[i]);
            if (i == idx) {
                if (f == null) { f = create(i); tx.add(R.id.fragmentHost, f, TAGS[i]); }
                else tx.show(f);
                target = f;
            } else if (f != null) {
                tx.hide(f);
            }
        }
        tx.commitNow();
        boolean changed = current != idx;
        current = idx;
        styleNav(changed);
        if (target instanceof Tab && target.isAdded()) ((Tab) target).onShown();
    }

    private void styleNav(boolean animate) {
        if (animate) TransitionManager.beginDelayedTransition(nav, new AutoTransition().setDuration(220));
        int accent = ContextCompat.getColor(this, R.color.accent);
        for (int i = 0; i < 4; i++) {
            View item = navItems[i];
            boolean on = i == current;
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) item.getLayoutParams();
            lp.weight = on ? 2.1f : 1f;
            item.setLayoutParams(lp);
            item.setBackground(on ? Ui.round(0xFF2A3566, Ui.dp(this, 30)) : null);
            item.findViewById(R.id.navLabel).setVisibility(on ? View.VISIBLE : View.GONE);
            ImageViewCompat.setImageTintList(item.findViewById(R.id.navIcon),
                    ColorStateList.valueOf(on ? accent : 0xFFD5D7DE));
            item.setSelected(on);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        out.putInt("tab", Math.max(current, 0));
        super.onSaveInstanceState(out);
    }

    /** Lets a tab ask the activity to reload (e.g. after server change). */
    public void refreshAll() {
        for (String t : TAGS) {
            Fragment f = getSupportFragmentManager().findFragmentByTag(t);
            if (f instanceof HomeFragment) ((HomeFragment) f).reload(true);
        }
    }
}
