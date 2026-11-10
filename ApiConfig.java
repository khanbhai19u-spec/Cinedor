package com.cinedor.app;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Optional: your own personal server (not needed for the automatic movie catalog, see MovieApi).
 * The token is only sent to BASE_URL.
 */
public final class ApiConfig {

    /** Your personal server. Updated with your Termux server IP and port */
    public static final String BASE_URL = "http://192.168.0.104:5000";

    /** Same value as APP_TOKEN on the server. Leave "" if you did not set a token. */
    public static final String APP_TOKEN = "";

    private ApiConfig() {}

    public static String get(String path) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(BASE_URL + path).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setRequestProperty("Accept", "application/json");
        if (!APP_TOKEN.isEmpty()) c.setRequestProperty("X-App-Token", APP_TOKEN);
        int code = c.getResponseCode();
        InputStream in = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
        StringBuilder sb = new StringBuilder();
        if (in != null) {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }
        }
        c.disconnect();
        if (code < 200 || code >= 300) throw new Exception("Server error " + code);
        return sb.toString();
    }
}
