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

    /**
     * With a personal server: a small form that sends the message to Cinedor Console.
     * Without one: your Telegram link if set, otherwise the system share sheet.
     */
    static void feedback(Activity a) {
        if (ApiConfig.hasServer()) { form(a); return; }
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

    private static void form(Activity a) {
        android.widget.LinearLayout box = new android.widget.LinearLayout(a);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        int p = Ui.dp(a, 20);
        box.setPadding(p, Ui.dp(a, 8), p, 0);
        android.widget.EditText msg = new android.widget.EditText(a);
        msg.setHint("Describe the problem or your idea (add the title & year for movies/series)");
        msg.setMinLines(4);
        msg.setGravity(android.view.Gravity.TOP);
        msg.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        box.addView(msg);
        android.widget.EditText contact = new android.widget.EditText(a);
        contact.setHint("Your name or contact (optional)");
        contact.setSingleLine();
        box.addView(contact);

        new androidx.appcompat.app.AlertDialog.Builder(a)
                .setTitle("Send feedback")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Send", (d, w) -> {
                    String text = msg.getText().toString().trim();
                    if (text.length() < 5) { Toast.makeText(a, "Please write a little more", Toast.LENGTH_SHORT).show(); return; }
                    String who = contact.getText().toString().trim();
                    String ver = version(a);
                    new Thread(() -> {
                        String result;
                        try {
                            ApiConfig.post("/api/feedback", new org.json.JSONObject()
                                    .put("text", text).put("contact", who).put("app", ver).toString());
                            result = "Thanks! Feedback sent";
                        } catch (Exception e) {
                            result = "Couldn't send: " + e.getMessage();
                        }
                        final String shown = result;
                        a.runOnUiThread(() -> Toast.makeText(a, shown, Toast.LENGTH_LONG).show());
                    }).start();
                })
                .show();
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
