package com.store.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.CookieManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 👑 RoyalAuthManager - Enterprise Universal Auth & Session Handoff Dispatcher
 *
 * الوظائف الأساسية:
 * 1. استلام وتدقيق روابط العودة (Deep Links و App Links).
 * 2. دعم بروتوكول تسليم الجلسة الآمن (Session Handoff Protocol).
 * 3. تحويل رموز المصادقة والـ Handoff Tokens إلى روابط استلام صالحة لموقع العميل.
 * 4. تسليم الرابط النهائي إلى الـ WebView ليقوم السيرفر بزرع الـ Session Cookies تلقائياً وبشكل طبيعي.
 */
public final class RoyalAuthManager {

    private static final String TAG = "RoyalAuthManager";
    public static final String CUSTOM_AUTH_SCHEME = "com.store.app.auth";
    public static final String HANDOFF_PARAM = "royal_handoff";

    private final Activity activity;
    private final Context context;
    private final AtomicBoolean destroyed = new AtomicBoolean(false);

    public RoyalAuthManager(@NonNull Activity activity, @NonNull Context context) {
        this.activity = activity;
        this.context = context.getApplicationContext();
        Log.i(TAG, "👑 RoyalAuthManager (Session Handoff Engine) initialized");
    }

    /**
     * فحص ذكي للتحقق مما إذا كان الـ URI يمثل رابط عودة مصادقة أو تسليم جلسة
     */
    public static boolean isAuthCallback(@Nullable Uri uri) {
        if (uri == null) return false;

        String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase() : "";
        String uriString = uri.toString().toLowerCase();

        // 1. فحص المخطط المخصص للتطبيق
        if (CUSTOM_AUTH_SCHEME.equalsIgnoreCase(scheme)) {
            return true;
        }

        // 2. فحص روابط HTTPS / HTTP العائدة من بروتوكول التسليم أو OAuth
        boolean isValidWebScheme = "https".equals(scheme) || "http".equals(scheme);
        if (isValidWebScheme) {
            return uriString.contains("handoff_token=")
                    || uriString.contains("royal_handoff=")
                    || uriString.contains("code=")
                    || uriString.contains("state=")
                    || uriString.contains("token=")
                    || uriString.contains("/auth/callback")
                    || uriString.contains("/oauth/callback")
                    || uriString.contains("/royal-callback");
        }

        return false;
    }

    /**
     * معالجة Intent القادم بعد اكتمال تسجيل الدخول في Custom Tab
     */
    public boolean handleRedirectIntent(@Nullable Intent intent) {
        if (destroyed.get() || intent == null) {
            return false;
        }

        Uri data = intent.getData();
        if (data == null) {
            return false;
        }

        Log.i(TAG, "🔗 Incoming Auth Redirect: " + data);

        if (isAuthCallback(data)) {
            Log.i(TAG, "🎯 Valid Auth/Handoff detected. Resolving target URL...");

            String resolvedUrl = resolveHandoffUrl(data);

            if (TextUtils.isEmpty(resolvedUrl)) {
                Log.w(TAG, "⚠️ Failed to resolve a valid destination URL from redirect intent.");
                return false;
            }

            Log.i(TAG, "🚀 Dispatching resolved auth URL to WebView: " + resolvedUrl);

            if (activity instanceof MainActivity) {
                MainActivity mainActivity = (MainActivity) activity;
                mainActivity.dispatchAuthUrlToWebView(resolvedUrl);
                return true;
            }
        } else {
            Log.d(TAG, "ℹ️ Intent received is not an authentication callback.");
        }

        return false;
    }

    /**
     * فك شفرة الرابط وتحويله إلى رابط استهلاك الجلسة على موقع العميل
     */
    @Nullable
    private String resolveHandoffUrl(@NonNull Uri uri) {
        String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase() : "";

        // الحالة الأولى: رابط بمخطط مخصص com.store.app.auth://...
        if (CUSTOM_AUTH_SCHEME.equalsIgnoreCase(scheme)) {
            String target = uri.getQueryParameter("target");
            String handoffToken = uri.getQueryParameter("handoff_token");
            if (handoffToken == null) handoffToken = uri.getQueryParameter("token");
            if (handoffToken == null) handoffToken = uri.getQueryParameter("code");

            // استخراج النطاق المستهدف من الـ parameters
            String baseTarget = null;
            if (target != null && (target.startsWith("http://") || target.startsWith("https://"))) {
                baseTarget = target;
            } else {
                String redirectUri = uri.getQueryParameter("redirect_uri");
                if (redirectUri != null && (redirectUri.startsWith("http://") || redirectUri.startsWith("https://"))) {
                    baseTarget = redirectUri;
                }
            }

            if (baseTarget != null) {
                Uri.Builder builder = Uri.parse(baseTarget).buildUpon();

                // تمرير رمز التسليم الآمن لخادم الموقع
                if (handoffToken != null) {
                    builder.appendQueryParameter(HANDOFF_PARAM, handoffToken);
                }

                // نقل المعلمات الإضافية (مثل state إن وُجدت)
                for (String key : uri.getQueryParameterNames()) {
                    if (!"target".equalsIgnoreCase(key)
                            && !"redirect_uri".equalsIgnoreCase(key)
                            && !"handoff_token".equalsIgnoreCase(key)
                            && !"token".equalsIgnoreCase(key)
                            && !"code".equalsIgnoreCase(key)) {
                        for (String val : uri.getQueryParameters(key)) {
                            builder.appendQueryParameter(key, val);
                        }
                    }
                }
                return builder.build().toString();
            }
        }

        // الحالة الثانية: رابط App Link عادي (HTTPS) مباشرة
        return uri.toString();
    }

    /**
     * للتوافق مع الإصدارات السابقة
     */
    public boolean handleRedirect(@Nullable Intent intent) {
        return handleRedirectIntent(intent);
    }

    /**
     * تنظيف الذاكرة
     */
    public void destroy() {
        if (destroyed.compareAndSet(false, true)) {
            Log.i(TAG, "🧹 RoyalAuthManager destroyed safely");
        }
    }
                        }
