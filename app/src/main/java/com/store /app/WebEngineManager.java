package com.store.app;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebBackForwardList;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.webkit.Navigation;
import androidx.webkit.NavigationListener;
import androidx.webkit.Page;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import com.store.app.offline.OfflineStateManager;
import com.store.app.webview.SpeculativeEngine;
import com.store.app.webview.WebEngineConfig;
import com.store.app.webview.WebViewNavigationManager;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class WebEngineManager {

    private static final String TAG = "RoyalEngine";

    private static final String IME_VISUAL_VIEWPORT_JS =
            "(function(){"
                    + "if(window.__NEXUS_IME_VIEWPORT__)return;"
                    + "window.__NEXUS_IME_VIEWPORT__=true;"
                    + "function install(){"
                    + "if(!window.visualViewport)return;"
                    + "var root=document.documentElement;"
                    + "var viewport=window.visualViewport;"
                    + "var lastBottom=-1;"
                    + "function sync(){"
                    + "var keyboardHeight=Math.max(0,"
                    + "(window.innerHeight||0)-viewport.height-viewport.offsetTop);"
                    + "if(keyboardHeight===lastBottom)return;"
                    + "lastBottom=keyboardHeight;"
                    + "root.style.setProperty('--nexus-ime-bottom',keyboardHeight+'px');"
                    + "window.dispatchEvent(new CustomEvent('nexus:visual-viewport-ime',"
                    + "{detail:{bottom:keyboardHeight,height:viewport.height,offsetTop:viewport.offsetTop}}));"
                    + "}"
                    + "viewport.addEventListener('resize',sync);"
                    + "viewport.addEventListener('scroll',sync);"
                    + "window.addEventListener('resize',sync);"
                    + "sync();"
                    + "}"
                    + "if(document.readyState==='loading'){document.addEventListener('DOMContentLoaded',install,{once:true});}"
                    + "else{install();}"
                    + "})();";

    private final Context context;
    private final android.app.Activity activity;
    private final WebView webView;
    private final View splashOverlay;
    private final android.widget.ProgressBar progressBar;

    private final Runnable markSplashRemoved;
    private final SplashStateChecker splashChecker;

    private final Runnable scrollFinishedRunnable =
            RoyalNetworkEngine::notifyScrollFinished;

    private long splashStartTime = 0L;

    private final RoyalCapabilitiesEngine capabilitiesEngine;
    private final SpeculativeEngine speculativeEngine;
    private final WebEngineConfig webEngineConfig;
    private final WebViewNavigationManager navigationManager;

    public interface SplashStateChecker {
        boolean isRemoved();
    }

    public WebEngineManager(
            Context context,
            WebView webView,
            View splashOverlay,
            android.widget.ProgressBar progressBar,
            Runnable markSplashRemoved,
            SplashStateChecker splashChecker
    ) {
        this.context = context;
        this.webView = webView;
        this.splashOverlay = splashOverlay;
        this.progressBar = progressBar;
        this.markSplashRemoved = markSplashRemoved;
        this.splashChecker = splashChecker;

        this.activity = context instanceof android.app.Activity
                ? (android.app.Activity) context
                : null;

        if (this.webView != null) {
            this.webView.setBackgroundColor(
                    SystemUI.getDefaultSystemColor(this.context)
            );
        }

        this.capabilitiesEngine = new RoyalCapabilitiesEngine(this.activity);
        this.speculativeEngine = new SpeculativeEngine(this.activity, this.webView);
        this.webEngineConfig = new WebEngineConfig(
                this.context,
                this.webView,
                this.activity
        );

        this.navigationManager = new WebViewNavigationManager(
                this.context,
                this.activity,
                this.webView,
                this.webEngineConfig
        );
    }

    public boolean safeGoBack() {
        try {
            if (webView == null) return false;

            WebBackForwardList list = webView.copyBackForwardList();
            if (list == null) return false;

            int currentIndex = list.getCurrentIndex();

            for (int i = currentIndex - 1; i >= 0; i--) {
                String candidate = list.getItemAtIndex(i).getUrl();
                if (candidate == null) continue;

                String lower = candidate.toLowerCase();

                if (lower.startsWith("about:")
                        || lower.startsWith("data:")
                        || lower.contains("chromewebdata")) {
                    continue;
                }

                final int steps = i - currentIndex;

                if (activity != null) {
                    activity.runOnUiThread(() -> {
                        try {
                            webView.goBackOrForward(steps);
                        } catch (Exception e) {
                            Log.w(TAG, "safeGoBack: goBackOrForward failed", e);
                        }
                    });
                }

                return true;
            }

            return false;

        } catch (Exception e) {
            Log.w(TAG, "safeGoBack: error", e);
            return false;
        }
    }

    public RoyalCapabilitiesEngine getCapabilitiesHandler() {
        return capabilitiesEngine;
    }

    public void predict(String url) {
        if (speculativeEngine != null) {
            speculativeEngine.predict(url);
        }
    }

    public SpeculativeEngine getSpeculativeEngine() {
        return speculativeEngine;
    }

    public void setSplashStartTime(long startTime) {
        splashStartTime = startTime;
    }

    public void init() {
        if (webView == null) return;

        webView.setBackgroundColor(
                SystemUI.getDefaultSystemColor(context)
        );

        String currentUrl = webView.getUrl();

        if (currentUrl == null
                || currentUrl.equalsIgnoreCase("about:blank")
                || currentUrl.contains("chromewebdata")) {

            Log.w(
                    TAG,
                    "WebView stuck on invalid frame (" + currentUrl + "). Recovering state..."
            );

            if (NetworkMonitor.isInternetAvailable(context)) {
                webView.loadUrl(BuildConfig.CLIENT_URL);
            } else {
                OfflineStateManager.getInstance().setErrorPage(
                        true,
                        BuildConfig.CLIENT_URL
                );
            }

        } else if (RoyalWebViewHost.isReady()) {
            Log.i(
                    TAG,
                    "Warm Resume detected; enforcing fixed splash time."
            );
        }

        webEngineConfig.configureSettings();

        // OAuth and Custom Tabs remain managed by WebViewNavigationManager.
        navigationManager.setupCustomTabsSession();

        attachClients();

        speculativeEngine.initializeSpeculativeLoading();

        String clientUrl = BuildConfig.CLIENT_URL;
        if (clientUrl != null) {
            speculativeEngine.preconnectOrigin(clientUrl);
        }

        addNavigationListener();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            webView.setOnScrollChangeListener(
                    (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                        RoyalNetworkEngine.notifyScroll(scrollY);
                        v.removeCallbacks(scrollFinishedRunnable);
                        v.postDelayed(scrollFinishedRunnable, 90L);
                    }
            );
        }
    }

    private void addNavigationListener() {
        if (webView == null) return;

        if (!WebViewFeature.isFeatureSupported(
                WebViewFeature.NAVIGATION_LISTENER
        )) {
            return;
        }

        WebViewCompat.addNavigationListener(
                webView,
                new NavigationListener() {
                    @Override
                    public void onFirstContentfulPaintMillis(
                            @NonNull Page page,
                            long durationMillis
                    ) {
                        Log.i(
                                "Performance",
                                "FCP: " + durationMillis + "ms"
                        );
                        RoyalPanopticon.recordMetric("FCP", durationMillis);
                    }

                    @Override
                    public void onPageDomContentLoadedEvent(@NonNull Page page) {
                        Log.i("Performance", "DOMContentLoaded");
                    }

                    @Override
                    public void onPageLoadEvent(@NonNull Page page) {
                        Log.i("Performance", "Load event fired");
                    }

                    @Override
                    public void onNavigationStarted(
                            @NonNull Navigation navigation
                    ) {
                        if (speculativeEngine != null) {
                            speculativeEngine.onNavigationStarted();
                        }
                        Log.i("Performance", "Navigation started");
                    }

                    @Override
                    public void onNavigationRedirected(
                            @NonNull Navigation navigation
                    ) {
                        Log.i("Performance", "Navigation redirected");
                    }

                    @Override
                    public void onNavigationCompleted(
                            @NonNull Navigation navigation
                    ) {
                        Log.i("Performance", "Navigation completed");
                    }

                    @Override
                    public void onPageDeleted(@NonNull Page page) {
                        Log.i("Performance", "Page evicted");
                    }

                    @Override
                    public void onLargestContentfulPaintMillis(
                            @NonNull Page page,
                            long durationMillis
                    ) {
                        Log.i(
                                "Performance",
                                "LCP: " + durationMillis + "ms"
                        );
                        RoyalPanopticon.recordMetric("LCP", durationMillis);
                    }

                    @Override
                    public void onPerformanceMarkMillis(
                            @NonNull Page page,
                            @NonNull String markName,
                            long markTimeMillis
                    ) {
                        Log.i(
                                "Performance",
                                "Performance mark: "
                                        + markName
                                        + " at "
                                        + markTimeMillis
                                        + "ms"
                        );
                    }
                }
        );

        Log.i(TAG, "NavigationListener added for performance metrics.");
    }

    private void removeSplashInstantly() {
        if (activity == null) return;

        activity.runOnUiThread(() -> {
            if (splashOverlay != null
                    && splashOverlay.getParent() instanceof ViewGroup) {
                ((ViewGroup) splashOverlay.getParent())
                        .removeView(splashOverlay);
            }

            if (progressBar != null) {
                progressBar.setVisibility(View.GONE);
            }

            if (markSplashRemoved != null) {
                markSplashRemoved.run();
            }

            RoyalNetworkEngine.notifyRenderIdle();
        });
    }

    public void removeSplashSmoothly() {
        if (activity == null) return;
        if (splashChecker != null && splashChecker.isRemoved()) return;

        activity.runOnUiThread(() -> {
            if (splashOverlay != null && splashOverlay.getAlpha() > 0f) {
                splashOverlay.animate()
                        .alpha(0f)
                        .setDuration(400L)
                        .withEndAction(this::removeSplashInstantly)
                        .start();
            } else {
                removeSplashInstantly();
            }
        });
    }

    private void injectImeVisualViewportLayer(WebView view) {
        if (view == null) return;
        view.evaluateJavascript(IME_VISUAL_VIEWPORT_JS, null);
    }

    private void attachClients() {
        if (webView == null) return;

        attachWebViewClient();

        webView.setWebChromeClient(
                capabilitiesEngine.buildChromeClient(progressBar)
        );

        capabilitiesEngine.attachDownloadManager(webView);
    }

    private void attachWebViewClient() {
        if (webView == null) return;

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public void onPageStarted(
                    WebView view,
                    String url,
                    android.graphics.Bitmap favicon
            ) {
                super.onPageStarted(view, url, favicon);

                RoyalPanopticon.recordRequestSent();
                SystemUI.beginPageNavigation(activity, view, url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (view != null) {
                    // OAuth script is owned by WebViewNavigationManager.
                    navigationManager.injectOAuthScript(view);
                    injectImeVisualViewportLayer(view);
                }

                try {
                    CookieManager cookieManager = CookieManager.getInstance();
                    cookieManager.flush();

                    Log.i(
                            TAG,
                            "Session cookies flushed to disk for: " + url
                    );

                } catch (Exception e) {
                    Log.w(TAG, "Cookie flush failed.", e);
                }

                RoyalPanopticon.recordNavigationComplete();
                RoyalNetworkEngine.notifyRenderIdle();

                if (url == null
                        || url.startsWith("data:")
                        || url.startsWith("about:")
                        || url.contains("chromewebdata")) {

                    OfflineStateManager.getInstance().setPageValid(false);
                    Log.w(TAG, "Invalid/error page finished: " + url);
                    return;
                }

                if (OfflineStateManager.getInstance().isOnErrorPage()) {
                    Log.w(
                            TAG,
                            "Ignoring onPageFinished because page is marked as error."
                    );
                    return;
                }

                OfflineStateManager.getInstance().setPageValid(true);
                Log.i(TAG, "Page finished successfully. Page is valid.");

                if (activity instanceof MainActivity) {
                    ((MainActivity) activity)
                            .notifyPageFinishedForSplash(view);
                }
            }

            @Override
            public void onPageCommitVisible(WebView view, String url) {
                if (url != null
                        && !url.startsWith("data:")
                        && !url.startsWith("about:")
                        && !url.contains("chromewebdata")) {

                    Log.i(TAG, "Page committed successfully: " + url);

                    if (webEngineConfig.getTrustedHost() == null) {
                        webEngineConfig.setTrustedOrigin(url);
                    }

                    if (webEngineConfig.getTrustedHost() != null
                            && webEngineConfig.getTrustedScheme() != null
                            && speculativeEngine != null) {

                        speculativeEngine.setTrustedOrigin(
                                webEngineConfig.getTrustedScheme(),
                                webEngineConfig.getTrustedHost(),
                                webEngineConfig.getTrustedPort()
                        );
                    }

                    if (activity != null) {
                        activity.runOnUiThread(() ->
                                WebEnhancer.apply(view, context)
                        );
                    }

                    RoyalNetworkEngine.notifyRenderStart();

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                            && WebViewFeature.isFeatureSupported(
                                    WebViewFeature.VISUAL_STATE_CALLBACK
                            )) {

                        final String committedUrl = url;

                        WebViewCompat.postVisualStateCallback(
                                view,
                                System.nanoTime(),
                                requestId -> view.postDelayed(() -> {
                                    if (activity == null
                                            || activity.isFinishing()
                                            || view.getVisibility() != View.VISIBLE) {
                                        return;
                                    }

                                    if (committedUrl.equals(view.getUrl())) {
                                        webEngineConfig.syncStatusBarColor(view);
                                    }
                                }, 180L)
                        );

                    } else {
                        final String committedUrl = url;

                        view.postDelayed(() -> {
                            if (activity == null
                                    || activity.isFinishing()
                                    || view.getVisibility() != View.VISIBLE) {
                                return;
                            }

                            if (committedUrl.equals(view.getUrl())) {
                                webEngineConfig.syncStatusBarColor(view);
                            }
                        }, 220L);
                    }

                    if (NetworkMonitor.isInternetAvailable(context)
                            && !OfflineStateManager.getInstance().isOnErrorPage()
                            && OfflineStateManager.getInstance().isPageValid()) {

                        OfflineStateManager.getInstance().notifyPageReadyToHide();
                    }

                } else {
                    Log.w(TAG, "Invalid page committed: " + url);
                }
            }

            @Override
            public boolean onRenderProcessGone(
                    WebView view,
                    RenderProcessGoneDetail detail
            ) {
                Log.e(
                        TAG,
                        detail.didCrash()
                                ? "Chromium renderer crashed."
                                : "Chromium renderer killed by system."
                );

                RoyalNetworkEngine.notifyRenderIdle();
                RoyalWebViewHost.destroy();

                if (activity != null && !activity.isFinishing()) {
                    activity.recreate();
                }

                return true;
            }

            @Override
            public void onReceivedError(
                    WebView view,
                    WebResourceRequest request,
                    WebResourceError error
            ) {
                if (request != null && request.isForMainFrame()) {
                    OfflineStateManager.getInstance().setErrorPage(
                            true,
                            request.getUrl().toString()
                    );

                    Log.w(
                            TAG,
                            "Main-frame error detected. Native offline state activated."
                    );
                }
            }

            @SuppressWarnings("deprecation")
            @Override
            public void onReceivedError(
                    WebView view,
                    int errorCode,
                    String description,
                    String failingUrl
            ) {
                OfflineStateManager.getInstance().setErrorPage(
                        true,
                        failingUrl
                );

                Log.w(TAG, "Legacy main-frame error detected.");
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(
                    WebView view,
                    WebResourceRequest request
            ) {
                if (request == null || request.getUrl() == null) {
                    return null;
                }

                String url = request.getUrl().toString();

                if (url.contains("gorgias")
                        || url.contains("facebook.net")
                        || url.contains("analytics")
                        || url.contains("klaviyo")) {

                    String stubScript =
                            "/* Isolated by Nexus Script Shield to ensure 60FPS Performance */";

                    InputStream stubStream = new ByteArrayInputStream(
                            stubScript.getBytes(StandardCharsets.UTF_8)
                    );

                    Log.d(
                            TAG,
                            "Shield: Isolated script -> " + url
                    );

                    return new WebResourceResponse(
                            "application/javascript",
                            "UTF-8",
                            stubStream
                    );
                }

                if (url.endsWith("/nexus-service-worker.js")) {
                    try {
                        InputStream swStream = context.getAssets().open(
                                "public/js/nexus-service-worker.js"
                        );

                        Map<String, String> headers = new HashMap<>();
                        headers.put("Content-Type", "application/javascript");
                        headers.put("Service-Worker-Allowed", "/");
                        headers.put("Cache-Control", "no-cache");

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            return new WebResourceResponse(
                                    "application/javascript",
                                    "UTF-8",
                                    200,
                                    "OK",
                                    headers,
                                    swStream
                            );
                        }

                        return new WebResourceResponse(
                                "application/javascript",
                                "UTF-8",
                                swStream
                        );

                    } catch (Exception e) {
                        Log.e(
                                TAG,
                                "Failed to serve local Service Worker.",
                                e
                        );
                    }
                }

                if (!NetworkMonitor.isInternetAvailable(context)
                        && request.isForMainFrame()) {

                    Uri reqUri = request.getUrl();

                    if (navigationManager.isSensitiveNavigation(reqUri)
                            || OfflineStateManager.getInstance().isPageValid()) {

                        Log.i(
                                TAG,
                                "Offline main-frame request blocked without clearing the current page."
                        );

                        OfflineStateManager.getInstance()
                                .notifyOfflineClickAttempt();

                        InputStream emptyStream = new ByteArrayInputStream(
                                new byte[0]
                        );

                        return new WebResourceResponse(
                                "text/plain",
                                "UTF-8",
                                204,
                                "No Content",
                                null,
                                emptyStream
                        );
                    }

                    Log.i(
                            TAG,
                            "Offline main-frame request intercepted; activating native offline UI."
                    );

                    OfflineStateManager.getInstance().setErrorPage(
                            true,
                            reqUri.toString()
                    );

                    String cleanStub =
                            "<!DOCTYPE html><html><head>"
                                    + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">"
                                    + "</head><body style=\"background-color:transparent;\"></body></html>";

                    InputStream stubStream = new ByteArrayInputStream(
                            cleanStub.getBytes(StandardCharsets.UTF_8)
                    );

                    return new WebResourceResponse(
                            "text/html",
                            "UTF-8",
                            200,
                            "OK",
                            null,
                            stubStream
                    );
                }

                WebResourceResponse royalResponse =
                        RoyalNetworkEngine.interceptRequest(request);

                if (royalResponse != null) {
                    return royalResponse;
                }

                return null;
            }

            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view,
                    WebResourceRequest request
            ) {
                if (request == null || request.getUrl() == null) {
                    return false;
                }

                Uri uri = request.getUrl();

                if (!NetworkMonitor.isInternetAvailable(context)) {
                    OfflineStateManager.getInstance()
                            .notifyOfflineClickAttempt();
                    return true;
                }

                return navigationManager.handleUriLogic(
                        uri,
                        request.isForMainFrame()
                );
            }

            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view,
                    String url
            ) {
                if (url == null) return false;

                Uri uri = Uri.parse(url);

                if (!NetworkMonitor.isInternetAvailable(context)) {
                    OfflineStateManager.getInstance()
                            .notifyOfflineClickAttempt();
                    return true;
                }

                return navigationManager.handleUriLogic(uri, true);
            }
        });
    }

    /*
     * Keep these public methods so existing Java callers can continue
     * using WebEngineManager without needing to know about the new class.
     */

    public void clearNativeSession(Runnable onComplete) {
        navigationManager.clearNativeSession(onComplete);
    }

    public boolean launchSensitiveFlow(Uri uri) {
        return navigationManager.launchSensitiveFlow(uri);
    }

    public void handleAuthReturn(String redirectUrl) {
        navigationManager.handleAuthReturn(redirectUrl);
    }

    public boolean isPageValid() {
        return OfflineStateManager.getInstance().isPageValid();
    }

    public boolean isOnErrorPage() {
        return OfflineStateManager.getInstance().isOnErrorPage();
    }
}
