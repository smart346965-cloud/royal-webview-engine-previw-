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

/**
 * 👑 MainActivity - النواة الأساسية لإدارة محرك الويب المخصص
 * تم تطهيرها بالكامل من مخلفات الـ TWA لتعمل بأقصى سرعة استجابة (Zero-friction)
 *
 * 🚀 تم تحسينها بأعلى معايير الأداء من وثائق كروميوم:
 * - Time-Based Memory Purge (تفريغ الذاكرة الاستباقي)
 * - shouldInterceptRequest Short Circuit (تحسين اعتراض الطلبات)
 * - Renderer Importance API (أولوية معالج العرض)
 * - onTrimMemory Optimization (تحسين استجابة ضغط الذ memory)
 * - saveState/restoreState (تسريع حفظ واستعادة الحالة)
 * - Prefetch Native Library (تحميل المكتبات الأصلية مسبقاً)
 * - Threading Optimization (تحسين إدارة الخيوط)
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "RoyalMainActivity";

    /*
     * Splash readiness is based on:
     * 1. A protected minimum display time.
     * 2. A valid WebView page completion signal.
     * 3. A visual-state callback after onPageFinished.
     *
     * The maximum timeout prevents an infinite splash when the
     * remote page or WebView renderer fails.
     */
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

    // 🔥 مدير واجهات الأوفلاين
    private OfflineUIController offlineController;

    // 🔥 مدير المصادقة والدفع
    private RoyalAuthManager royalAuthManager;

    /*
     * OAuth callback may arrive through onNewIntent() before
     * RoyalAuthManager and the WebView are ready.
     */
    private Intent pendingAuthIntent;

    private FrameLayout rootContainer;

    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    // 👑 الحاويات النيتيف الثلاث المخصصة للباقة الفاخرة (VIP Module Slots)
    private FrameLayout headerContainer;
    private FrameLayout bottomContainer;
    private FrameLayout sidebarContainer;
    private FrameLayout webViewContainer;

    // =========================================================
    // 🚀 دورة الحياة الأساسية
    // =========================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        final SplashScreen splashScreen =
                SplashScreen.installSplashScreen(this);

        splashStartTime = System.currentTimeMillis();

        /*
         * Splash policy:
         *
         * - It must remain visible for at least three seconds.
         * - After three seconds, it may exit only after the WebView
         *   reports a valid page completion followed by a visual-state
         *   callback.
         * - The maximum timeout prevents an infinite splash if the
         *   remote page never completes.
         */
        splashScreen.setKeepOnScreenCondition(() -> {
            long elapsed = System.currentTimeMillis() - splashStartTime;

            if (elapsed < MIN_SPLASH_TIME) {
                return true;
            }

            if (splashPageFinished && splashVisualStateReady) {
                return false;
            }

            return elapsed < MAX_SPLASH_TIME;
        });

        splashScreen.setOnExitAnimationListener(
                splashScreenView -> {

                    splashScreenView.getView()
                            .animate()
                            .alpha(0f)
                            .setDuration(500L)
                            .withEndAction(
                                    splashScreenView::remove
                            )
                            .start();
                }
        );

        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        // 👑 تحديد اللون الأولي للنافذة تلقائياً حسب وضع النظام (النهاري #FFFFFF / الليلي #121212)
        int initialColor = SystemUI.getDefaultSystemColor(this);

        getWindow().setBackgroundDrawable(
                new ColorDrawable(initialColor)
        );

        try {
            RoyalPanopticon.startAwareness();

            Log.i(
                    TAG,
                    "RoyalPanopticon Engine: Active and running in background."
            );

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Failed to initialize RoyalPanopticon.",
                    e
            );
        }

        // 👑 إنشاء الجذر والحاويات البرمجية الثلاث
        rootContainer = new FrameLayout(this);
        rootContainer.setBackgroundColor(initialColor);

        // 1. حاوية الـ WebView الرئيسية
        webViewContainer = new FrameLayout(this);

        // 2. حاوية الهيدر العلوي (افتراضياً مخفية View.GONE)
        headerContainer = new FrameLayout(this);
        headerContainer.setVisibility(View.GONE);

        // 3. حاوية القائمة السفلية (افتراضياً مخفية View.GONE)
        bottomContainer = new FrameLayout(this);
        bottomContainer.setVisibility(View.GONE);

        // 4. حاوية القائمة الجانبية (افتراضياً مخفية View.GONE)
        sidebarContainer = new FrameLayout(this);
        sidebarContainer.setVisibility(View.GONE);

        // 👑 حساب الارتفاع الناتيفي لشريط الحالة صريحاً فوراً بدون انتظر
        int statusBarHeight = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            statusBarHeight = getResources().getDimensionPixelSize(resourceId);
        }

        // 👑 إنشاء الدرع الناتيف الصلب المطابق تماماً للون السبلاش
        View topVisualSurface = new View(this);
        topVisualSurface.setId(View.generateViewId());
        topVisualSurface.setTag("TOP_VISUAL_SURFACE");
        topVisualSurface.setBackgroundColor(initialColor);

        // إضافة العنصر فوراً بارتفاعه الصريح للدرع
        FrameLayout.LayoutParams surfaceParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 
                statusBarHeight > 0 ? statusBarHeight : ViewGroup.LayoutParams.WRAP_CONTENT
        );
        topVisualSurface.setLayoutParams(surfaceParams);

        setContentView(rootContainer);
        rootContainer.addView(topVisualSurface);

        // تحديث الارتفاع بدقة متناهية عند حساب النوتش دون إخفاء العنصر في Frame 0
        ViewCompat.setOnApplyWindowInsetsListener(rootContainer, (v, insets) -> {

            int insetTop = insets.getInsets(
                    WindowInsetsCompat.Type.statusBars()
                            | WindowInsetsCompat.Type.displayCutout()
            ).top;

            if (insetTop > 0) {

                ViewGroup.LayoutParams lp =
                        topVisualSurface.getLayoutParams();

                if (lp.height != insetTop) {

                    lp.height = insetTop;

                    topVisualSurface.setLayoutParams(lp);
                }
            }

            // 👑 مراقبة Navigation Bar
            boolean navigationVisible =
                    insets.isVisible(
                            WindowInsetsCompat.Type.navigationBars()
                    );

            SystemUI.onNavigationBarVisibilityChanged(
                    MainActivity.this,
                    navigationVisible
            );

            return insets;
        });

        /*
         * Chromium startup barrier.
         *
         * RoyalApplication بدأ WebView startup
         * منذ لحظة إنشاء الـ process.
         */
        RoyalWebViewHost.whenStartupReady(
                () -> initializeWebView(savedInstanceState)
        );
    }

    private void initializeWebView(Bundle savedInstanceState) {

        if (isFinishing() ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1
                        && isDestroyed())) {
            return;
        }

        /*
         * الآن فقط يسمح لـ Host بإنشاء WebView.
         */
        RoyalWebViewHost.create(this);

        activeWebView =
                RoyalWebViewHost.attach(this);

        activeWebView.setVisibility(View.VISIBLE);
        activeWebView.setAlpha(1f);

        // 👑 إضافة الـ WebView داخل حاويته المخصصة لعدم التأثير على الهيدر والفوتر
        webViewContainer.addView(
                activeWebView,
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                )
        );

        // تنظيم إضافة الحاويات داخل الـ rootContainer بالترتيب الصحيح
        rootContainer.addView(webViewContainer, 0, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        rootContainer.addView(headerContainer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        
        FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bottomParams.gravity = android.view.Gravity.BOTTOM;
        rootContainer.addView(bottomContainer, bottomParams);

        rootContainer.addView(sidebarContainer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 👑 تقليص Viewport المحرك أصلياً عبر Top Margin لحماية عناصر position: fixed
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

        /*
         * System UI بعد وجود WebView.
         */
        int initialColor = SystemUI.getDefaultSystemColor(this);
        SystemUI.applyKingMode(
                this,
                activeWebView,
                initialColor
        );

        // 👑 توحيد مصدر لون الخلفية والأيقونات من الدالة الموحدة لمنع التضارب
        SystemUI.applyHeaderColor(this, initialColor);

        /*
         * WebEngineManager الآن فقط.
         * لأنه يحتاج WebView حقيقي.
         *
         * تمرير null للـ splashOverlay و progressBar لأننا نعتمد على System Splash.
         */
        engineManager = new WebEngineManager(
                this,
                activeWebView,
                null,   // splashOverlay
                null,   // progressBar
                () -> splashRemoved = true,
                () -> splashRemoved
        );

        // ✅ ربط المحرك
        capabilitiesEngine = engineManager.getCapabilitiesHandler();

        // 🔗 ربط الـ Bridge بعد اكتمال WebEngineManager
        RoyalWebViewHost.bindEngineManager(engineManager);

        // 🔗 تأكيد ربط واجهة الجافاسكريبت بالـ WebView
        activeWebView.addJavascriptInterface(
                new RoyalJsBridge(activeWebView, engineManager),
                "RoyalJsBridge"
        );

        engineManager.setSplashStartTime(
                splashStartTime
        );

        engineManager.init();

        /*
         * Navigation manager كان يأخذ engineManager = null
         * في النسخة القديمة.
         *
         * الآن يأخذ object حقيقي.
         */
        new com.store.app.navigation.RoyalBackNavigation(
                this,
                activeWebView,
                engineManager,
                null // progressBar غير مستخدم
        ).setupBackNavigation();

        /*
         * =====================================================
         * استعادة / تحميل الصفحة
         * =====================================================
         *
         * يوجد مالك واحد فقط للـ navigation.
         */

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

                            // 👑 مزامنة خفية بعد الاستعادة (بدون لمس الأيقونات مباشرة)
                            SystemUI.syncStatusBarWithWebEarly(
                                    MainActivity.this,
                                    activeWebView
                            );
                        }
                );

                restored = true;
                isPageLoaded = true;

                Log.i(
                        TAG,
                        "🔄 WebView restored from Activity state."
                );

            } catch (Throwable t) {

                Log.w(
                        TAG,
                        "WebView restoreState failed.",
                        t
                );
            }
        }

        if (!restored) {

            restored = RoyalSessionSentinel.resurrect(
                    activeWebView,
                    this
            );

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

                            // 👑 مزامنة خفية بعد الإحياء
                            SystemUI.syncStatusBarWithWebEarly(
                                    MainActivity.this,
                                    activeWebView
                            );
                        }
                );

                isPageLoaded = true;

                Log.i(
                        TAG,
                        "🧊 WebView session resurrected."
                );
            }
        }

        if (!restored) {

            activeWebView.loadUrl(
                    BuildConfig.CLIENT_URL
            );

            isPageLoaded = true;

            webViewReady = true;

            if (WebViewFeature.isFeatureSupported(
                    WebViewFeature.VISUAL_STATE_CALLBACK
            )) {

                RoyalWebViewHost.revealWhenVisualStateReady(
                        activeWebView,
                        System.nanoTime(),
                        () -> {

                            visualStateReady = true;
                            webViewRevealed = true;

                            /*
                             * Do not release the Android Splash here.
                             * Splash release is controlled only by:
                             * onPageFinished -> notifyPageFinishedForSplash()
                             * -> VisualStateCallback.
                             */

                            Log.i(
                                    TAG,
                                    "🎨 First visual state rendered."
                            );

                            // 👑 المزامنة الخفية أولاً (لون الشريط فقط دون لمس الأيقونات)
                            // تمنع الومض بين السبلاش الأبيض والهيدر
                            SystemUI.syncStatusBarWithWebEarly(
                                    MainActivity.this,
                                    activeWebView
                            );

                            // 👑 ثم جدولة المزامنة الكاملة (تحدّث الأيقونات أيضاً)
                            // بمهلة موسّعة لصفحات SPA
                            SystemUI.scheduleStatusBarSync(
                                    MainActivity.this,
                                    activeWebView
                            );
                        }
                );

            } else {

                activeWebView.setVisibility(View.VISIBLE);

                visualStateReady = true;
                webViewRevealed = true;
            }
        }

        /*
         * Offline
         */
        NetworkMonitor.init(this);

        offlineController =
                new OfflineUIController(
                        this,
                        activeWebView,
                        engineManager
                );

        offlineController.init();

        OfflineStateManager
                .getInstance()
                .bind(
                        activeWebView,
                        offlineController
                );

        /*
         * Auth / Payment
         */
        royalAuthManager =
                new RoyalAuthManager(
                        this,
                        getApplicationContext()
                );

        // ✅ معالجة Intent الأولي أو Intent المؤجل للـ Auth
        Intent authIntentToProcess = pendingAuthIntent != null
                ? pendingAuthIntent
                : getIntent();

        pendingAuthIntent = null;

        handleInitialAuthIntent(authIntentToProcess);

        if (!NetworkMonitor.isInternetAvailable(this)) {

            offlineController.setOfflineUIVisibility(
                    true
            );
        }

        // 👑 فحص وتفعيل الموديولات للباقة الفاخرة (إن وجدت)
        loadVIPModules();
    }

    /**
     * Called only after WebEngineManager receives a valid onPageFinished event.
     *
     * onPageFinished alone is not considered visually ready. We request a
     * second visual-state confirmation to ensure that Chromium has committed
     * a drawable frame before allowing the Android splash to leave.
     */
    public void notifyPageFinishedForSplash(@NonNull WebView view) {
        if (view != activeWebView || splashPageFinished) {
            return;
        }

        splashPageFinished = true;

        if (WebViewFeature.isFeatureSupported(
                WebViewFeature.VISUAL_STATE_CALLBACK
        )) {
            WebViewCompat.postVisualStateCallback(
                    view,
                    System.nanoTime(),
                    requestId -> view.post(() -> {
                        if (view != activeWebView || isFinishing()) {
                            return;
                        }

                        splashVisualStateReady = true;
                        visualStateReady = true;

                        Log.i(
                                TAG,
                                "🎨 Splash visual readiness confirmed after onPageFinished."
                        );
                    })
            );
        } else {
            /*
             * On old WebView implementations, onPageFinished is the strongest
             * available signal.
             */
            splashVisualStateReady = true;
            visualStateReady = true;

            Log.i(
                    TAG,
                    "🎨 VisualStateCallback unavailable; using onPageFinished."
            );
        }
    }

    // =========================================================
    // 🚀 Touch Interaction (تمرير اللمس طبيعياً للـ WebView دون الاستجابة للأشرطة)
    // =========================================================

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent event) {
        // تمرير الأحداث للواجهة دون استدعاء showSystemBarsOnInteraction
        return super.dispatchTouchEvent(event);
    }

    // =========================================================
    // 🔄 دورة الحياة المحدّثة
    // =========================================================

    @Override
    protected void onPause() {

        if (activeWebView != null) {
            activeWebView.onPause();
        }

        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (activeWebView != null) {
            activeWebView.onResume();

            // 👑 إخفاء وتثبيت وضع الأشرطة مباشرة دون تضارب
            SystemUI.hideSystemBars(this);
            SystemUI.restoreHeaderOnResume(this);

            // 👑 المزامنة الخفية ثم الكاملة بمهلة موسّعة لصفحات SPA
            SystemUI.syncStatusBarWithWebEarly(
                    this,
                    activeWebView
            );

            if (System.currentTimeMillis() - splashStartTime >= MIN_SPLASH_TIME) {
                SystemUI.scheduleStatusBarSync(
                        this,
                        activeWebView
                );
            }
        }

        if (offlineController != null) {
            offlineController.onResume();
        }

        if (!isPageLoaded
                && activeWebView != null
                && activeWebView.getUrl() == null) {

            activeWebView.loadUrl(
                    BuildConfig.CLIENT_URL
            );

            isPageLoaded = true;

            if (WebViewFeature.isFeatureSupported(
                    WebViewFeature.VISUAL_STATE_CALLBACK
            )) {

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

        // ✅ إضافة التدمير للمحرك كأولوية
        if (capabilitiesEngine != null) {
            capabilitiesEngine.destroy();
        }

        mainHandler.removeCallbacksAndMessages(null);

        if (activeWebView != null) {
            activeWebView.stopLoading();
        }

        webViewReady = false;
        visualStateReady = false;
        webViewRevealed = false;

        if (offlineController != null) {
            offlineController.destroy();
            offlineController = null;
        }

        OfflineStateManager
                .getInstance()
                .unbind();

        if (royalAuthManager != null) {
            royalAuthManager.destroy();
            royalAuthManager = null;
        }

        if (!isChangingConfigurations()) {
            RoyalWebViewHost.detach();
        }

        activeWebView = null;

        super.onDestroy();
    }

    // =========================================================
    // 💾 حفظ واستعادة الحالة
    // =========================================================

    @Override
    protected void onSaveInstanceState(Bundle outState) {

        if (activeWebView != null) {

            try {

                if (androidx.webkit.WebViewFeature.isFeatureSupported(
                        androidx.webkit.WebViewFeature.SAVE_STATE)) {

                    androidx.webkit.WebViewCompat.saveState(
                            activeWebView,
                            outState,
                            1024 * 1024,
                            false
                    );

                } else {

                    activeWebView.saveState(
                            outState
                    );
                }

            } catch (Throwable t) {

                Log.w(
                        TAG,
                        "WebView state save failed.",
                        t
                );
            }
        }

        super.onSaveInstanceState(outState);
    }

    // لا نستخدم onRestoreInstanceState، لأن الاستعادة تتم في initializeWebView.

    // =========================================================
    // 🔄 نتائج النشاطات والصلاحيات (محسّن)
    // =========================================================

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // ✅ معالجة النتائج واختيار الملفات مباشرة عبر محرك القدرات
        if (capabilitiesEngine != null && capabilitiesEngine.handleActivityResult(requestCode, resultCode, data)) {
            return;
        }
    }

    // =========================================================
    // 🔗 معالجة الروابط العميقة (Deep Links / OAuth Callbacks)
    // =========================================================

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if (intent == null) {
            return;
        }

        setIntent(intent);

        Uri data = intent.getData();

        if (data == null || !RoyalAuthManager.isAuthCallback(data)) {
            return;
        }

        Log.i(
                TAG,
                "🔗 OAuth callback received in onNewIntent: "
                        + data.toString()
        );

        /*
         * With singleTask, the callback can arrive while the Activity exists
         * but before the WebView/Auth manager has completed initialization.
         * Preserve the exact Intent instead of losing the callback.
         */
        if (royalAuthManager == null || activeWebView == null) {
            pendingAuthIntent = new Intent(intent);
            Log.i(
                    TAG,
                    "⏳ OAuth callback queued until WebView/Auth are ready."
            );
            return;
        }

        royalAuthManager.handleRedirectIntent(intent);
    }

    /**
     * معالجة Intent الأولي للـ Auth عند بدء التطبيق
     */
    private void handleInitialAuthIntent(Intent intent) {

        if (intent == null || royalAuthManager == null || activeWebView == null) {
            return;
        }

        Uri data = intent.getData();

        if (data == null || !RoyalAuthManager.isAuthCallback(data)) {
            return;
        }

        Log.i(
                TAG,
                "🔗 Initial OAuth callback received: "
                        + data.toString()
        );

        royalAuthManager.handleRedirectIntent(intent);
    }

    // =========================================================
    // 🔐 صلاحيات التطبيق (مدمجة مع محرك القدرات)
    // =========================================================

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        // ✅ التعديل الجراحي الموصى به
        if (capabilitiesEngine != null) {
            capabilitiesEngine.handlePermissionResult(requestCode, permissions, grantResults);
        }
    }

    // =========================================================
    // 👑 دالة التوزيع المباشر لرابط العودة واستلام الـ Session Cookie فوراً
    // =========================================================

    /**
     * 👑 دالة التوزيع المباشر لرابط العودة، إغلاق الـ Custom Tab وتأكيد الـ Session Cookie
     */
    public void dispatchAuthUrlToWebView(@NonNull String url) {
        runOnUiThread(() -> {
            // 1. تقديم MainActivity للواجهة فوراً لإغلاق الـ Custom Tab
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);

            // 2. معالجة وتمرير الرابط الحقيقي الحاوي على code=
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

    // =========================================================
    // 👑 دالة معالجة زر الرجوع وإعادة ضبط مؤقت شريط التنقل السفلي
    // =========================================================
    @Override
    public void onBackPressed() {
        // 👑 إعادة ضبط مؤقت شريط التنقل السفلي عند الضغط على زر الرجوع
        SystemUI.notifyNavigationUserInteraction(this);
        super.onBackPressed();
    }

    // =========================================================
    // 👑 المزامنة الناتيفية القاطعة لأيقونات شريط الحالة عند استعادة تركيز النافذة
    // =========================================================
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            // 👑 إعادة تطبيق الإخفاء الموحد والأنيق بدلاً من refreshNavigationBar المسببة للومضة
            SystemUI.hideSystemBars(this);
            SystemUI.restoreHeaderOnResume(this);

            // 👑 المزامنة الخفية الفورية (لون فقط) ثم الكاملة بمهلة موسّعة
            if (activeWebView != null) {
                SystemUI.syncStatusBarWithWebEarly(
                        this,
                        activeWebView
                );

                if (System.currentTimeMillis() - splashStartTime >= MIN_SPLASH_TIME) {
                    SystemUI.scheduleStatusBarSync(
                            this,
                            activeWebView
                    );
                }
            }
        }
    }

    // =========================================================
    // 👑 دالة تفعيل الموديولات النيتيف الفاخرة آمنة تماماً (Zero-Crash)
    // =========================================================
    private void loadVIPModules() {
        try {
            // محاولة استدعاء حقن الموديول النيتيف فقط إذا تم حقنه أثناء البناء للباقة الفاخرة
            Class<?> moduleInjector = Class.forName("com.store.app.modules.CustomModuleInjector");
            java.lang.reflect.Method injectMethod = moduleInjector.getMethod("inject", 
                    AppCompatActivity.class, FrameLayout.class, FrameLayout.class, FrameLayout.class, WebView.class);
            
            injectMethod.invoke(null, this, headerContainer, bottomContainer, sidebarContainer, activeWebView);
            Log.i(TAG, "👑 VIP Native Modules Loaded Successfully!");
        } catch (ClassNotFoundException e) {
            // في الباقة العادية: الكلاس غير موجود، تظل الحاويات GONE وتعمل WebView بكامل الشاشة بأقصى أداء!
            Log.i(TAG, "ℹ️ Basic Plan Active: Native Modules Slot Empty (Full Screen WebView).");
        } catch (Throwable t) {
            Log.e(TAG, "⚠️ Failed to initialize Native Modules.", t);
        }
    }
                                }
