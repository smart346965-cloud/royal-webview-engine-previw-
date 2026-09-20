package com.store.app;

import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import com.store.app.offline.OfflineStateManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RoyalJsBridge {

    private static final String TAG = "RoyalJsBridge";
    private final WebView webView;
    private final WebEngineManager webEngineManager;
    private final android.app.Activity activity;
    private Runnable onHideSplashCallback;

    // 🚀 جسر الصواريخ: مسار خلفي معزول (Single Thread) لمعالجة أوامر JS دون خنق واجهة المستخدم
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();

    public RoyalJsBridge(
            WebView webView,
            WebEngineManager webEngineManager) {

        this.webView = webView;
        this.webEngineManager = webEngineManager;

        android.content.Context context = webView.getContext();

        if (context instanceof android.app.Activity) {
            this.activity = (android.app.Activity) context;
        } else {
            this.activity = null;
        }

        RoyalPanopticon.registerDependency(
                "WebChromeEngine",
                "JS-BridgeChannel"
        );
    }

    // =========================================================
    // 🧠 ROYAL BRIDGE V6
    // Single Prediction API
    // =========================================================
    @JavascriptInterface
    public void predict(String url) {

        if (url == null || url.length() == 0) {
            return;
        }

        if (webEngineManager == null) {
            return;
        }

        webView.post(() -> {

            try {

                RoyalPanopticon.pulse(
                        "JS-BridgeChannel"
                );

                webEngineManager.predict(url);

            } catch (Exception e) {

                Log.w(
                        TAG,
                        "Prediction dispatch failed.",
                        e
                );
            }
        });
    }

    /**
     * 📴 إشعار الأوفلاين المباشر من الجافاسكربت
     */
    @JavascriptInterface
    public void notifyOfflineClick() {
        if (webView == null) return;
        webView.post(() -> {
            try {
                RoyalPanopticon.pulse("JS-BridgeChannel");
                OfflineStateManager.getInstance().notifyOfflineClickAttempt();
                Log.i(TAG, "📴 Offline click notification triggered via JS Bridge.");
            } catch (Exception e) {
                Log.e(TAG, "notifyOfflineClick error", e);
            }
        });
    }

    /**
     * 👑 Native Auto-OAuth Trigger: الاعتراض الآلي المباشر لزر تسجيل الدخول
     * يفتح الـ Custom Tab من أول كسر في الثانية ويمنع كتابة الـ state في ذاكرة الـ WebView
     */
    @JavascriptInterface
    public void startOAuth(String authUrl) {
        if (authUrl == null || authUrl.length() == 0) {
            return;
        }

        if (webEngineManager == null || webView == null) {
            return;
        }

        webView.post(() -> {
            try {
                RoyalPanopticon.pulse("JS-BridgeChannel");

                // 📴 حماية الأوفلاين: اهتزاز الشريط السفلي فوراً ومنع فتح الـ Custom Tab في الأوفلاين
                if (!NetworkMonitor.isInternetAvailable(webView.getContext())) {
                    OfflineStateManager.getInstance().notifyOfflineClickAttempt();
                    Log.i(TAG, "📴 OAuth blocked by native offline guard.");
                    return;
                }

                // 🌐 في الأونلاين: إطلاق الـ Custom Tab كالمعتاد
                Log.i(TAG, "🚀 Native Auto-OAuth Intercepted: " + authUrl);
                webEngineManager.launchSensitiveFlow(android.net.Uri.parse(authUrl));
            } catch (Exception e) {
                Log.e(TAG, "startOAuth dispatch failed.", e);
            }
        });
    }

    public void setOnHideSplashCallback(Runnable callback) {
        this.onHideSplashCallback = callback;
    }

    /**
     * 🌊 Scroll velocity hint
     */
    @JavascriptInterface
    public void scrollHint(int velocity) {
        backgroundExecutor.execute(() -> {
            try {
                RoyalPanopticon.pulse("JS-BridgeChannel");
                // Log.d(TAG, "Scroll velocity: " + velocity);
            } catch (Exception e) {
                Log.e(TAG, "scrollHint error", e);
            }
        });
    }

    /**
     * 🧠 JS diagnostic channel
     */
    @JavascriptInterface
    public void log(String message) {
        Log.d(TAG, "JS: " + message);
        RoyalPanopticon.pulse("WebChromeEngine");
    }

    /**
     * ⚡ Instant Visual Color Channel
     *
     * هذه القناة مخصصة فقط للون الهيدر/الخلفية المرئية.
     * ممنوع إرسالها إلى backgroundExecutor لأن الهدف
     * هو الوصول إلى UI thread بأقل latency ممكن.
     */
    @JavascriptInterface
    public void headerColorChanged(String color) {

        if (color == null || color.length() == 0) {
            return;
        }

        final String finalColor =
                color.replace("\"", "").trim();

        if (finalColor.length() == 0 ||
                finalColor.equalsIgnoreCase("null")) {
            return;
        }

        webView.post(() -> {

            try {

                RoyalPanopticon.pulse(
                        "JS-BridgeChannel"
                );

                if (activity == null) return;

                SystemUI.applyInstantHeaderColor(
                        activity,
                        finalColor
                );

            } catch (Throwable t) {

                Log.w(
                        TAG,
                        "Instant headerColorChanged failed: "
                                + finalColor,
                        t
                );
            }
        });
    }

    /**
     * 🎭 Visual Completeness Signal
     * يُستدعى من الجافاسكريبت عندما يكتمل رسم الموقع بالكامل
     */
    @JavascriptInterface
    public void hideSplash() {
        if (onHideSplashCallback != null) {
            if (webView != null) {
                webView.post(onHideSplashCallback);
            }
        }
    }

    /**
     * 👁️‍عون Panopticon Telemetry Receiver
     * يستقبل الحالة الصحية للمتصفح من الجافاسكريبت ويرسلها للعقل المدبر
     */
    @JavascriptInterface
    public void reportBrowserState(int domNodes, int fps, long jsMemoryMB, int longTasks) {
        backgroundExecutor.execute(() -> {
            try {
                RoyalPanopticon.syncBrowserState(domNodes, fps, jsMemoryMB, longTasks);
                RoyalPanopticon.pulse("WebChromeEngine");
            } catch (Exception e) {
                Log.e(TAG, "Failed to sync browser state", e);
            }
        });
    }

    @JavascriptInterface
    public void inspect() {
        backgroundExecutor.execute(() -> {
            try {
                String report = RoyalPanopticon.buildReport();

                // معالجة النصوص الثقيلة في المسار الخلفي
                report = report
                        .replace("\\", "\\\\")
                        .replace("`", "\\`")
                        .replace("$", "\\$");

                final String js = "console.log(`" + report + "`);";
                
                // إرسال النتيجة النهائية فقط للمسار الرئيسي
                if (webView != null) {
                    webView.post(() -> webView.evaluateJavascript(js, null));
                }
            } catch (Exception e) {
                Log.e("RoyalJsBridge", "Inspect failed", e);
            }
        });
    }

    /**
     * 🔁 Native → JS callback
     */
    public void dispatchToJS(String script) {
        if (webView == null) return;
        webView.post(() -> webView.evaluateJavascript(script, null));
    }
}
