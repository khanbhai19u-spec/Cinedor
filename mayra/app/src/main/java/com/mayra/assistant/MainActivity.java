package com.mayra.assistant;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.anthropic.models.messages.ContentBlockParam;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity {

    public static final String EXTRA_VOICE = "voice";

    private static final int REQ_GALLERY = 11, REQ_CAMERA = 12, REQ_DOC = 13;
    private static final int PERM_MIC = 21, PERM_OTHER = 22;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private Prefs prefs;
    private MemoryStore memory;
    private ChatStore chatStore;
    private MayraAgent agent;
    private PhoneTools tools;
    private VoiceInput voice;
    private Speaker speaker;

    private final List<ChatStore.Msg> msgs = new ArrayList<>();
    private final List<Attachment> pending = new ArrayList<>();

    // views
    private LinearLayout list;
    private ScrollView scroll;
    private View welcome;
    private EditText input;
    private TextView sendBtn, micBtn, langBtn, codeBtn, subtitle;
    private HorizontalScrollView attachScroll;
    private LinearLayout attachRow;

    private boolean listenAfterPermission;
    private boolean visible;

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = new Prefs(this);
        memory = new MemoryStore(this);
        chatStore = new ChatStore(this);
        tools = new PhoneTools(this, memory, perms -> requestPermissions(perms, PERM_OTHER));
        agent = new MayraAgent(prefs, memory, this::runToolOnUi);
        voice = new VoiceInput(this);
        speaker = new Speaker(this);

        setContentView(buildUi());

        msgs.addAll(chatStore.load());
        agent.restore(msgs);
        for (ChatStore.Msg m : msgs) {
            if (m.user) addUserBubble(m.text, null);
            else renderInto(addAssistantBubble(), m.text, false);
        }
        refreshWelcome();
        scrollToEnd();

        if (prefs.clapEnabled() && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            ClapService.start(this);
        }
        if (prefs.apiKey().isEmpty()) {
            ui.postDelayed(this::firstRunDialog, 400);
        }
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent i) {
        if (i == null) return;
        boolean wantVoice = i.getBooleanExtra(EXTRA_VOICE, false) || Intent.ACTION_ASSIST.equals(i.getAction());
        if (!wantVoice) return;
        i.removeExtra(EXTRA_VOICE);
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true); }
        ui.postDelayed(() -> speaker.speak(greeting(), prefs.speechRate(), this::startListening), 350);
    }

    private String greeting() {
        String n = prefs.userName();
        return n.isEmpty() ? "জি, বলুন?" : "জি " + n + ", বলুন?";
    }

    @Override
    protected void onResume() {
        super.onResume();
        visible = true;
        MayraApp.uiVisible = true;
        updateSubtitle(null);
        codeBtn.setTextColor(prefs.codeMode() ? Ui.ACCENT2 : Ui.MUTED);
        langBtn.setText("en".equals(prefs.voiceLang()) ? "EN" : "বাং");
    }

    @Override
    protected void onPause() {
        super.onPause();
        visible = false;
        MayraApp.uiVisible = false;
        voice.stop();
        setMicActive(false);
    }

    @Override
    protected void onDestroy() {
        speaker.shutdown();
        voice.stop();
        super.onDestroy();
    }

    // ------------------------------------------------------------------ UI

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.setFitsSystemWindows(true);

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), dp(10), dp(6), dp(10));
        TextView logo = Ui.text(this, "M", 18, 0xFFFFFFFF);
        logo.setTypeface(Typeface.DEFAULT_BOLD);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(Ui.circleGradient());
        header.addView(logo, new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(10), 0, 0, 0);
        TextView title = Ui.text(this, "MAYRA", 19, Ui.TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setLetterSpacing(0.12f);
        subtitle = Ui.text(this, "Your Personal AI Assistant", 12, Ui.MUTED);
        titles.addView(title);
        titles.addView(subtitle);
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        codeBtn = Ui.iconButton(this, "</>", 40, null);
        codeBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        codeBtn.setOnClickListener(v -> toggleCodeMode());
        TextView memBtn = Ui.iconButton(this, "🧠", 40, null);
        memBtn.setOnClickListener(v -> startActivity(new Intent(this, MemoryActivity.class)));
        TextView newBtn = Ui.iconButton(this, "＋", 40, null);
        newBtn.setOnClickListener(v -> confirmNewChat());
        TextView setBtn = Ui.iconButton(this, "⚙", 40, null);
        setBtn.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        header.addView(codeBtn);
        header.addView(memBtn);
        header.addView(newBtn);
        header.addView(setBtn);
        root.addView(header);

        View divider = new View(this);
        divider.setBackgroundColor(Ui.STROKE);
        root.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));

        // Messages
        FrameLayout middle = new FrameLayout(this);
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(12), dp(12), dp(12), dp(12));
        scroll.addView(list);
        middle.addView(scroll);
        welcome = buildWelcome();
        middle.addView(welcome, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(middle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        scroll.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> { if (b < ob) scrollToEnd(); });

        // Attachments preview row
        attachScroll = new HorizontalScrollView(this);
        attachScroll.setHorizontalScrollBarEnabled(false);
        attachRow = new LinearLayout(this);
        attachRow.setPadding(dp(12), dp(6), dp(12), 0);
        attachScroll.addView(attachRow);
        attachScroll.setVisibility(View.GONE);
        root.addView(attachScroll);

        // Input bar
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.BOTTOM);
        bar.setPadding(dp(8), dp(8), dp(8), dp(10));

        TextView attachBtn = Ui.iconButton(this, "📎", 44, Ui.round(Ui.SURFACE, dp(22)));
        attachBtn.setOnClickListener(this::showAttachMenu);
        bar.addView(attachBtn);

        LinearLayout field = new LinearLayout(this);
        field.setGravity(Gravity.CENTER_VERTICAL);
        field.setBackground(Ui.roundStroke(Ui.SURFACE, dp(22), dp(1), Ui.STROKE));
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        flp.leftMargin = dp(6);
        flp.rightMargin = dp(6);
        input = new EditText(this);
        input.setHint("MAYRA-কে কিছু বলুন…");
        input.setHintTextColor(Ui.MUTED);
        input.setTextColor(Ui.TEXT);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
        input.setBackground(null);
        input.setMaxLines(6);
        input.setMinHeight(dp(44));
        input.setPadding(dp(14), dp(10), dp(4), dp(10));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        field.addView(input, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        langBtn = Ui.text(this, "বাং", 12, Ui.MUTED);
        langBtn.setTypeface(Typeface.DEFAULT_BOLD);
        langBtn.setPadding(dp(8), dp(8), dp(12), dp(8));
        langBtn.setOnClickListener(v -> toggleVoiceLang());
        field.addView(langBtn);
        bar.addView(field, flp);

        micBtn = Ui.iconButton(this, "🎤", 44, Ui.round(Ui.SURFACE2, dp(22)));
        micBtn.setOnClickListener(v -> onMic());
        bar.addView(micBtn);

        sendBtn = Ui.iconButton(this, "➤", 44, Ui.circleGradient());
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(dp(44), dp(44));
        slp.leftMargin = dp(6);
        sendBtn.setLayoutParams(slp);
        sendBtn.setOnClickListener(v -> onSend());
        bar.addView(sendBtn);
        root.addView(bar);

        return root;
    }

    private View buildWelcome() {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(20), dp(36), dp(20), dp(20));

        TextView orb = Ui.text(this, "M", 40, 0xFFFFFFFF);
        orb.setTypeface(Typeface.DEFAULT_BOLD);
        orb.setGravity(Gravity.CENTER);
        orb.setBackground(Ui.circleGradient());
        orb.setElevation(dp(8));
        box.addView(orb, new LinearLayout.LayoutParams(dp(96), dp(96)));

        TextView hi = Ui.text(this, "আসসালামু আলাইকুম! আমি MAYRA", 21, Ui.TEXT);
        hi.setTypeface(Typeface.DEFAULT_BOLD);
        hi.setGravity(Gravity.CENTER);
        hi.setPadding(0, dp(18), 0, dp(4));
        box.addView(hi);
        TextView sub = Ui.text(this, "আপনার পার্সোনাল AI অ্যাসিস্ট্যান্ট — লিখুন, কথা বলুন, বা দুইবার হাততালি দিন 👏", 14, Ui.MUTED);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, 0, 0, dp(22));
        box.addView(sub);

        String[][] chips = {
                {"🎤", "কথা বলে প্রশ্ন করুন", "@mic"},
                {"⏰", "কাল সকাল ৬টায় অ্যালার্ম দাও", "কাল সকাল ৬টায় অ্যালার্ম দাও"},
                {"🔎", "আজকের বাংলাদেশের বড় খবর কী?", "আজকের বাংলাদেশের বড় খবরগুলো কী?"},
                {"🖼️", "ছবি দেখিয়ে জিজ্ঞেস করুন", "@image"},
                {"📄", "PDF / ডকুমেন্টের সারাংশ", "@doc"},
                {"💻", "Python-এ একটা ক্যালকুলেটর বানাও", "Python-এ একটা সহজ ক্যালকুলেটর প্রোগ্রাম বানাও, ধাপে ধাপে বুঝিয়ে দাও"},
                {"🧠", "আমার সম্পর্কে মনে রাখো", "আমার সম্পর্কে কিছু জিনিস মনে রাখো: "},
                {"🔦", "টর্চ জ্বালাও", "টর্চ জ্বালাও"},
        };
        for (String[] c : chips) {
            LinearLayout chip = new LinearLayout(this);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            chip.setPadding(dp(14), dp(12), dp(14), dp(12));
            chip.setBackground(Ui.ripple(Ui.roundStroke(Ui.SURFACE, dp(14), dp(1), Ui.STROKE)));
            chip.addView(Ui.text(this, c[0], 18, Ui.TEXT));
            TextView t = Ui.text(this, c[1], 14.5f, Ui.TEXT);
            t.setPadding(dp(12), 0, 0, 0);
            chip.addView(t);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(8);
            final String action = c[2];
            chip.setOnClickListener(v -> {
                switch (action) {
                    case "@mic": onMic(); break;
                    case "@image": pickImage(); break;
                    case "@doc": pickDocument(); break;
                    default:
                        if (action.endsWith(": ")) {
                            input.setText(action);
                            input.setSelection(action.length());
                            input.requestFocus();
                        } else {
                            send(action, false);
                        }
                }
            });
            box.addView(chip, lp);
        }
        sv.addView(box);
        return sv;
    }

    private void refreshWelcome() {
        Ui.gone(welcome, !msgs.isEmpty() || list.getChildCount() > 0);
    }

    private int dp(float v) { return Ui.dp(this, v); }

    private void addUserBubble(String text, List<Attachment> atts) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setGravity(Gravity.END);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wlp.bottomMargin = dp(10);
        wlp.leftMargin = dp(48);
        if (atts != null) {
            for (Attachment a : atts) {
                if (a.preview != null) {
                    ImageView iv = new ImageView(this);
                    iv.setImageBitmap(a.preview);
                    iv.setAdjustViewBounds(true);
                    iv.setClipToOutline(true);
                    iv.setBackground(Ui.round(Ui.SURFACE, dp(14)));
                    LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(160), ViewGroup.LayoutParams.WRAP_CONTENT);
                    ilp.bottomMargin = dp(4);
                    wrap.addView(iv, ilp);
                }
            }
        }
        if (!text.isEmpty()) {
            TextView t = Ui.text(this, text, 15.5f, 0xFFFFFFFF);
            t.setLineSpacing(0, 1.15f);
            t.setPadding(dp(14), dp(10), dp(14), dp(10));
            t.setBackground(Ui.gradient(dp(18)));
            t.setOnLongClickListener(v -> { Ui.copy(this, text); return true; });
            wrap.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        list.addView(wrap, wlp);
    }

    /** Assistant bubble: a vertical box (markdown views) + a small status line. */
    private LinearLayout addAssistantBubble() {
        LinearLayout row = new LinearLayout(this);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.bottomMargin = dp(12);

        TextView av = Ui.text(this, "M", 12, 0xFFFFFFFF);
        av.setTypeface(Typeface.DEFAULT_BOLD);
        av.setGravity(Gravity.CENTER);
        av.setBackground(Ui.circleGradient());
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(26), dp(26));
        alp.rightMargin = dp(8);
        alp.topMargin = dp(4);
        row.addView(av, alp);

        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setPadding(dp(14), dp(10), dp(14), dp(10));
        bubble.setBackground(Ui.roundStroke(Ui.SURFACE, dp(18), dp(1), Ui.STROKE));
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        bubble.addView(body);
        TextView status = Ui.text(this, "", 12.5f, Ui.MUTED);
        status.setVisibility(View.GONE);
        bubble.addView(status);
        bubble.setTag(new View[]{body, status});
        row.addView(bubble, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        list.addView(row, rlp);
        return bubble;
    }

    private static LinearLayout bodyOf(LinearLayout bubble) { return (LinearLayout) ((View[]) bubble.getTag())[0]; }
    private static TextView statusOf(LinearLayout bubble) { return (TextView) ((View[]) bubble.getTag())[1]; }

    private void renderInto(LinearLayout bubble, String md, boolean error) {
        LinearLayout body = bodyOf(bubble);
        Markdown.render(this, body, md);
        if (error) {
            for (int i = 0; i < body.getChildCount(); i++) {
                View v = body.getChildAt(i);
                if (v instanceof TextView) ((TextView) v).setTextColor(Ui.DANGER);
            }
        }
        final String text = md;
        bubble.setOnLongClickListener(v -> { assistantMenu(v, text); return true; });
        for (int i = 0; i < body.getChildCount(); i++) {
            body.getChildAt(i).setOnLongClickListener(v -> { assistantMenu(v, text); return true; });
        }
    }

    private void assistantMenu(View anchor, String text) {
        PopupMenu m = new PopupMenu(this, anchor);
        m.getMenu().add(0, 1, 0, "⧉ কপি করুন");
        m.getMenu().add(0, 2, 1, "🔊 পড়ে শোনাও");
        m.getMenu().add(0, 3, 2, "↗ শেয়ার করুন");
        m.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) Ui.copy(this, text);
            else if (item.getItemId() == 2) speaker.speak(text, prefs.speechRate(), null);
            else startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share"));
            return true;
        });
        m.show();
    }

    private void scrollToEnd() {
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private void updateSubtitle(String s) {
        if (s != null) { subtitle.setText(s); subtitle.setTextColor(Ui.ACCENT2); return; }
        String m = prefs.model();
        String short_ = m.contains("opus") ? "Opus 5.5" : m.contains("sonnet") ? "Sonnet 5.5" : "Haiku 5.5";
        subtitle.setText("● অনলাইন · " + short_ + (prefs.codeMode() ? " · কোডিং মোড" : ""));
        subtitle.setTextColor(prefs.apiKey().isEmpty() ? Ui.DANGER : Ui.OK);
        if (prefs.apiKey().isEmpty()) subtitle.setText("API key দেওয়া হয়নি — ⚙ সেটিংসে যান");
    }

    // ------------------------------------------------------------------ sending

    private void onSend() {
        if (agent.isBusy()) { agent.cancel(); return; }
        String text = input.getText().toString().trim();
        if (text.isEmpty() && pending.isEmpty()) {
            onMic();
            return;
        }
        send(text, false);
    }

    private void send(String text, boolean byVoice) {
        if (agent.isBusy()) { Toast.makeText(this, "একটু অপেক্ষা করুন…", Toast.LENGTH_SHORT).show(); return; }
        if (prefs.apiKey().isEmpty()) {
            Toast.makeText(this, "আগে সেটিংসে Claude API key দিন", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, SettingsActivity.class));
            return;
        }
        speaker.stop();
        input.setText("");
        List<Attachment> atts = new ArrayList<>(pending);
        pending.clear();
        refreshAttachRow();

        List<ContentBlockParam> content = new ArrayList<>();
        StringBuilder record = new StringBuilder();
        for (Attachment a : atts) {
            content.add(a.block);
            record.append(a.label()).append('\n');
        }
        String prompt = text;
        if (prompt.isEmpty()) prompt = "(ফাইলটা দেখে বিশ্লেষণ করো এবং গুরুত্বপূর্ণ বিষয়গুলো সংক্ষেপে বলো)";
        content.add(ContentBlockParam.ofText(prompt));
        record.append(text);
        String recordText = record.toString().trim();

        addUserBubble(recordText, atts);
        msgs.add(new ChatStore.Msg(true, recordText));
        refreshWelcome();

        final LinearLayout bubble = addAssistantBubble();
        final TextView status = statusOf(bubble);
        status.setText("ভাবছি…");
        status.setVisibility(View.VISIBLE);
        scrollToEnd();
        setBusy(true);
        updateSubtitle(byVoice ? "শুনেছি, ভাবছি…" : "ভাবছি…");

        final boolean speakReply = byVoice || prefs.alwaysSpeak();
        agent.send(content, recordText, byVoice, new MayraAgent.Listener() {
            long lastRender;
            String latest;
            final Runnable renderLatest = () -> { if (latest != null) { renderInto(bubble, latest, false); scrollIfNearEnd(); } };

            @Override public void onStatus(String s) {
                ui.post(() -> { status.setText(s); status.setVisibility(View.VISIBLE); updateSubtitle(s); });
            }

            @Override public void onText(String soFar) {
                latest = soFar;
                long now = System.currentTimeMillis();
                if (now - lastRender > 90) {
                    lastRender = now;
                    ui.post(renderLatest);
                }
            }

            @Override public void onDone(String full, Map<String, String> sources) {
                ui.post(() -> {
                    ui.removeCallbacks(renderLatest);
                    String shown = full;
                    if (!sources.isEmpty()) {
                        StringBuilder sb = new StringBuilder(full).append("\n\n**সূত্র:**\n");
                        int n = 0;
                        for (Map.Entry<String, String> e : sources.entrySet()) {
                            if (++n > 5) break;
                            sb.append("- [").append(e.getValue().replace("[", "(").replace("]", ")")).append("](").append(e.getKey()).append(")\n");
                        }
                        shown = sb.toString().trim();
                    }
                    renderInto(bubble, shown, false);
                    status.setVisibility(View.GONE);
                    msgs.add(new ChatStore.Msg(false, shown));
                    chatStore.save(msgs);
                    setBusy(false);
                    updateSubtitle(null);
                    scrollIfNearEnd();
                    if (speakReply && visible) {
                        updateSubtitle("🔊 বলছি…");
                        speaker.speak(full, prefs.speechRate(), () -> {
                            updateSubtitle(null);
                            if (byVoice && prefs.handsFree() && visible && !agent.isBusy()) startListening();
                        });
                    }
                });
            }

            @Override public void onError(String message) {
                ui.post(() -> {
                    ui.removeCallbacks(renderLatest);
                    renderInto(bubble, "⚠️ " + message, true);
                    status.setVisibility(View.GONE);
                    // the failed question is not kept in the saved chat (the agent rolled it back too)
                    msgs.remove(msgs.size() - 1);
                    chatStore.save(msgs);
                    setBusy(false);
                    updateSubtitle(null);
                    if (speakReply && visible) speaker.speak(message, prefs.speechRate(), null);
                });
            }
        });
    }

    private void scrollIfNearEnd() {
        View child = scroll.getChildAt(0);
        if (child == null) return;
        int distance = child.getBottom() - (scroll.getScrollY() + scroll.getHeight());
        if (distance < dp(400)) scrollToEnd();
    }

    private void setBusy(boolean busy) {
        sendBtn.setText(busy ? "■" : "➤");
    }

    /** Called by the agent thread; runs the phone tool on the UI thread and waits for it. */
    private String runToolOnUi(String name, Map<String, Object> input) throws Exception {
        FutureTask<String> task = new FutureTask<>(() -> tools.run(name, input));
        ui.post(task);
        try {
            return task.get(30, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            Throwable c = e.getCause();
            if (c instanceof Exception) throw (Exception) c;
            throw e;
        }
    }

    // ------------------------------------------------------------------ voice

    private void onMic() {
        if (speaker.isSpeaking()) { speaker.stop(); updateSubtitle(null); }
        if (voice.isListening()) { voice.stop(); setMicActive(false); updateSubtitle(null); return; }
        startListening();
    }

    private void startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            listenAfterPermission = true;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, PERM_MIC);
            return;
        }
        if (!voice.available()) {
            Toast.makeText(this, "এই ফোনে ভয়েস রিকগনিশন নেই — Google অ্যাপ ইনস্টল/আপডেট করুন", Toast.LENGTH_LONG).show();
            return;
        }
        speaker.stop();
        voice.start(prefs.voiceLang(), new VoiceInput.Callback() {
            @Override public void onListening() { setMicActive(true); updateSubtitle("🎤 শুনছি… বলুন"); }
            @Override public void onPartial(String text) { input.setText(text); input.setSelection(text.length()); }
            @Override public void onLevel(float db) {
                float s = 1f + Math.max(0f, Math.min(10f, db)) / 25f;
                micBtn.setScaleX(s);
                micBtn.setScaleY(s);
            }
            @Override public void onResult(String text) {
                setMicActive(false);
                updateSubtitle(null);
                send(text, true);
            }
            @Override public void onError(String message) {
                setMicActive(false);
                updateSubtitle(null);
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setMicActive(boolean on) {
        micBtn.setBackground(Ui.ripple(on ? Ui.circleGradient() : Ui.round(Ui.SURFACE2, dp(22))));
        if (!on) { micBtn.setScaleX(1f); micBtn.setScaleY(1f); }
    }

    private void toggleVoiceLang() {
        String next = "en".equals(prefs.voiceLang()) ? "bn" : "en";
        prefs.setVoiceLang(next);
        langBtn.setText("en".equals(next) ? "EN" : "বাং");
        Toast.makeText(this, "en".equals(next) ? "Voice: English" : "ভয়েস: বাংলা", Toast.LENGTH_SHORT).show();
    }

    private void toggleCodeMode() {
        boolean on = !prefs.codeMode();
        prefs.setCodeMode(on);
        codeBtn.setTextColor(on ? Ui.ACCENT2 : Ui.MUTED);
        updateSubtitle(null);
        Toast.makeText(this, on ? "💻 কোডিং মোড চালু" : "কোডিং মোড বন্ধ", Toast.LENGTH_SHORT).show();
    }

    // ------------------------------------------------------------------ attachments

    private void showAttachMenu(View anchor) {
        PopupMenu m = new PopupMenu(this, anchor);
        m.getMenu().add(0, 1, 0, "🖼️ গ্যালারি থেকে ছবি");
        m.getMenu().add(0, 2, 1, "📷 ক্যামেরা দিয়ে ছবি তুলুন");
        m.getMenu().add(0, 3, 2, "📄 PDF / ডকুমেন্ট / কোড ফাইল");
        m.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) pickImage();
            else if (item.getItemId() == 2) takePhoto();
            else pickDocument();
            return true;
        });
        m.show();
    }

    private void pickImage() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);
        safeStart(i, REQ_GALLERY);
    }

    private void takePhoto() {
        Uri out = CameraFileProvider.photoUri();
        //noinspection ResultOfMethodCallIgnored
        CameraFileProvider.photoFile(this).delete();
        Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                .putExtra(MediaStore.EXTRA_OUTPUT, out)
                .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        i.setClipData(ClipData.newRawUri("photo", out));
        safeStart(i, REQ_CAMERA);
    }

    private void pickDocument() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                        "application/pdf", "text/*", "application/json", "application/xml",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "application/octet-stream", "image/*"});
        safeStart(i, REQ_DOC);
    }

    private void safeStart(Intent i, int req) {
        try {
            startActivityForResult(i, req);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "এই কাজের জন্য কোনো অ্যাপ পাওয়া যায়নি", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK) return;
        final Uri uri;
        final boolean camera = req == REQ_CAMERA;
        if (camera) uri = CameraFileProvider.photoUri();
        else if (data != null && data.getData() != null) uri = data.getData();
        else return;
        Toast.makeText(this, "ফাইল প্রস্তুত করছি…", Toast.LENGTH_SHORT).show();
        io.execute(() -> {
            try {
                Attachment a = camera ? Attachment.fromCameraFile(this, uri) : Attachment.from(this, uri);
                ui.post(() -> {
                    pending.add(a);
                    refreshAttachRow();
                    input.requestFocus();
                });
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : e.toString();
                ui.post(() -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show());
            }
        });
    }

    private void refreshAttachRow() {
        attachRow.removeAllViews();
        for (Attachment a : new ArrayList<>(pending)) {
            LinearLayout chip = new LinearLayout(this);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            chip.setPadding(dp(6), dp(6), dp(10), dp(6));
            chip.setBackground(Ui.roundStroke(Ui.SURFACE2, dp(14), dp(1), Ui.STROKE));
            if (a.preview != null) {
                ImageView iv = new ImageView(this);
                iv.setImageBitmap(a.preview);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                iv.setClipToOutline(true);
                iv.setBackground(Ui.round(0, dp(8)));
                chip.addView(iv, new LinearLayout.LayoutParams(dp(36), dp(36)));
            }
            TextView t = Ui.text(this, a.preview != null ? a.name : a.label(), 13, Ui.TEXT);
            t.setMaxWidth(dp(170));
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            t.setPadding(dp(8), 0, dp(8), 0);
            chip.addView(t);
            TextView x = Ui.text(this, "✕", 14, Ui.MUTED);
            x.setOnClickListener(v -> { pending.remove(a); refreshAttachRow(); });
            chip.addView(x);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(8);
            attachRow.addView(chip, lp);
        }
        Ui.gone(attachScroll, pending.isEmpty());
        input.setHint(pending.isEmpty() ? "MAYRA-কে কিছু বলুন…" : "এটা নিয়ে কী জানতে চান?");
    }

    // ------------------------------------------------------------------ misc

    private void confirmNewChat() {
        if (msgs.isEmpty()) return;
        new AlertDialog.Builder(this)
                .setTitle("নতুন চ্যাট")
                .setMessage("এই চ্যাট মুছে নতুন করে শুরু করবেন? (মেমরি থেকে যাবে)")
                .setPositiveButton("হ্যাঁ", (d, w) -> {
                    agent.cancel();
                    agent.reset();
                    msgs.clear();
                    chatStore.clear();
                    list.removeAllViews();
                    speaker.stop();
                    refreshWelcome();
                })
                .setNegativeButton("না", null)
                .show();
    }

    private void firstRunDialog() {
        if (isFinishing()) return;
        new AlertDialog.Builder(this)
                .setTitle("MAYRA-তে স্বাগতম 👋")
                .setMessage("MAYRA চালাতে একটা Claude API key লাগবে।\n\n১. console.anthropic.com এ অ্যাকাউন্ট খুলুন\n২. Billing-এ কিছু ক্রেডিট যোগ করুন\n৩. API Keys থেকে নতুন key বানিয়ে কপি করুন\n৪. সেটিংসে পেস্ট করুন")
                .setPositiveButton("সেটিংসে যাই", (d, w) -> startActivity(new Intent(this, SettingsActivity.class)))
                .setNegativeButton("পরে", null)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        if (req == PERM_MIC && listenAfterPermission) {
            listenAfterPermission = false;
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startListening();
            else Toast.makeText(this, "ভয়েসের জন্য মাইক্রোফোনের অনুমতি দরকার", Toast.LENGTH_LONG).show();
        }
    }
}
