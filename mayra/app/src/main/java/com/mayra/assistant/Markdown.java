package com.mayra.assistant;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tiny Markdown renderer: headings, bold, italic, inline code, links, lists and copyable code blocks. */
public final class Markdown {

    private Markdown() {}

    private static final Pattern BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern ITALIC = Pattern.compile("(?<![*\\w])[*_](?![\\s*_])(.+?)(?<![\\s*_])[*_](?![*\\w])");
    private static final Pattern CODE = Pattern.compile("`([^`\\n]+)`");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]\\n]+)\\]\\((https?://[^)\\s]+)\\)");

    /** Fills {@code box} with views for the markdown text (replaces existing children). */
    public static void render(Context c, LinearLayout box, String md) {
        box.removeAllViews();
        String[] parts = md.split("```", -1);
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            if (i % 2 == 0) {
                String trimmed = trimNewlines(p);
                if (!trimmed.isEmpty()) box.addView(textView(c, trimmed));
            } else {
                int nl = p.indexOf('\n');
                String lang = nl >= 0 ? p.substring(0, nl).trim() : "";
                String code = nl >= 0 ? p.substring(nl + 1) : p;
                if (code.endsWith("\n")) code = code.substring(0, code.length() - 1);
                box.addView(codeView(c, lang, code));
            }
        }
    }

    /** Plain text for text-to-speech: no code, no markdown symbols, no links. */
    public static String toSpeech(String md) {
        String s = md.replaceAll("(?s)```.*?```", " (কোড স্ক্রিনে দেওয়া আছে) ");
        s = s.replaceAll("(?s)```.*$", " ");
        s = LINK.matcher(s).replaceAll("$1");
        s = s.replaceAll("https?://\\S+", " ");
        s = s.replaceAll("[*_`#>|]", "");
        s = s.replaceAll("(?m)^\\s*[-•]\\s+", "");
        return s.replaceAll("[ \\t]+", " ").trim();
    }

    private static String trimNewlines(String s) {
        int a = 0, b = s.length();
        while (a < b && s.charAt(a) == '\n') a++;
        while (b > a && (s.charAt(b - 1) == '\n' || s.charAt(b - 1) == ' ')) b--;
        return s.substring(a, b);
    }

    private static TextView textView(Context c, String md) {
        TextView t = new TextView(c);
        t.setTextColor(Ui.TEXT);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
        t.setLineSpacing(0, 1.18f);
        t.setText(spans(md));
        Linkify.addLinks(t, Linkify.WEB_URLS);
        t.setText(t.getText()); // keep our spans after Linkify
        t.setMovementMethod(LinkMovementMethod.getInstance());
        t.setLinkTextColor(Ui.ACCENT2);
        return t;
    }

    static CharSequence spans(String md) {
        SpannableStringBuilder out = new SpannableStringBuilder();
        String[] lines = md.split("\n", -1);
        for (int li = 0; li < lines.length; li++) {
            String line = lines[li];
            int start = out.length();
            float size = 0;
            if (line.startsWith("### ")) { line = line.substring(4); size = 1.08f; }
            else if (line.startsWith("## ")) { line = line.substring(3); size = 1.16f; }
            else if (line.startsWith("# ")) { line = line.substring(2); size = 1.25f; }
            else if (line.matches("^\\s*[-*+] .*")) { line = line.replaceFirst("^(\\s*)[-*+] ", "$1•  "); }
            else if (line.startsWith("> ")) { line = "▎ " + line.substring(2); }
            inline(out, line);
            if (size > 0) {
                out.setSpan(new RelativeSizeSpan(size), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new StyleSpan(Typeface.BOLD), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (li < lines.length - 1) out.append('\n');
        }
        return out;
    }

    /** Appends one line, converting inline markdown into spans. */
    private static void inline(SpannableStringBuilder out, String line) {
        // Process in order: code (no nested formatting), links, bold, italic.
        int i = 0;
        while (i < line.length()) {
            Match m = earliest(line, i);
            if (m == null) { out.append(line.substring(i)); return; }
            out.append(line, i, m.start);
            int s = out.length();
            switch (m.kind) {
                case 0:
                    out.append(m.g1);
                    out.setSpan(new TypefaceSpan("monospace"), s, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    out.setSpan(new BackgroundColorSpan(Ui.CODE_BG), s, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    out.setSpan(new ForegroundColorSpan(0xFFFFB86C), s, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    break;
                case 1:
                    out.append(m.g1);
                    out.setSpan(new URLSpan(m.g2), s, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    break;
                case 2:
                    inline(out, m.g1);
                    out.setSpan(new StyleSpan(Typeface.BOLD), s, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    break;
                default:
                    inline(out, m.g1);
                    out.setSpan(new StyleSpan(Typeface.ITALIC), s, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            i = m.end;
        }
    }

    private static final class Match {
        int kind, start, end; String g1, g2;
    }

    private static Match earliest(String line, int from) {
        Pattern[] ps = {CODE, LINK, BOLD, ITALIC};
        Match best = null;
        for (int k = 0; k < ps.length; k++) {
            Matcher mm = ps[k].matcher(line);
            if (mm.find(from) && (best == null || mm.start() < best.start)) {
                best = new Match();
                best.kind = k; best.start = mm.start(); best.end = mm.end();
                best.g1 = mm.group(1); best.g2 = mm.groupCount() > 1 ? mm.group(2) : null;
            }
        }
        return best;
    }

    private static LinearLayout codeView(Context c, String lang, String code) {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.roundStroke(Ui.CODE_BG, Ui.dp(c, 12), Ui.dp(c, 1), Ui.STROKE));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(c, 8);
        lp.bottomMargin = Ui.dp(c, 8);
        card.setLayoutParams(lp);

        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Ui.dp(c, 12), Ui.dp(c, 6), Ui.dp(c, 6), 0);
        TextView l = Ui.text(c, lang.isEmpty() ? "code" : lang, 12, Ui.MUTED);
        head.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView copy = Ui.text(c, "⧉ কপি", 12, Ui.ACCENT);
        copy.setPadding(Ui.dp(c, 10), Ui.dp(c, 6), Ui.dp(c, 10), Ui.dp(c, 6));
        copy.setBackground(Ui.ripple(Ui.round(0, Ui.dp(c, 8))));
        final String codeText = code;
        copy.setOnClickListener(v -> Ui.copy(c, codeText));
        head.addView(copy);
        card.addView(head);

        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        TextView t = new TextView(c);
        t.setTypeface(Typeface.MONOSPACE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setTextColor(0xFFD6DEEB);
        t.setText(code);
        t.setTextIsSelectable(true);
        t.setPadding(Ui.dp(c, 12), Ui.dp(c, 4), Ui.dp(c, 12), Ui.dp(c, 12));
        hs.addView(t);
        card.addView(hs);
        return card;
    }
}
