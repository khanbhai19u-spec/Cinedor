package com.cinedor.app;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

/** Profile header + menu (My List, Downloads, categories, Telegram, App Settings, About). */
public class SettingsFragment extends Fragment implements MainActivity.Tab {

    private TextView name, count;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle state) {
        name = v.findViewById(R.id.profileName);
        count = v.findViewById(R.id.profileCount);
        v.findViewById(R.id.avatar).setBackground(Ui.round(0xFFE11D2E, Ui.dp(requireContext(), 18)));
        v.findViewById(R.id.profileHeader).setOnClickListener(x -> editName());

        LinearLayout top = v.findViewById(R.id.menuTop);
        add(top, R.drawable.ic_person_outline, "Profile Settings", "", this::editName);
        add(top, R.drawable.ic_add, "My List", "", () -> openLibrary(0));
        add(top, R.drawable.ic_download, "Downloads", "", () -> openLibrary(2));
        add(top, R.drawable.ic_tv, "K-Drama", "Korean series", () -> CategoryActivity.open(requireContext(), Tmdb.K_DRAMA));
        add(top, R.drawable.ic_tv, "C-Drama", "Chinese series", () -> CategoryActivity.open(requireContext(), Tmdb.C_DRAMA));
        add(top, R.drawable.ic_film, "Anime", "Japanese", () -> CategoryActivity.open(requireContext(), Tmdb.ANIME));
        add(top, R.drawable.ic_movie, "Hindi movies", "Bollywood", () -> CategoryActivity.open(requireContext(), Tmdb.BOLLYWOOD));
        add(top, R.drawable.ic_star, "Top rated", "", () -> CategoryActivity.open(requireContext(), Tmdb.TOP_RATED));

        LinearLayout bottom = v.findViewById(R.id.menuBottom);
        String telegram = getString(R.string.telegram_url).trim();
        if (!telegram.isEmpty()) add(bottom, R.drawable.ic_send, "Join the Telegram Channel", "", () -> openUrl(telegram));
        add(bottom, R.drawable.ic_tune, "App Settings", "", () ->
                startActivity(new Intent(requireContext(), AppSettingsActivity.class)));
        add(bottom, R.drawable.ic_info, "About", "", this::about);
        refresh();
    }

    @Override public void onShown() { refresh(); }
    @Override public void onResume() { super.onResume(); refresh(); }

    private void refresh() {
        if (name == null || !isAdded()) return;
        name.setText(prefs().getString("name", "Your Cinedor"));
        int n = Store.favorites(requireContext()).size();
        count.setText(n + " in My List");
    }

    private void add(LinearLayout box, int icon, String title, String value, Runnable action) {
        View row = LayoutInflater.from(requireContext()).inflate(R.layout.item_menu, box, false);
        ((ImageView) row.findViewById(R.id.menuIcon)).setImageResource(icon);
        ((TextView) row.findViewById(R.id.menuTitle)).setText(title);
        TextView val = row.findViewById(R.id.menuValue);
        val.setText(value);
        val.setVisibility(value.isEmpty() ? View.GONE : View.VISIBLE);
        row.setOnClickListener(x -> action.run());
        box.addView(row);
    }

    private android.content.SharedPreferences prefs() {
        return requireContext().getSharedPreferences("profile", Context.MODE_PRIVATE);
    }

    private void editName() {
        EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setText(prefs().getString("name", "Your Cinedor"));
        input.setSelectAllOnFocus(true);
        FrameLayout wrap = new FrameLayout(requireContext());
        int p = Ui.dp(requireContext(), 22);
        wrap.setPadding(p, Ui.dp(requireContext(), 8), p, 0);
        wrap.addView(input);
        new AlertDialog.Builder(requireContext())
                .setTitle("Profile name")
                .setView(wrap)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    String s = input.getText().toString().trim();
                    prefs().edit().putString("name", s.isEmpty() ? "Your Cinedor" : s).apply();
                    refresh();
                })
                .show();
    }

    private void openLibrary(int tab) {
        if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).openLibrary(tab);
    }

    private void openUrl(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception e) { Toast.makeText(requireContext(), "Can't open link", Toast.LENGTH_SHORT).show(); }
    }

    private void about() {
        String version = "";
        try {
            version = requireContext().getPackageManager().getPackageInfo(requireContext().getPackageName(), 0).versionName;
            if (version == null) version = "";
        } catch (Exception ignored) {}
        new AlertDialog.Builder(requireContext())
                .setTitle("Cinedor " + version)
                .setMessage("Your pocket cinema guide.\n\n"
                        + "• Discover what's trending, K-Drama, C-Drama, Anime and more\n"
                        + "• Watch trailers and see ratings & cast\n"
                        + "• Keep your favourites in My List\n\n"
                        + "Made with ❤ for movie lovers.\n\nData: TMDB")
                .setPositiveButton("OK", null)
                .show();
    }
}
