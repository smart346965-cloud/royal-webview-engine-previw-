package com.store.app;

import android.app.Activity;
import android.content.Context;
import android.content.MutableContextWrapper;
import android.graphics.Color;
import android.os.Build;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebView;

import androidx.webkit.Profile;
import androidx.webkit.ProfileStore;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import com.store.app.BuildConfig;

import java.util.ArrayList;

public final class RoyalWebViewHost {
    private static final String TAG = "RoyalWebViewHost";

    private static WebView webViewInstance;
    private static MutableContextWrapper contextWrapper;
    private static RoyalJsBridge jsBridgeInstance;
    private static volatile boolean isInitialized = false;

    // حالات Startup
    private static volatile boolean webViewStartupReady = false;
    private static volatile Throwable webViewStartupFailure;
    private static final ArrayList<Runnable> startupListeners = new ArrayList<>();

    private RoyalWebViewHost() {}

    // =========================================================
    // 🚀 دوال إدارة Startup
    // =========================================================

    public static void onWebViewStartupReady(Context context) {

        ArrayList<Runnable> listeners;

        synchronized (RoyalWebViewHost.class) {

            webViewStartupReady = true;
            webViewStartupFailure = null;

            listeners = new ArrayList<>(startupListeners);
            startupListeners.clear();
        }

        Log.i(TAG, "🔥 WebView startup barrier OPEN.");

        warmUpDefaultProfile(context);

        for (Runnable listener : listeners) {
            try {
                listener.run();
            } catch (Throwable t) {
                Log.e(TAG, "Startup listener failed.", t);
            }
        }
    }

    public static synchronized void onWebViewStartupFailed(Throwable error) {
        webViewStartupFailure = error;
        Log.e(TAG, "❌ WebView startup barrier FAILED.", error);
    }

    public static synchronized boolean isWebViewStartupReady() {
        return webViewStartupReady;
    }

    public static synchronized boolean hasWebViewStartupFailed() {
        return webViewStartupFailure != null;
    }

    public static void whenStartupReady(Runnable listener) {
        if (listener == null) {
            return;
        }

        boolean runNow = false;

        synchronized (RoyalWebViewHost.class) {
            if (webViewStartupReady) {
                runNow = true;

            } else if (webViewStartupFailure != null) {
                Log.e(
                        TAG,
                        "WebView startup failed; callback will not run.",
                        webViewStartupFailure
                );

                return;

            } else {
                startupListeners.add(listener);
            }
        }

        if (runNow) {
            listener.run();
        }
    }

    private static void warmUpDefaultProfile(Context context) {
        try {
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                Log.w(TAG, "MULTI_PROFILE not supported.");
                return;
            }

            Profile profile = ProfileStore.getInstance()
                    .getOrCreateProfile(Profile.DEFAULT_PROFILE_NAME);

            if (WebViewFeature.isFeatureSupported(WebViewFeature.WARM_UP_RENDERER_PROCESS)) {
                profile.warmUpRendererProcess();
                Log.i(TAG, "🧠 Chromium renderer warm-up requested.");
            }

            if (WebViewFeature.isFeatureSupported(WebViewFeature.PRECONNECT)) {
                android.net.Uri clientUri =
                        android.net.Uri.parse(BuildConfig.CLIENT_URL);

                android.net.Uri originUri =
                        new android.net.Uri.Builder()
                                .scheme(clientUri.getScheme())
                                .authority(clientUri.getAuthority())
                                .build();

                profile.preconnect(originUri.toString());
                Log.i(TAG, "🌐 WebView preconnect requested for: " + originUri.toString());
            }

            if (WebViewFeature.isFeatureSupported(WebViewFeature.ADD_QUIC_HINTS_V1)) {
                android.net.Uri clientUri =
                        android.net.Uri.parse(BuildConfig.CLIENT_URL);

                android.net.Uri originUri =
                        new android.net.Uri.Builder()
                                .scheme(clientUri.getScheme())
                                .authority(clientUri.getAuthority())
                                .build();

                java.util.Set<String> origins = new java.util.HashSet<>();
                origins.add(originUri.toString());
                profile.addQuicHints(origins);
                Log.i(TAG, "🚀 QUIC hint registered for client origin.");
            }

        } catch (Throwable t) {
            Log.w(TAG, "Profile warm-up failed: " + t.getMessage(), t);
        }
    }

    // =========================================================
    // 🚀 إقلاع النواة (create)
    // =========================================================

    public static synchronized boolean create(Activity activity) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new IllegalStateException(
                    "RoyalWebViewHost.create() must run on Main Looper."
            );
        }

        if (!webViewStartupReady) {
            Log.w(
                    TAG,
                    "WebView startup is not ready; creation deferred."
            );

            return false;
        }

        if (webViewInstance != null && isInitialized) {
            return true;
        }

        try {
            Log.i(TAG, "🔥 Creating production WebView on UI thread.");

            if (contextWrapper == null) {
                contextWrapper = new MutableContextWrapper(activity);
            } else {
                contextWrapper.setBaseContext(activity);
            }

            CookieManager.getInstance().setAcceptCookie(true);

            WebView webView = new WebView(contextWrapper);
            webViewInstance = webView;

            /*
             * WebView يبقى VISIBLE أثناء تجهيز الصفحة.
             */
            webView.setVisibility(View.VISIBLE);

            /*
             * نفس لون الجذر لمنع أي White Flash.
             */
            webView.setBackgroundColor(Color.TRANSPARENT);

            WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);

            android.webkit.WebSettings settings = webView.getSettings();
            settings.setCacheMode(android.webkit.WebSettings.LOAD_DEFAULT);
            settings.setDomStorageEnabled(true);

            /*
             * WebView ظاهر لكنه attached.
             * السماح بالرسم المسبق مهم حتى يكون أول Frame جاهزاً.
             */
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                settings.setOffscreenPreRaster(true);
            }

            RoyalHybridEngine.prime(
                    webView,
                    activity.getApplicationContext()
            );

            RoyalNetworkEngine.install(
                    activity.getApplicationContext()
            );

            webViewInstance = webView;
            isInitialized = true;

            Log.i(
                    TAG,
                    "✅ Production WebView created, attached-visible, ready for rendering."
            );

            return true;

        } catch (Throwable t) {

            isInitialized = false;
            webViewInstance = null;
            jsBridgeInstance = null;

            Log.e(
                    TAG,
                    "❌ WebView creation failed.",
                    t
            );

            return false;
        }
    }

    // =========================================================
    // 🔗 ربط WebEngineManager مع Bridge
    // =========================================================

    public static synchronized void bindEngineManager(
            WebEngineManager webEngineManager
    ) {
        if (webViewInstance == null || !isInitialized) {
            throw new IllegalStateException(
                    "WebView must be created before binding WebEngineManager."
            );
        }

        if (webEngineManager == null) {
            throw new IllegalArgumentException(
                    "WebEngineManager must not be null."
            );
        }

        try {
            // إزالة Bridge قديم إن وجد
            if (jsBridgeInstance != null) {
                webViewInstance.removeJavascriptInterface("RoyalBridge");
                jsBridgeInstance = null;
            }

            jsBridgeInstance = new RoyalJsBridge(
                    webViewInstance,
                    webEngineManager
            );

            webViewInstance.addJavascriptInterface(
                    jsBridgeInstance,
                    "RoyalBridge"
            );

            Log.i(
                    TAG,
                    "🔗 RoyalJsBridge successfully bound to WebEngineManager."
            );

        } catch (Throwable t) {
            Log.e(
                    TAG,
                    "❌ Failed to bind RoyalJsBridge.",
                    t
            );
            throw t;
        }
    }

    // =========================================================
    // 🔗 attach / detach / destroy
    // =========================================================

    public static synchronized WebView attach(Activity activity) {
        if (!isInitialized || webViewInstance == null) {
            boolean created = create(activity);

            if (!created || webViewInstance == null) {
                return null;
            }
        }

        if (contextWrapper != null) {
            contextWrapper.setBaseContext(activity);
        }

        safeRemoveFromParent();

        webViewInstance.setVisibility(View.VISIBLE);

        webViewInstance.onResume();
        webViewInstance.resumeTimers();

        Log.i(
                TAG,
                "🔗 WebView attached visible; rendering may proceed."
        );

        return webViewInstance;
    }

    // =========================================================
    // 🎨 Reveal WebView when Visual State Ready
    // =========================================================

    public static void revealWhenVisualStateReady(
            WebView webView,
            long requestId,
            Runnable onReady
    ) {
        if (webView == null) {
            return;
        }

        if (!WebViewFeature.isFeatureSupported(
                WebViewFeature.VISUAL_STATE_CALLBACK
        )) {
            if (onReady != null) {
                onReady.run();
            }

            return;
        }

        WebViewCompat.postVisualStateCallback(
                webView,
                requestId,
                new WebViewCompat.VisualStateCallback() {
                    @Override
                    public void onComplete(
                            long callbackRequestId
                    ) {
                        webView.post(() -> {

                            if (webView.getParent() == null) {
                                return;
                            }

                            if (onReady != null) {
                                onReady.run();
                            }

                            Log.i(
                                    TAG,
                                    "🎨 Visual state ready."
                            );
                        });
                    }
                }
        );
    }

    public static synchronized void detach() {
        if (webViewInstance == null) return;

        safeRemoveFromParent();
        RoyalSessionSentinel.freeze(webViewInstance);

        webViewInstance.onPause();
        webViewInstance.pauseTimers();
        webViewInstance.setVisibility(View.INVISIBLE);

        if (contextWrapper != null) {
            contextWrapper.setBaseContext(webViewInstance.getContext().getApplicationContext());
        }

        Log.i(TAG, "❄️ WebView detached and session frozen.");
    }

    public static synchronized void destroy() {

        if (webViewInstance != null) {

            WebView dead = webViewInstance;

            safeRemoveFromParent();

            webViewInstance = null;
            jsBridgeInstance = null;
            isInitialized = false;

            try {
                dead.stopLoading();
            } catch (Throwable ignored) {
            }

            try {
                dead.destroy();
            } catch (Throwable t) {
                Log.w(TAG, "WebView destroy failed.", t);
            }
        }

        RoyalHybridEngine.reset();

        Log.i(TAG, "💀 WebView engine destroyed.");
    }

    // =========================================================
    // 🧹 أدوات مساعدة
    // =========================================================

    private static void safeRemoveFromParent() {
        if (webViewInstance != null && webViewInstance.getParent() instanceof ViewGroup) {
            ((ViewGroup) webViewInstance.getParent()).removeView(webViewInstance);
        }
    }

    public static boolean isReady() {
        return isInitialized && webViewInstance != null;
    }

    public static RoyalJsBridge getBridge() {
        return jsBridgeInstance;
    }

    public static WebView getWebView() {
        return webViewInstance;
    }
                    }
