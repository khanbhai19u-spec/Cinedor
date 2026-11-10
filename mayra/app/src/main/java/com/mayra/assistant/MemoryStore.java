package com.mayra.assistant;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Long-term personal memory: short facts about the user, kept on the phone only. */
public final class MemoryStore {

    public static final class Item {
        public final String id;
        public final String text;
        public final long time;
        Item(String id, String text, long time) { this.id = id; this.text = text; this.time = time; }
    }

    private static final int MAX_ITEMS = 300;
    private final File file;

    public MemoryStore(Context c) {
        file = new File(c.getApplicationContext().getFilesDir(), "memory.json");
    }

    public synchronized List<Item> all() {
        List<Item> out = new ArrayList<>();
        try {
            if (!file.exists()) return out;
            byte[] b = new byte[(int) file.length()];
            try (FileInputStream in = new FileInputStream(file)) {
                int off = 0, n;
                while (off < b.length && (n = in.read(b, off, b.length - off)) > 0) off += n;
            }
            JSONArray a = new JSONArray(new String(b, StandardCharsets.UTF_8));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(new Item(o.getString("id"), o.getString("text"), o.optLong("time")));
            }
        } catch (Exception ignored) { }
        return out;
    }

    public synchronized Item add(String text) {
        List<Item> items = all();
        String clean = text.trim();
        for (Item it : items) if (it.text.equalsIgnoreCase(clean)) return it;
        int max = 0;
        for (Item it : items) {
            try { max = Math.max(max, Integer.parseInt(it.id.substring(1))); } catch (Exception ignored) { }
        }
        Item item = new Item("m" + (max + 1), clean, System.currentTimeMillis());
        items.add(item);
        while (items.size() > MAX_ITEMS) items.remove(0);
        save(items);
        return item;
    }

    public synchronized boolean remove(String id) {
        List<Item> items = all();
        boolean removed = false;
        for (int i = items.size() - 1; i >= 0; i--) {
            if (items.get(i).id.equalsIgnoreCase(id.trim())) { items.remove(i); removed = true; }
        }
        if (removed) save(items);
        return removed;
    }

    public synchronized void clear() { save(new ArrayList<>()); }

    /** Text block placed in the system prompt so Claude knows what it remembers. */
    public String asPromptText() {
        List<Item> items = all();
        if (items.isEmpty()) return "(nothing saved yet)";
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        StringBuilder sb = new StringBuilder();
        for (Item it : items) sb.append("- [").append(it.id).append("] ").append(it.text)
                .append(" (saved ").append(f.format(new Date(it.time))).append(")\n");
        return sb.toString();
    }

    private void save(List<Item> items) {
        try {
            JSONArray a = new JSONArray();
            for (Item it : items) a.put(new JSONObject().put("id", it.id).put("text", it.text).put("time", it.time));
            File tmp = new File(file.getPath() + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(a.toString().getBytes(StandardCharsets.UTF_8));
            }
            //noinspection ResultOfMethodCallIgnored
            tmp.renameTo(file);
        } catch (Exception ignored) { }
    }
}
