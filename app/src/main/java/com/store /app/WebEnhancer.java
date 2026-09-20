package com.store.app;

import android.content.Context;
import android.util.Log;
import android.webkit.WebView;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * =========================================================
 * 🧠 Royal Web Enhancer (Enterprise Atomic Injector)
 * =========================================================
 * - Single atomic injection
 * - Deterministic execution order
 * - WebView / SPA safe
 * - Radar-compatible (HUD profiler)
 */
public final class WebEnhancer {

    private static final String TAG = "RoyalWebEnhancer";

    // 👑 كاش الذاكرة العشوائية: يمنع قراءة الملفات من القرص أكثر من مرة واحدة
    private static volatile String cachedPayload = null;
    private static volatile boolean preloaded = false;

    /**
     * 🔑 Entry point
     */
    public static synchronized void apply(WebView webView, Context context) {
        if (webView == null || context == null) return;

        preload(context);

        if (cachedPayload != null) {
            webView.evaluateJavascript(cachedPayload, null);
        }
        Log.i(TAG, "✅ Royal Engine injected & cached in RAM successfully");
    }

    /**
     * 🔥 Preload JS Engine into RAM
     */
    public static synchronized void preload(Context context) {
        if (preloaded || context == null) return;

        // 👑 التعديل الجوهري: royal_loader.js هو أول ما يجب أن يدخل الذاكرة!
        final String[] scripts = {
                "public/js/royal_loader.js", // 👈 المحرك يسبق الجميع
                "public/js/royal-native-illusion.js",
                "public/js/sw-register.js",
                "public/js/index.js"
        };

        StringBuilder payload = new StringBuilder(131072); // توسيع الذاكرة لـ 128KB
        payload.append("(function(){\ntry{\n");

        for (String path : scripts) {
            appendScript(payload, context, path);
        }

        // 👑 التعديل: إضافة إشارة الجاهزية (Handshake) في نهاية السكربت
        payload.append("\n/* 🏁 Handshake Signal */\n");
        payload.append("if (window.RoyalBridge) { window.RoyalBridge.hideSplash(); }\n");
        payload.append("\n}catch(e){console.error(e);}\n})();");

        cachedPayload = payload.toString();

        preloaded = true;

        Log.i(TAG, "🔥 JS Engine Preloaded Into RAM");
    }

    /**
     * 🧬 Atomic Batch Injector
     */
    private static void injectBatch(WebView webView, Context context, String[] assetPaths) {
        // نوسع الذاكرة المبدئية إلى 64KB لمنع إعادة التحجيم أثناء القراءة
        StringBuilder payload = new StringBuilder(65536);

        payload.append("(function(){\ntry {\n");

        for (String path : assetPaths) {
            appendScript(payload, context, path);
        }

        payload.append("\n} catch(e) { console.error('❌ Royal Engine bootstrap failed', e); }\n})();");

        // 👑 حفظ النتيجة المعالجة في الذاكرة العشوائية لخدمة الصفحات القادمة بسرعة البرق
        cachedPayload = payload.toString();

        webView.evaluateJavascript(cachedPayload, null);
        Log.i(TAG, "✅ Royal Engine injected & cached in RAM successfully");
    }

    /**
     * 📦 Append asset with DevTools visibility
     */
    private static void appendScript(StringBuilder out, Context context, String assetPath) {
        try (InputStream is = context.getAssets().open(assetPath);
             InputStreamReader isr = new InputStreamReader(is, "UTF-8")) {

            String fileName = assetPath.substring(assetPath.lastIndexOf('/') + 1);
            out.append("\n/* ===== ").append(fileName).append(" ===== */\n");

            // 🚀 قراءة كتلية (Chunk Reading): أسرع بـ 10 أضعاف من القراءة سطر بسطر ولا تخنق المعالج
            char[] buffer = new char[8192]; 
            int charsRead;
            while ((charsRead = isr.read(buffer)) != -1) {
                out.append(buffer, 0, charsRead);
            }

            out.append("\n//# sourceURL=royal://").append(fileName).append("\n");

        } catch (Exception ex) {
            Log.e(TAG, "❌ Failed to load asset: " + assetPath, ex);
        }
    }

    private WebEnhancer() {}
    }
