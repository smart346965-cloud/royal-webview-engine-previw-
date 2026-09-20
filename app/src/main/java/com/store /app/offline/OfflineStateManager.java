package com.store.app.offline;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.WebView;

import com.store.app.MainActivity;
import com.store.app.NetworkMonitor;
import com.store.app.RoyalNetworkEngine;

/**
 * 👑 OfflineStateManager - يدير حالة الأوفلاين بشكل مركزي
 * 
 * - يحتفظ بحالة الصفحة (isPageValid, isOnErrorPage)
 * - يستمع لتغيرات الشبكة وينفذ منطق NetErrorAutoReloader
 * - يتحكم في واجهات الأوفلاين عبر OfflineUIController
 */
public class OfflineStateManager {

    private static final String TAG = "OfflineStateManager";

    // ==========================================
    // 🔥 المتغيرات
    // ==========================================

    private static OfflineStateManager instance;

    // حالة الأوفلاين
    private boolean isOnErrorPage = false;
    private boolean isPageValid = false;
    private String lastFailedUrl = null;
    private boolean isNetworkAvailable = true;
    private boolean isOfflineBarVisible = false;

    // 👑 الرابط الذي فشل تحميله وسنفتحه عند عودة الشبكة
    private String pendingUrl = null;

    // مراجع للتحكم
    private WebView webView;
    private OfflineUIController uiController;

    // معالج الخيط الرئيسي
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ==========================================
    // 🔒 Singleton
    // ==========================================

    public static synchronized OfflineStateManager getInstance() {
        if (instance == null) {
            instance = new OfflineStateManager();
        }
        return instance;
    }

    private OfflineStateManager() {}

    // ==========================================
    // 🔗 الربط مع المكونات الأخرى
    // ==========================================

    public void bind(WebView webView, OfflineUIController uiController) {

        this.webView = webView;
        this.uiController = uiController;

        if (this.pendingUrl == null) {
            this.pendingUrl = com.store.app.BuildConfig.CLIENT_URL;
        }

        isNetworkAvailable =
                NetworkMonitor.isInternetAvailable(webView.getContext());

        // حماية من تسجيل مستمع مكرر
        NetworkMonitor.setListener(connected -> {
            // تفادي استدعاءات متزامنة
            mainHandler.post(() -> {
                isNetworkAvailable = connected;
                handleNetworkChange(connected);
            });
        });

        // تشغيل واجهة الأوفلاين فوراً عند الإقلاع بدون شبكة
        if (!isNetworkAvailable) {

            isPageValid = false;
            isOnErrorPage = false;

            if (uiController != null) {
                uiController.setOfflineUIVisibility(true);
            }

            Log.i(TAG, "📴 Offline startup detected. Native Offline UI shown.");
        }

        Log.i(TAG, "🔗 Bound to WebView and UIController");
    }

    // ==========================================
    // 📡 معالجة تغيرات الشبكة (معدلة)
    // ==========================================

    private void handleNetworkChange(boolean connected) {
        if (webView == null) return;

        // فقدان الشبكة
        if (!connected) {
            isNetworkAvailable = false;

            if (isPageValid && !isOnErrorPage) {
                if (uiController != null) uiController.setOfflineBarVisibility(true);
                Log.i(TAG, "📴 Network lost. Keeping current valid page and showing offline bar.");
            } else {
                if (uiController != null) uiController.setOfflineUIVisibility(true);
                Log.i(TAG, "📴 Network lost with no valid page. Showing Native Offline UI.");
            }
            return;
        }

        // الإنترنت عاد
        isNetworkAvailable = true;

        // تأخير قصير للسماح لنظام الشبكة بالاستقرار
        mainHandler.postDelayed(() -> {
            if (webView == null) return;
            if (!NetworkMonitor.isInternetAvailable(webView.getContext())) return;

            // الحالة 1: الصفحة صالحة → لا تعيد تحميلها، بل حدث واحد للصفحة + تحديث الشريط بصرياً
            if (isPageValid && !isOnErrorPage && webView.getUrl() != null && !webView.getUrl().equals("about:blank")) {
                Log.i(TAG, "🌐 Internet restored. Page valid — notifying page and updating bar.");
                if (uiController != null) uiController.showOnlineBarTransition(); // دالة جديدة في UIController
                // إرسال حدث JS واحد فقط
                webView.post(() -> webView.evaluateJavascript("window.dispatchEvent(new Event('online'));", null));
                return;
            }

            // الحالة 2: الصفحة غير صالحة → إعادة تحميل الرابط المحفوظ بسلاسة
            String urlToLoad = pendingUrl != null ? pendingUrl : com.store.app.BuildConfig.CLIENT_URL;
            isOnErrorPage = false;
            isPageValid = false;

            Log.i(TAG, "🚀 Internet restored. Loading: " + urlToLoad);
            if (uiController != null) uiController.showLoadingOverlay(); // دالة جديدة لعرض progress فوق الويب فيو
            webView.post(() -> webView.loadUrl(urlToLoad));
        }, 300);
    }

    // ==========================================
    // 🔧 دوال تحديث حالة الصفحة
    // ==========================================

    public void setPageValid(boolean valid) {

        this.isPageValid = valid;

        if (valid) {
            this.isOnErrorPage = false;
        }

        Log.d(TAG, "📄 Page valid set to: " + valid);
    }

    public void setErrorPage(boolean error, String url) {

        this.isOnErrorPage = error;
        this.isPageValid = !error;

        if (error && url != null) {
            // لا تحفظ الرابط إذا كنا أوفلاين (لتفادي تنفيذ النقرات لاحقاً)
            if (webView != null && NetworkMonitor.isInternetAvailable(webView.getContext())) {
                this.pendingUrl = url;
                this.lastFailedUrl = url;
            }
        }

        Log.d(TAG,
                "🛡️ Error tracking: "
                        + error
                        + " for URL: "
                        + url);
    }

    public void setOfflineBarVisible(boolean visible) {
        this.isOfflineBarVisible = visible;
    }

    // ==========================================
    // 🔍 دوال الاستعلام عن الحالة
    // ==========================================

    public boolean isOnErrorPage() {
        return isOnErrorPage;
    }

    public boolean isPageValid() {
        return isPageValid;
    }

    public String getLastFailedUrl() {
        return lastFailedUrl;
    }

    public boolean isNetworkAvailable() {
        return isNetworkAvailable;
    }

    public boolean isOfflineBarVisible() {
        return isOfflineBarVisible;
    }

    // ==========================================
    // 🔓 إلغاء الربط
    // ==========================================

    public void unbind() {
        this.webView = null;
        this.uiController = null;
        // إزالة المستمع من NetworkMonitor
        NetworkMonitor.setListener(null);
        Log.i(TAG, "🔓 Unbound from WebView and UIController");
    }

    // ==========================================
    // 💥 إشعار محاولة النقر الفاشلة
    // ==========================================

    /**
     * [إضافة جراحية في OfflineStateManager.java]
     * يُستدعى عند محاولة النقر على رابط أثناء انقطاع الشبكة
     * لإعلام المستخدم بهز الشريط السفلي
     */
    public void notifyOfflineClickAttempt() {
        if (uiController != null) {
            // هز الشريط السفلي لجذب انتباه العميل دون تجميد الصفحة
            uiController.shakeOfflineBar();
        }
    }

    // ==========================================
    // 🚀 إخطار جاهزية الصفحة (الإضافة الجديدة)
    // ==========================================

    public void notifyPageReadyToHide() {
        mainHandler.post(() -> {
            if (!isPageValid || isOnErrorPage) return;
            if (uiController != null) {
                uiController.hideLoadingOverlay(); // دالة جديدة
                uiController.forceHideAllInternal();
            }
            Log.i(TAG, "✅ Valid page ready. Offline UI hidden.");
        });
    }
                }
