package com.store.app.webview;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.browser.customtabs.CustomTabColorSchemeParams;
import androidx.browser.customtabs.CustomTabsCallback;
import androidx.browser.customtabs.CustomTabsClient;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.browser.customtabs.CustomTabsServiceConnection;
import androidx.browser.customtabs.CustomTabsSession;

import com.store.app.NetworkMonitor;
import com.store.app.offline.OfflineStateManager;

import java.util.Locale;

public final class WebViewNavigationManager {

    private static final String TAG = "RoyalEngine";

    private final Context context;
    private final Activity activity;
    private final WebView webView;
    private final WebEngineConfig webEngineConfig;

    private CustomTabsClient customTabsClient;
    private CustomTabsSession customTabsSession;
    private boolean isCustomTabOpen = false;

    public WebViewNavigationManager(
            Context context,
            Activity activity,
            WebView webView,
            WebEngineConfig webEngineConfig
    ) {
        this.context = context;
        this.activity = activity;
        this.webView = webView;
        this.webEngineConfig = webEngineConfig;
    }

    private static final String OAUTH_AUTO_INJECTOR_JS =
            "(function() {" +
            "  if (window.__royalOAuthInjected) return;" +
            "  window.__royalOAuthInjected = true;" +
            "  function isOAuthUrl(url) {" +
            "    if (!url) return false;" +
            "    var l = String(url).toLowerCase();" +
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
            "      if (window.RoyalJsBridge && window.RoyalJsBridge.notifyOfflineClick) {" +
            "        window.RoyalJsBridge.notifyOfflineClick();" +
            "      }" +
            "      return true;" +
            "    }" +
            "    if (window.RoyalJsBridge && window.RoyalJsBridge.startOAuth) {" +
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
            "    if (url && isOAuthUrl(url)) handleOAuthAction(url, e);" +
            "  }, true);" +
            "  try {" +
            "    var originalAssign = window.location.assign;" +
            "    if (typeof originalAssign === 'function') {" +
            "      window.location.assign = function(url) {" +
            "        if (isOAuthUrl(url) && handleOAuthAction(url, null)) return;" +
            "        return originalAssign.apply(this, arguments);" +
            "      };" +
            "    }" +
            "    var originalReplace = window.location.replace;" +
            "    if (typeof originalReplace === 'function') {" +
            "      window.location.replace = function(url) {" +
            "        if (isOAuthUrl(url) && handleOAuthAction(url, null)) return;" +
            "        return originalReplace.apply(this, arguments);" +
            "      };" +
            "    }" +
            "    var proto = Object.getPrototypeOf(window.location);" +
            "    var descriptor = Object.getOwnPropertyDescriptor(proto || window.location, 'href');" +
            "    if (descriptor && descriptor.set) {" +
            "      var origSet = descriptor.set;" +
            "      Object.defineProperty(window.location, 'href', {" +
            "        configurable: true," +
            "        set: function(val) {" +
            "          if (isOAuthUrl(val) && handleOAuthAction(val, null)) return;" +
            "          origSet.call(window.location, val);" +
            "        }" +
            "      });" +
            "    }" +
            "  } catch(e) {}" +
            "})();";

    /**
     * Called by WebEngineManager.onPageFinished().
     */
    public void injectOAuthScript(WebView view) {
        if (view != null) {
            view.evaluateJavascript(OAUTH_AUTO_INJECTOR_JS, null);
        }
    }

    public void setupCustomTabsSession() {
        if (activity == null) return;

        try {
            String packageName = CustomTabsClient.getPackageName(context, null);
            if (packageName == null) return;

            CustomTabsClient.bindCustomTabsService(
                    context,
                    packageName,
                    new CustomTabsServiceConnection() {
                        @Override
                        public void onCustomTabsServiceConnected(
                                @NonNull ComponentName name,
                                @NonNull CustomTabsClient client
                        ) {
                            customTabsClient = client;
                            customTabsClient.warmup(0L);

                            customTabsSession = customTabsClient.newSession(
                                    new CustomTabsCallback() {
                                        @Override
                                        public void onNavigationEvent(
                                                int navigationEvent,
                                                android.os.Bundle extras
                                        ) {
                                            super.onNavigationEvent(
                                                    navigationEvent,
                                                    extras
                                            );

                                            if (navigationEvent
                                                    == CustomTabsCallback.TAB_HIDDEN
                                                    && isCustomTabOpen) {
                                                isCustomTabOpen = false;

                                                Log.i(
                                                        TAG,
                                                        "Custom Tab hidden; refreshing WebView session."
                                                );

                                                if (activity != null && webView != null) {
                                                    activity.runOnUiThread(() ->
                                                            webView.postDelayed(() -> {
                                                                if (webEngineConfig.getTrustedHost() != null
                                                                        && webEngineConfig.getTrustedScheme() != null) {
                                                                    webView.loadUrl(
                                                                            webEngineConfig.getTrustedScheme()
                                                                                    + "://"
                                                                                    + webEngineConfig.getTrustedHost()
                                                                                    + (webEngineConfig.getTrustedPort() > 0
                                                                                    ? ":" + webEngineConfig.getTrustedPort()
                                                                                    : "")
                                                                    );
                                                                } else {
                                                                    webView.reload();
                                                                }
                                                            }, 300L)
                                                    );
                                                }
                                            }
                                        }
                                    }
                            );
                        }

                        @Override
                        public void onServiceDisconnected(ComponentName name) {
                            customTabsClient = null;
                            customTabsSession = null;
                        }
                    }
            );
        } catch (Throwable t) {
            Log.w(TAG, "CustomTabsService binding failed.", t);
        }
    }

    public boolean isSensitiveNavigation(Uri uri) {
        if (uri == null) return false;

        String scheme = uri.getScheme();
        if (scheme == null) return false;

        scheme = scheme.toLowerCase(Locale.ROOT);
        if (!"https".equals(scheme) && !"http".equals(scheme)) return false;

        String host = uri.getHost();
        if (host == null) return false;

        host = host.toLowerCase(Locale.ROOT);

        return host.equals("accounts.google.com")
                || host.endsWith(".google.com")
                || host.equals("appleid.apple.com")
                || host.endsWith(".microsoftonline.com")
                || host.equals("login.live.com")
                || host.endsWith(".linkedin.com")
                || host.equals("github.com")
                || host.endsWith(".github.com");
    }

    private boolean isLogoutUrl(Uri uri) {
        if (uri == null) return false;

        String url = uri.toString().toLowerCase(Locale.ROOT);

        return url.contains("/logout")
                || url.contains("/signout")
                || url.contains("/sign-out")
                || url.contains("/log-out")
                || url.contains("action=logout");
    }

    public void clearNativeSession(Runnable onComplete) {
        if (activity == null) {
            if (onComplete != null) onComplete.run();
            return;
        }

        activity.runOnUiThread(() -> {
            try {
                CookieManager cookieManager = CookieManager.getInstance();

                cookieManager.removeSessionCookies(ignored -> {
                    cookieManager.removeAllCookies(success -> {
                        cookieManager.flush();

                        Log.i(
                                TAG,
                                "Native session cookies cleared."
                        );

                        if (onComplete != null) onComplete.run();
                    });
                });

            } catch (Exception e) {
                Log.w(TAG, "Failed to clear native session.", e);
                if (onComplete != null) onComplete.run();
            }
        });
    }

    public boolean launchSensitiveFlow(Uri uri) {
        if (activity == null || uri == null) return false;

        if (!NetworkMonitor.isInternetAvailable(context)) {
            OfflineStateManager.getInstance().notifyOfflineClickAttempt();

            Log.w(
                    TAG,
                    "Offline mode; blocked OAuth Custom Tab launch: " + uri
            );

            return true;
        }

        try {
            CustomTabsIntent.Builder builder =
                    customTabsSession != null
                            ? new CustomTabsIntent.Builder(customTabsSession)
                            : new CustomTabsIntent.Builder();

            CustomTabColorSchemeParams darkParams =
                    new CustomTabColorSchemeParams.Builder()
                            .setToolbarColor(Color.parseColor("#090D16"))
                            .setNavigationBarColor(Color.parseColor("#090D16"))
                            .build();

            builder.setDefaultColorSchemeParams(darkParams);

            try {
                int screenHeight = activity.getResources()
                        .getDisplayMetrics().heightPixels;

                builder.setInitialActivityHeightPx(
                        (int) (screenHeight * 0.88f),
                        CustomTabsIntent.ACTIVITY_HEIGHT_DEFAULT
                );
            } catch (Throwable ignored) {
                // Keep default Custom Tab height if the API is unavailable.
            }

            builder.setStartAnimations(
                    activity,
                    android.R.anim.fade_in,
                    android.R.anim.fade_out
            );

            builder.setExitAnimations(
                    activity,
                    android.R.anim.fade_in,
                    android.R.anim.fade_out
            );

            builder.setShowTitle(true);
            builder.setShareState(CustomTabsIntent.SHARE_STATE_OFF);

            isCustomTabOpen = true;
            builder.build().launchUrl(activity, uri);

            Log.i(TAG, "Sensitive navigation opened in Custom Tab: " + uri);
            return true;

        } catch (Throwable e) {
            isCustomTabOpen = false;
            Log.e(TAG, "Failed to launch sensitive navigation.", e);
            return false;
        }
    }

    public void handleAuthReturn(String redirectUrl) {
        if (activity == null || webView == null) return;

        isCustomTabOpen = false;

        activity.runOnUiThread(() -> {
            Log.i(TAG, "Auth return received: " + redirectUrl);

            if (redirectUrl != null && !redirectUrl.isEmpty()) {
                if (redirectUrl.startsWith("com.store.app.auth")) {
                    reloadTrustedOrigin();
                    return;
                }

                Uri redirectUri = Uri.parse(redirectUrl);
                if (isHttpOrHttps(redirectUri)) {
                    webView.loadUrl(redirectUrl);
                } else {
                    reloadTrustedOrigin();
                    return;
                }

                flushCookiesSafely();
                return;
            }

            reloadTrustedOrigin();
            flushCookiesSafely();
        });
    }

    private void reloadTrustedOrigin() {
        if (webView == null) return;

        String scheme = webEngineConfig.getTrustedScheme();
        String host = webEngineConfig.getTrustedHost();
        int port = webEngineConfig.getTrustedPort();

        if (scheme != null && host != null) {
            String url = scheme + "://" + host
                    + (port > 0 ? ":" + port : "");
            webView.loadUrl(url);
        } else {
            webView.reload();
        }
    }

    private void flushCookiesSafely() {
        try {
            CookieManager.getInstance().flush();
        } catch (Exception e) {
            Log.w(TAG, "Cookie flush failed.", e);
        }
    }

    private boolean launchExternalWebUrl(Uri uri) {
        if (activity == null || uri == null) return false;

        try {
            CustomTabsIntent.Builder builder =
                    customTabsSession != null
                            ? new CustomTabsIntent.Builder(customTabsSession)
                            : new CustomTabsIntent.Builder();

            builder.setShowTitle(true);
            builder.setShareState(CustomTabsIntent.SHARE_STATE_OFF);
            builder.build().launchUrl(activity, uri);

            Log.i(TAG, "External HTTP/HTTPS opened in Custom Tab: " + uri);
            return true;

        } catch (Throwable e) {
            Log.e(TAG, "Failed to open external URL in Custom Tab: " + uri, e);
            return launchExternal(uri);
        }
    }

    private boolean launchExternal(Uri uri) {
        if (uri == null) return true;

        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            if (intent.resolveActivity(context.getPackageManager()) != null) {
                applyNativeExitTransition();
                context.startActivity(intent);
            } else {
                Log.w(TAG, "No Activity found for: " + uri);
            }

        } catch (Exception e) {
            Log.e(TAG, "External launch failed.", e);
        }

        return true;
    }

    private void applyNativeExitTransition() {
        if (activity != null) {
            activity.runOnUiThread(() ->
                    activity.overridePendingTransition(
                            android.R.anim.fade_in,
                            android.R.anim.fade_out
                    )
            );
        }
    }

    private boolean launchIntentScheme(Uri uri) {
        if (activity == null || uri == null) return true;

        try {
            Intent intent = Intent.parseUri(
                    uri.toString(),
                    Intent.URI_INTENT_SCHEME
            );

            if (intent.resolveActivity(activity.getPackageManager()) != null) {
                activity.startActivity(intent);
                return true;
            }

            String fallbackUrl = intent.getStringExtra("browser_fallback_url");

            if (fallbackUrl != null && !fallbackUrl.trim().isEmpty()) {
                Uri fallbackUri = Uri.parse(fallbackUrl);

                if (isHttpOrHttps(fallbackUri)) {
                    new CustomTabsIntent.Builder()
                            .build()
                            .launchUrl(activity, fallbackUri);
                    return true;
                }
            }

        } catch (Exception e) {
            Log.w(TAG, "Payment intent:// handling failed.", e);
        }

        return true;
    }

    private boolean isPaymentExternalScheme(String scheme) {
        if (scheme == null) return false;

        String normalized = scheme.toLowerCase(Locale.ROOT);

        return "intent".equals(normalized)
                || "upi".equals(normalized)
                || "pay".equals(normalized)
                || "paypal".equals(normalized)
                || "alipay".equals(normalized)
                || "gpay".equals(normalized)
                || "applepay".equals(normalized);
    }

    private boolean isHttpOrHttps(Uri uri) {
        if (uri == null || uri.getScheme() == null) return false;

        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        return "http".equals(scheme) || "https".equals(scheme);
    }

    /**
     * Called by WebEngineManager's WebViewClient.
     * Returns true when the navigation has been handled externally or blocked.
     */
    public boolean handleUriLogic(Uri uri, boolean isMainFrame) {
        if (uri == null) return true;

        if (!NetworkMonitor.isInternetAvailable(context)) {
            OfflineStateManager.getInstance().notifyOfflineClickAttempt();
            return true;
        }

        String scheme = uri.getScheme() == null
                ? null
                : uri.getScheme().toLowerCase(Locale.ROOT);

        if (scheme == null) return true;

        if (isSensitiveNavigation(uri)) {
            return launchSensitiveFlow(uri);
        }

        if (isPaymentExternalScheme(scheme)) {
            if ("intent".equals(scheme)) {
                return launchIntentScheme(uri);
            }
            return launchExternal(uri);
        }

        if ("com.store.app.auth".equals(scheme)) {
            return true;
        }

        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            return launchExternal(uri);
        }

        if (webEngineConfig.isSameOrigin(uri)) {
            if (isLogoutUrl(uri)) {
                clearNativeSession(null);
            }
            return false;
        }

        return launchExternalWebUrl(uri);
    }
    }
