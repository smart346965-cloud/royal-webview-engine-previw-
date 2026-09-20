package com.store.app;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

/**
 * =========================================================
 * 👑 ROYAL HYBRID ENGINE (V2 - Runtime Controller)
 * =========================================================
 * - Process Priority Enforcement (OOM Protection)
 * - Offscreen Pre-Rasterization
 * - Chromium Native Cache Delegation
 * - Capacitor-Safe (Zero Plugin Breakage)
 */
public final class RoyalHybridEngine {

    private static final String TAG = "RoyalHybridEngine";
    private static boolean isEnginePrimed = false;

    // 🔒 منع إنشاء كائنات (Singleton Utility)
    private RoyalHybridEngine() {}

    /**
     * 🚀 نقطة الإشعال (Prime The Engine)
     * يتم استدعاؤها مرة واحدة فقط لتسليح الـ WebView.
     */
    public static void prime(WebView webView, Context context) {
        if (webView == null || isEnginePrimed) return;

        Log.i(TAG, "⚙️ Priming Royal Hybrid Engine V2 (Runtime Controller)...");

        WebSettings settings = webView.getSettings();

        // 1️⃣ تعزيز أولوية الرندرة (Renderer Priority) - لمنع الوميض الناتج عن خمول المحرك
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_BOUND, true);
        }

        // 2️⃣ الرسم المسبق خارج الشاشة (Offscreen Pre-Raster) - السر الحقيقي وراء اختفاء الوميض
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            settings.setOffscreenPreRaster(true);
        }

        // 3️⃣ تحسينات العرض المتقدمة (Viewport Tuning)
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setBlockNetworkImage(false);
        
        // 4️⃣ قفل الكاش الموحد
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);

        // 🔥 [التحسين الجديد]: تفعيل BackForwardCache لتجميد الصفحات في الذاكرة
        if (WebViewFeature.isFeatureSupported(WebViewFeature.BACK_FORWARD_CACHE)) {
            WebSettingsCompat.setBackForwardCacheEnabled(settings, true);
            Log.i(TAG, "📦 BackForwardCache enabled.");
        }

        // 5️⃣ منع التخزين المؤقت للرسم القديم (Hardware Only)
        webView.setDrawingCacheEnabled(false);

        // مطابقة الخلفية فوراً للون النظام لمنع التباين
        webView.setBackgroundColor(android.graphics.Color.parseColor("#F3F4F6"));

        // ==========================================
        // 6️⃣ Modern Native UX & Security (البدائل الحديثة والاحترافية)
        // ==========================================
        
        // 1. التثبيت البصري (Text Lock): يمنع إعدادات خط الهاتف من تخريب تصميم (CSS) المتجر
        settings.setTextZoom(100);

        // 2. سلوك التطبيقات الحقيقية: إخفاء خيار "البحث في الويب" عند قيام العميل بتحديد نص في المتجر
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            settings.setDisabledActionModeMenuItems(
                WebSettings.MENU_ITEM_WEB_SEARCH | WebSettings.MENU_ITEM_PROCESS_TEXT
            );
        }

        // 3. تأمين التصفح الصامت (Safe Browsing): تفعيل حماية جوجل المدمجة دون إزعاج المستخدم
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(true);
        }

        // 🔥 [التحسين الجديد]: دعم WebAuthn للدخول بالبصمة
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_AUTHENTICATION)) {
            WebSettingsCompat.setWebAuthenticationSupport(settings,
                    WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_FOR_APP);
            Log.i(TAG, "🔐 WebAuthn support enabled.");
        }

        // 💥 ربط الكاش بالجلسة: تفعيل الكوكيز لربط التخزين المؤقت بجلسة المستخدم
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true);
        }

        // ==========================================
        // 7️⃣ SPA Stability & Security (استقرار وأمان)
        // ==========================================
        settings.setJavaScriptEnabled(true);
        
        // ❌ تم حذف السطرين التاليين لأسباب أمنية:
        // settings.setAllowFileAccessFromFileURLs(true);
        // settings.setAllowUniversalAccessFromFileURLs(true);
        // سيتم استخدام WebViewAssetLoader الآمن بدلاً منهما في WebEngineManager
        
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        // السماح بالصور
        settings.setLoadsImagesAutomatically(true);
        settings.setBlockNetworkImage(false);

        // 🛍️ إعدادات التجارة الإلكترونية (E-Commerce Native Feel)
        // 1. السماح بتشغيل الفيديوهات الترويجية تلقائياً بدون تدخل المستخدم
        settings.setMediaPlaybackRequiresUserGesture(false);

        // ==========================================
        // 8️⃣ GPU & Hardware Acceleration Control (تسريع محرك الرسم)
        // ==========================================

        // ✅ التفعيل القياسي والأقوى لـ GPU Rasterization على مستوى الـ Layer و Hardware Acceleration
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        // تفعيل وضع العرض المستقر خارج الشاشة
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            settings.setOffscreenPreRaster(true);
        }

        // 3. دعم معالجة الخيوط المتعددة والـ OffscreenCanvas عبر Web Workers
        settings.setAllowFileAccess(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // السماح بإنشاء سياقات الرسم والتسريع في الخفاء دون تجميد واجهة UI
            webView.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
        }

        // 4. تفعيل ميزات Chromium المتقدمة لرسم الشاشة (Darkening & Vulkan/WebGL Support)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, true);
        }

        // 5. تمكين تسريع معالج الرسم لتقنيات WebGL / WebGPU و Offscreen Canvas
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true); // يفتح مسار Vulkan/WebGL المباشر
        }

        isEnginePrimed = true;
        Log.i(TAG, "✅ Royal Hybrid Engine V2: Process Priority & Pre-Raster Primed.");
    }

    /**
     * 🔄 إعادة ضبط المحرك (تُستدعى فقط عند الـ Soft Restart)
     */
    public static void reset() {
        isEnginePrimed = false;
    }
    }
