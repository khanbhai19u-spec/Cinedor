package com.cinedor.app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

/** Server address / token and library housekeeping. */
public class SettingsFragment extends Fragment implements MainActivity.Tab {

    private EditText server, token;
    private TextView status;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle state) {
        server = v.findViewById(R.id.inputServer);
        token = v.findViewById(R.id.inputToken);
        status = v.findViewById(R.id.serverStatus);
        server.setText(ApiConfig.baseUrl());
        token.setText(ApiConfig.token());

        v.findViewById(R.id.btnSave).setOnClickListener(b -> {
            ApiConfig.save(requireContext(), server.getText().toString(), token.getText().toString());
            server.setText(ApiConfig.baseUrl());
            status.setText("Connecting to " + ApiConfig.baseUrl() + " …");
            Catalog.load(true, (items, error) -> {
                if (getView() == null) return;
                status.setText(error == null ? "✓ Connected — " + items.size() + " titles" : "✗ " + error);
                if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).refreshAll();
            });
        });

        v.findViewById(R.id.btnClearHistory).setOnClickListener(b -> confirm("Clear watch history?", () -> {
            Store.clearHistory(requireContext());
            Toast.makeText(requireContext(), "History cleared", Toast.LENGTH_SHORT).show();
        }));
        v.findViewById(R.id.btnClearFavorites).setOnClickListener(b -> confirm("Clear all favorites?", () -> {
            Store.clearFavorites(requireContext());
            Toast.makeText(requireContext(), "Favorites cleared", Toast.LENGTH_SHORT).show();
        }));

        String version = "";
        try {
            version = requireContext().getPackageManager().getPackageInfo(requireContext().getPackageName(), 0).versionName;
            if (version == null) version = "";
        } catch (Exception ignored) {}
        ((TextView) v.findViewById(R.id.about)).setText("Cinedor " + version
                + "\nPlays videos from your own Cinedor server.\n" + getString(R.string.long_press_remove));
    }

    @Override
    public void onShown() {
        if (server != null && !server.hasFocus()) server.setText(ApiConfig.baseUrl());
    }

    private void confirm(String msg, Runnable ok) {
        new AlertDialog.Builder(requireContext())
                .setMessage(msg)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", (d, w) -> ok.run())
                .show();
    }
}
