package com.store.app;

import android.app.Application;
import android.content.pm.PackageInfo;
import android.os.Process;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.webkit.WebView;

import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewOutcomeReceiver;
import androidx.webkit.WebViewStartUpConfig;
import androidx.webkit.WebViewStartUpResult;
import androidx.webkit.WebViewStartupException;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RoyalApplication extends Application {

    // 👑 Executor خاص بـ startUpWebView
    private static final ExecutorService STARTUP_EXECUTOR = Executors.newSingleThreadExecutor();

    @Override
    public void onCreate() {
        super.onCreate();

        final String TAG = "RoyalEngine";

        Log.i(TAG, "🚀 Royal Application Ignite!");

        // هذه الخدمات لا تعتمد على إنشاء WebView.
        NetworkMonitor.init(this);
        RoyalPanopticon.startAwareness();

        // 🟢 تسخين كاش الذاكرة (RAM Page Cache) لمكتبات C++ في خيط خلفي خفيف
        prewarmWebViewPageCache();

        /*
         * =========================================================
         * Chromium / WebView asynchronous startup
         * =========================================================
         *
         * مهم جداً:
         * لا تنشئ WebView هنا.
         * لا تستدعِ CookieManager.
         * لا تستدعِ ProfileStore.
         * لا تستدعِ WebViewFeature.
         *
         * كل ذلك ينتظر callback.
         */

        try {

            WebViewStartUpConfig config =
                    new WebViewStartUpConfig.Builder(
                            STARTUP_EXECUTOR
                    )
                    .setShouldRunUiThreadStartUpTasks(true)
                    .build();

            WebViewCompat.startUpWebView(
                    getApplicationContext(),
                    config,
                    new WebViewOutcomeReceiver<
                            WebViewStartUpResult,
                            WebViewStartupException>() {

                        @Override
                        public void onResult(WebViewStartUpResult result) {

                            Log.i(TAG,
                                    "✅ Chromium startup completed. "
                                            + "UI is now safe to create WebView.");

                            // 🟢 تسخين الـ GPU Context، ومحرك V8، وقواعد البيانات فور جاهزية المحرك
                            executeDeepEngineWarmup();

                            /*
                             * هذا callback يأتي على Main Looper.
                             * من هذه النقطة فقط نسمح للـ WebView
                             * بالدخول في دورة حياته.
                             */

                            RoyalWebViewHost.onWebViewStartupReady(
                                    getApplicationContext()
                            );
                        }

                        @Override
                        public void onError(WebViewStartupException exception) {

                            Log.e(
                                    TAG,
                                    "❌ WebView startup failed: "
                                            + exception.getMessage(),
                                    exception
                            );

                            RoyalWebViewHost.onWebViewStartupFailed(exception);
                        }
                    }
            );

            Log.i(TAG,
                    "🚀 Async WebView startup requested.");

        } catch (Throwable t) {

            Log.e(
                    TAG,
                    "❌ Unable to start WebView startup pipeline.",
                    t
            );

            RoyalWebViewHost.onWebViewStartupFailed(t);
        }
    }

    /**
     * 1. تسخين ذاكرة الصفحات (Page Fault Pre-warming)
     * قراءة صامتة لملف APK الخاص بـ WebView Provider لخداع الكيرنل
     * وسحب مكتبات C++ إلى ذاكرة الـ RAM تلقائياً في الخلفية.
     */
    private void prewarmWebViewPageCache() {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                PackageInfo webViewPackage = WebViewCompat.getCurrentWebViewPackage(this);
                if (webViewPackage != null && webViewPackage.applicationInfo != null) {
                    String apkPath = webViewPackage.applicationInfo.publicSourceDir;
                    if (apkPath != null) {
                        try (InputStream is = new FileInputStream(apkPath)) {
                            byte[] buffer = new byte[64 * 1024];
                            while (is.read(buffer) != -1) {
                                // I/O صامت لملء الـ RAM Page Cache
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {
                // صمام أمان محكم لمنع أي تأثير على التطبيق
            }
        });
    }

    /**
     * 2, 3, 4. تسخين بيئة الرسومات (EGL/GPU) + محرك V8 + قواعد البيانات (SQLite)
     * يتم استدعاؤها حصراً بعد نجاح startUpWebView لضمان عدم التعارض.
     */
    private void executeDeepEngineWarmup() {
        try {
            // تسخين قواعد البيانات وفتح قنوات الـ IPC للـ Cookies والـ Storage
            CookieManager.getInstance().flush();
            WebStorage.getInstance();

            // تسخين سياق الـ GPU (EGL Context) وتجهيز V8 Isolate Heap عبر كائن وهمي صامت
            WebView dummyWebView = new WebView(this);
            dummyWebView.loadUrl("about:blank");
            dummyWebView.destroy();

            Log.i("RoyalEngine", "🔥 Deep Engine Warmup (EGL + V8 + IPC + Cache) Executed!");
        } catch (Throwable t) {
            Log.w("RoyalEngine", "Safe deep warmup bypass: " + t.getMessage());
        }
    }
        }
