package com.store.app;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.*;

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

public class WebEngineManager {

    private static final String TAG = "RoyalEngine";

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
    private final WebViewNavigationManager navigationManager;

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
        this.navigationManager = new WebViewNavigationManager(
                this.context,
                this.activity,
                this.webView,
                this.webEngineConfig,
                this.speculativeEngine
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
        navigationManager.setupCustomTabsSession();
        attachClients();
        speculativeEngine.initializeSpeculativeLoading();
        String clientUrl = BuildConfig.CLIENT_URL;
        if (clientUrl != null) speculativeEngine.preconnectOrigin(clientUrl);

        navigationManager.addNavigationListener();

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

    private void attachClients() {
        navigationManager.attachWebViewClient();

        webView.setWebChromeClient(
                capabilitiesEngine.buildChromeClient(progressBar)
        );

        capabilitiesEngine.attachDownloadManager(webView);
    }

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
