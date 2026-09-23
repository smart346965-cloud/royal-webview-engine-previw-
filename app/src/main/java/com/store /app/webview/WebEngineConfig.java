package com.store.app.webview;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;

import com.store.app.NetworkMonitor;
import com.store.app.SystemUI;

public class WebEngineConfig {

    private static final String TAG = "RoyalEngine";

    private final Context context;
    private final Activity activity;
    private final WebView webView;

    // =========================================================
    // 🔒 Trusted Origin State
    // =========================================================
    private String trustedScheme = null;
    private String trustedHost = null;
    private int trustedPort = -1;
    private String configuredScheme;
    private String configuredHost;
    private int configuredPort = -1;

    public WebEngineConfig(
            Context context,
            WebView webView,
            Activity activity
    ) {
        this.context = context;
        this.webView = webView;
        this.activity = activity;

        initializeConfiguredOrigin();
    }

    // =========================================================
    // ⚙️ WebView Configuration
    // =========================================================
    public void configureSettings() {

        WebSettings settings =
                webView.getSettings();

        /*
         * =====================================================
         * 🚀 NATIVE COMPOSITOR SCROLL
         * =====================================================
         *
         * لا LayerType يدوي.
         * لا scroll animation من التطبيق.
         * لا JS scroll interception.
         */

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            settings.setOffscreenPreRaster(true);
        }

        settings.setLayoutAlgorithm(
                WebSettings.LayoutAlgorithm.NORMAL
        );

        webView.setOverScrollMode(
                View.OVER_SCROLL_NEVER
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            webView.setForceDarkAllowed(false);
            webView.setVerticalScrollbarThumbDrawable(null);
        }

        /*
         * =====================================================
         * WebView Core
         * =====================================================
         */

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);

        /*
         * Payment providers may use window.open() for:
         * - 3-D Secure
         * - PayPal approval
         * - bank authentication
         * - hosted payment challenge pages
         *
         * The actual popup is controlled by RoyalCapabilitiesEngine
         * through WebChromeClient.onCreateWindow().
         */
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(true);

        configureNexusUserAgent(settings);

        settings.setCacheMode(
                WebSettings.LOAD_DEFAULT
        );

        settings.setSafeBrowsingEnabled(true);

        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setAllowFileAccessFromFileURLs(false);

        if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.JELLY_BEAN_MR1
        ) {
            settings.setMediaPlaybackRequiresUserGesture(
                    false
            );
        }

        settings.setMixedContentMode(
                WebSettings.MIXED_CONTENT_NEVER_ALLOW
        );

        /*
         * Required by PayPal, 3-D Secure, bank authentication,
         * and payment providers that use window.open().
         */
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(true);
        settings.setSupportZoom(false);

        /*
         * =====================================================
         * 🍪 Cookies
         * =====================================================
         */

        CookieManager cookieManager =
                CookieManager.getInstance();

        cookieManager.setAcceptCookie(true);

        if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.LOLLIPOP
        ) {
            cookieManager.setAcceptThirdPartyCookies(
                    webView,
                    true
            );
        }
    }

    private void configureNexusUserAgent(WebSettings settings) {
        if (settings == null) {
            return;
        }

        String originalUserAgent = settings.getUserAgentString();

        if (originalUserAgent == null || originalUserAgent.trim().isEmpty()) {
            return;
        }

        String nexusUserAgent = originalUserAgent
                .replace("; wv", "")
                .replace("Version/4.0 ", "")
                .replace("Version/4.0", "")
                .replaceAll("\\s{2,}", " ")
                .trim();

        if (!nexusUserAgent.contains("NexusEngine/1.0")) {
            nexusUserAgent = nexusUserAgent + " NexusEngine/1.0";
        }

        settings.setUserAgentString(nexusUserAgent);

        Log.i(
                TAG,
                "✅ Nexus-compatible Chromium User-Agent configured."
        );
    }

    // =========================================================
    // 🔒 Trusted Origin
    // =========================================================
    private void initializeConfiguredOrigin() {
        try {
            Uri configuredUri =
                    Uri.parse(
                            com.store.app.BuildConfig.CLIENT_URL
                    );

            configuredScheme =
                    configuredUri.getScheme() != null
                            ? configuredUri.getScheme()
                                    .toLowerCase(
                                            java.util.Locale.ROOT
                                    )
                            : null;

            configuredHost =
                    configuredUri.getHost() != null
                            ? configuredUri.getHost()
                                    .toLowerCase(
                                            java.util.Locale.ROOT
                                    )
                            : null;

            if (configuredUri.getPort() != -1) {
                configuredPort =
                        configuredUri.getPort();
            } else if ("https".equals(configuredScheme)) {
                configuredPort = 443;
            } else if ("http".equals(configuredScheme)) {
                configuredPort = 80;
            }

        } catch (Throwable t) {
            Log.w(
                    TAG,
                    "Configured origin initialization failed.",
                    t
            );
        }
    }

    public void setTrustedOrigin(String url) {

        if (url == null) {
            return;
        }

        Uri uri = Uri.parse(url);

        String scheme = uri.getScheme();
        String host = uri.getHost();

        if (scheme == null || host == null) {
            return;
        }

        trustedScheme = scheme.toLowerCase();
        trustedHost = host.toLowerCase();

        trustedPort =
                uri.getPort() == -1
                        ? ("https".equals(trustedScheme) ? 443 : 80)
                        : uri.getPort();

        Log.i(
                TAG,
                "🔒 Trusted Origin = "
                        + trustedScheme
                        + "://"
                        + trustedHost
                        + ":"
                        + trustedPort
        );
    }

    // =========================================================
    // 🔥 Same Origin Policy
    // =========================================================
    public boolean isSameOrigin(Uri uri) {
        if (uri == null
                || configuredScheme == null
                || configuredHost == null) {
            return false;
        }

        String targetScheme =
                uri.getScheme() != null
                        ? uri.getScheme()
                                .toLowerCase(
                                        java.util.Locale.ROOT
                                )
                        : null;

        String targetHost =
                uri.getHost() != null
                        ? uri.getHost()
                                .toLowerCase(
                                        java.util.Locale.ROOT
                                )
                        : null;

        if (targetScheme == null
                || targetHost == null) {
            return false;
        }

        int targetPort =
                uri.getPort() != -1
                        ? uri.getPort()
                        : ("https".equals(targetScheme)
                                ? 443
                                : 80);

        return configuredScheme.equals(targetScheme)
                && configuredHost.equals(targetHost)
                && configuredPort == targetPort;
    }

    // =========================================================
    // 👑 Status Bar Delegation
    // SystemUI هو المالك الوحيد لشريط النظام
    // =========================================================
    public void syncStatusBarColor(WebView view) {

        if (activity == null ||
                activity.isFinishing() ||
                view == null) {
            return;
        }

        /*
         * لا نقوم هنا بأي:
         *
         * setStatusBarColor()
         * setNavigationBarColor()
         * setDynamicIcons()
         * evaluateJavascript()
         *
         * SystemUI هو المسؤول الوحيد عن ذلك.
         */
        SystemUI.scheduleStatusBarSync(
                activity,
                view
        );
    }

    // =========================================================
    // 🔗 Trusted Origin State Accessors
    // =========================================================
    public String getTrustedScheme() {
        return trustedScheme;
    }

    public String getTrustedHost() {
        return trustedHost;
    }

    public int getTrustedPort() {
        return trustedPort;
    }
            }
