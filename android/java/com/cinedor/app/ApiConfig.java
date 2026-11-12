package com.cinedor.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Server address + optional token (editable in Settings), and a tiny HTTP GET helper. */
public final class ApiConfig {

    /** Default server address — change it here or later inside the app (Settings). */
    public static final String DEFAULT_BASE_URL = "http://192.168.0.104:3000";

    private static final String PREFS = "server";
    private static volatile String baseUrl = DEFAULT_BASE_URL;
    private static volatile String token = "";

    private ApiConfig() {}

    public static void init(Context ctx) {
        SharedPreferences p = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        baseUrl = clean(p.getString("base", DEFAULT_BASE_URL));
        token = p.getString("token", "");
    }

    public static void save(Context ctx, String base, String tok) {
        baseUrl = clean(base);
        token = tok == null ? "" : tok.trim();
        ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("base", baseUrl).putString("token", token).apply();
    }

    public static String baseUrl() { return baseUrl; }
    public static String token() { return token; }

    /** True when requests to this url should carry our token. */
    public static boolean needsToken(String url) {
        return !token.isEmpty() && url != null && url.startsWith(baseUrl);
    }

    /** Turns a server-relative path into an absolute url. */
    public static String abs(String u) {
        if (u == null || u.isEmpty() || "null".equals(u)) return "";
        if (u.contains("://")) return u;
        return baseUrl + (u.startsWith("/") ? u : "/" + u);
    }

    private static String clean(String s) {
        s = s == null ? "" : s.trim();
        if (s.isEmpty()) s = DEFAULT_BASE_URL;
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://" + s;
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    public static String get(String path) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(baseUrl + path).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(20000);
        c.setRequestProperty("Accept", "application/json");
        if (!token.isEmpty()) c.setRequestProperty("X-App-Token", token);
        try {
            int code = c.getResponseCode();
            InputStream in = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
            StringBuilder sb = new StringBuilder();
            if (in != null) {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                }
            }
            if (code == 401) throw new Exception("wrong token");
            if (code < 200 || code >= 300) throw new Exception("server error " + code);
            return sb.toString();
        } finally {
            c.disconnect();
        }
    }
}
