package com.store.app;

import android.app.ActivityManager;
import android.content.Context;
import android.util.Log;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class RoyalNetworkEngine {

    private static final String TAG = "RoyalNetworkEngine";

    private static final long PREFETCH_COOLDOWN_MS = 250L;
    private static final int MAX_TRACKED_URLS = 80;
    private static final int MAX_WARMED_HOSTS = 15;

    private static final Set<String> prefetchedUrls = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private static final Set<String> warmedHosts = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    // محرك خفيف منظم ومحمي لمنع التداخل أو استهلاك طاقة المعالج
    private static final ThreadPoolExecutor prefetchExecutor = new ThreadPoolExecutor(
            1, 2, 20L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(50) // حد أقصى لطابور الانتظار لمنع تضخم الذاكرة
    );

    private static long lastPrefetchTime = 0;
    private static volatile boolean renderBusy = false;
    private static boolean allowPrefetch = true;
    private static boolean isLowEndDevice = false;

    private static int lastScrollY = 0;
    private static long lastScrollTime = 0;
    private static int scrollVelocity = 0;
    private static volatile boolean scrolling = false;

    // [أضف الترقيم وتحديد مستويات الأولويات كـ Enum في أعلى الكلاس]
    public enum ResourcePriority {
        VERY_HIGH, // HTML, CSS الرئيسي
        HIGH,      // Scripts, Fonts
        MEDIUM,    // Images, Media
        LOW        // Prefetch, Analytics, Third-party
    }

    private RoyalNetworkEngine() {}

    public static void install(Context context) {
        try {
            ActivityManager am =
                    (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

            isLowEndDevice = am != null && am.isLowRamDevice();

        } catch (Exception ignored) {
            isLowEndDevice = false;
        }

        RoyalCacheManager.init(context);

        Log.i(TAG, "🌐 Royal Network Engine Active.");
    }

    // [تعديل دالة interceptRequest]
    public static WebResourceResponse interceptRequest(WebResourceRequest request) {
        if (request == null || request.getUrl() == null) return null;

        String url = request.getUrl().toString();

        // إذا كان الطلب رئيسياً ومستدعاً من الصفحة فوراً، نرفع أولوية الشبكة ونوقف التحميل المسبق الخفي
        if (request.isForMainFrame()) {
            notifyRenderStart();
        }

        // خط الدفاع الأول: الكاش (الذي أصبح يستخدم FNV-1a الآن)
        WebResourceResponse cached = RoyalCacheManager.intercept(request);
        if (cached != null) return cached;

        if (request.hasGesture()) {
            notifyRenderIdle();
        }

        return null; // ترك الباقي للكروميوم المدعوم بـ Wasm
    }

    private static boolean isLikelyCacheable(String url) {
        return url.contains("cdn") || url.contains("static") || url.contains("assets") ||
                url.contains(".css") || url.contains(".js") || isImageResource(url);
    }

    private static boolean isImageResource(String url) {
        String u = url.toLowerCase();
        return u.endsWith(".png") || u.endsWith(".jpg") || u.endsWith(".jpeg") ||
                u.endsWith(".webp") || u.endsWith(".avif") || u.contains("cdn") || u.contains("img");
    }

    // [أضف دالة تصنيف أولويات الموارد بناءً على طبيعتها]
    private static ResourcePriority classifyPriority(String urlString) {
        String u = urlString.toLowerCase();

        if (u.contains(".css") || u.contains("main.js") || u.contains("app.js")) {
            return ResourcePriority.VERY_HIGH;
        }
        if (u.contains(".woff") || u.contains(".woff2") || u.contains(".ttf") || u.contains(".js")) {
            return ResourcePriority.HIGH;
        }
        if (isImageResource(u)) {
            return ResourcePriority.MEDIUM;
        }
        return ResourcePriority.LOW;
    }

    private static boolean isSafeToWarmup(String url) {

        String u = url.toLowerCase();

        // الطلبات غير المناسبة للتنبؤ
        if (u.contains("/api/")) return false;
        if (u.contains("graphql")) return false;
        if (u.contains("/ajax")) return false;
        if (u.contains("admin")) return false;

        // صفحات الحساب والجلسة
        if (u.contains("/account")) return false;
        if (u.contains("/my-account")) return false;
        if (u.contains("/profile")) return false;
        if (u.contains("/login")) return false;
        if (u.contains("/logout")) return false;
        if (u.contains("/register")) return false;
        if (u.contains("/signin")) return false;

        // عربة التسوق والدفع
        if (u.contains("/cart")) return false;
        if (u.contains("/checkout")) return false;
        if (u.contains("/payment")) return false;
        if (u.contains("/paypal")) return false;
        if (u.contains("/stripe")) return false;

        return true;
    }

    private static void scheduleWarmup(String urlString, boolean isHighPriority) {
        if (scrolling) {
            return;
        }

        if (!allowPrefetch || renderBusy || isLowEndDevice) return;
        if (!isSafeToWarmup(urlString)) return;

        // [🔥 التعديل هنا] استثناء للموارد الحرجة جداً
        ResourcePriority priority = classifyPriority(urlString);
        if (priority != ResourcePriority.VERY_HIGH && !isHighPriority) {
            // باقي الشروط للتحقق من السرعة والتبريد
            boolean isFastScroll = scrollVelocity > 5;
            long deltaTime = System.currentTimeMillis() - lastScrollTime;
            boolean isFling = deltaTime < 50;
            if ((!isFastScroll && !isFling) && !isLikelyCacheable(urlString)) return;

            long now = System.currentTimeMillis();
            if (now - lastPrefetchTime < PREFETCH_COOLDOWN_MS) return;
        }

        long now = System.currentTimeMillis();

        if (prefetchedUrls.contains(urlString)) return;

        if (prefetchedUrls.size() > MAX_TRACKED_URLS) {

            java.util.Iterator<String> it = prefetchedUrls.iterator();

            int remove = MAX_TRACKED_URLS / 4;

            while (it.hasNext() && remove-- > 0) {
                it.next();
                it.remove();
            }
        }

        prefetchedUrls.add(urlString);
        lastPrefetchTime = now;

        warmupHost(urlString);

        try {
            prefetchExecutor.execute(() -> {
                // 👑 فحص لحظي أثناء خروج المهمة من الطابور للتنفيذ:
                // إذا بدأ التمرير أو الرندر وكانت المهمة ليست أولوية قصوى، يتم إلغاؤها فوراً لتفريغ المعالج والشبكة
                ResourcePriority execPriority = classifyPriority(urlString);
                if ((scrolling || renderBusy) && execPriority != ResourcePriority.VERY_HIGH && !isHighPriority) {
                    return;
                }

                // تحديد أولوية الخيط بشكل مناسب
                android.os.Process.setThreadPriority(
                        isHighPriority || execPriority == ResourcePriority.VERY_HIGH
                                ? android.os.Process.THREAD_PRIORITY_DEFAULT
                                : android.os.Process.THREAD_PRIORITY_BACKGROUND
                );

                HttpURLConnection connection = null;
                try {
                    URL url = new URL(urlString);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setUseCaches(true);
                    connection.setInstanceFollowRedirects(true);

                    connection.setRequestProperty(
                            "Accept-Encoding",
                            "gzip"
                    );

                    connection.setRequestProperty(
                            "Accept",
                            "*/*"
                    );

                    connection.setConnectTimeout(1200);
                    connection.setReadTimeout(1200);

                    int code = connection.getResponseCode();
                    if (code == HttpURLConnection.HTTP_OK) {
                        String cacheControl = connection.getHeaderField("Cache-Control");

                        if (cacheControl != null) {

                            String cc = cacheControl.toLowerCase();

                            if (cc.contains("no-store")
                                    || cc.contains("private")) {

                                return;
                            }
                        }

                        RoyalCacheManager.store(
                                urlString,
                                new BufferedInputStream(connection.getInputStream()),
                                connection.getHeaderFields()
                        );
                    }
                } catch (Exception ignored) {
                } finally {
                    if (connection != null) {
                        try {
                            // 👑 بدلاً من disconnect() التي تقتل الـ Socket، نقوم بتفريغ أي بيانات متبقية
                            // وإغلاق الـ Stream فقط. هذا يترك القناة مفتوحة ودافئة (Socket Reuse) لـ HTTP/2
                            if (connection.getErrorStream() != null) {
                                byte[] buf = new byte[1024];
                                InputStream es = connection.getErrorStream();
                                while (es.read(buf) > 0) { /* drain error stream */ }
                                es.close();
                            }
                        } catch (Exception ignored) {}
                    }
                }
            });
        } catch (Exception ignored) {} // حماية الطابور الممتلئ من التسبب بـ Crash
    }

    // 👑 العصب السري: المُدقق الشبكي في الخلفية (Background Revalidator)
    public static void revalidateInBackground(String urlString, java.util.Map<String, String> validationHeaders) {
        if (isLowEndDevice || !allowPrefetch) return;

        try {
            prefetchExecutor.execute(() -> {
                HttpURLConnection connection = null;
                try {
                    URL url = new URL(urlString);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setUseCaches(false); // إجبار التخاطب مع السيرفر الحقيقي
                    connection.setInstanceFollowRedirects(true);
                    connection.setConnectTimeout(3000);
                    connection.setReadTimeout(3000);

                    // حقن رؤوس التحقق (If-None-Match / If-Modified-Since)
                    if (validationHeaders != null) {
                        for (java.util.Map.Entry<String, String> entry : validationHeaders.entrySet()) {
                            connection.setRequestProperty(entry.getKey(), entry.getValue());
                        }
                    }

                    int code = connection.getResponseCode();

                    if (code == 304) {
                        // ⚡ السيرفر يقول: الملف لم يتغير. تحديث الـ Metadata فقط لتمديد الصلاحية.
                        RoyalCacheManager.updateValidationMeta(urlString, connection.getHeaderFields());
                    } else if (code >= 200 && code < 300) {
                        // 🔄 السيرفر يقول: هناك نسخة جديدة. تحميلها واستبدالها بصمت في الخلفية.
                        RoyalCacheManager.store(
                                urlString,
                                new BufferedInputStream(connection.getInputStream()),
                                connection.getHeaderFields()
                        );
                    }
                } catch (Exception ignored) {
                } finally {
                    if (connection != null) {
                        try {
                            if (connection.getErrorStream() != null) {
                                byte[] buf = new byte[1024];
                                InputStream es = connection.getErrorStream();
                                while (es.read(buf) > 0) { /* drain error stream */ }
                                es.close();
                            }
                        } catch (Exception ignored) {}
                    }
                }
            });
        } catch (Exception ignored) {}
    }

    // [إضافة دالة جديدة في RoyalNetworkEngine.java]
    public static void primeCriticalAssets(String[] urls) {
        for (String url : urls) {
            scheduleWarmup(url, true); // أولوية قصوى
        }
    }

    public static void notifyRenderStart() { renderBusy = true; }
    public static void notifyRenderIdle() { renderBusy = false; }
    public static void setNetworkPrefetchAllowed(boolean allowed) { allowPrefetch = allowed; }

    public static void notifyScroll(int scrollY) {

        long now = System.currentTimeMillis();

        scrolling = true;

        int deltaY = scrollY - lastScrollY;
        long deltaTime = now - lastScrollTime;

        if (deltaTime > 0) {
            scrollVelocity = (int) (Math.abs(deltaY) / deltaTime);
        }

        lastScrollY = scrollY;
        lastScrollTime = now;
    }

    public static void notifyScrollFinished() {
        scrolling = false;
    }

    public static void warmupLink(String urlString) {
        scheduleWarmup(urlString, true);
    }

    // [تعديل دالة warmupHost الموجودة في أسفل RoyalNetworkEngine.java]
    private static void warmupHost(String urlString) {
        if (urlString == null || urlString.isEmpty()) return;

        try {
            URL url = new URL(urlString);
            String host = url.getHost();

            if (host == null || host.isEmpty()) return;

            if (!warmedHosts.add(host)) return;

            if (warmedHosts.size() > MAX_WARMED_HOSTS) {
                java.util.Iterator<String> it = warmedHosts.iterator();
                int remove = Math.max(1, MAX_WARMED_HOSTS / 3);

                while (it.hasNext() && remove-- > 0) {
                    it.next();
                    it.remove();
                }
            }

        } catch (Exception ignored) {
        }
    }
                }
