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

    public WebEngineConfig(
            Context context,
            WebView webView,
            Activity activity
    ) {
        this.context = context;
        this.webView = webView;
        this.activity = activity;
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
            settings.setOffscreenPreRaster(false);
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

        settings.setSupportMultipleWindows(false);
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

    // =========================================================
    // 🔒 Trusted Origin
    // =========================================================
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

        if (uri == null) {
            return false;
        }

        if (trustedHost == null) {
            return false;
        }

        String targetHost = uri.getHost();

        if (targetHost == null) {
            return false;
        }

        targetHost = targetHost.toLowerCase();

        String trusted =
                trustedHost.toLowerCase();

        String targetScheme =
                uri.getScheme();

        int port = uri.getPort();

        if (port == -1) {
            port =
                    "https".equals(targetScheme)
                            ? 443
                            : 80;
        }

        // السماح بـ subdomains
        boolean hostMatches =
                trusted.equalsIgnoreCase(targetHost)
                        || targetHost.endsWith("." + trusted);

        return hostMatches
                && trustedScheme.equalsIgnoreCase(targetScheme)
                && trustedPort == port;
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
