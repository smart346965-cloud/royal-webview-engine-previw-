package com.store.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.webkit.CookieManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 👑 RoyalAuthManager - Nexus Universal Auth Dispatcher
 *
 * النسخة الخفيفة المجمعة والمطهرة من القيود الشديدة.
 * وظيفته الأساسية:
 * 1. استلام وتوجيه روابط العودة من المصادقة (App Links / Deep Links).
 * 2. التثبت المرن من معلمات النجاح (code, state, token, callback, auth).
 * 3. تسليم الرابط المكتمل إلى الـ WebView لتشغيل الـ Session Cookie فوراً.
 */
public final class RoyalAuthManager {

    private static final String TAG = "RoyalAuthManager";

    private final Activity activity;
    private final Context context;
    private final AtomicBoolean destroyed = new AtomicBoolean(false);

    public RoyalAuthManager(@NonNull Activity activity, @NonNull Context context) {
        this.activity = activity;
        this.context = context.getApplicationContext();
        Log.i(TAG, "👑 RoyalAuthManager (Nexus Dispatcher) initialized");
    }

    /**
     * التحقق المرن والذكاء الذاتي مما إذا كان الـ URI يمثل رابط عودة لمصادقة
     */
    public static boolean isAuthCallback(@Nullable Uri uri) {
        if (uri == null) return false;

        String uriString = uri.toString().toLowerCase();
        String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase() : "";

        // 1. فحص الكلمات المفتاحية لمسارات المصادقة العائدة
        boolean containsAuthKeywords = uriString.contains("code=")
                || uriString.contains("state=")
                || uriString.contains("token=")
                || uriString.contains("callback")
                || uriString.contains("/auth")
                || uriString.contains("/oauth")
                || uriString.contains("/login");

        // 2. فحص الـ Schemes المسموحة (HTTPS, HTTP, Custom Scheme)
        boolean isValidScheme = "https".equals(scheme) 
                || "http".equals(scheme) 
                || "com.store.app.auth".equals(scheme);

        return isValidScheme && containsAuthKeywords;
    }

    /**
     * استقبال Intent العودة وتمريره إلى الـ WebView مع التثبيت النيتيف الدائم للجلسة
     */
    public boolean handleRedirectIntent(@Nullable Intent intent) {
        if (destroyed.get() || intent == null) {
            return false;
        }

        Uri data = intent.getData();
        if (data == null) {
            return false;
        }

        Log.i(TAG, "🔗 Incoming Redirect Intent: " + data.toString());

        if (isAuthCallback(data)) {
            Log.i(TAG, "🎯 Valid OAuth Callback detected -> Dispatching to WebView");
            
            String finalUrl = data.toString();

            // إذا كان الرابط يتبع المخطط المخصص، نعيد بناء رابط الـ HTTPS مع الحفاظ على التوكن ونقل الكوكي للقرص الصلب
            if (data.getScheme() != null && "com.store.app.auth".equalsIgnoreCase(data.getScheme())) {
                String targetParam = data.getQueryParameter("target");
                String redirectParam = data.getQueryParameter("redirect_uri");
                String tokenParam = data.getQueryParameter("code");
                if (tokenParam == null) tokenParam = data.getQueryParameter("id_token");

                String baseTarget = (targetParam != null && targetParam.startsWith("http")) ? targetParam 
                                  : (redirectParam != null && redirectParam.startsWith("http")) ? redirectParam : null;

                if (baseTarget != null) {
                    Uri.Builder builder = Uri.parse(baseTarget).buildUpon();
                    for (String key : data.getQueryParameterNames()) {
                        if (!"target".equalsIgnoreCase(key) && !"redirect_uri".equalsIgnoreCase(key)) {
                            for (String val : data.getQueryParameters(key)) {
                                builder.appendQueryParameter(key, val);
                            }
                        }
                    }
                    finalUrl = builder.build().toString();

                    // 🚀 زرع وتثبيت الكوكي نيتيفياً فوراً على القرص الصلب لجميع البيئات
                    if (tokenParam != null) {
                        try {
                            CookieManager cookieManager = CookieManager.getInstance();
                            cookieManager.setAcceptCookie(true);
                            cookieManager.setCookie(baseTarget, "nexus_session=" + tokenParam + "; Path=/; Max-Age=86400; Secure; SameSite=Lax");
                            cookieManager.flush();
                            Log.i(TAG, "💾 Native Session Cookie Injected & Flushed for: " + baseTarget);
                        } catch (Exception e) {
                            Log.w(TAG, "Failed to inject native cookie", e);
                        }
                    }

                    Log.i(TAG, "👑 Preserved Parameters in Final Auth URL -> " + finalUrl);
                }
            }

            if (activity instanceof MainActivity) {
                MainActivity mainActivity = (MainActivity) activity;
                mainActivity.dispatchAuthUrlToWebView(finalUrl);
                return true;
            }
        } else {
            Log.d(TAG, "ℹ️ Intent received is not an OAuth callback.");
        }

        return false;
    }

    /**
     * Alias للتوافقية مع التجميعات القديمة
     */
    public boolean handleRedirect(@Nullable Intent intent) {
        return handleRedirectIntent(intent);
    }

    /**
     * تنظيف الموارد عند التدمير
     */
    public void destroy() {
        if (destroyed.compareAndSet(false, true)) {
            Log.i(TAG, "🧹 RoyalAuthManager destroyed");
        }
    }
                            }
