package com.mayra.assistant;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Saves the visible chat so it is still there after the app is closed. */
public final class ChatStore {

    public static final class Msg {
        public final boolean user;
        public final String text;
        public Msg(boolean user, String text) { this.user = user; this.text = text; }
    }

    private static final int MAX = 200;
    private final File file;

    public ChatStore(Context c) {
        file = new File(c.getApplicationContext().getFilesDir(), "chat.json");
    }

    public synchronized List<Msg> load() {
        List<Msg> out = new ArrayList<>();
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
                out.add(new Msg(o.getBoolean("u"), o.getString("t")));
            }
        } catch (Exception ignored) { }
        return out;
    }

    public synchronized void save(List<Msg> msgs) {
        try {
            JSONArray a = new JSONArray();
            int start = Math.max(0, msgs.size() - MAX);
            for (int i = start; i < msgs.size(); i++) {
                a.put(new JSONObject().put("u", msgs.get(i).user).put("t", msgs.get(i).text));
            }
            File tmp = new File(file.getPath() + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(a.toString().getBytes(StandardCharsets.UTF_8));
            }
            //noinspection ResultOfMethodCallIgnored
            tmp.renameTo(file);
        } catch (Exception ignored) { }
    }

    public synchronized void clear() {
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
