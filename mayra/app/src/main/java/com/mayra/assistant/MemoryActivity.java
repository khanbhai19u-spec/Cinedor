package com.mayra.assistant;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Date;
import java.util.List;

/** Shows what MAYRA remembers about the user; items can be added or deleted. */
public class MemoryActivity extends Activity {

    private MemoryStore memory;
    private LinearLayout list;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        memory = new MemoryStore(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.setFitsSystemWindows(true);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(12), dp(10), dp(12), dp(6));
        TextView back = Ui.iconButton(this, "←", 40, null);
        back.setOnClickListener(v -> finish());
        top.addView(back);
        TextView title = Ui.text(this, "🧠 MAYRA যা মনে রেখেছে", 19, Ui.TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(dp(8), 0, 0, 0);
        top.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView add = Ui.iconButton(this, "＋", 40, null);
        add.setOnClickListener(v -> addDialog());
        top.addView(add);
        root.addView(top);

        TextView hint = Ui.text(this, "চ্যাটে বলুন \"মনে রাখো…\" — অথবা ＋ চেপে নিজে যোগ করুন। সব কিছু শুধু এই ফোনে থাকে।", 13, Ui.MUTED);
        hint.setPadding(dp(20), 0, dp(20), dp(8));
        root.addView(hint);

        ScrollView sv = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(16), dp(4), dp(16), dp(24));
        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
        refresh();
    }

    private int dp(float v) { return Ui.dp(this, v); }

    private void refresh() {
        list.removeAllViews();
        List<MemoryStore.Item> items = memory.all();
        if (items.isEmpty()) {
            TextView e = Ui.text(this, "এখনো কিছু মনে রাখা হয়নি।", 15, Ui.MUTED);
            e.setGravity(Gravity.CENTER);
            e.setPadding(0, dp(40), 0, 0);
            list.addView(e);
            return;
        }
        for (int i = items.size() - 1; i >= 0; i--) {
            MemoryStore.Item it = items.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(12), dp(6), dp(12));
            row.setBackground(Ui.roundStroke(Ui.SURFACE, dp(14), dp(1), Ui.STROKE));
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            TextView t = Ui.text(this, it.text, 15, Ui.TEXT);
            t.setLineSpacing(0, 1.1f);
            col.addView(t);
            col.addView(Ui.text(this, DateFormat.getDateFormat(this).format(new Date(it.time)), 11.5f, Ui.MUTED));
            row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            TextView del = Ui.iconButton(this, "🗑", 40, null);
            del.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            del.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setMessage("এটা মুছে ফেলবেন?\n\n" + it.text)
                    .setPositiveButton("মুছুন", (d, w) -> { memory.remove(it.id); refresh(); })
                    .setNegativeButton("না", null).show());
            row.addView(del);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(8);
            list.addView(row, lp);
        }
    }

    private void addDialog() {
        EditText e = new EditText(this);
        e.setHint("যেমন: আমার জন্মদিন ১২ মার্চ");
        int p = dp(20);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setPadding(p, dp(8), p, 0);
        wrap.addView(e, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this)
                .setTitle("নতুন মেমরি")
                .setView(wrap)
                .setPositiveButton("সেভ", (d, w) -> {
                    String s = e.getText().toString().trim();
                    if (!s.isEmpty()) { memory.add(s); refresh(); }
                })
                .setNegativeButton("বাতিল", null)
                .show();
    }
}
