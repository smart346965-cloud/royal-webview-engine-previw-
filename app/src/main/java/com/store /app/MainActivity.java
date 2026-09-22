package com.store.app;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import com.store.app.offline.OfflineUIController;
import com.store.app.offline.OfflineStateManager;
import com.store.app.RoyalAuthManager;
import com.store.app.RoyalJsBridge;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "RoyalMainActivity";
    private static final long MIN_SPLASH_TIME = 3000L;
    private static final long MAX_SPLASH_TIME = 10000L;

    private boolean splashRemoved = false;
    private boolean isPageLoaded = false;
    private boolean webViewReady = false;
    private boolean visualStateReady = false;
    private boolean webViewRevealed = false;
    private boolean splashPageFinished = false;
    private boolean splashVisualStateReady = false;

    private WebEngineManager engineManager;
    private RoyalCapabilitiesEngine capabilitiesEngine;
    private WebView activeWebView;
    private ProgressBar progressBar;
    private long splashStartTime = 0;
    private OfflineUIController offlineController;
    private RoyalAuthManager royalAuthManager;
    private Intent pendingAuthIntent;
    private FrameLayout rootContainer;
    private int appliedImeBottomInset = 0;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private FrameLayout headerContainer;
    private FrameLayout bottomContainer;
    private FrameLayout sidebarContainer;
    private FrameLayout webViewContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        final SplashScreen splashScreen = SplashScreen.installSplashScreen(this);
        splashStartTime = System.currentTimeMillis();

        splashScreen.setKeepOnScreenCondition(() -> {
            long elapsed = System.currentTimeMillis() - splashStartTime;
            if (elapsed < MIN_SPLASH_TIME) return true;
            if (splashPageFinished && splashVisualStateReady) return false;
            return elapsed < MAX_SPLASH_TIME;
        });

        splashScreen.setOnExitAnimationListener(splashScreenView -> {
            splashScreenView.getView().animate().alpha(0f).setDuration(500L)
                    .withEndAction(splashScreenView::remove).start();
        });

        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        int initialColor = SystemUI.getDefaultSystemColor(this);
        getWindow().setBackgroundDrawable(new ColorDrawable(initialColor));

        try {
            RoyalPanopticon.startAwareness();
            Log.i(TAG, "RoyalPanopticon Engine: Active and running in background.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize RoyalPanopticon.", e);
        }

        rootContainer = new FrameLayout(this);
        rootContainer.setBackgroundColor(initialColor);

        webViewContainer = new FrameLayout(this);

        headerContainer = new FrameLayout(this);
        headerContainer.setVisibility(View.GONE);

        bottomContainer = new FrameLayout(this);
        bottomContainer.setVisibility(View.GONE);

        sidebarContainer = new FrameLayout(this);
        sidebarContainer.setVisibility(View.GONE);

        int statusBarHeight = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            statusBarHeight = getResources().getDimensionPixelSize(resourceId);
        }

        View topVisualSurface = new View(this);
        topVisualSurface.setId(View.generateViewId());
        topVisualSurface.setTag("TOP_VISUAL_SURFACE");
        topVisualSurface.setBackgroundColor(initialColor);

        FrameLayout.LayoutParams surfaceParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                statusBarHeight > 0 ? statusBarHeight : ViewGroup.LayoutParams.WRAP_CONTENT
        );
        topVisualSurface.setLayoutParams(surfaceParams);

        setContentView(rootContainer);
        rootContainer.addView(topVisualSurface);

        ViewCompat.setOnApplyWindowInsetsListener(rootContainer, (v, insets) -> {
            int insetTop = insets.getInsets(
                    WindowInsetsCompat.Type.statusBars()
                            | WindowInsetsCompat.Type.displayCutout()
            ).top;

            if (insetTop > 0) {
                ViewGroup.LayoutParams lp = topVisualSurface.getLayoutParams();
                if (lp.height != insetTop) {
                    lp.height = insetTop;
                    topVisualSurface.setLayoutParams(lp);
                }
            }

            boolean navigationVisible = insets.isVisible(WindowInsetsCompat.Type.navigationBars());

            SystemUI.onNavigationBarVisibilityChanged(MainActivity.this, navigationVisible);

            int imeBottomInset = insets.getInsets(
                    WindowInsetsCompat.Type.ime()
            ).bottom;

            int navigationBottomInset = insets.getInsets(
                    WindowInsetsCompat.Type.navigationBars()
            ).bottom;

            boolean imeVisible = insets.isVisible(
                    WindowInsetsCompat.Type.ime()
            );

            /*
             * The WebView must be resized through its container.
             * Padding changes the inside of WebView but does not change
             * the layout viewport used by position: fixed web elements.
             */
            int effectiveBottomInset = imeVisible
                    ? Math.max(imeBottomInset, navigationBottomInset)
                    : 0;

            if (webViewContainer != null
                    && effectiveBottomInset != appliedImeBottomInset) {

                appliedImeBottomInset = effectiveBottomInset;

                ViewGroup.LayoutParams rawParams =
                        webViewContainer.getLayoutParams();

                if (rawParams instanceof ViewGroup.MarginLayoutParams) {
                    ViewGroup.MarginLayoutParams params =
                            (ViewGroup.MarginLayoutParams) rawParams;

                    params.bottomMargin = effectiveBottomInset;
                    webViewContainer.setLayoutParams(params);
                    webViewContainer.requestLayout();

                    dispatchImeInsetToWebView(effectiveBottomInset);
                }
            }

            if (imeVisible) {
                ensureFocusedWebInputVisible();
            }

            return insets;
        });

        RoyalWebViewHost.whenStartupReady(() -> initializeWebView(savedInstanceState));
    }

    private void initializeWebView(Bundle savedInstanceState) {
        if (isFinishing() || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed())) {
            return;
        }

        RoyalWebViewHost.create(this);
        activeWebView = RoyalWebViewHost.attach(this);
        activeWebView.setVisibility(View.VISIBLE);
        activeWebView.setAlpha(1f);

        webViewContainer.addView(activeWebView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        rootContainer.addView(webViewContainer, 0, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        ViewCompat.requestApplyInsets(rootContainer);

        rootContainer.addView(headerContainer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bottomParams.gravity = android.view.Gravity.BOTTOM;
        rootContainer.addView(bottomContainer, bottomParams);

        rootContainer.addView(sidebarContainer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ViewCompat.setOnApplyWindowInsetsListener(activeWebView, (v, insets) -> {
            int insetTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()
                    | WindowInsetsCompat.Type.displayCutout()).top;

            ViewGroup.LayoutParams lp = v.getLayoutParams();
            if (lp instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) lp;
                if (params.topMargin != insetTop) {
                    params.topMargin = insetTop;
                    v.setLayoutParams(params);
                }
            }

            return insets;
        });

        int initialColor = SystemUI.getDefaultSystemColor(this);
        SystemUI.applyKingMode(this, activeWebView, initialColor);
        SystemUI.applyHeaderColor(this, initialColor);

        engineManager = new WebEngineManager(
                this,
                activeWebView,
                null,
                null,
                () -> splashRemoved = true,
                () -> splashRemoved
        );

        capabilitiesEngine = engineManager.getCapabilitiesHandler();
        RoyalWebViewHost.bindEngineManager(engineManager);

        activeWebView.addJavascriptInterface(
                new RoyalJsBridge(activeWebView, engineManager),
                "RoyalJsBridge"
        );

        engineManager.setSplashStartTime(splashStartTime);
        engineManager.init();

        new com.store.app.navigation.RoyalBackNavigation(
                this,
                activeWebView,
                engineManager,
                null
        ).setupBackNavigation();

        boolean restored = false;

        if (savedInstanceState != null) {
            try {
                activeWebView.restoreState(savedInstanceState);
                activeWebView.setVisibility(View.INVISIBLE);
                webViewReady = true;

                RoyalWebViewHost.revealWhenVisualStateReady(
                        activeWebView,
                        System.nanoTime(),
                        () -> {
                            visualStateReady = true;
                            webViewRevealed = true;
                            splashPageFinished = true;
                            splashVisualStateReady = true;
                            SystemUI.syncStatusBarWithWebEarly(MainActivity.this, activeWebView);
                        }
                );

                restored = true;
                isPageLoaded = true;
                Log.i(TAG, "🔄 WebView restored from Activity state.");
            } catch (Throwable t) {
                Log.w(TAG, "WebView restoreState failed.", t);
            }
        }

        if (!restored) {
            restored = RoyalSessionSentinel.resurrect(activeWebView, this);

            if (restored) {
                activeWebView.setVisibility(View.INVISIBLE);
                webViewReady = true;

                RoyalWebViewHost.revealWhenVisualStateReady(
                        activeWebView,
                        System.nanoTime(),
                        () -> {
                            visualStateReady = true;
                            webViewRevealed = true;
                            splashPageFinished = true;
                            splashVisualStateReady = true;
                            SystemUI.syncStatusBarWithWebEarly(MainActivity.this, activeWebView);
                        }
                );

                isPageLoaded = true;
                Log.i(TAG, "🧊 WebView session resurrected.");
            }
        }

        if (!restored) {
            activeWebView.loadUrl(BuildConfig.CLIENT_URL);
            isPageLoaded = true;
            webViewReady = true;

            if (WebViewFeature.isFeatureSupported(WebViewFeature.VISUAL_STATE_CALLBACK)) {
                RoyalWebViewHost.revealWhenVisualStateReady(
                        activeWebView,
                        System.nanoTime(),
                        () -> {
                            visualStateReady = true;
                            webViewRevealed = true;
                            Log.i(TAG, "🎨 First visual state rendered.");
                            SystemUI.syncStatusBarWithWebEarly(MainActivity.this, activeWebView);
                            SystemUI.scheduleStatusBarSync(MainActivity.this, activeWebView);
                        }
                );
            } else {
                activeWebView.setVisibility(View.VISIBLE);
                visualStateReady = true;
                webViewRevealed = true;
            }
        }

        NetworkMonitor.init(this);

        offlineController = new OfflineUIController(this, activeWebView, engineManager);
        offlineController.init();

        OfflineStateManager.getInstance().bind(activeWebView, offlineController);

        royalAuthManager = new RoyalAuthManager(this, getApplicationContext());

        Intent authIntentToProcess = pendingAuthIntent != null ? pendingAuthIntent : getIntent();
        pendingAuthIntent = null;
        handleInitialAuthIntent(authIntentToProcess);

        if (!NetworkMonitor.isInternetAvailable(this)) {
            offlineController.setOfflineUIVisibility(true);
        }

        loadVIPModules();
    }

    public void notifyPageFinishedForSplash(@NonNull WebView view) {
        if (view != activeWebView || splashPageFinished) return;
        splashPageFinished = true;

        if (WebViewFeature.isFeatureSupported(WebViewFeature.VISUAL_STATE_CALLBACK)) {
            WebViewCompat.postVisualStateCallback(view, System.nanoTime(), requestId -> view.post(() -> {
                if (view != activeWebView || isFinishing()) return;
                splashVisualStateReady = true;
                visualStateReady = true;
                Log.i(TAG, "🎨 Splash visual readiness confirmed after onPageFinished.");
            }));
        } else {
            splashVisualStateReady = true;
            visualStateReady = true;
            Log.i(TAG, "🎨 VisualStateCallback unavailable; using onPageFinished.");
        }
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent event) {
        return super.dispatchTouchEvent(event);
    }

    @Override
    protected void onPause() {
        if (activeWebView != null) activeWebView.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (activeWebView != null) {
            activeWebView.onResume();
            SystemUI.hideSystemBars(this);
            SystemUI.restoreHeaderOnResume(this);
            SystemUI.syncStatusBarWithWebEarly(this, activeWebView);

            if (System.currentTimeMillis() - splashStartTime >= MIN_SPLASH_TIME) {
                SystemUI.scheduleStatusBarSync(this, activeWebView);
            }
        }

        if (offlineController != null) offlineController.onResume();

        if (!isPageLoaded && activeWebView != null && activeWebView.getUrl() == null) {
            activeWebView.loadUrl(BuildConfig.CLIENT_URL);
            isPageLoaded = true;

            if (WebViewFeature.isFeatureSupported(WebViewFeature.VISUAL_STATE_CALLBACK)) {
                RoyalWebViewHost.revealWhenVisualStateReady(
                        activeWebView,
                        System.nanoTime(),
                        () -> {
                            visualStateReady = true;
                            webViewRevealed = true;
                        }
                );
            } else {
                activeWebView.setVisibility(View.VISIBLE);
                visualStateReady = true;
                webViewRevealed = true;
            }
        }
    }

    @Override
    protected void onDestroy() {
        SystemUI.cancelStatusBarSync();
        SystemUI.cancelNavigationBarHide();
        SystemUI.cancelSystemBarsHide();

        if (capabilitiesEngine != null) capabilitiesEngine.destroy();

        mainHandler.removeCallbacksAndMessages(null);

        if (activeWebView != null) activeWebView.stopLoading();

        webViewReady = false;
        visualStateReady = false;
        webViewRevealed = false;

        if (offlineController != null) {
            offlineController.destroy();
            offlineController = null;
        }

        OfflineStateManager.getInstance().unbind();

        if (royalAuthManager != null) {
            royalAuthManager.destroy();
            royalAuthManager = null;
        }

        if (!isChangingConfigurations()) RoyalWebViewHost.detach();

        activeWebView = null;
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (activeWebView != null) {
            try {
                if (androidx.webkit.WebViewFeature.isFeatureSupported(
                        androidx.webkit.WebViewFeature.SAVE_STATE)) {
                    androidx.webkit.WebViewCompat.saveState(activeWebView, outState, 1024 * 1024, false);
                } else {
                    activeWebView.saveState(outState);
                }
            } catch (Throwable t) {
                Log.w(TAG, "WebView state save failed.", t);
            }
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (capabilitiesEngine != null && capabilitiesEngine.handleActivityResult(requestCode, resultCode, data)) {
            return;
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent == null) return;

        setIntent(intent);

        Uri data = intent.getData();
        if (data == null || !RoyalAuthManager.isAuthCallback(data)) return;

        Log.i(TAG, "🔗 OAuth callback received in onNewIntent: " + data.toString());

        if (royalAuthManager == null || activeWebView == null) {
            pendingAuthIntent = new Intent(intent);
            Log.i(TAG, "⏳ OAuth callback queued until WebView/Auth are ready.");
            return;
        }

        royalAuthManager.handleRedirectIntent(intent);
    }

    private void handleInitialAuthIntent(Intent intent) {
        if (intent == null || royalAuthManager == null || activeWebView == null) return;

        Uri data = intent.getData();
        if (data == null || !RoyalAuthManager.isAuthCallback(data)) return;

        Log.i(TAG, "🔗 Initial OAuth callback received: " + data.toString());
        royalAuthManager.handleRedirectIntent(intent);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (capabilitiesEngine != null) {
            capabilitiesEngine.handlePermissionResult(requestCode, permissions, grantResults);
        }
    }

    public void dispatchAuthUrlToWebView(@NonNull String url) {
        runOnUiThread(() -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);

            if (engineManager != null) {
                engineManager.handleAuthReturn(url);
            } else if (activeWebView != null) {
                Log.i(TAG, "🚀 Dispatching OAuth Callback URL directly to WebView: " + url);
                activeWebView.loadUrl(url);
                android.webkit.CookieManager.getInstance().flush();
            } else {
                Log.w(TAG, "⚠️ activeWebView and engineManager are null.");
            }
        });
    }

    @Override
    public void onBackPressed() {
        SystemUI.notifyNavigationUserInteraction(this);
        super.onBackPressed();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            SystemUI.hideSystemBars(this);
            SystemUI.restoreHeaderOnResume(this);

            if (activeWebView != null) {
                SystemUI.syncStatusBarWithWebEarly(this, activeWebView);
                if (System.currentTimeMillis() - splashStartTime >= MIN_SPLASH_TIME) {
                    SystemUI.scheduleStatusBarSync(this, activeWebView);
                }
            }
        }
    }

    private void dispatchImeInsetToWebView(int imeBottomInset) {
        if (activeWebView == null) {
            return;
        }

        String script =
                "window.dispatchEvent(new CustomEvent('nexus:ime-inset', {"
                        + "detail:{bottom:"
                        + imeBottomInset
                        + "}"
                        + "}));";

        activeWebView.post(() ->
                activeWebView.evaluateJavascript(script, null)
        );
    }

    private void ensureFocusedWebInputVisible() {
        if (activeWebView == null) {
            return;
        }

        activeWebView.postDelayed(() -> {
            if (activeWebView == null
                    || isFinishing()
                    || activeWebView.getUrl() == null) {
                return;
            }

            activeWebView.evaluateJavascript(
                    "(function(){"
                            + "var e=document.activeElement;"
                            + "if(!e)return;"
                            + "var tag=(e.tagName||'').toLowerCase();"
                            + "var editable=e.isContentEditable"
                            + "||tag==='input'"
                            + "||tag==='textarea'"
                            + "||tag==='select';"
                            + "if(!editable)return;"
                            + "try{"
                            + "e.scrollIntoView({"
                            + "block:'nearest',"
                            + "inline:'nearest',"
                            + "behavior:'auto'"
                            + "});"
                            + "}catch(_){"
                            + "try{e.scrollIntoView(false);}catch(__){}"
                            + "}"
                            + "})();",
                    null
            );
        }, 160L);
    }

    private void loadVIPModules() {
        try {
            Class<?> moduleInjector = Class.forName("com.store.app.modules.CustomModuleInjector");
            java.lang.reflect.Method injectMethod = moduleInjector.getMethod("inject",
                    AppCompatActivity.class, FrameLayout.class, FrameLayout.class, FrameLayout.class, WebView.class);

            injectMethod.invoke(null, this, headerContainer, bottomContainer, sidebarContainer, activeWebView);
            Log.i(TAG, "👑 VIP Native Modules Loaded Successfully!");
        } catch (ClassNotFoundException e) {
            Log.i(TAG, "ℹ️ Basic Plan Active: Native Modules Slot Empty (Full Screen WebView).");
        } catch (Throwable t) {
            Log.e(TAG, "⚠️ Failed to initialize Native Modules.", t);
        }
    }
            }
