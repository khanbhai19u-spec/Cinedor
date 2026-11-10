package com.mayra.assistant;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class SettingsActivity extends Activity {

    private static final int PERM_CLAP = 31, PERM_ALL = 32;

    private Prefs prefs;
    private Switch clapSwitch;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = new Prefs(this);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Ui.BG);
        sv.setFitsSystemWindows(true);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(12), dp(16), dp(32));
        sv.addView(box);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = Ui.iconButton(this, "←", 40, null);
        back.setOnClickListener(v -> finish());
        top.addView(back);
        TextView title = Ui.text(this, "সেটিংস", 20, Ui.TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(dp(8), 0, 0, 0);
        top.addView(title);
        box.addView(top);

        // --- API key
        LinearLayout keyCard = card(box, "🔑 Claude API key");
        keyCard.addView(note("console.anthropic.com → API Keys থেকে key বানিয়ে এখানে দিন। key শুধু এই ফোনেই থাকে।"));
        EditText key = field(prefs.apiKey(), "sk-ant-...");
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyCard.addView(key);
        LinearLayout keyBtns = new LinearLayout(this);
        keyBtns.setPadding(0, dp(10), 0, 0);
        TextView save = Ui.pillButton(this, "সেভ করুন", true);
        save.setOnClickListener(v -> {
            prefs.setApiKey(key.getText().toString());
            Toast.makeText(this, prefs.apiKey().isEmpty() ? "key মুছে ফেলা হয়েছে" : "✅ সেভ হয়েছে", Toast.LENGTH_SHORT).show();
        });
        keyBtns.addView(save);
        TextView show = Ui.pillButton(this, "দেখাও", false);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.leftMargin = dp(8);
        show.setOnClickListener(v -> {
            boolean hidden = (key.getInputType() & InputType.TYPE_TEXT_VARIATION_PASSWORD) == InputType.TYPE_TEXT_VARIATION_PASSWORD;
            key.setInputType(InputType.TYPE_CLASS_TEXT | (hidden ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD : InputType.TYPE_TEXT_VARIATION_PASSWORD));
            show.setText(hidden ? "লুকাও" : "দেখাও");
        });
        keyBtns.addView(show, slp);
        TextView getKey = Ui.pillButton(this, "key নিন ↗", false);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        glp.leftMargin = dp(8);
        getKey.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://console.anthropic.com/settings/keys"))));
        keyBtns.addView(getKey, glp);
        keyCard.addView(keyBtns);

        // --- Name
        LinearLayout nameCard = card(box, "🙂 আপনার নাম");
        EditText name = field(prefs.userName(), "যেমন: Bayzid");
        name.setOnFocusChangeListener((v, f) -> { if (!f) prefs.setUserName(name.getText().toString()); });
        nameCard.addView(name);

        // --- Model
        LinearLayout modelCard = card(box, "🤖 AI মডেল");
        RadioGroup rg = new RadioGroup(this);
        for (int i = 0; i < Prefs.MODEL_IDS.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setId(View.generateViewId());
            rb.setText(Prefs.MODEL_LABELS[i]);
            rb.setTextColor(Ui.TEXT);
            rb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f);
            rb.setPadding(dp(4), dp(8), 0, dp(8));
            rb.setChecked(Prefs.MODEL_IDS[i].equals(prefs.model()));
            final String id = Prefs.MODEL_IDS[i];
            rb.setOnCheckedChangeListener((b, c) -> { if (c) prefs.setModel(id); });
            rg.addView(rb);
        }
        modelCard.addView(rg);

        // --- Voice
        LinearLayout voiceCard = card(box, "🔊 ভয়েস");
        voiceCard.addView(toggle("সব উত্তর পড়ে শোনাবে", "বন্ধ থাকলে শুধু ভয়েসে প্রশ্ন করলে শোনাবে",
                prefs.alwaysSpeak(), (b, c) -> prefs.setAlwaysSpeak(c)));
        voiceCard.addView(toggle("হ্যান্ডস-ফ্রি কথোপকথন", "উত্তর শেষ হলে আবার নিজে থেকে শুনবে",
                prefs.handsFree(), (b, c) -> prefs.setHandsFree(c)));
        TextView rateLabel = Ui.text(this, "কথার গতি: " + String.format(java.util.Locale.US, "%.1fx", prefs.speechRate()), 14, Ui.TEXT);
        rateLabel.setPadding(0, dp(10), 0, 0);
        voiceCard.addView(rateLabel);
        SeekBar rate = new SeekBar(this);
        rate.setMax(15);
        rate.setProgress(Math.round((prefs.speechRate() - 0.5f) * 10));
        rate.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) {
                float r = 0.5f + p / 10f;
                prefs.setSpeechRate(r);
                rateLabel.setText("কথার গতি: " + String.format(java.util.Locale.US, "%.1fx", r));
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        voiceCard.addView(rate);
        TextView ttsBtn = Ui.pillButton(this, "বাংলা ভয়েস ইনস্টল/সেটআপ", false);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = dp(8);
        ttsBtn.setOnClickListener(v -> {
            try { startActivity(new Intent("com.android.settings.TTS_SETTINGS")); }
            catch (Exception e) { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
        });
        voiceCard.addView(ttsBtn, tlp);

        // --- Clap
        LinearLayout clapCard = card(box, "👏 হাততালি দিয়ে চালু");
        clapCard.addView(note("দুইবার হাততালি দিলে MAYRA খুলে আপনার কথা শুনবে। চালু থাকলে নোটিফিকেশনে দেখাবে এবং কিছুটা বেশি ব্যাটারি খরচ হবে।"));
        clapSwitch = toggle("হাততালি শোনা চালু", null, prefs.clapEnabled(), null);
        clapSwitch.setOnCheckedChangeListener((b, c) -> setClap(c));
        clapCard.addView(clapSwitch);
        TextView sensLabel = Ui.text(this, "সেনসিটিভিটি", 14, Ui.TEXT);
        sensLabel.setPadding(0, dp(10), 0, dp(4));
        clapCard.addView(sensLabel);
        RadioGroup sens = new RadioGroup(this);
        sens.setOrientation(RadioGroup.HORIZONTAL);
        String[] sl = {"কম", "মাঝারি", "বেশি"};
        for (int i = 0; i < 3; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setId(View.generateViewId());
            rb.setText(sl[i]);
            rb.setTextColor(Ui.TEXT);
            rb.setChecked(prefs.clapSensitivity() == i);
            final int v = i;
            rb.setOnCheckedChangeListener((b, c) -> { if (c) prefs.setClapSensitivity(v); });
            sens.addView(rb);
        }
        clapCard.addView(sens);
        TextView overlay = Ui.pillButton(this, "স্ক্রিন বন্ধ/অন্য অ্যাপে থাকলেও খুলতে অনুমতি দিন", false);
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        olp.topMargin = dp(10);
        overlay.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()))));
        clapCard.addView(overlay, olp);
        clapCard.addView(note("টিপস: কিছু ফোনে (Xiaomi, Oppo, Vivo, Realme) Battery → MAYRA → \"No restrictions\" দিতে হয়, না হলে কিছুক্ষণ পর হাততালি শোনা বন্ধ হয়ে যায়।"));

        // --- Web
        LinearLayout webCard = card(box, "🌐 ওয়েব রিসার্চ");
        webCard.addView(toggle("ইন্টারনেটে খুঁজে উত্তর দেবে", "খবর, দাম, আবহাওয়া ইত্যাদির জন্য (প্রতি সার্চে সামান্য খরচ)",
                prefs.webSearch(), (b, c) -> prefs.setWebSearch(c)));

        // --- Permissions
        LinearLayout permCard = card(box, "📱 ফোনের কাজের অনুমতি");
        permCard.addView(note("কল করা, কন্টাক্ট থেকে নাম খোঁজা, মাইক্রোফোন আর নোটিফিকেশনের অনুমতি একসাথে দিন।"));
        TextView permBtn = Ui.pillButton(this, "সব অনুমতি দিন", true);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.topMargin = dp(8);
        permBtn.setOnClickListener(v -> requestAll());
        permCard.addView(permBtn, plp);

        TextView about = Ui.text(this, "MAYRA v" + versionName() + " · Claude দ্বারা চালিত", 12, Ui.MUTED);
        about.setGravity(Gravity.CENTER);
        about.setPadding(0, dp(18), 0, 0);
        box.addView(about);

        setContentView(sv);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // name field may still be focused
        View f = getCurrentFocus();
        if (f instanceof EditText) f.clearFocus();
    }

    private void setClap(boolean on) {
        if (on) {
            List<String> need = new ArrayList<>();
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) need.add(Manifest.permission.RECORD_AUDIO);
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                need.add(Manifest.permission.POST_NOTIFICATIONS);
            if (!need.isEmpty()) {
                requestPermissions(need.toArray(new String[0]), PERM_CLAP);
                return;
            }
            prefs.setClapEnabled(true);
            ClapService.start(this);
            Toast.makeText(this, "👏👏 এখন দুইবার হাততালি দিন (অ্যাপ বন্ধ করে)", Toast.LENGTH_LONG).show();
        } else {
            prefs.setClapEnabled(false);
            ClapService.stop(this);
        }
    }

    private void requestAll() {
        List<String> p = new ArrayList<>();
        p.add(Manifest.permission.RECORD_AUDIO);
        p.add(Manifest.permission.READ_CONTACTS);
        p.add(Manifest.permission.CALL_PHONE);
        if (Build.VERSION.SDK_INT >= 33) p.add(Manifest.permission.POST_NOTIFICATIONS);
        requestPermissions(p.toArray(new String[0]), PERM_ALL);
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        if (req == PERM_CLAP) {
            boolean mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
            if (mic) setClap(true);
            else {
                clapSwitch.setChecked(false);
                Toast.makeText(this, "মাইক্রোফোনের অনুমতি ছাড়া হাততালি শোনা যাবে না", Toast.LENGTH_LONG).show();
            }
        } else if (req == PERM_ALL) {
            Toast.makeText(this, "✅ ঠিক আছে", Toast.LENGTH_SHORT).show();
        }
    }

    // --- view helpers

    private int dp(float v) { return Ui.dp(this, v); }

    private LinearLayout card(LinearLayout parent, String title) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        c.setBackground(Ui.roundStroke(Ui.SURFACE, dp(16), dp(1), Ui.STROKE));
        TextView t = Ui.text(this, title, 16, Ui.TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, 0, 0, dp(6));
        c.addView(t);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        parent.addView(c, lp);
        return c;
    }

    private TextView note(String s) {
        TextView t = Ui.text(this, s, 13, Ui.MUTED);
        t.setLineSpacing(0, 1.15f);
        t.setPadding(0, dp(2), 0, dp(6));
        return t;
    }

    private EditText field(String value, String hint) {
        EditText e = new EditText(this);
        e.setText(value);
        e.setHint(hint);
        e.setHintTextColor(Ui.MUTED);
        e.setTextColor(Ui.TEXT);
        e.setSingleLine(true);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        e.setBackground(Ui.roundStroke(Ui.SURFACE2, dp(12), dp(1), Ui.STROKE));
        return e;
    }

    private Switch toggle(String label, String hint, boolean value, CompoundButton.OnCheckedChangeListener l) {
        Switch s = new Switch(this);
        s.setText(hint == null ? label : label + "\n" + hint);
        s.setTextColor(Ui.TEXT);
        s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f);
        s.setPadding(0, dp(8), 0, dp(8));
        s.setChecked(value);
        if (l != null) s.setOnCheckedChangeListener(l);
        return s;
    }

    private String versionName() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (Exception e) { return "?"; }
    }
}
