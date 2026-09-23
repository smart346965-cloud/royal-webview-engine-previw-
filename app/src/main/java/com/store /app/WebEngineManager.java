package com.store.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.*;

import androidx.annotation.NonNull;
import androidx.browser.customtabs.CustomTabColorSchemeParams;
import androidx.browser.customtabs.CustomTabsCallback;
import androidx.browser.customtabs.CustomTabsClient;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.browser.customtabs.CustomTabsServiceConnection;
import androidx.browser.customtabs.CustomTabsSession;
import androidx.webkit.Navigation;
import androidx.webkit.NavigationListener;
import androidx.webkit.Page;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import com.store.app.offline.OfflineStateManager;
import com.store.app.webview.SpeculativeEngine;
import com.store.app.webview.WebEngineConfig;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

public class WebEngineManager {

    private static final String TAG = "RoyalEngine";

    private static final String OAUTH_AUTO_INJECTOR_JS =
        "(function() {" +
        "  if (window.__royalOAuthInjected) return;" +
        "  window.__royalOAuthInjected = true;" +
        "  function isOAuthUrl(url) {" +
        "    if (!url) return false;" +
        "    var l = url.toLowerCase();" +
        "    return l.indexOf('accounts.google.com') !== -1 ||" +
        "           l.indexOf('appleid.apple.com') !== -1 ||" +
        "           l.indexOf('facebook.com/v') !== -1 ||" +
        "           l.indexOf('facebook.com/dialog/oauth') !== -1 ||" +
        "           l.indexOf('login.microsoftonline.com') !== -1 ||" +
        "           l.indexOf('login.live.com') !== -1 ||" +
        "           l.indexOf('github.com/login/oauth') !== -1 ||" +
        "           l.indexOf('twitter.com/i/oauth2') !== -1 ||" +
        "           l.indexOf('auth0.com') !== -1;" +
        "  }" +
        "  function getValidUrl(target) {" +
        "    var href = target.getAttribute('href') || target.getAttribute('data-href') || target.getAttribute('action') || '';" +
        "    if (!href || href === '#' || href.indexOf('javascript:') === 0) return null;" +
        "    try { return new URL(href, window.location.href).href; } catch(e) { return null; }" +
        "  }" +
        "  function handleOAuthAction(url, e) {" +
        "    if (!navigator.onLine) {" +
        "      if (e) { e.preventDefault(); e.stopPropagation(); e.stopImmediatePropagation(); }" +
        "      if (window.RoyalJsBridge && window.RoyalJsBridge.notifyOfflineClick) { window.RoyalJsBridge.notifyOfflineClick(); }" +
        "      return true;" +
        "    }" +
        "    if (window.RoyalJsBridge) {" +
        "      if (e) { e.preventDefault(); e.stopPropagation(); e.stopImmediatePropagation(); }" +
        "      window.RoyalJsBridge.startOAuth(url);" +
        "      return true;" +
        "    }" +
        "    return false;" +
        "  }" +
        "  document.addEventListener('click', function(e) {" +
        "    var target = e.target.closest('a, button, [role=\"button\"], input[type=\"submit\"], form');" +
        "    if (!target) return;" +
        "    var url = getValidUrl(target);" +
        "    if (url && isOAuthUrl(url)) { handleOAuthAction(url, e); }" +
        "  }, true);" +
        "  try {" +
        "    var originalAssign = window.location.assign;" +
        "    if (typeof originalAssign === 'function') {" +
        "      window.location.assign = function(url) {" +
        "        if (isOAuthUrl(url)) { if (handleOAuthAction(url, null)) return; }" +
        "        return originalAssign.apply(this, arguments);" +
        "      };" +
        "    }" +
        "    var originalReplace = window.location.replace;" +
        "    if (typeof originalReplace === 'function') {" +
        "      window.location.replace = function(url) {" +
        "        if (isOAuthUrl(url)) { if (handleOAuthAction(url, null)) return; }" +
        "        return originalReplace.apply(this, arguments);" +
        "      };" +
        "    }" +
        "    var proto = Object.getPrototypeOf(window.location);" +
        "    var descriptor = Object.getOwnPropertyDescriptor(proto || window.location, 'href');" +
        "    if (descriptor && descriptor.set) {" +
        "      var origSet = descriptor.set;" +
        "      Object.defineProperty(window.location, 'href', {" +
        "        set: function(val) {" +
        "          if (isOAuthUrl(val)) { if (handleOAuthAction(val, null)) return; }" +
        "          origSet.call(window.location, val);" +
        "        }" +
        "      });" +
        "    }" +
        "  } catch(e) {}" +
        "})();";

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

    private long splashStartTime = 0;

    private final RoyalCapabilitiesEngine capabilitiesEngine;
    private final SpeculativeEngine speculativeEngine;
    private final WebEngineConfig webEngineConfig;

    private CustomTabsClient customTabsClient = null;
    private CustomTabsSession customTabsSession = null;
    private boolean isCustomTabOpen = false;

    public interface SplashStateChecker { boolean isRemoved(); }

    public WebEngineManager(Context context, WebView webView, View splashOverlay,
                            android.widget.ProgressBar progressBar,
                            Runnable markSplashRemoved, SplashStateChecker splashChecker) {
        this.context = context;
        this.webView = webView;
        this.splashOverlay = splashOverlay;
        this.progressBar = progressBar;
        this.markSplashRemoved = markSplashRemoved;
        this.splashChecker = splashChecker;
        this.activity = (context instanceof android.app.Activity) ? (android.app.Activity) context : null;
        if (this.webView != null) this.webView.setBackgroundColor(SystemUI.getDefaultSystemColor(this.context));
        this.capabilitiesEngine = new RoyalCapabilitiesEngine(this.activity);
        this.speculativeEngine = new SpeculativeEngine(this.activity, this.webView);
        this.webEngineConfig = new WebEngineConfig(this.context, this.webView, this.activity);
    }

    private void setupCustomTabsSession() {
        if (activity == null) return;
        try {
            String packageName = CustomTabsClient.getPackageName(context, null);
            if (packageName == null) return;
            CustomTabsClient.bindCustomTabsService(context, packageName, new CustomTabsServiceConnection() {
                @Override
                public void onCustomTabsServiceConnected(@NonNull ComponentName name, @NonNull CustomTabsClient client) {
                    customTabsClient = client;
                    customTabsClient.warmup(0L);
                    customTabsSession = customTabsClient.newSession(new CustomTabsCallback() {
                        @Override
                        public void onNavigationEvent(int navigationEvent, Bundle extras) {
                            super.onNavigationEvent(navigationEvent, extras);
                            if (navigationEvent == CustomTabsCallback.TAB_HIDDEN && isCustomTabOpen) {
                                isCustomTabOpen = false;
                                Log.i(TAG, "🔄 Custom Tab hidden -> Triggering WebView Session Refresh");
                                if (activity != null && webView != null) {
                                    activity.runOnUiThread(() -> webView.postDelayed(() -> {
                                        if (webEngineConfig.getTrustedHost() != null) {
                                            webView.loadUrl(webEngineConfig.getTrustedScheme() + "://" + webEngineConfig.getTrustedHost());
                                        } else webView.reload();
                                    }, 300));
                                }
                            }
                        }
                    });
                }
                @Override
                public void onServiceDisconnected(ComponentName name) {
                    customTabsClient = null;
                    customTabsSession = null;
                }
            });
        } catch (Throwable t) { Log.w(TAG, "⚠️ CustomTabsService binding failed.", t); }
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
                if (lower.startsWith("about:") || lower.startsWith("data:") || lower.contains("chromewebdata")) continue;
                final int steps = i - currentIndex;
                if (activity != null) {
                    activity.runOnUiThread(() -> {
                        try { webView.goBackOrForward(steps); }
                        catch (Exception e) { Log.w(TAG, "safeGoBack: goBackOrForward failed", e); }
                    });
                }
                return true;
            }
            return false;
        } catch (Exception e) { Log.w(TAG, "safeGoBack: error", e); return false; }
    }

    public RoyalCapabilitiesEngine getCapabilitiesHandler() { return this.capabilitiesEngine; }
    public void predict(String url) { if (speculativeEngine != null) speculativeEngine.predict(url); }
    public SpeculativeEngine getSpeculativeEngine() { return this.speculativeEngine; }
    public void setSplashStartTime(long startTime) { this.splashStartTime = startTime; }

    public void init() {
        if (this.webView != null) this.webView.setBackgroundColor(SystemUI.getDefaultSystemColor(this.context));
        String currentUrl = webView.getUrl();
        if (currentUrl == null || currentUrl.equalsIgnoreCase("about:blank") || currentUrl.contains("chromewebdata")) {
            Log.w(TAG, "⚠️ WebView stuck on invalid frame (" + currentUrl + "). Recovering state...");
            if (NetworkMonitor.isInternetAvailable(context)) webView.loadUrl(com.store.app.BuildConfig.CLIENT_URL);
            else OfflineStateManager.getInstance().setErrorPage(true, com.store.app.BuildConfig.CLIENT_URL);
        } else if (RoyalWebViewHost.isReady()) {
            Log.i("RoyalEngine", "🔥 Warm Resume Detected, enforcing fixed splash time.");
        }
        webEngineConfig.configureSettings();
        setupCustomTabsSession();
        attachClients();
        speculativeEngine.initializeSpeculativeLoading();
        String clientUrl = BuildConfig.CLIENT_URL;
        if (clientUrl != null) speculativeEngine.preconnectOrigin(clientUrl);
        if (WebViewFeature.isFeatureSupported(WebViewFeature.NAVIGATION_LISTENER)) {
            WebViewCompat.addNavigationListener(webView, new NavigationListener() {
                @Override public void onFirstContentfulPaintMillis(@NonNull Page page, long durationMillis) { Log.i("Performance", "🎯 FCP: " + durationMillis + "ms"); RoyalPanopticon.recordMetric("FCP", durationMillis); }
                @Override public void onPageDomContentLoadedEvent(@NonNull Page page) { Log.i("Performance", "📄 DOMContentLoaded"); }
                @Override public void onPageLoadEvent(@NonNull Page page) { Log.i("Performance", "📦 Load event fired"); }
                @Override public void onNavigationStarted(@NonNull Navigation navigation) { speculativeEngine.onNavigationStarted(); Log.i("Performance", "🚀 Navigation started"); }
                @Override public void onNavigationRedirected(@NonNull Navigation navigation) { Log.i("Performance", "↪️ Navigation redirected"); }
                @Override public void onNavigationCompleted(@NonNull Navigation navigation) { Log.i("Performance", "✅ Navigation completed"); }
                @Override public void onPageDeleted(@NonNull Page page) { Log.i("Performance", "🗑️ Page evicted"); }
                @Override public void onLargestContentfulPaintMillis(@NonNull Page page, long durationMillis) { Log.i("Performance", "🏆 LCP: " + durationMillis + "ms"); RoyalPanopticon.recordMetric("LCP", durationMillis); }
                @Override public void onPerformanceMarkMillis(@NonNull Page page, @NonNull String markName, long markTimeMillis) { Log.i("Performance", "📊 Performance mark: " + markName + " at " + markTimeMillis + "ms"); }
            });
            Log.i("RoyalEngine", "📊 NavigationListener added for performance metrics.");
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            webView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                RoyalNetworkEngine.notifyScroll(scrollY);
                v.removeCallbacks(scrollFinishedRunnable);
                v.postDelayed(scrollFinishedRunnable, 90);
            });
        }
    }

    private void removeSplashInstantly() {
        if (activity == null) return;
        activity.runOnUiThread(() -> {
            if (splashOverlay != null && splashOverlay.getParent() instanceof ViewGroup) ((ViewGroup) splashOverlay.getParent()).removeView(splashOverlay);
            if (progressBar != null) progressBar.setVisibility(View.GONE);
            markSplashRemoved.run();
            RoyalNetworkEngine.notifyRenderIdle();
        });
    }

    public void removeSplashSmoothly() {
        if (activity == null || splashChecker.isRemoved()) return;
        activity.runOnUiThread(() -> {
            if (splashOverlay != null && splashOverlay.getAlpha() > 0f) {
                splashOverlay.animate().alpha(0f).setDuration(400).withEndAction(this::removeSplashInstantly).start();
            } else removeSplashInstantly();
        });
    }

    private void injectImeVisualViewportLayer(WebView view) {
        if (view == null) return;
        view.evaluateJavascript(IME_VISUAL_VIEWPORT_JS, null);
    }

    private void attachClients() {
        webView.setWebViewClient(new WebViewClient() {

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                RoyalPanopticon.recordRequestSent();
                SystemUI.beginPageNavigation(activity, view, url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (view != null) {
                    view.evaluateJavascript(OAUTH_AUTO_INJECTOR_JS, null);
                    injectImeVisualViewportLayer(view);
                }
                try {
                    CookieManager cookieManager = CookieManager.getInstance();
                    cookieManager.flush();
                    Log.i(TAG, "💾 Session Permanent Disk Flush Completed for: " + url);
                } catch (Exception e) { Log.w(TAG, "⚠️ Cookie flush failed", e); }
                RoyalPanopticon.recordNavigationComplete();
                RoyalNetworkEngine.notifyRenderIdle();
                if (url == null || url.startsWith("data:") || url.startsWith("about:") || url.contains("chromewebdata")) {
                    OfflineStateManager.getInstance().setPageValid(false);
                    Log.w(TAG, "⚠️ Invalid/error page finished: " + url);
                    return;
                }
                if (OfflineStateManager.getInstance().isOnErrorPage()) {
                    Log.w(TAG, "⚠️ Ignoring onPageFinished because page is marked as error.");
                    return;
                }
                OfflineStateManager.getInstance().setPageValid(true);
                Log.i(TAG, "✅ Page finished successfully. Page is valid.");
                if (activity instanceof MainActivity) {
                    ((MainActivity) activity).notifyPageFinishedForSplash(view);
                }
            }

            @Override
            public void onPageCommitVisible(WebView view, String url) {
                if (url != null && !url.startsWith("data:") && !url.startsWith("about:") && !url.contains("chromewebdata")) {
                    Log.i(TAG, "✅ Page committed successfully: " + url);
                    if (webEngineConfig.getTrustedHost() == null) webEngineConfig.setTrustedOrigin(url);
                    if (webEngineConfig.getTrustedHost() != null && webEngineConfig.getTrustedScheme() != null) {
                        speculativeEngine.setTrustedOrigin(webEngineConfig.getTrustedScheme(), webEngineConfig.getTrustedHost(), webEngineConfig.getTrustedPort());
                    }
                    if (activity != null) activity.runOnUiThread(() -> WebEnhancer.apply(view, context));
                    RoyalNetworkEngine.notifyRenderStart();

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                            && WebViewFeature.isFeatureSupported(
                                    WebViewFeature.VISUAL_STATE_CALLBACK
                            )) {

                        WebViewCompat.postVisualStateCallback(
                                view,
                                System.nanoTime(),
                                new WebViewCompat.VisualStateCallback() {

                                    @Override
                                    public void onComplete(long requestId) {

                                        Log.i(
                                                TAG,
                                                "🎨 Visual state ready for first valid draw."
                                        );

                                        RoyalPanopticon.recordMetric(
                                                "VisualStateReady",
                                                System.currentTimeMillis()
                                        );

                                        view.postDelayed(
                                                () -> {
                                                    if (activity == null
                                                            || activity.isFinishing()
                                                            || view.getVisibility() != View.VISIBLE) {
                                                        return;
                                                    }

                                                    webEngineConfig.syncStatusBarColor(view);
                                                },
                                                120L
                                        );
                                    }
                                }
                        );

                    } else {

                        view.postDelayed(
                                () -> {
                                    if (activity == null
                                            || activity.isFinishing()
                                            || view.getVisibility() != View.VISIBLE) {
                                        return;
                                    }

                                    webEngineConfig.syncStatusBarColor(view);
                                },
                                180L
                        );
                    }

                    if (NetworkMonitor.isInternetAvailable(context)
                            && !OfflineStateManager.getInstance().isOnErrorPage()
                            && OfflineStateManager.getInstance().isPageValid()) {
                        OfflineStateManager.getInstance().notifyPageReadyToHide();
                    }
                } else Log.w(TAG, "⚠️ Invalid page committed: " + url);
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                Log.e(TAG, detail.didCrash() ? "☠️ Chromium Renderer crashed." : "⚠️ Chromium Renderer killed by system.");
                RoyalNetworkEngine.notifyRenderIdle();
                RoyalWebViewHost.destroy();
                if (activity != null && !activity.isFinishing()) activity.recreate();
                return true;
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request != null && request.isForMainFrame()) {
                    OfflineStateManager.getInstance().setErrorPage(true, request.getUrl().toString());
                    Log.w(TAG, "🛡️ Main frame error detected. Native offline state activated.");
                }
            }

            @SuppressWarnings("deprecation")
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                OfflineStateManager.getInstance().setErrorPage(true, failingUrl);
                Log.w(TAG, "🛡️ Legacy main frame error detected. Page invalid.");
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (request == null || request.getUrl() == null) return null;
                String url = request.getUrl().toString();
                if (url.contains("gorgias") || url.contains("facebook.net") || url.contains("analytics") || url.contains("klaviyo")) {
                    String stubScript = "/* Isolated by Nexus Script Shield to ensure 60FPS Performance */";
                    InputStream stubStream = new ByteArrayInputStream(stubScript.getBytes());
                    Log.d("RoyalEngine", "🛡️ Shield: Isolated Parasitic Script -> " + url);
                    return new WebResourceResponse("application/javascript", "UTF-8", stubStream);
                }
                if (url.endsWith("/nexus-service-worker.js")) {
                    try {
                        java.io.InputStream swStream = context.getAssets().open("public/js/nexus-service-worker.js");
                        java.util.Map<String, String> headers = new java.util.HashMap<>();
                        headers.put("Content-Type", "application/javascript");
                        headers.put("Service-Worker-Allowed", "/");
                        headers.put("Cache-Control", "no-cache");
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) return new WebResourceResponse("application/javascript", "UTF-8", 200, "OK", headers, swStream);
                        else return new WebResourceResponse("application/javascript", "UTF-8", swStream);
                    } catch (Exception e) { android.util.Log.e("RoyalEngine", "Failed to serve local Service Worker", e); }
                }
                if (!NetworkMonitor.isInternetAvailable(context) && request.isForMainFrame()) {
                    Uri reqUri = request.getUrl();
                    if (isSensitiveNavigation(reqUri) || OfflineStateManager.getInstance().isPageValid()) {
                        Log.i(TAG, "📴 Offline main-frame request blocked safely. Shaking bar without page clear.");
                        OfflineStateManager.getInstance().notifyOfflineClickAttempt();
                        InputStream emptyStream = new ByteArrayInputStream(new byte[0]);
                        return new WebResourceResponse("text/plain", "UTF-8", 204, "No Content", null, emptyStream);
                    }
                    Log.i(TAG, "📴 Offline main-frame error request intercepted. Triggering Native Offline UI.");
                    OfflineStateManager.getInstance().setErrorPage(true, reqUri.toString());
                    String cleanStub = "<!DOCTYPE html><html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"></head><body style=\"background-color:transparent;\"></body></html>";
                    InputStream stubStream = new ByteArrayInputStream(cleanStub.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    return new WebResourceResponse("text/html", "UTF-8", 200, "OK", null, stubStream);
                }
                WebResourceResponse royalResponse = RoyalNetworkEngine.interceptRequest(request);
                if (royalResponse != null) return royalResponse;
                return null;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request == null || request.getUrl() == null) return false;
                Uri uri = request.getUrl();
                if (!NetworkMonitor.isInternetAvailable(context)) {
                    OfflineStateManager.getInstance().notifyOfflineClickAttempt();
                    return true;
                }
                return handleUriLogic(uri, request.isForMainFrame());
            }

            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url == null) return false;
                Uri uri = Uri.parse(url);
                if (!NetworkMonitor.isInternetAvailable(context)) {
                    OfflineStateManager.getInstance().notifyOfflineClickAttempt();
                    return true;
                }
                return handleUriLogic(uri, true);
            }
        });

        webView.setWebChromeClient(capabilitiesEngine.buildChromeClient(progressBar));
        capabilitiesEngine.attachDownloadManager(webView);
    }

    private boolean isSensitiveNavigation(Uri uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme();
        if (scheme == null) return false;
        scheme = scheme.toLowerCase();
        if (!"https".equals(scheme) && !"http".equals(scheme)) return false;
        String host = uri.getHost();
        if (host == null) return false;
        host = host.toLowerCase();
        return host.equals("accounts.google.com") || host.endsWith(".google.com")
                || host.equals("appleid.apple.com") || host.endsWith(".microsoftonline.com")
                || host.equals("login.live.com") || host.endsWith(".linkedin.com")
                || host.equals("github.com") || host.endsWith(".github.com");
    }

    private boolean isLogoutUrl(Uri uri) {
        if (uri == null) return false;
        String urlStr = uri.toString().toLowerCase();
        return urlStr.contains("/logout") || urlStr.contains("/signout") || urlStr.contains("/sign-out")
                || urlStr.contains("/log-out") || urlStr.contains("action=logout");
    }

    public void clearNativeSession(Runnable onComplete) {
        if (activity == null) return;
        activity.runOnUiThread(() -> {
            try {
                CookieManager cookieManager = CookieManager.getInstance();
                cookieManager.removeSessionCookies(null);
                cookieManager.removeAllCookies(success -> {
                    cookieManager.flush();
                    Log.i(TAG, "🧹 Native Session & Cookies Completely Purged from Disk Storage.");
                    if (onComplete != null) onComplete.run();
                });
            } catch (Exception e) {
                Log.w(TAG, "⚠️ Failed to clear native session", e);
                if (onComplete != null) onComplete.run();
            }
        });
    }

    public boolean launchSensitiveFlow(Uri uri) {
        if (activity == null || uri == null) return false;
        if (!NetworkMonitor.isInternetAvailable(context)) {
            OfflineStateManager.getInstance().notifyOfflineClickAttempt();
            Log.w(TAG, "📴 Offline Mode Active -> Blocked OAuth Custom Tab launch for: " + uri);
            return false;
        }
        try {
            isCustomTabOpen = true;
            CustomTabsIntent.Builder builder = (customTabsSession != null) ? new CustomTabsIntent.Builder(customTabsSession) : new CustomTabsIntent.Builder();
            CustomTabColorSchemeParams darkParams = new CustomTabColorSchemeParams.Builder()
                    .setToolbarColor(android.graphics.Color.parseColor("#090D16"))
                    .setNavigationBarColor(android.graphics.Color.parseColor("#090D16"))
                    .build();
            builder.setDefaultColorSchemeParams(darkParams);
            try {
                int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;
                int initialHeight = (int) (screenHeight * 0.88);
                builder.setInitialActivityHeightPx(initialHeight, CustomTabsIntent.ACTIVITY_HEIGHT_DEFAULT);
            } catch (Throwable ignored) {}
            builder.setStartAnimations(activity, android.R.anim.fade_in, android.R.anim.fade_out);
            builder.setExitAnimations(activity, android.R.anim.fade_in, android.R.anim.fade_out);
            builder.setShowTitle(true);
            builder.setShareState(CustomTabsIntent.SHARE_STATE_OFF);
            CustomTabsIntent customTabsIntent = builder.build();
            customTabsIntent.launchUrl(activity, uri);
            Log.i(TAG, "🔐 Sensitive navigation launched as In-App Bottom Sheet: " + uri);
            return true;
        } catch (Throwable e) {
            isCustomTabOpen = false;
            Log.e(TAG, "❌ Failed to launch sensitive navigation.", e);
            return false;
        }
    }

    public void handleAuthReturn(String redirectUrl) {
        if (activity == null || webView == null) return;
        isCustomTabOpen = false;
        activity.runOnUiThread(() -> {
            Log.i(TAG, "👑 Auth Return Executing in WebView -> " + redirectUrl);
            if (redirectUrl != null && !redirectUrl.isEmpty()) {
                if (redirectUrl.startsWith("com.store.app.auth")) {
                    Log.i(TAG, "ℹ️ Custom scheme unhandled, reloading home origin instead.");
                    if (webEngineConfig.getTrustedHost() != null) webView.loadUrl(webEngineConfig.getTrustedScheme() + "://" + webEngineConfig.getTrustedHost());
                    else webView.reload();
                    return;
                }
                webView.loadUrl(redirectUrl);
                try {
                    CookieManager.getInstance().flush();
                    Log.i(TAG, "💾 CookieManager flushed successfully.");
                } catch (Exception e) { Log.w(TAG, "Failed to flush CookieManager", e); }
            } else if (webEngineConfig.getTrustedHost() != null) {
                webView.loadUrl(webEngineConfig.getTrustedScheme() + "://" + webEngineConfig.getTrustedHost());
                CookieManager.getInstance().flush();
            } else {
                webView.reload();
                CookieManager.getInstance().flush();
            }
        });
    }

    private boolean launchExternalWebUrl(Uri uri) {
        if (activity == null || uri == null) return false;
        try {
            CustomTabsIntent.Builder builder = (customTabsSession != null) ? new CustomTabsIntent.Builder(customTabsSession) : new CustomTabsIntent.Builder();
            builder.setShowTitle(true);
            builder.setShareState(CustomTabsIntent.SHARE_STATE_OFF);
            CustomTabsIntent customTabsIntent = builder.build();
            customTabsIntent.launchUrl(activity, uri);
            Log.i(TAG, "🌐 External HTTP/HTTPS opened in Custom Tab: " + uri);
            return true;
        } catch (Throwable e) {
            Log.e(TAG, "❌ Failed to open external URL in Custom Tab: " + uri, e);
            return launchExternal(uri);
        }
    }

    private boolean launchExternal(Uri uri) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (intent.resolveActivity(context.getPackageManager()) != null) {
                applyNativeExitTransition();
                context.startActivity(intent);
            } else Log.w("RoyalEngine", "No Activity found for: " + uri);
        } catch (Exception e) { Log.e("RoyalEngine", "External launch failed", e); }
        return true;
    }

    private void applyNativeExitTransition() {
        if (activity != null) activity.runOnUiThread(() -> activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out));
    }

    public boolean isPageValid() { return OfflineStateManager.getInstance().isPageValid(); }
    public boolean isOnErrorPage() { return OfflineStateManager.getInstance().isOnErrorPage(); }

    private boolean launchIntentScheme(Uri uri) {
        if (activity == null || uri == null) return true;
        try {
            Intent intent = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME);
            if (intent.resolveActivity(activity.getPackageManager()) != null) {
                activity.startActivity(intent);
                return true;
            }
            String fallbackUrl = intent.getStringExtra("browser_fallback_url");
            if (fallbackUrl != null && !fallbackUrl.trim().isEmpty()) {
                Uri fallbackUri = Uri.parse(fallbackUrl);
                if ("https".equalsIgnoreCase(fallbackUri.getScheme())) {
                    new CustomTabsIntent.Builder().build().launchUrl(activity, fallbackUri);
                    return true;
                }
            }
        } catch (Exception e) { Log.w(TAG, "Payment intent:// handling failed.", e); }
        return true;
    }

    private boolean isPaymentExternalScheme(String scheme) {
        if (scheme == null) return false;
        String normalized = scheme.toLowerCase(java.util.Locale.ROOT);
        return "intent".equals(normalized)
                || "upi".equals(normalized)
                || "pay".equals(normalized)
                || "paypal".equals(normalized)
                || "alipay".equals(normalized)
                || "gpay".equals(normalized)
                || "applepay".equals(normalized);
    }

    private boolean isHttpOrHttps(Uri uri) {
        if (uri == null || uri.getScheme() == null) {
            return false;
        }
        String scheme = uri.getScheme().toLowerCase(java.util.Locale.ROOT);
        return "http".equals(scheme) || "https".equals(scheme);
    }

    private boolean handleUriLogic(
            Uri uri,
            boolean isMainFrame
    ) {
        if (uri == null) {
            return true;
        }

        if (!NetworkMonitor.isInternetAvailable(context)) {
            OfflineStateManager.getInstance()
                    .notifyOfflineClickAttempt();

            return true;
        }

        String scheme =
                uri.getScheme() != null
                        ? uri.getScheme()
                                .toLowerCase(
                                        java.util.Locale.ROOT
                                )
                        : null;

        if (scheme == null) {
            return true;
        }

        /*
         * OAuth remains on its dedicated Custom Tab flow.
         */
        if (isSensitiveNavigation(uri)) {
            return launchSensitiveFlow(uri);
        }

        /*
         * Wallet/payment application schemes are never loaded inside
         * the normal WebView.
         */
        if (isPaymentExternalScheme(scheme)) {
            if ("intent".equals(scheme)) {
                return launchIntentScheme(uri);
            }

            return launchExternal(uri);
        }

        /*
         * Custom OAuth callback scheme must be consumed by the
         * Activity/RoyalAuthManager.
         */
        if ("com.store.app.auth".equals(scheme)) {
            Log.i(
                    TAG,
                    "✅ Custom auth scheme delegated to RoyalAuthManager."
            );

            return true;
        }

        /*
         * Telephone, mail, maps, marketplace, WhatsApp, and all
         * other non-web schemes are external.
         */
        if (!"http".equals(scheme)
                && !"https".equals(scheme)) {
            return launchExternal(uri);
        }

        /*
         * Logout cleanup is allowed, but does not decide navigation.
         */
        if (isLogoutUrl(uri)) {
            clearNativeSession(null);
        }

        /*
         * Only the exact configured origin stays in WebView.
         * Any other HTTP/HTTPS origin opens in Custom Tabs.
         */
        if (webEngineConfig.isSameOrigin(uri)) {
            return false;
        }

        return launchExternalWebUrl(uri);
    }
                }
