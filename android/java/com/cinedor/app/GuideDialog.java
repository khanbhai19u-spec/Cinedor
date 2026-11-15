package com.cinedor.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

/** "README & user guide" popup: shown on start until "Don't show again", also reachable from Settings. */
public final class GuideDialog {

    private GuideDialog() {}

    public static void showIfNeeded(Activity a) {
        if (!Prefs.guideHidden(a)) show(a);
    }

    public static void show(Activity a) {
        if (a.isFinishing() || a.isDestroyed()) return;
        View v = LayoutInflater.from(a).inflate(R.layout.dialog_guide, null);
        Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setContentView(v);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawableResource(R.drawable.bg_guide_dialog);
            int width = Math.min(Ui.screenWidth(a) - Ui.dp(a, 32), Ui.dp(a, 420));
            int maxH = Math.round(a.getResources().getDisplayMetrics().heightPixels * 0.88f);
            w.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
            w.setDimAmount(0.65f);
            final int limit = maxH;
            v.post(() -> {
                if (v.getHeight() > limit && w != null) w.setLayout(width, limit);
            });
        }
        v.findViewById(R.id.guideOk).setOnClickListener(x -> d.dismiss());
        v.findViewById(R.id.guideNever).setOnClickListener(x -> {
            Prefs.setGuideHidden(a, true);
            d.dismiss();
        });
        v.findViewById(R.id.guideFeedback).setOnClickListener(x -> {
            d.dismiss();
            feedback(a);
        });
        d.show();
    }

    /** Opens your Telegram link when set, otherwise lets the user pick any app (mail, chat…) to send feedback. */
    static void feedback(Activity a) {
        String tg = a.getString(R.string.telegram_url).trim();
        Intent i;
        if (!tg.isEmpty()) {
            i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(tg));
        } else {
            i = Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, "Cinedor feedback")
                    .putExtra(Intent.EXTRA_TEXT, "Cinedor " + version(a) + "\n\nTell us what happened or what you'd like "
                            + "(title name & year if it's about a movie or series):\n\n"), "Send feedback");
        }
        try { a.startActivity(i); }
        catch (Exception e) { Toast.makeText(a, "No app found to send feedback", Toast.LENGTH_SHORT).show(); }
    }

    private static String version(Activity a) {
        try {
            String v = a.getPackageManager().getPackageInfo(a.getPackageName(), 0).versionName;
            return v == null ? "" : v;
        } catch (Exception e) {
            return "";
        }
    }
}
