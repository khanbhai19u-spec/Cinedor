package com.mayra.assistant;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.BadRequestException;
import com.anthropic.errors.OverloadedException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.helpers.MessageAccumulator;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.TextCitation;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlock;
import com.anthropic.models.messages.WebFetchTool20260209;
import com.anthropic.models.messages.WebSearchTool20260209;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The brain: talks to Claude with streaming, runs phone tools, web search/fetch (server side),
 * and keeps the conversation history.
 */
public final class MayraAgent {

    public interface Listener {
        void onStatus(String status);
        void onText(String textSoFar);
        void onDone(String text, Map<String, String> sources);
        void onError(String message);
    }

    /** Runs a client tool on the UI thread and returns its result. */
    public interface ToolRunner {
        String run(String name, Map<String, Object> input) throws Exception;
    }

    private static final int MAX_STEPS = 16;
    private static final long MAX_TOKENS = 64000L;

    private static final String SYSTEM_PROMPT =
            "You are MAYRA, a warm, smart personal AI assistant living inside the user's Android phone.\n\n"
            + "Language:\n"
            + "- Reply in the language the user uses. If they write or speak Bangla (or Banglish — Bangla in English letters), reply in natural, friendly Bangla script. If they use English, reply in English.\n"
            + "- Use simple everyday words. Address the user respectfully (আপনি) unless they prefer otherwise.\n\n"
            + "What you can do:\n"
            + "- Answer questions, explain, write, translate, plan, and help with study and work.\n"
            + "- Understand images, PDFs and documents the user attaches.\n"
            + "- Search the web and read web pages for current information (news, prices, weather, sports, facts that may have changed). Mention sources briefly.\n"
            + "- Act on the phone with your tools: open apps, call, prepare SMS/WhatsApp messages, alarms, timers, calendar, maps, YouTube, flashlight, volume, settings screens, device status.\n"
            + "- Remember personal facts with the remember tool, and use saved memories to personalise answers.\n"
            + "- Coding: write, explain, review and debug code in any language. Put code in fenced code blocks with the language name.\n\n"
            + "How to act:\n"
            + "- When the user asks for a phone action, just do it with the right tool, then confirm in one short sentence. Ask only if something essential is missing (e.g. which contact).\n"
            + "- Call someone only when the user clearly asked to call. SMS/WhatsApp/calendar open pre-filled; tell the user to tap send/save.\n"
            + "- If Android does not allow an action (e.g. toggling Wi-Fi directly, reading other apps' messages), say so honestly and open the closest settings screen or app instead.\n"
            + "- For the current time, date or battery, use device_status.\n"
            + "- Save memories when the user asks, or when they share a stable personal detail worth keeping (name, family, preferences, routines, goals). Never save passwords, PINs, OTPs, card or bank numbers.\n"
            + "- Be concise by default; give more detail when asked or when the task needs it.";

    private final Prefs prefs;
    private final MemoryStore memory;
    private final ToolRunner toolRunner;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final List<MessageParam> history = new ArrayList<>();

    private AnthropicClient client;
    private String clientKey;
    private volatile boolean cancelled;
    private volatile StreamResponse<RawMessageStreamEvent> currentStream;
    private volatile boolean busy;

    public MayraAgent(Prefs prefs, MemoryStore memory, ToolRunner toolRunner) {
        this.prefs = prefs; this.memory = memory; this.toolRunner = toolRunner;
    }

    public boolean isBusy() { return busy; }

    /** Rebuild history from the saved chat (text only) after the app restarts. */
    public synchronized void restore(List<ChatStore.Msg> msgs) {
        history.clear();
        boolean expectUser = true;
        for (ChatStore.Msg m : msgs) {
            if (m.user != expectUser || m.text.trim().isEmpty()) continue;
            history.add(m.user ? MessageParam.ofUser(m.text) : MessageParam.ofAssistant(m.text));
            expectUser = !expectUser;
        }
        if (!expectUser) history.remove(history.size() - 1); // must end with an assistant turn
    }

    public synchronized void reset() { history.clear(); }

    public void cancel() {
        cancelled = true;
        StreamResponse<RawMessageStreamEvent> s = currentStream;
        if (s != null) {
            try { s.close(); } catch (Exception ignored) { }
        }
    }

    private synchronized AnthropicClient client() {
        String key = prefs.apiKey();
        if (client == null || !key.equals(clientKey)) {
            if (client != null) {
                try { client.close(); } catch (Exception ignored) { }
            }
            client = AnthropicOkHttpClient.builder()
                    .apiKey(key)
                    .timeout(Duration.ofMinutes(10))
                    .maxRetries(2)
                    .build();
            clientKey = key;
        }
        return client;
    }

    /**
     * Sends one user turn. {@code content} holds attachments followed by the text.
     * {@code plainText} is the text-only version used if the turn has to be rolled back.
     */
    public void send(List<ContentBlockParam> content, String plainText, boolean voice, Listener l) {
        busy = true;
        cancelled = false;
        exec.execute(() -> {
            try {
                runTurn(content, plainText, voice, l);
            } finally {
                busy = false;
            }
        });
    }

    private void runTurn(List<ContentBlockParam> content, String plainText, boolean voice, Listener l) {
        final int mark;
        synchronized (this) {
            mark = history.size();
            history.add(MessageParam.ofUserOfContentBlockParams(content));
        }
        StringBuilder all = new StringBuilder();
        Map<String, String> sources = new LinkedHashMap<>();
        try {
            for (int step = 0; step < MAX_STEPS; step++) {
                MessageCreateParams params = buildParams(voice);
                MessageAccumulator acc = MessageAccumulator.create();
                try (StreamResponse<RawMessageStreamEvent> stream = client().messages().createStreaming(params)) {
                    currentStream = stream;
                    Iterator<RawMessageStreamEvent> it = stream.stream().iterator();
                    while (!cancelled && it.hasNext()) {
                        RawMessageStreamEvent ev = acc.accumulate(it.next());
                        ev.contentBlockStart().ifPresent(start -> {
                            start.contentBlock().serverToolUse().ifPresent(s ->
                                    l.onStatus(s.name().toString().contains("fetch") ? "🌐 ওয়েবপেজ পড়ছি…" : "🔎 ওয়েবে খুঁজছি…"));
                            start.contentBlock().toolUse().ifPresent(t -> l.onStatus(PhoneTools.label(t.name())));
                            if (start.contentBlock().text().isPresent() && all.length() > 0 && !all.toString().endsWith("\n")) {
                                all.append("\n\n");
                            }
                        });
                        ev.contentBlockDelta().ifPresent(d -> d.delta().text().ifPresent(t -> {
                            all.append(t.text());
                            l.onText(all.toString());
                        }));
                    }
                } finally {
                    currentStream = null;
                }
                if (cancelled) {
                    rollback(mark, plainText, all.toString());
                    l.onDone(all.length() > 0 ? all + " …" : "(থামানো হয়েছে)", sources);
                    return;
                }

                Message msg = acc.message();
                synchronized (this) { history.add(msg.toParam()); }
                collectSources(msg, sources);

                StopReason stop = msg.stopReason().orElse(StopReason.END_TURN);
                if (StopReason.TOOL_USE.equals(stop)) {
                    List<ContentBlockParam> results = new ArrayList<>();
                    for (ContentBlock b : msg.content()) {
                        if (!b.toolUse().isPresent()) continue;
                        ToolUseBlock tu = b.toolUse().get();
                        results.add(runTool(tu));
                    }
                    synchronized (this) { history.add(MessageParam.ofUserOfContentBlockParams(results)); }
                    l.onStatus("ভাবছি…");
                    continue;
                }
                if (StopReason.PAUSE_TURN.equals(stop)) {
                    continue; // server tool (web search) paused a long turn; resend to let it continue
                }
                if (StopReason.REFUSAL.equals(stop)) {
                    if (all.length() > 0) all.append("\n\n");
                    all.append("দুঃখিত, এই অনুরোধে আমি সাহায্য করতে পারছি না।");
                } else if (StopReason.MAX_TOKENS.equals(stop)) {
                    all.append("\n\n…(উত্তর অনেক বড় হয়ে গেছে, \"চালিয়ে যাও\" লিখুন)");
                }
                break;
            }
            l.onDone(all.length() > 0 ? all.toString() : "✅", sources);
        } catch (Throwable e) {
            rollback(mark, plainText, all.toString());
            if (cancelled) {
                l.onDone(all.length() > 0 ? all + " …" : "(থামানো হয়েছে)", sources);
            } else {
                l.onError(friendly(e));
            }
        }
    }

    private ContentBlockParam runTool(ToolUseBlock tu) {
        String result;
        boolean error = false;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> input = tu._input().convert(Map.class);
            result = toolRunner.run(tu.name(), input != null ? input : new LinkedHashMap<>());
        } catch (Throwable e) {
            error = true;
            result = "Error: " + (e.getMessage() != null ? e.getMessage() : e.toString());
        }
        return ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
                .toolUseId(tu.id())
                .content(result)
                .isError(error)
                .build());
    }

    /** Undo a failed/cancelled turn, keeping a text-only record so the conversation stays valid. */
    private synchronized void rollback(int mark, String userText, String partialReply) {
        while (history.size() > mark) history.remove(history.size() - 1);
        if (!partialReply.trim().isEmpty()) {
            history.add(MessageParam.ofUser(userText.isEmpty() ? "(attachment)" : userText));
            history.add(MessageParam.ofAssistant(partialReply + " …(interrupted)"));
        }
    }

    private MessageCreateParams buildParams(boolean voice) {
        String model = prefs.model();
        boolean code = prefs.codeMode();

        StringBuilder ctx = new StringBuilder();
        SimpleDateFormat df = new SimpleDateFormat("EEEE, d MMMM yyyy", Locale.US);
        ctx.append("Today is ").append(df.format(new Date()))
                .append(" (time zone ").append(TimeZone.getDefault().getID()).append(").\n");
        String name = prefs.userName();
        if (!name.isEmpty()) ctx.append("The user's name is ").append(name).append(".\n");
        ctx.append("\nSaved memories about the user:\n").append(memory.asPromptText());
        if (code) {
            ctx.append("\nCODING MODE is on: act as an expert software engineer. Give complete, runnable code, "
                    + "explain briefly, point out bugs and edge cases, and prefer step-by-step fixes.\n");
        }
        if (voice) {
            ctx.append("\nThe user is talking by VOICE and your reply will be read aloud: answer in 1-4 short, "
                    + "natural spoken sentences, with no markdown, lists, emojis, tables or URLs (unless they asked for code).\n");
        }

        List<TextBlockParam> system = new ArrayList<>();
        system.add(TextBlockParam.builder().text(SYSTEM_PROMPT).build());
        system.add(TextBlockParam.builder().text(ctx.toString()).build());

        MessageCreateParams.Builder b = MessageCreateParams.builder()
                .model(model)
                .maxTokens(MAX_TOKENS)
                .systemOfTextBlockParams(system)
                .outputConfig(OutputConfig.builder()
                        .effort(code ? OutputConfig.Effort.HIGH : (voice ? OutputConfig.Effort.LOW : OutputConfig.Effort.MEDIUM))
                        .build())
                // Automatic prompt caching of the conversation prefix
                .cacheControl(CacheControlEphemeral.builder().build());

        for (Tool t : PhoneTools.definitions()) b.addTool(t);
        if (prefs.webSearch()) {
            b.addTool(WebSearchTool20260209.builder().maxUses(5L).build());
            b.addTool(WebFetchTool20260209.builder().maxUses(5L).build());
        }

        // If a safety classifier declines on Opus/Sonnet, let the API retry on a fallback model.
        if (!model.startsWith("claude-haiku")) {
            b.putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01");
            b.putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
        }

        synchronized (this) { b.messages(new ArrayList<>(history)); }
        return b.build();
    }

    private static void collectSources(Message msg, Map<String, String> sources) {
        for (ContentBlock b : msg.content()) {
            b.text().ifPresent(t -> t.citations().ifPresent(list -> {
                for (TextCitation c : list) {
                    c.webSearchResultLocation().ifPresent(w ->
                            sources.put(w.url(), w.title().orElse(w.url())));
                }
            }));
        }
    }

    private static String friendly(Throwable e) {
        if (e instanceof UnauthorizedException) return "API key সঠিক নয়। ⚙️ সেটিংসে গিয়ে সঠিক Claude API key দিন।";
        if (e instanceof PermissionDeniedException) return "এই API key দিয়ে এই মডেল ব্যবহারের অনুমতি নেই। সেটিংসে অন্য মডেল বেছে দেখুন।";
        if (e instanceof RateLimitException) return "অল্প সময়ে অনেক বেশি অনুরোধ হয়েছে, বা API ক্রেডিট শেষ। একটু পরে আবার চেষ্টা করুন।";
        if (e instanceof OverloadedException) return "Claude সার্ভার এখন ব্যস্ত। কিছুক্ষণ পর আবার চেষ্টা করুন।";
        if (e instanceof BadRequestException) {
            String m = String.valueOf(e.getMessage());
            if (m.contains("credit")) return "API অ্যাকাউন্টে ক্রেডিট নেই। console.anthropic.com থেকে ক্রেডিট যোগ করুন।";
            return "অনুরোধে সমস্যা: " + m;
        }
        if (e instanceof AnthropicIoException) return "ইন্টারনেট সংযোগ পাওয়া যাচ্ছে না। নেট চেক করে আবার চেষ্টা করুন।";
        if (e instanceof AnthropicServiceException) return "সার্ভার সমস্যা (" + ((AnthropicServiceException) e).statusCode() + ")। আবার চেষ্টা করুন।";
        String m = e.getMessage();
        return "সমস্যা হয়েছে: " + (m != null ? m : e.getClass().getSimpleName());
    }
}
