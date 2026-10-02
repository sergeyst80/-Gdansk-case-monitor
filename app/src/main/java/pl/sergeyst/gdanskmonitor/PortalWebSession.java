package pl.sergeyst.gdanskmonitor;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.http.SslError;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebStorage;
import android.webkit.ServiceWorkerClient;
import android.webkit.ServiceWorkerController;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PortalWebSession {
    public interface Callback { void done(JSONObject snapshot, String error); }
    public static final String URL = "https://klient.gdansk.uw.gov.pl/";
    private static final AtomicBoolean PORTAL_BUSY = new AtomicBoolean();
    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final String adapter;
    private WebView webView;
    private Callback callback;
    private Account account;
    private boolean submitted, loaded, ownsLock;
    private boolean cleaning, destroyed;
    private int generation;
    private long deadline;
    private int stage;
    public static boolean isBusy() { return PORTAL_BUSY.get(); }
    private void phase(int value) {
        if (stage != value) { stage = value; AppStatus.portal(value, true); }
    }

    public PortalWebSession(Context context) {
        this.context = context.getApplicationContext();
        try (InputStream in = this.context.getAssets().open("portal_adapter.js")) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) != -1) bytes.write(buffer, 0, n);
            adapter = bytes.toString(StandardCharsets.UTF_8.name());
        } catch (Exception e) { throw new IllegalStateException("Cannot load portal adapter", e); }
    }

    public void check(Account a, Callback cb) {
        if (destroyed || cleaning || callback != null || !PORTAL_BUSY.compareAndSet(false, true)) {
            cb.done(null, I18n.errorCode("portal_busy"));
            return;
        }
        ownsLock = true;
        account = a; callback = cb; submitted = false; loaded = false;
        stage = 0; phase(R.string.action_start);
        deadline = SystemClock.elapsedRealtime() + 90000;
        final int run = ++generation;
        try {
            webView = new WebView(context);
            WebSettings s = webView.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            s.setAllowFileAccess(false);
            s.setAllowContentAccess(false);
            s.setAllowFileAccessFromFileURLs(false);
            s.setAllowUniversalAccessFromFileURLs(false);
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            s.setSafeBrowsingEnabled(true);
            s.setCacheMode(WebSettings.LOAD_NO_CACHE);
            ServiceWorkerController workers = ServiceWorkerController.getInstance();
            workers.getServiceWorkerWebSettings().setAllowFileAccess(false);
            workers.getServiceWorkerWebSettings().setAllowContentAccess(false);
            workers.getServiceWorkerWebSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
            workers.setServiceWorkerClient(new ServiceWorkerClient() {
                @Override public WebResourceResponse shouldInterceptRequest(WebResourceRequest request) {
                    return blockedResource(request);
                }
            });
            s.setLoadsImagesAutomatically(true);
            s.setUseWideViewPort(true);
            s.setLoadWithOverviewMode(true);
            // Detached WebViews need a real viewport for Vaadin layout and visibility.
            webView.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY));
            webView.layout(0, 0, 1080, 1920);
            webView.setWebViewClient(new WebViewClient() {
                @Override public void onPageStarted(WebView view, String url, Bitmap favicon) {
                    if (active(run)) { if (!trusted(url)) fail(I18n.errorCode("redirect_error")); else phase(R.string.action_load); }
                }
                @Override public void onPageFinished(WebView view, String url) {
                    if (active(run)) loaded = true;
                }
                @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    if (!trusted(request.getUrl().toString())) {
                        if (active(run)) fail(I18n.errorCode("redirect_error"));
                        return true;
                    }
                    return false;
                }
                @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                    return blockedResource(request);
                }
                @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                    if (active(run) && request.isForMainFrame()) fail(I18n.errorCode("network_error", error.getErrorCode()));
                }
                @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                    if (active(run) && request.isForMainFrame()) fail(I18n.errorCode("http_error", response.getStatusCode()));
                }
                @Override public void onReceivedSslError(WebView view, SslErrorHandler ssl, SslError error) {
                    ssl.cancel();
                    if (active(run)) fail(sslMessage(error));
                }
            });
            CookieManager cm = CookieManager.getInstance();
            cm.setAcceptCookie(true);
            cm.setAcceptThirdPartyCookies(webView, false);
            WebStorage.getInstance().deleteAllData();
            handler.postDelayed(() -> timeout(run), 90000);
            cm.removeAllCookies(v -> {
                if (!active(run)) return;
                cm.flush();
                phase(R.string.action_connect);
                webView.loadUrl(URL);
                // Poll independently of onPageFinished and stalled subresources.
                handler.postDelayed(() -> poll(run), 500);
            });
        } catch (Exception e) { fail(I18n.errorCode("webview_error")); }
    }

    private boolean trusted(String url) {
        return PortalPolicy.trusted(url);
    }

    private static WebResourceResponse blockedResource(WebResourceRequest request) {
        if (PORTAL_BUSY.get() && PortalPolicy.trusted(request.getUrl().toString())) return null;
        return new WebResourceResponse("text/plain", "UTF-8", 403, "Forbidden",
                java.util.Collections.emptyMap(), new ByteArrayInputStream(new byte[0]));
    }

    private static String sslMessage(SslError error) {
        return I18n.errorCode("ssl_error", error.getPrimaryError());
    }
    private boolean active(int run) { return callback != null && generation == run; }

    private void timeout(int run) {
        if (!active(run)) return;
        long remaining = deadline - SystemClock.elapsedRealtime();
        if (remaining > 0) { handler.postDelayed(() -> timeout(run), remaining); return; }
        fail(I18n.errorCode("timeout_error"));
    }

    private void poll(int run) {
        if (!active(run)) return;
        if (SystemClock.elapsedRealtime() >= deadline) { timeout(run); return; }
        if (!trusted(webView.getUrl() == null ? "" : webView.getUrl())) {
            handler.postDelayed(() -> poll(run), 500); return;
        }
        String js = "(" + adapter + ")(" + JSONObject.quote(submitted ? "read" : "login") + ","
                + JSONObject.quote(submitted ? "" : account.login) + ","
                + JSONObject.quote(submitted ? "" : account.password) + ")";
        webView.evaluateJavascript(js, value -> {
            if (!active(run)) return;
            try {
                JSONObject result = new JSONObject(new JSONArray("[" + value + "]").getString(0));
                switch (result.optString("state")) {
                    case "SUBMITTED":
                        submitted = true; phase(R.string.action_read);
                        deadline = SystemClock.elapsedRealtime() + 60000;
                        break;
                    case "READY":
                        if (submitted) { finish(result.getJSONObject("snapshot"), null); return; }
                        phase(R.string.action_login);
                        break;
                    case "CHALLENGE": fail(I18n.errorCode("challenge_error")); return;
                    case "REJECTED": fail(I18n.errorCode("rejected_error")); return;
                    case "WAIT_FORM": phase(R.string.action_login); break;
                    case "LOGIN": phase(R.string.action_login); break;
                    default: if (loaded && !submitted) phase(R.string.action_load);
                }
            } catch (Exception ignored) { phase(R.string.action_read); }
            handler.postDelayed(() -> poll(run), submitted ? 1200 : 700);
        });
    }

    private void fail(String error) { finish(null, error); }
    private void finish(JSONObject snapshot, String error) {
        if (callback == null) return;
        Callback cb = callback; callback = null; account = null;
        cleanup(success -> {
            if (!destroyed) cb.done(success ? snapshot : null, success ? error : I18n.errorCode("webview_error"));
        });
    }
    private void cleanup(java.util.function.Consumer<Boolean> completed) {
        if (cleaning) return;
        if (!ownsLock) { if (completed != null) completed.accept(true); return; }
        cleaning = true;
        generation++;
        handler.removeCallbacksAndMessages(null);
        try {
            if (webView != null) {
                webView.stopLoading(); webView.clearCache(true); webView.clearHistory();
                webView.destroy(); webView = null;
            }
            WebStorage.getInstance().deleteAllData();
            // Keep the global lock until asynchronous cookie removal completes.
            CookieManager.getInstance().removeAllCookies(removed -> {
                try {
                    CookieManager.getInstance().flush();
                    ownsLock = false; cleaning = false; PORTAL_BUSY.set(false);
                    AppStatus.portal(R.string.action_idle, false);
                } catch (Exception e) {
                    // Fail closed: no new account may use an uncleared session.
                    if (completed != null) completed.accept(false);
                    return;
                }
                if (completed != null) completed.accept(true);
            });
        } catch (Exception e) { if (completed != null) completed.accept(false); }
    }
    public void destroy() { destroyed = true; callback = null; account = null; cleanup(null); }
}
