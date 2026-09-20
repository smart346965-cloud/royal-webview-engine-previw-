package com.store.app.webview;

import android.app.Activity;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.SystemClock;
import android.util.Log;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.webkit.Page;
import androidx.webkit.Profile;
import androidx.webkit.ProfileStore;
import androidx.webkit.PrerenderException;
import androidx.webkit.PrerenderOperationCallback;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.util.HashSet;
import java.util.Set;

/**
 * ============================================================
 * 👑 SpeculativeEngine
 * ============================================================
 *
 * المحرك السيادي للتسخين والتنبؤ Native Speculative Loading.
 *
 * مسؤول فقط عن:
 *
 * - MULTI_PROFILE
 * - Profile / ProfileStore
 * - WARM_UP_RENDERER_PROCESS
 * - PRECONNECT
 * - PRERENDER_WITH_URL
 * - CancellationSignal
 * - Prediction cooldown
 * - Prediction budget
 * - speculative URL tracking
 * - Origin validation الخاصة بالتنبؤ
 *
 * لا يحتوي على:
 *
 * - Custom Tabs
 * - OAuth
 * - WebViewClient
 * - WebChromeClient
 * - Navigation routing
 * - Offline logic
 * - Splash logic
 * - Scroll logic
 * - External intents
 *
 * ============================================================
 */
public final class SpeculativeEngine {

    private static final String TAG = "SpeculativeEngine";

    // ============================================================
    // ⚙️ Core WebView Context
    // ============================================================

    private final Activity activity;
    private final WebView webView;

    // ============================================================
    // 👤 Chromium Profile
    // ============================================================

    private Profile webProfile = null;

    private boolean speculativeLoadingReady = false;

    // ============================================================
    // 💰 Prediction Budget
    // ============================================================

    private static final int MAX_SPECULATIVE_URLS = 8;

    private final Set<String> speculativeUrls =
            new HashSet<>();

    private long lastPredictionTime = 0L;

    private static final long PREDICTION_COOLDOWN_MS = 350L;

    // ============================================================
    // 🚀 Active Prerender
    // ============================================================

    private CancellationSignal activePrerenderCancellationSignal = null;

    private String activePrerenderUrl = null;

    // ============================================================
    // 🔐 Trusted Origin
    // ============================================================

    /*
     * هذه القيم لا تُنشأ هنا.
     *
     * WebEngineManager هو صاحب حالة الـ trusted origin.
     * عند تغيرها يقوم بتمريرها إلى هذا المحرك بواسطة
     * setTrustedOrigin().
     */
    private String trustedScheme = null;
    private String trustedHost = null;
    private int trustedPort = 443;

    // ============================================================
    // 🏗️ Constructor
    // ============================================================

    public SpeculativeEngine(
            Activity activity,
            WebView webView
    ) {

        this.activity = activity;
        this.webView = webView;
    }

    // ============================================================
    // 🔐 Trusted Origin Binding
    // ============================================================

    /**
     * يربط محرك التنبؤ بالـ origin الموثوق الحالي.
     *
     * لا يقوم هذا المحرك باختيار الـ trusted origin.
     * WebEngineManager هو المسؤول عن ذلك.
     */
    public void setTrustedOrigin(
            String scheme,
            String host,
            int port
    ) {

        if (scheme == null || host == null) {
            trustedScheme = null;
            trustedHost = null;
            trustedPort = 443;
            return;
        }

        trustedScheme = scheme.toLowerCase();
        trustedHost = host.toLowerCase();

        trustedPort =
                port == -1
                        ? 443
                        : port;
    }

    // ============================================================
    // ⚡ Initialize Native Speculative Loading
    // ============================================================

    public void initializeSpeculativeLoading() {

        if (activity == null) {
            return;
        }

        activity.runOnUiThread(() -> {

            try {

                if (!WebViewFeature.isFeatureSupported(
                        WebViewFeature.MULTI_PROFILE
                )) {

                    Log.w(
                            TAG,
                            "⚠️ MULTI_PROFILE not supported."
                    );

                    speculativeLoadingReady = false;
                    return;
                }

                webProfile =
                        ProfileStore
                                .getInstance()
                                .getOrCreateProfile(
                                        Profile.DEFAULT_PROFILE_NAME
                                );

                speculativeLoadingReady =
                        webProfile != null;

                if (!speculativeLoadingReady) {
                    return;
                }

                /*
                 * 🔥 Renderer warm-up
                 *
                 * التسخين يتم مرة واحدة أثناء تجهيز المحرك.
                 * لا يدخل في مسار التفاعل أو السكرول.
                 */
                if (WebViewFeature.isFeatureSupported(
                        WebViewFeature.WARM_UP_RENDERER_PROCESS
                )) {

                    try {

                        webProfile.warmUpRendererProcess();

                    } catch (Throwable warmupError) {

                        Log.w(
                                TAG,
                                "⚠️ Renderer warm-up unavailable.",
                                warmupError
                        );
                    }
                }

                Log.i(
                        TAG,
                        "⚡ Native Chromium speculative engine ready."
                );

            } catch (Throwable e) {

                speculativeLoadingReady = false;

                Log.e(
                        TAG,
                        "❌ Failed to initialize Chromium Profile.",
                        e
                );
            }
        });
    }

    // ============================================================
    // ⚡ Native Preconnect
    // ============================================================

    public void preconnectOrigin(String url) {

        if (activity == null || url == null) {
            return;
        }

        activity.runOnUiThread(() -> {

            try {

                if (!WebViewFeature.isFeatureSupported(
                        WebViewFeature.PRECONNECT
                )) {

                    Log.w(
                            TAG,
                            "⚠️ PRECONNECT not supported."
                    );

                    return;
                }

                /*
                 * إذا لم يتم إنشاء Profile لأي سبب،
                 * نحاول الحصول على الـ default profile.
                 */
                if (webProfile == null) {

                    if (!WebViewFeature.isFeatureSupported(
                            WebViewFeature.MULTI_PROFILE
                    )) {

                        Log.w(
                                TAG,
                                "⚠️ MULTI_PROFILE not supported; cannot obtain default profile."
                        );

                        return;
                    }

                    webProfile =
                            ProfileStore
                                    .getInstance()
                                    .getOrCreateProfile(
                                            Profile.DEFAULT_PROFILE_NAME
                                    );
                }

                if (webProfile == null) {
                    return;
                }

                Uri uri = Uri.parse(url);

                String origin =
                        buildOrigin(uri);

                if (origin == null) {
                    return;
                }

                webProfile.preconnect(origin);

                Log.d(
                        TAG,
                        "⚡ Preconnected: " + origin
                );

            } catch (Throwable e) {

                Log.w(
                        TAG,
                        "⚠️ Preconnect failed: " + url,
                        e
                );
            }
        });
    }

    // ============================================================
    // 🌐 Origin Builder
    // ============================================================

    private String buildOrigin(Uri uri) {

        if (uri == null) {
            return null;
        }

        String scheme = uri.getScheme();
        String host = uri.getHost();

        if (scheme == null || host == null) {
            return null;
        }

        scheme = scheme.toLowerCase();
        host = host.toLowerCase();

        /*
         * التنبؤ هنا HTTPS فقط.
         */
        if (!"https".equals(scheme)) {
            return null;
        }

        int port = uri.getPort();

        if (port == -1 || port == 443) {
            return scheme + "://" + host;
        }

        return scheme + "://" + host + ":" + port;
    }

    // ============================================================
    // 🛡️ Speculative Origin Policy
    // ============================================================

    private boolean isSafePredictionUrl(Uri uri) {

        if (uri == null) {
            return false;
        }

        String scheme = uri.getScheme();
        String host = uri.getHost();

        if (scheme == null || host == null) {
            return false;
        }

        scheme = scheme.toLowerCase();
        host = host.toLowerCase();

        /*
         * HTTPS فقط.
         */
        if (!"https".equals(scheme)) {
            return false;
        }

        /*
         * لا يوجد Trusted Origin بعد.
         */
        if (trustedHost == null ||
                trustedScheme == null) {

            return false;
        }

        /*
         * نفس الـ scheme.
         */
        if (!trustedScheme.equalsIgnoreCase(scheme)) {
            return false;
        }

        /*
         * توحيد المنفذ الافتراضي.
         */
        int port = uri.getPort();

        if (port == -1) {
            port = 443;
        }

        /*
         * يجب أن يكون نفس المنفذ.
         */
        if (port != trustedPort) {
            return false;
        }

        String trusted =
                trustedHost.toLowerCase();

        /*
         * السماح:
         *
         * https://example.com
         *
         * و:
         *
         * https://www.example.com
         * https://shop.example.com
         * https://cdn.example.com
         *
         * ما دام النطاق تحت الـ trusted host.
         */
        boolean hostMatches =
                trusted.equals(host)
                        || host.endsWith("." + trusted);

        if (!hostMatches) {

            Log.w(
                    TAG,
                    "🛡️ Prediction rejected: foreign origin -> "
                            + uri
            );

            return false;
        }

        return true;
    }

    // ============================================================
    // 🧠 Royal Prediction Entry Point
    // ============================================================

    public void predict(String url) {

        if (
                activity == null ||
                webView == null ||
                url == null ||
                url.isEmpty()
        ) {
            return;
        }

        activity.runOnUiThread(() -> {

            try {

                Uri uri =
                        Uri.parse(url);

                /*
                 * 🔐 طبقة الأمان النهائية Native.
                 */
                if (!isSafePredictionUrl(uri)) {
                    return;
                }

                String normalizedUrl =
                        uri.toString();

                /*
                 * منع إعادة تسخين نفس الرابط.
                 */
                if (speculativeUrls.contains(
                        normalizedUrl
                )) {
                    return;
                }

                /*
                 * Prediction cooldown.
                 *
                 * يمنع burst متتالي من prerender.
                 */
                long now =
                        SystemClock.uptimeMillis();

                if (
                        now - lastPredictionTime <
                        PREDICTION_COOLDOWN_MS
                ) {
                    return;
                }

                lastPredictionTime = now;

                /*
                 * Prediction budget.
                 *
                 * عند امتلاء الميزانية يتم
                 * تصفير السجل القديم كما كان
                 * في WebEngineManager.
                 */
                if (
                        speculativeUrls.size() >=
                        MAX_SPECULATIVE_URLS
                ) {

                    speculativeUrls.clear();
                }

                speculativeUrls.add(
                        normalizedUrl
                );

                Log.d(
                        TAG,
                        "🧠 Native prediction accepted: "
                                + normalizedUrl
                );

                /*
                 * المرحلة الأولى:
                 *
                 * DNS + TCP + TLS
                 */
                preconnectOrigin(
                        normalizedUrl
                );

                /*
                 * المرحلة الثانية:
                 *
                 * Chromium Native Prerender
                 */
                startPrerender(
                        normalizedUrl
                );

            } catch (Throwable e) {

                Log.w(
                        TAG,
                        "⚠️ Native prediction failed: "
                                + url,
                        e
                );
            }
        });
    }

    // ============================================================
    // 🛑 Cancel Active Prerender
    // ============================================================

    public void cancelActivePrerender() {

        if (activePrerenderCancellationSignal != null) {

            try {

                activePrerenderCancellationSignal.cancel();

                Log.d(
                        TAG,
                        "🛑 Active prerender cancelled: "
                                + activePrerenderUrl
                );

            } catch (Throwable e) {

                Log.w(
                        TAG,
                        "⚠️ Failed to cancel active prerender.",
                        e
                );
            }
        }

        activePrerenderCancellationSignal = null;
        activePrerenderUrl = null;
    }

    // ============================================================
    // 🚀 Chromium Native Prerender
    // ============================================================

    private void startPrerender(String url) {

        if (
                activity == null ||
                webView == null ||
                url == null
        ) {
            return;
        }

        if (!WebViewFeature.isFeatureSupported(
                WebViewFeature.PRERENDER_WITH_URL
        )) {

            Log.d(
                    TAG,
                    "ℹ️ Chromium prerender unavailable."
            );

            return;
        }

        try {

            /*
             * لا نسمح بأكثر من prerender نشط.
             */
            cancelActivePrerender();

            CancellationSignal
                    cancellationSignal =
                    new CancellationSignal();

            activePrerenderCancellationSignal =
                    cancellationSignal;

            activePrerenderUrl =
                    url;

            WebViewCompat.prerenderUrlAsync(
                    webView,
                    url,
                    cancellationSignal,
                    activity.getMainExecutor(),

                    new PrerenderOperationCallback() {

                        @Override
                        public void onPrerenderActivated() {

                            Log.d(
                                    TAG,
                                    "🚀 Chromium prerender activated: "
                                            + url
                            );

                            if (
                                    url.equals(
                                            activePrerenderUrl
                                    )
                            ) {

                                activePrerenderCancellationSignal =
                                        null;

                                activePrerenderUrl =
                                        null;
                            }
                        }

                        @Override
                        public void onError(
                                @NonNull
                                PrerenderException exception
                        ) {

                            Log.d(
                                    TAG,
                                    "ℹ️ Chromium prerender unavailable/rejected: "
                                            + url
                                            + " error="
                                            + exception.getMessage()
                            );

                            if (
                                    url.equals(
                                            activePrerenderUrl
                                    )
                            ) {

                                activePrerenderCancellationSignal =
                                        null;

                                activePrerenderUrl =
                                        null;
                            }
                        }
                    }
            );

            Log.d(
                    TAG,
                    "🚀 Chromium prerender requested: "
                            + url
            );

        } catch (Throwable e) {

            Log.w(
                    TAG,
                    "⚠️ Chromium prerender failed: "
                            + url,
                    e
            );

            if (
                    url.equals(
                            activePrerenderUrl
                    )
            ) {

                activePrerenderCancellationSignal =
                        null;

                activePrerenderUrl =
                        null;
            }
        }
    }

    // ============================================================
    // 🧹 Navigation Lifecycle
    // ============================================================

    /**
     * يتم استدعاؤها من WebEngineManager عند بدء Navigation.
     *
     * هذا الجزء كان سابقاً داخل NavigationListener.
     */
    public void onNavigationStarted() {

        speculativeUrls.clear();

        cancelActivePrerender();

        Log.i(
                TAG,
                "🚀 Navigation started -> speculative state reset."
        );
    }

    // ============================================================
    // 🔎 State Access
    // ============================================================

    public boolean isReady() {
        return speculativeLoadingReady;
    }

    public Profile getWebProfile() {
        return webProfile;
    }

    public int getPredictionBudget() {
        return MAX_SPECULATIVE_URLS;
    }

    public long getPredictionCooldownMs() {
        return PREDICTION_COOLDOWN_MS;
    }
      }
