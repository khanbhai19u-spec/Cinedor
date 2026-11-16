package com.cinedor.console;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Cinedor Console — the admin app. It is a separate app from Cinedor (own icon, package and releases)
 * that signs in to YOUR server's console. Nothing here is part of the viewer app.
 */
public class MainActivity extends AppCompatActivity {

    private static final String PREFS = "console";

    private WebView web;
    private ProgressBar progress;
    private View connect, offline;
    private EditText serverInput;
    private TextView status, offlineText;
    private ValueCallback<Uri[]> chooser;
    private boolean pageFailed;

    private final ActivityResultLauncher<Intent> picker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (chooser == null) return;
                Uri[] uris = WebChromeClient.FileChooserParams.parseResult(result.getResultCode(), result.getData());
                chooser.onReceiveValue(uris);
                chooser = null;
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        web = findViewById(R.id.web);
        progress = findViewById(R.id.progress);
        connect = findViewById(R.id.connect);
        offline = findViewById(R.id.offline);
        serverInput = findViewById(R.id.server);
        status = findViewById(R.id.status);
        offlineText = findViewById(R.id.offlineText);

        findViewById(R.id.btnConnect).setOnClickListener(v -> tryConnect());
        serverInput.setOnEditorActionListener((t, action, e) -> {
            if (action == EditorInfo.IME_ACTION_GO || action == EditorInfo.IME_ACTION_DONE) { tryConnect(); return true; }
            return false;
        });
        findViewById(R.id.btnRetry).setOnClickListener(v -> openConsole());
        findViewById(R.id.btnChange).setOnClickListener(v -> changeServer());

        setupWebView();
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (web.getVisibility() == View.VISIBLE && web.canGoBack()) web.goBack();
                else { setEnabled(false); getOnBackPressedDispatcher().onBackPressed(); }
            }
        });

        if (server().isEmpty()) showConnect(); else openConsole();
    }

    // ---------- server address ----------
    private SharedPreferences prefs() { return getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
    private String server() { return prefs().getString("server", ""); }

    private static String clean(String s) {
        s = s == null ? "" : s.trim();
        if (s.isEmpty()) return "";
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://" + s;
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        // the console lives at /console — accept it if the user pasted the full link
        if (s.endsWith("/console")) s = s.substring(0, s.length() - "/console".length());
        return s;
    }

    private void tryConnect() {
        final String url = clean(serverInput.getText().toString());
        if (url.isEmpty()) { status.setText("Enter your server address first"); return; }
        status.setText("Connecting…");
        hideKeyboard();
        new Thread(() -> {
            String error = null;
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url + "/console/api/state").openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                c.setRequestProperty("Accept", "application/json");
                int code = c.getResponseCode();
                if (code != 200) throw new Exception("The server answered " + code + " — is this a Cinedor server?");
                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                }
                c.disconnect();
                if (!new JSONObject(sb.toString()).has("configured")) throw new Exception("That doesn't look like a Cinedor server");
            } catch (java.net.UnknownHostException e) {
                error = "Can't find that address";
            } catch (java.net.SocketTimeoutException | java.net.ConnectException e) {
                error = "Can't reach the server — same Wi-Fi? Is it running?";
            } catch (Exception e) {
                error = e.getMessage() == null ? "Can't connect" : e.getMessage();
            }
            final String err = error;
            runOnUiThread(() -> {
                if (err != null) { status.setText("✗ " + err); return; }
                prefs().edit().putString("server", url).apply();
                status.setText("");
                openConsole();
            });
        }).start();
    }

    private void changeServer() {
        prefs().edit().remove("server").apply();
        CookieManager.getInstance().removeAllCookies(null);
        CookieManager.getInstance().flush();
        web.loadUrl("about:blank");
        web.clearHistory();
        showConnect();
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && getCurrentFocus() != null) imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
    }

    // ---------- screens ----------
    private void showConnect() {
        web.setVisibility(View.GONE);
        offline.setVisibility(View.GONE);
        progress.setVisibility(View.GONE);
        connect.setVisibility(View.VISIBLE);
        serverInput.setText(server());
    }

    private void openConsole() {
        String url = server();
        if (url.isEmpty()) { showConnect(); return; }
        connect.setVisibility(View.GONE);
        offline.setVisibility(View.GONE);
        web.setVisibility(View.VISIBLE);
        pageFailed = false;
        web.loadUrl(url + "/console/");
    }

    private void showOffline(String why) {
        offlineText.setText("Can't reach your server\n" + server() + "\n\n" + why);
        web.setVisibility(View.GONE);
        progress.setVisibility(View.GONE);
        offline.setVisibility(View.VISIBLE);
    }

    // ---------- web view ----------
    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void setupWebView() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);          // the console is a small web app
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setUserAgentString(s.getUserAgentString() + " CinedorConsoleApp/" + version());
        web.setBackgroundColor(0xFF0F1014);
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);

        web.addJavascriptInterface(new Bridge(), "CinedorAdmin");
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                String base = server();
                if (!base.isEmpty() && u.toString().startsWith(base)) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) {}
                return true;   // never navigate the console away from your server
            }

            @Override public void onPageStarted(WebView v, String url, Bitmap favicon) {
                progress.setVisibility(View.VISIBLE);
            }

            @Override public void onPageFinished(WebView v, String url) {
                progress.setVisibility(View.GONE);
                CookieManager.getInstance().flush();
            }

            @Override public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame()) { pageFailed = true; showOffline(String.valueOf(e.getDescription())); }
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView v, int p) { progress.setProgress(p); }

            @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (chooser != null) chooser.onReceiveValue(null);
                chooser = cb;
                try {
                    picker.launch(params.createIntent());
                } catch (Exception e) {
                    chooser = null;
                    Toast.makeText(MainActivity.this, "Can't open the file picker", Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            }
        });
    }

    /** Small bridge the console page uses to show app-only options. */
    private class Bridge {
        @JavascriptInterface public String version() { return MainActivity.this.version(); }
        @JavascriptInterface public String server() { return MainActivity.this.server(); }
        @JavascriptInterface public void changeServer() {
            runOnUiThread(() -> new AlertDialog.Builder(MainActivity.this)
                    .setMessage("Disconnect from " + MainActivity.this.server() + " and choose another server?")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Change", (d, w) -> MainActivity.this.changeServer())
                    .show());
        }
    }

    private String version() {
        try {
            String v = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            return v == null ? "" : v;
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    protected void onPause() {
        CookieManager.getInstance().flush();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (chooser != null) { chooser.onReceiveValue(null); chooser = null; }
        web.destroy();
        super.onDestroy();
    }
}
