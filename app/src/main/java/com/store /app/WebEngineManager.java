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

    // =========================================================
    // 🛡️ السكربت الاحترافي الخفي للحقن الآلي - يعترض كافة مزودي OAuth تلقائياً مع حماية الأوفلاين الصارمة
    // =========================================================
    private static final String OAUTH_AUTO_INJECTOR_JS =
        "(function() {" +
        "  if (window.__royalOAuthInjected) return;" +
        "  window.__royalOAuthInjected = true;" +
        "" +
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
        "" +
        "  function getValidUrl(target) {" +
        "    var href = target.getAttribute('href') || target.getAttribute('data-href') || target.getAttribute('action') || '';" +
        "    if (!href || href === '#' || href.indexOf('javascript:') === 0) return null;" +
        "    try {" +
        "      return new URL(href, window.location.href).href;" +
        "    } catch(e) {" +
        "      return null;" +
        "    }" +
        "  }" +
        "" +
        "  function handleOAuthAction(url, e) {" +
        "    if (!navigator.onLine) {" +
        "      if (e) { e.preventDefault(); e.stopPropagation(); e.stopImmediatePropagation(); }" +
        "      if (window.RoyalJsBridge && window.RoyalJsBridge.notifyOfflineClick) {" +
        "        window.RoyalJsBridge.notifyOfflineClick();" +
        "      }" +
        "      return true;" +
        "    }" +
        "    if (window.RoyalJsBridge) {" +
        "      if (e) { e.preventDefault(); e.stopPropagation(); e.stopImmediatePropagation(); }" +
        "      window.RoyalJsBridge.startOAuth(url);" +
        "      return true;" +
        "    }" +
        "    return false;" +
        "  }" +
        "" +
        "  /* 1. اعتراض النقر المباشر على الأزرار والروابط */" +
        "  document.addEventListener('click', function(e) {" +
        "    var target = e.target.closest('a, button, [role=\"button\"], input[type=\"submit\"], form');" +
        "    if (!target) return;" +
        "    var url = getValidUrl(target);" +
        "    if (url && isOAuthUrl(url)) {" +
        "      handleOAuthAction(url, e);" +
        "    }" +
        "  }, true);" +
        "" +
        "  /* 2. اعتراض التحويل البرمجي لـ Client-side SDKs */" +
        "  try {" +
        "    var originalAssign = window.location.assign;" +
        "    if (typeof originalAssign === 'function') {" +
        "      window.location.assign = function(url) {" +
        "        if (isOAuthUrl(url)) {" +
        "          if (handleOAuthAction(url, null)) return;" +
        "        }" +
        "        return originalAssign.apply(this, arguments);" +
        "      };" +
        "    }" +
        "    var originalReplace = window.location.replace;" +
        "    if (typeof originalReplace === 'function') {" +
        "      window.location.replace = function(url) {" +
        "        if (isOAuthUrl(url)) {" +
        "          if (handleOAuthAction(url, null)) return;" +
        "        }" +
        "        return originalReplace.apply(this, arguments);" +
        "      };" +
        "    }" +
        "    var proto = Object.getPrototypeOf(window.location);" +
        "    var descriptor = Object.getOwnPropertyDescriptor(proto || window.location, 'href');" +
        "    if (descriptor && descriptor.set) {" +
        "      var origSet = descriptor.set;" +
        "      Object.defineProperty(window.location, 'href', {" +
        "        set: function(val) {" +
        "          if (isOAuthUrl(val)) {" +
        "            if (handleOAuthAction(val, null)) return;" +
        "          }" +
        "          origSet.call(window.location, val);" +
        "        }" +
        "      });" +
        "    }" +
        "  } catch(e) {}" +
        "})();";

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

    // =========================================================
    // 🔐 Smart Custom Tabs Session Fields
    // =========================================================
    private CustomTabsClient customTabsClient = null;
    private CustomTabsSession customTabsSession = null;
    private boolean isCustomTabOpen = false;

    public interface SplashStateChecker {
        boolean isRemoved();
    }

    public WebEngineManager(Context context,
                            WebView webView,
                            View splashOverlay,
                            android.widget.ProgressBar progressBar,
                            Runnable markSplashRemoved,
                            SplashStateChecker splashChecker) {

        this.context = context;
        this.webView = webView;
        this.splashOverlay = splashOverlay;
        this.progressBar = progressBar;
        this.markSplashRemoved = markSplashRemoved;
        this.splashChecker = splashChecker;

        this.activity = (context instanceof android.app.Activity)
                ? (android.app.Activity) context
                : null;

        // 👑 القضاء على الومضة البيضاء: قفل خلفية الـ WebView فوراً على ثيم الـ Splash والنظام (#121212 ليلاً / #FFFFFF نهاراً)
        if (this.webView != null) {
            this.webView.setBackgroundColor(SystemUI.getDefaultSystemColor(this.context));
        }

        this.capabilitiesEngine = new RoyalCapabilitiesEngine(this.activity);
        this.speculativeEngine = new SpeculativeEngine(this.activity, this.webView);

        /**
         * ⚙️ Bind WebEngineConfig to the exact same WebView instance.
         */
        this.webEngineConfig = new WebEngineConfig(
                this.context,
                this.webView,
                this.activity
        );
    }

    // =========================================================
    // 🔐 Smart Custom Tabs Session Setup
    // =========================================================
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

                            // TAB_HIDDEN = 6 (عندما يُغلق المتصفح أو يعود التطبيق للواجهة)
                            if (navigationEvent == CustomTabsCallback.TAB_HIDDEN && isCustomTabOpen) {
                                isCustomTabOpen = false;
                                Log.i(TAG, "🔄 Custom Tab hidden -> Triggering WebView Session Refresh");

                                if (activity != null && webView != null) {
                                    activity.runOnUiThread(() -> {
                                        webView.postDelayed(() -> {
                                            if (webEngineConfig.getTrustedHost() != null) {
                                                webView.loadUrl(
                                                        webEngineConfig.getTrustedScheme()
                                                                + "://"
                                                                + webEngineConfig.getTrustedHost()
                                                );
                                            } else {
                                                webView.reload();
                                            }
                                        }, 300);
                                    });
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
        } catch (Throwable t) {
            Log.w(TAG, "⚠️ CustomTabsService binding failed.", t);
        }
    }

    // =========================================================
    // 🔒 Safe navigation back helper
    // =========================================================
    public boolean safeGoBack() {
        try {
            if (webView == null) return false;

            WebBackForwardList list = webView.copyBackForwardList();
            if (list == null) return false;

            int currentIndex = list.getCurrentIndex();
            // scan backwards for the first valid URL
            for (int i = currentIndex - 1; i >= 0; i--) {
                String candidate = list.getItemAtIndex(i).getUrl();
                if (candidate == null) continue;
                String lower = candidate.toLowerCase();
                if (lower.startsWith("about:") || lower.startsWith("data:") || lower.contains("chromewebdata")) {
                    continue; // skip invalid entries
                }
                final int steps = i - currentIndex; // negative value
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
        return this.capabilitiesEngine;
    }

    // =========================================================
    // 🧠 ROYAL PREDICTION DELEGATE
    // =========================================================
    /**
     * توجيه طلب التنبؤ والتسخين المسبق مباشرة إلى SpeculativeEngine
     */
    public void predict(String url) {
        if (speculativeEngine != null) {
            speculativeEngine.predict(url);
        }
    }

    /**
     * (اختياري) للوصول المباشر لكائن المحرك إذا احتجته مستقبلاً
     */
    public SpeculativeEngine getSpeculativeEngine() {
        return this.speculativeEngine;
    }

    public void setSplashStartTime(long startTime) {
        this.splashStartTime = startTime;
    }

    public void init() {
        // 👑 تأكيد مطابقة خلفية الـ WebView مع ثيم الـ Splash والنظام قبل البدء بأي عملية تحميل
        if (this.webView != null) {
            this.webView.setBackgroundColor(SystemUI.getDefaultSystemColor(this.context));
        }

        // ✅ الكود الجديد: معالجة التعافي الذكي من about:blank
        String currentUrl = webView.getUrl();
        if (currentUrl == null || currentUrl.equalsIgnoreCase("about:blank") || currentUrl.contains("chromewebdata")) {
            Log.w(TAG, "⚠️ WebView stuck on invalid frame (" + currentUrl + "). Recovering state...");
            if (NetworkMonitor.isInternetAvailable(context)) {
                webView.loadUrl(com.store.app.BuildConfig.CLIENT_URL);
            } else {
                OfflineStateManager.getInstance().setErrorPage(true, com.store.app.BuildConfig.CLIENT_URL);
            }
        } else if (RoyalWebViewHost.isReady()) {
            Log.i("RoyalEngine", "🔥 Warm Resume Detected, enforcing fixed splash time.");
        }

        webEngineConfig.configureSettings();

        setupCustomTabsSession();

        attachClients();

        speculativeEngine.initializeSpeculativeLoading();

        // ⚡ Preconnect للـ origin الأساسي مبكراً
        String clientUrl = BuildConfig.CLIENT_URL;

        if (clientUrl != null) {
            speculativeEngine.preconnectOrigin(clientUrl);
        }

        if (WebViewFeature.isFeatureSupported(WebViewFeature.NAVIGATION_LISTENER)) {
            WebViewCompat.addNavigationListener(webView, new NavigationListener() {
                @Override
                public void onFirstContentfulPaintMillis(@NonNull Page page, long durationMillis) {
                    Log.i("Performance", "🎯 FCP: " + durationMillis + "ms");
                    RoyalPanopticon.recordMetric("FCP", durationMillis);
                }

                @Override
                public void onPageDomContentLoadedEvent(@NonNull Page page) {
                    Log.i("Performance", "📄 DOMContentLoaded");
                }

                @Override
                public void onPageLoadEvent(@NonNull Page page) {
                    Log.i("Performance", "📦 Load event fired");
                }

                @Override
                public void onNavigationStarted(@NonNull Navigation navigation) {
                    speculativeEngine.onNavigationStarted();
                    Log.i("Performance", "🚀 Navigation started");
                }

                @Override
                public void onNavigationRedirected(@NonNull Navigation navigation) {
                    Log.i("Performance", "↪️ Navigation redirected");
                }

                @Override
                public void onNavigationCompleted(@NonNull Navigation navigation) {
                    Log.i("Performance", "✅ Navigation completed");
                }

                @Override
                public void onPageDeleted(@NonNull Page page) {
                    Log.i("Performance", "🗑️ Page evicted");
                }

                @Override
                public void onLargestContentfulPaintMillis(@NonNull Page page, long durationMillis) {
                    Log.i("Performance", "🏆 LCP: " + durationMillis + "ms");
                    RoyalPanopticon.recordMetric("LCP", durationMillis);
                }

                @Override
                public void onPerformanceMarkMillis(@NonNull Page page, @NonNull String markName, long markTimeMillis) {
                    Log.i("Performance", "📊 Performance mark: " + markName + " at " + markTimeMillis + "ms");
                }
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
            if (splashOverlay != null && splashOverlay.getParent() instanceof ViewGroup) {
                ((ViewGroup) splashOverlay.getParent()).removeView(splashOverlay);
            }
            if (progressBar != null) {
                progressBar.setVisibility(View.GONE);
            }
            markSplashRemoved.run();
            RoyalNetworkEngine.notifyRenderIdle();
        });
    }

    public void removeSplashSmoothly() {
        if (activity == null || splashChecker.isRemoved()) return;

        activity.runOnUiThread(() -> {
            if (splashOverlay != null && splashOverlay.getAlpha() > 0f) {
                splashOverlay.animate()
                        .alpha(0f)
                        .setDuration(400)
                        .withEndAction(this::removeSplashInstantly)
                        .start();
            } else {
                removeSplashInstantly();
            }
        });
    }

    // =========================================================
    // ❌ تم حذف الدالة triggerFinalReveal() نهائياً
    // =========================================================

    private void attachClients() {
        webView.setWebViewClient(new WebViewClient() {

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                RoyalPanopticon.recordRequestSent();
            }

            // =========================================================
            // 🔥 [تعديل 1] onPageFinished المحسّن مع التثبيت الدائم للجلسة
            // =========================================================
            @Override
            public void onPageFinished(WebView view, String url) {

                // 🛡️ حقن سكربت الاعتراض الآلي خفياً عند اكتمال الصفحة
                if (view != null) {
                    view.evaluateJavascript(OAUTH_AUTO_INJECTOR_JS, null);
                }

                // 💾 تثبيت الكوكيز نيتيفياً فوراً على القرص الصلب (Disk Storage) لمنع ضياع الجلسة عند الخروج
                try {
                    CookieManager cookieManager = CookieManager.getInstance();
                    cookieManager.flush();
                    Log.i(TAG, "💾 Session Permanent Disk Flush Completed for: " + url);
                } catch (Exception e) {
                    Log.w(TAG, "⚠️ Cookie flush failed", e);
                }

                RoyalPanopticon.recordNavigationComplete();
                RoyalNetworkEngine.notifyRenderIdle();

                if (url == null
                        || url.startsWith("data:")
                        || url.startsWith("about:")
                        || url.contains("chromewebdata")) {

                    OfflineStateManager.getInstance().setPageValid(false);

                    Log.w(TAG, "⚠️ Invalid/error page finished: " + url);
                    return;
                }

                // إذا كان النظام سجل Error قبل onPageFinished
                // لا نقلب الحالة إلى Valid مرة أخرى.
                if (OfflineStateManager.getInstance().isOnErrorPage()) {
                    Log.w(TAG, "⚠️ Ignoring onPageFinished because page is marked as error.");
                    return;
                }

                OfflineStateManager.getInstance().setPageValid(true);

                Log.i(TAG, "✅ Page finished successfully. Page is valid.");
            }

            // =========================================================
            // 🔥 [تعديل 2] onPageCommitVisible النهائي
            // =========================================================
            @Override
            public void onPageCommitVisible(WebView view, String url) {

                if (url != null
                        && !url.startsWith("data:")
                        && !url.startsWith("about:")
                        && !url.contains("chromewebdata")) {

                    Log.i(TAG, "✅ Page committed successfully: " + url);

                    if (webEngineConfig.getTrustedHost() == null) {
                        webEngineConfig.setTrustedOrigin(url);
                    }

                    if (webEngineConfig.getTrustedHost() != null
                            && webEngineConfig.getTrustedScheme() != null) {

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

                    // =========================================================
                    // 🔥 إضافة Visual State Callback
                    // =========================================================
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
                                    }
                                }
                        );
                    }

                    webEngineConfig.syncStatusBarColor(view);

                    if (NetworkMonitor.isInternetAvailable(context)
                            && !OfflineStateManager.getInstance().isOnErrorPage()
                            && OfflineStateManager.getInstance().isPageValid()) {

                        OfflineStateManager.getInstance().notifyPageReadyToHide();
                    }

                } else {

                    Log.w(TAG, "⚠️ Invalid page committed: " + url);
                }
            }

            // =========================================================
            // 🔥 [تعديل 11] onRenderProcessGone المُصحَّح
            // =========================================================
            @Override
            public boolean onRenderProcessGone(
                    WebView view,
                    RenderProcessGoneDetail detail
            ) {

                Log.e(
                        TAG,
                        detail.didCrash()
                                ? "☠️ Chromium Renderer crashed."
                                : "⚠️ Chromium Renderer killed by system."
                );

                RoyalNetworkEngine.notifyRenderIdle();

                RoyalWebViewHost.destroy();

                if (activity != null && !activity.isFinishing()) {
                    activity.recreate();
                }

                return true;
            }

            // ✅ الكود الجديد: إبلاغ الأوفلاين النيتيف بدون قطع عملية الرسم
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

            // =========================================================
            // 🔥 [تعديل 10] shouldInterceptRequest المبسَّط مع HTTP 204 للأوفلاين
            // =========================================================
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

                // تم حذف كتل royal_nucleus.js و nexus-worker.js و royal_nucleus.wasm

                if (url.endsWith("/nexus-service-worker.js")) {
                    try {
                        java.io.InputStream swStream = context.getAssets().open("public/js/nexus-service-worker.js");
                        java.util.Map<String, String> headers = new java.util.HashMap<>();
                        headers.put("Content-Type", "application/javascript");
                        headers.put("Service-Worker-Allowed", "/");
                        headers.put("Cache-Control", "no-cache");

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            return new WebResourceResponse("application/javascript", "UTF-8", 200, "OK", headers, swStream);
                        } else {
                            return new WebResourceResponse("application/javascript", "UTF-8", swStream);
                        }
                    } catch (Exception e) {
                        android.util.Log.e("RoyalEngine", "Failed to serve local Service Worker", e);
                    }
                }

                // ✅ الكود المحصن: حماية السجل ومنع الصفحة البيضاء عبر إرجاع HTTP 204 No Content
                if (!NetworkMonitor.isInternetAvailable(context) && request.isForMainFrame()) {
                    Uri reqUri = request.getUrl();
                    
                    // إذا كان الطلب لمسار مصادقة حساس أو كنا على صفحة صالحة أصلاً
                    if (isSensitiveNavigation(reqUri) || OfflineStateManager.getInstance().isPageValid()) {
                        Log.i(TAG, "📴 Offline main-frame request blocked safely. Shaking bar without page clear.");
                        
                        // هز الشريط السفلي
                        OfflineStateManager.getInstance().notifyOfflineClickAttempt();
                        
                        // إرجاع استجابة 204 No Content تمنع محرك Chromium من مسح الشاشة الحالية أو تحويلها لصفحة بيضاء
                        InputStream emptyStream = new ByteArrayInputStream(new byte[0]);
                        return new WebResourceResponse("text/plain", "UTF-8", 204, "No Content", null, emptyStream);
                    }

                    Log.i(TAG, "📴 Offline main-frame error request intercepted. Triggering Native Offline UI.");
                    OfflineStateManager.getInstance().setErrorPage(true, reqUri.toString());

                    String cleanStub = "<!DOCTYPE html><html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"></head><body style=\"background-color:transparent;\"></body></html>";
                    InputStream stubStream = new ByteArrayInputStream(cleanStub.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    return new WebResourceResponse("text/html", "UTF-8", 200, "OK", null, stubStream);
                }

                // =========================================================
                // 🔥 الجزء المحسَّن من shouldInterceptRequest (بدون قياس زمن أو تعديل أولوية)
                // =========================================================
                WebResourceResponse royalResponse = RoyalNetworkEngine.interceptRequest(request);

                if (royalResponse != null) {
                    return royalResponse;
                }

                return null; // ❌ لا نستخدم super.shouldInterceptRequest
            }

            // =========================================================
            // 🔥 [تعديل 4] shouldOverrideUrlLoading (الإصدار الجديد)
            // =========================================================
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request == null || request.getUrl() == null) return false;
                Uri uri = request.getUrl();

                // 📴 حماية الأوفلاين: حظر كافة الملاحة عند غياب الإنترنت واهتزاز الشريط
                if (!NetworkMonitor.isInternetAvailable(context)) {
                    OfflineStateManager.getInstance().notifyOfflineClickAttempt();
                    return true; // حظر الملاحة تماماً
                }
                return handleUriLogic(uri, request.isForMainFrame());
            }

            // =========================================================
            // 🔥 [تعديل 5] shouldOverrideUrlLoading (الإصدار القديم)
            // =========================================================
            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url == null) return false;
                Uri uri = Uri.parse(url);

                // 📴 حماية الأوفلاين
                if (!NetworkMonitor.isInternetAvailable(context)) {
                    OfflineStateManager.getInstance().notifyOfflineClickAttempt();
                    return true; // حظر الملاحة تماماً
                }
                return handleUriLogic(uri, true);
            }
        });

        webView.setWebChromeClient(capabilitiesEngine.buildChromeClient(progressBar));

        capabilitiesEngine.attachDownloadManager(webView);
    }

    // ==========================================
    // 🧠 محرك الروابط السيادي (معدل)
    // ==========================================

    /**
     * تحديد ما إذا كان الـ URI يمثل مسار مصادقة حساس (OAuth, Identity Provider).
     * هذه ليست قائمة ثقة أمنية، بل مجرد classifier لمسار المصادقة.
     * الحماية الحقيقية هي الـ Bridge/flow state.
     */
    private boolean isSensitiveNavigation(Uri uri) {

        if (uri == null) {
            return false;
        }

        String scheme = uri.getScheme();

        if (scheme == null) {
            return false;
        }

        scheme = scheme.toLowerCase();

        if (!"https".equals(scheme) &&
                !"http".equals(scheme)) {
            return false;
        }

        String host = uri.getHost();

        if (host == null) {
            return false;
        }

        host = host.toLowerCase();

        /*
         * OAuth identity providers.
         *
         * هذه ليست قائمة ثقة أمنية.
         * هي فقط classifier لمسار المصادقة.
         */
        return host.equals("accounts.google.com")
                || host.endsWith(".google.com")
                || host.equals("appleid.apple.com")
                || host.endsWith(".microsoftonline.com")
                || host.equals("login.live.com")
                || host.endsWith(".linkedin.com")
                || host.equals("github.com")
                || host.endsWith(".github.com");
    }

    /**
     * 👑 الكشف التلقائي عن مسارات وتسجيل الخروج (Logout Classifier)
     */
    private boolean isLogoutUrl(Uri uri) {
        if (uri == null) return false;
        String urlStr = uri.toString().toLowerCase();
        return urlStr.contains("/logout")
                || urlStr.contains("/signout")
                || urlStr.contains("/sign-out")
                || urlStr.contains("/log-out")
                || urlStr.contains("action=logout");
    }

    /**
     * 🧹 تطهير الجلسة نيتيفياً ومسح الكوكيز بالكامل من الـ RAM والقرص الصلب
     */
    public void clearNativeSession(Runnable onComplete) {
        if (activity == null) return;
        activity.runOnUiThread(() -> {
            try {
                CookieManager cookieManager = CookieManager.getInstance();
                cookieManager.removeSessionCookies(null);
                cookieManager.removeAllCookies(success -> {
                    cookieManager.flush();
                    Log.i(TAG, "🧹 Native Session & Cookies Completely Purged from Disk Storage.");
                    if (onComplete != null) {
                        onComplete.run();
                    }
                });
            } catch (Exception e) {
                Log.w(TAG, "⚠️ Failed to clear native session", e);
                if (onComplete != null) onComplete.run();
            }
        });
    }

    /**
     * إطلاق مسار المصادقة الحساس في Custom Tab بمظهر In-App Bottom Sheet ناعم وفاخر داخل التطبيق (مع حماية الأوفلاين).
     */
    public boolean launchSensitiveFlow(Uri uri) {

        if (activity == null || uri == null) {
            return false;
        }

        // 📴 1. فحص حماية الأوفلاين: إذا لا يوجد إنترنت، حظر فتح المتصفح واهتزاز شريط الأوفلاين فوراً
        if (!NetworkMonitor.isInternetAvailable(context)) {
            OfflineStateManager.getInstance().notifyOfflineClickAttempt();
            Log.w(TAG, "📴 Offline Mode Active -> Blocked OAuth Custom Tab launch for: " + uri);
            return false;
        }

        try {
            isCustomTabOpen = true;

            CustomTabsIntent.Builder builder = (customTabsSession != null)
                    ? new CustomTabsIntent.Builder(customTabsSession)
                    : new CustomTabsIntent.Builder();

            // 🎨 2. تخصيص لون الشريط العلوي والسفلي ليطابق الهوية البصرية الداكنة الفاخرة لتطبيقك (#090D16)
            CustomTabColorSchemeParams darkParams = new CustomTabColorSchemeParams.Builder()
                    .setToolbarColor(android.graphics.Color.parseColor("#090D16"))
                    .setNavigationBarColor(android.graphics.Color.parseColor("#090D16"))
                    .build();

            builder.setDefaultColorSchemeParams(darkParams);

            // 👑 3. تحويل النافذة إلى Bottom Sheet انزلاقي ناعم (تغطي 88% من الشاشة داخل التطبيق بدون خروج)
            try {
                int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;
                int initialHeight = (int) (screenHeight * 0.88);
                builder.setInitialActivityHeightPx(initialHeight, CustomTabsIntent.ACTIVITY_HEIGHT_DEFAULT);
            } catch (Throwable ignored) {}

            // 🚀 4. إضافة أنميشن ظهور وإغلاق ناعم جداً من الأسفل
            builder.setStartAnimations(activity, android.R.anim.fade_in, android.R.anim.fade_out);
            builder.setExitAnimations(activity, android.R.anim.fade_in, android.R.anim.fade_out);

            // 🛡️ 5. تبسيط الشريط العلوي لمنحه مظهراً نيتيفياً مستقلاً وإلغاء خيارات المتصفح
            builder.setShowTitle(true);
            builder.setShareState(CustomTabsIntent.SHARE_STATE_OFF);

            CustomTabsIntent customTabsIntent = builder.build();

            // ❌ إزالة FLAG_ACTIVITY_NEW_TASK لإبقاء الشاشة داخل نطاق مهمة التطبيق الحالية
            // customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            customTabsIntent.launchUrl(activity, uri);

            Log.i(TAG, "🔐 Sensitive navigation launched as In-App Bottom Sheet: " + uri);

            return true;

        } catch (Throwable e) {
            isCustomTabOpen = false;
            Log.e(TAG, "❌ Failed to launch sensitive navigation.", e);
            return false;
        }
    }

    /**
     * دالة معالجة العودة الفورية وحقن الكوكي
     */
    public void handleAuthReturn(String redirectUrl) {
        if (activity == null || webView == null) return;

        isCustomTabOpen = false;

        activity.runOnUiThread(() -> {
            Log.i(TAG, "👑 Auth Return Executing in WebView -> " + redirectUrl);
            if (redirectUrl != null && !redirectUrl.isEmpty()) {
                // إذا كان الرابط لا يزال يحمل مخطط مخصص ولم يتفكك، ننظفه
                if (redirectUrl.startsWith("com.store.app.auth")) {
                    Log.i(TAG, "ℹ️ Custom scheme unhandled, reloading home origin instead.");
                    if (webEngineConfig.getTrustedHost() != null) {
                        webView.loadUrl(webEngineConfig.getTrustedScheme() + "://" + webEngineConfig.getTrustedHost());
                    } else {
                        webView.reload();
                    }
                    return;
                }
                
                // 🚀 تحميل رابط الـ Callback المكتمل ليصدر السيرفر Set-Cookie
                webView.loadUrl(redirectUrl);
                
                // 💾 تثبيت الكوكيز نيتيفياً فوراً على القرص الصلب
                try {
                    CookieManager.getInstance().flush();
                    Log.i(TAG, "💾 CookieManager flushed successfully.");
                } catch (Exception e) {
                    Log.w(TAG, "Failed to flush CookieManager", e);
                }

            } else if (webEngineConfig.getTrustedHost() != null) {
                webView.loadUrl(
                        webEngineConfig.getTrustedScheme()
                                + "://"
                                + webEngineConfig.getTrustedHost()
                );
                CookieManager.getInstance().flush();
            } else {
                webView.reload();
                CookieManager.getInstance().flush();
            }
        });
    }

    // ==========================================
    // 🧭 دوال فتح الروابط الخارجية
    // ==========================================

    /**
     * فتح رابط HTTP/HTTPS خارجي في Custom Tab مع جلسة مسبقة إن وجدت.
     */
    private boolean launchExternalWebUrl(Uri uri) {
        if (activity == null || uri == null) {
            return false;
        }

        try {
            CustomTabsIntent.Builder builder =
                    (customTabsSession != null)
                            ? new CustomTabsIntent.Builder(customTabsSession)
                            : new CustomTabsIntent.Builder();

            builder.setShowTitle(true);
            builder.setShareState(CustomTabsIntent.SHARE_STATE_OFF);

            CustomTabsIntent customTabsIntent = builder.build();

            customTabsIntent.launchUrl(activity, uri);

            Log.i(TAG, "🌐 External HTTP/HTTPS opened in Custom Tab: " + uri);

            return true;

        } catch (Throwable e) {
            Log.e(TAG, "❌ Failed to open external URL in Custom Tab: " + uri, e);

            // Fallback آمن للمتصفح/النظام
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
            } else {
                Log.w("RoyalEngine", "No Activity found for: " + uri);
            }
        } catch (Exception e) {
            Log.e("RoyalEngine", "External launch failed", e);
        }
        return true;
    }

    private void applyNativeExitTransition() {
        if (activity != null) {
            activity.runOnUiThread(() -> {
                activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            });
        }
    }

    // =====================================================================
    // 🔥 دوال الحالة العامة المطلوبة من الخارج
    // =====================================================================
    public boolean isPageValid() {
        return OfflineStateManager.getInstance().isPageValid();
    }

    public boolean isOnErrorPage() {
        return OfflineStateManager.getInstance().isOnErrorPage();
    }

    private boolean handleUriLogic(Uri uri, boolean isMainFrame) {
        if (uri == null) return false;

        // ✅ إذا كنا أوفلاين → تجاهل كل النقرات فوراً
        if (!NetworkMonitor.isInternetAvailable(context)) {
            OfflineStateManager.getInstance().notifyOfflineClickAttempt();
            return true; // لا تنفذ أي رابط، لا داخلي ولا خارجي
        }

        String scheme = uri.getScheme();
        if (scheme == null) return false;
        scheme = scheme.toLowerCase();

        // 🧹 1. الاعتراض النيتيف لمسار تسجيل الخروج وتطهير الكوكيز نيتيفياً من القرص
        if (isLogoutUrl(uri)) {
            Log.i(TAG, "🧹 Logout URL detected -> Triggering Native Session Purge for: " + uri);
            clearNativeSession(null);
            return false; // نترك الـ WebView ينفذ الرابط أيضاً ليلغي السيرفر الجلسة لديه
        }

        if (webEngineConfig.isSameOrigin(uri)) {
            return false;
        }

        if (isSensitiveNavigation(uri)) {
            return launchSensitiveFlow(uri);
        }

        switch (scheme) {
            case "tel":
            case "mailto":
            case "sms":
            case "smsto":
            case "geo":
            case "market":
            case "intent":
            case "whatsapp":
                return launchExternal(uri);
        }

        if ("http".equals(scheme) || "https".equals(scheme)) {
            return launchExternalWebUrl(uri);
        }

        // ✅ منع فتح روابط المصادقة المخصصة داخليًا أو خارجيًا
        if ("com.store.app.auth".equals(scheme)) {
            Log.i(TAG, "✅ Custom auth scheme detected, handled by RoyalAuthManager.");
            return true; // لا تفتح الرابط، دعه يمر عبر onNewIntent
        }

        return launchExternal(uri);
    }
    }
