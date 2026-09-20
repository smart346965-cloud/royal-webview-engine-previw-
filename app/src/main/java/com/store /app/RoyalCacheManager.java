package com.store.app;

import android.content.Context;
import android.util.LruCache;
import android.util.Log;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

import java.io.*;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class RoyalCacheManager {

    private static class CacheMeta {
        long expiry;
        String etag;
        String lastModified;
        long created;
    }

    private static final String TAG = "RoyalCacheManager";

    private static File cacheDir;

    // 👑 تحويل السقف إلى متغير ديناميكي يلتهم المساحة المتاحة بذكاء
    private static long MAX_DISK_CACHE;

    // 1. رفع سقف الـ RAM L1 إلى 32 ميجابايت (مناسب للـ CSS/JS/fonts)
    private static final int RAM_LIMIT = 32 * 1024 * 1024;
    private static final int RAM_THRESHOLD = 2 * 1024 * 1024; // عتبة ترقية للرام

    // [حقن في بداية RoyalCacheManager]
    private static final long BLIND_TRUST_WINDOW = 100; // نافذة الثقة (100 مللي ثانية)

    private static final LruCache<String, byte[]> memoryCache =
            new LruCache<String, byte[]>(RAM_LIMIT) {
                @Override
                protected int sizeOf(String key, byte[] value) {
                    return value.length;
                }
            };

    private static final Set<String> writingNow =
            Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    private static long lastEviction = 0;

    private static final Set<String> EXT = new HashSet<>(Arrays.asList(
            ".png", ".jpg", ".jpeg", ".webp", ".avif", ".gif", ".ico", ".svg",
            ".css", ".js", ".mjs",
            ".woff", ".woff2", ".ttf", ".otf",
            ".mp4", ".webm", ".mp3", ".wav",
            ".pdf", ".doc", ".docx"
    ));

    private static final Map<String, String> MIME = new HashMap<>();
    static {
        MIME.put(".webp", "image/webp");
        MIME.put(".avif", "image/avif");
        MIME.put(".woff2", "font/woff2");
        MIME.put(".mjs", "application/javascript");
        MIME.put(".svg", "image/svg+xml");
        MIME.put(".html", "text/html");
        // MIME إضافية
        MIME.put(".js", "text/javascript");
        MIME.put(".css", "text/css");
        MIME.put(".json", "application/json");
        MIME.put(".xml", "application/xml");
        MIME.put(".mp4", "video/mp4");
        MIME.put(".webm", "video/webm");
        MIME.put(".mp3", "audio/mpeg");
    }

    private RoyalCacheManager() {}

    public static void init(Context context) {
        if (cacheDir != null) return;

        // 👑 التوجيه نحو الذاكرة الخارجية دائماً للحصول على المساحة العملاقة
        File extCache = context.getExternalCacheDir();
        cacheDir = new File(extCache != null ? extCache : context.getCacheDir(), "royal_warehouse_v5");
        if (!cacheDir.exists()) cacheDir.mkdirs();

        // 🚀 مساحة قرص L2 محسوبة: 1GB كحد أقصى، أو 20% من المساحة المتاحة، بحد أدنى 256MB
        long usableSpace = cacheDir.getUsableSpace();
        long targetCache = 1024L * 1024 * 1024; // 1 GB
        long safeSpace = Math.max(256L * 1024 * 1024, usableSpace / 5);

        MAX_DISK_CACHE = Math.min(targetCache, safeSpace);

        Log.i(TAG, "🏗️ Royal Warehouse Initialized: " + (MAX_DISK_CACHE / (1024 * 1024)) + " MB Allocated.");

        performLRUEviction();
    }

    // ==========================================
    // 🔥 INTERCEPT (L1 → L2)
    // ==========================================

    public static WebResourceResponse intercept(WebResourceRequest request) {

        long startTime = System.nanoTime();
        long memoryBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        boolean success = true;

        try {
            if (cacheDir == null) return null;

            String url = request.getUrl().toString();
            if (!"GET".equalsIgnoreCase(request.getMethod())) return null;

            // 👑 حماية النخبة: منع تخزين أي طلب API خفي يتنكر كرابط عادي
            Map<String, String> requestHeaders = request.getRequestHeaders();
            if (requestHeaders != null) {
                String accept = requestHeaders.get("Accept");
                if (accept != null && (accept.contains("application/json") || accept.contains("text/event-stream"))) {
                    return null; // دعه يمر للإنترنت لأنه بيانات ديناميكية
                }

                // حماية من الطلبات الشخصية (Authorization, Range)
                String authorization = requestHeaders.get("Authorization");
                if (authorization != null && !authorization.isEmpty()) {
                    return null;
                }
                String range = requestHeaders.get("Range");
                if (range != null && !range.isEmpty()) {
                    return null;
                }
            }

            if (!isCacheable(url)) return null;

            maybeEvict();

            String key = generateAtomicKey(url);

            // ⚡ L1 RAM
            byte[] mem = memoryCache.get(key);
            if (mem != null) {
                return new WebResourceResponse(
                        getMime(url),
                        getEncoding(url),
                        200,
                        "OK",
                        buildResponseHeaders(url, null, mem.length),
                        new ByteArrayInputStream(mem)
                );
            }

            // 🔒 [تعديل داخل intercept] منع قراءة ملف قيد الكتابة حالياً (Single-Flight Conflict Avoidance)
            if (writingNow.contains(key)) {
                // الملف قيد الكتابة حالياً بواسطة خيط آخر؛ ننسحب ودع الشبكة/SW يتعامل لتجنب قراءة ملف ناقص
                return null;
            }

            // 💾 L2 Disk
            File file = new File(cacheDir, key);
            if (!file.exists()) return null;

            CacheMeta meta = loadMeta(key);

            if (meta == null) {
                file.delete();
                return null;
            }

            // 👑 تطبيق معمارية Stale-While-Revalidate (العرض الفوري والتحديث بالخلفية)
            // [تعديل جراحي في RoyalCacheManager.java]

            // 1. استدعاء قرار النواة (الذي يتم تمريره عبر الجسر أو حسابه محلياً بنفس المنطق)
            String strategy = "DEFAULT";
            if (url.endsWith(".js") || url.endsWith(".css")) {
                // نحن نستخدم نفس منطق الـ C++ هنا لضمان الانسجام
                strategy = "BINARY_TRUST_CACHE";
            }

            long now = System.currentTimeMillis();

            // 2. تطبيق سياسة التمرد الصارم
            if (meta != null) {
                if ("BINARY_TRUST_CACHE".equals(strategy)) {
                    // 👑 تمرد النواة: حتى لو انتهى الوقت، سنمرر الملف فوراً من الذاكرة
                    // ونحدث في الخلفية فقط إذا مر أكثر من أسبوع
                    if (now - meta.created > 7L * 24 * 60 * 60 * 1000) {
                        RoyalNetworkEngine.revalidateInBackground(url, getValidationHeaders(url));
                    }
                    Log.d(TAG, "🛡️ Stubborn Cache Access: " + url);
                } else if (now > meta.expiry) {
                    // التحديث العادي للموارد الأخرى
                    RoyalNetworkEngine.revalidateInBackground(url, getValidationHeaders(url));
                }
            }

            try {
                // 🔥 SMALL → RAM
                if (file.length() < RAM_THRESHOLD) {

                    FileInputStream fis = new FileInputStream(file);
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();

                    byte[] buffer = new byte[8192];
                    int r;

                    while ((r = fis.read(buffer)) != -1) {
                        bos.write(buffer, 0, r);
                    }

                    fis.close();

                    byte[] data = bos.toByteArray();
                    memoryCache.put(key, data);

                    file.setLastModified(System.currentTimeMillis());

                    return new WebResourceResponse(
                            getMime(url),
                            getEncoding(url),
                            200,
                            "OK",
                            buildResponseHeaders(url, meta != null ? meta.etag : null, data.length),
                            new ByteArrayInputStream(data)
                    );
                }

                // 🔥 LARGE → STREAM (بدون RAM)
                return new WebResourceResponse(
                        getMime(url),
                        getEncoding(url),
                        200,
                        "OK",
                        buildResponseHeaders(url, meta != null ? meta.etag : null, file.length()),
                        new BufferedInputStream(new FileInputStream(file))
                );

            } catch (Exception e) {
                return null;
            }

        } catch (Exception e) {
            success = false;
            return null;
        } finally {
            long latency = (System.nanoTime() - startTime) / 1_000_000;
            long memoryAfter = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            long memoryUsed = Math.max(0, memoryAfter - memoryBefore);

            RoyalPanopticon.recordExecution(
                    "RoyalCacheManager",
                    latency,
                    success,
                    memoryUsed
            );
        }
    }

    // ==========================================
    // 💾 STORE
    // ==========================================

    public static void store(String url, InputStream inputStream, Map<String, List<String>> headers) {

        long startTime = System.nanoTime();
        long memoryBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        boolean success = true;

        try {
            if (cacheDir == null) return;
            if (!isCacheable(url)) return;

            maybeEvict();

            String key = generateAtomicKey(url);

            // 🔒 atomic lock
            if (!writingNow.add(key)) return;

            try {
                CacheMeta meta = parseHeaders(url, headers);
                if (meta == null) return;

                File finalFile = new File(cacheDir, key);
                // السماح بالتحديث: حذف الملف القديم إن وجد
                if (finalFile.exists() && finalFile.length() > 0) {
                    finalFile.delete();
                }

                // 🛡️ الكتابة في ملف مؤقت أولاً (Atomic Write)
                File tmpFile = new File(cacheDir, key + ".tmp");
                FileOutputStream fos = new FileOutputStream(tmpFile);

                BufferedInputStream bis = new BufferedInputStream(inputStream);

                byte[] memBuffer = null;
                byte[] buffer = new byte[16384];
                int total = 0;
                int read;

                while ((read = bis.read(buffer)) != -1) {

                    fos.write(buffer, 0, read);
                    total += read;

                    if (total <= RAM_THRESHOLD) {
                        if (memBuffer == null) {
                            memBuffer = new byte[RAM_THRESHOLD];
                        }
                        System.arraycopy(buffer, 0, memBuffer, total - read, read);
                    }
                }

                fos.flush();
                fos.close();
                bis.close();

                // 🛡️ إنهاء عملية الكتابة الذرية بأمان
                if (tmpFile.length() == 0) {
                    tmpFile.delete();
                    return;
                } else {
                    // استبدال الملف القديم بالجديد في جزء من الثانية
                    if (!tmpFile.renameTo(finalFile)) {
                        if (finalFile.exists()) {
                            finalFile.delete();
                        }
                        if (!tmpFile.renameTo(finalFile)) {
                            tmpFile.delete();
                            return;
                        }
                    }
                }

                // ⚡ RAM promotion
                if (memBuffer != null) {
                    byte[] exact = Arrays.copyOf(memBuffer, total);
                    memoryCache.put(key, exact);
                }

                saveMeta(key, meta);

                // 🔥 runtime eviction (خفيف)
                if (new Random().nextInt(20) == 0) {
                    performLRUEviction();
                }

            } catch (Exception ignored) {
            } finally {
                writingNow.remove(key);
            }

        } catch (Exception e) {
            success = false;
        } finally {
            long latency = (System.nanoTime() - startTime) / 1_000_000;
            long memoryAfter = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            long memoryUsed = Math.max(0, memoryAfter - memoryBefore);

            RoyalPanopticon.recordExecution(
                    "RoyalCacheManager",
                    latency,
                    success,
                    memoryUsed
            );
        }
    }

    public static Map<String, String> getValidationHeaders(String url) {
        String key = generateAtomicKey(url);
        CacheMeta meta = loadMeta(key);

        if (meta == null) return null;

        Map<String, String> headers = new HashMap<>();

        if (meta.etag != null)
            headers.put("If-None-Match", meta.etag);

        if (meta.lastModified != null)
            headers.put("If-Modified-Since", meta.lastModified);

        return headers;
    }

    // ==========================================
    // 🧠 RULES
    // ==========================================

    private static boolean isCacheable(String url) {
        if (url == null || url.isEmpty()) return false;

        String clean = url.split("\\?", 2)[0].toLowerCase(Locale.US);

        if (!clean.startsWith("https://") && !clean.startsWith("http://")) {
            return false;
        }

        if (clean.contains("/checkout")
                || clean.contains("/payment")
                || clean.contains("/auth")
                || clean.contains("/login")
                || clean.contains("/logout")
                || clean.contains("/account")
                || clean.contains("/profile")
                || clean.contains("/cart")) {
            return false;
        }

        return isStaticResource(clean);
    }

    private static boolean isStaticResource(String url) {
        return url.endsWith(".css")
                || url.endsWith(".js")
                || url.endsWith(".mjs")
                || url.endsWith(".woff")
                || url.endsWith(".woff2")
                || url.endsWith(".ttf")
                || url.endsWith(".otf")
                || url.endsWith(".png")
                || url.endsWith(".jpg")
                || url.endsWith(".jpeg")
                || url.endsWith(".webp")
                || url.endsWith(".avif")
                || url.endsWith(".gif")
                || url.endsWith(".svg")
                || url.endsWith(".ico");
    }

    private static long resolveTTL(String url) {

        String u = url.toLowerCase(Locale.US);

        if (u.endsWith(".js"))
            return 6L * 60 * 60 * 1000;

        if (u.endsWith(".css"))
            return 6L * 60 * 60 * 1000;

        if (u.endsWith(".woff"))
            return 30L * 24 * 60 * 60 * 1000;

        if (u.endsWith(".woff2"))
            return 30L * 24 * 60 * 60 * 1000;

        if (u.endsWith(".ttf"))
            return 30L * 24 * 60 * 60 * 1000;

        if (u.endsWith(".otf"))
            return 30L * 24 * 60 * 60 * 1000;

        if (u.endsWith(".png"))
            return 7L * 24 * 60 * 60 * 1000;

        if (u.endsWith(".jpg"))
            return 7L * 24 * 60 * 60 * 1000;

        if (u.endsWith(".jpeg"))
            return 7L * 24 * 60 * 60 * 1000;

        if (u.endsWith(".webp"))
            return 7L * 24 * 60 * 60 * 1000;

        if (u.endsWith(".avif"))
            return 7L * 24 * 60 * 60 * 1000;

        // 👑 إذا كان الرابط هو صفحة HTML للمتجر، نعطيه TTL قصير جداً (5 دقائق)
        // هذا يضمن أن يفتح المتجر فوراً، ولكنه سيجبر المحرك على جلب النسخة الأحدث إذا تغيرت الأسعار.
        if (u.endsWith(".html") || !u.matches(".*\\.[a-z0-9]{2,5}$")) {
            return 5L * 60 * 1000; // 5 دقائق فقط
        }

        return 60L * 60 * 1000; // ساعة لباقي الملفات المجهولة
    }

    // ==========================================
    // 🧹 EVICTION
    // ==========================================

    private static void maybeEvict() {
        long now = System.currentTimeMillis();

        if (now - lastEviction > 5 * 60 * 1000) { // كل 5 دقائق
            lastEviction = now;
            performLRUEviction();
        }
    }

    private static void performLRUEviction() {

        long startTime = System.nanoTime();
        long memoryBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        boolean success = true;

        try {

            File[] files = cacheDir.listFiles();
            if (files == null) return;

            long total = 0;
            for (File f : files) {

                if (f.getName().endsWith(".meta"))
                    continue;

                total += f.length();

            }

            if (total < MAX_DISK_CACHE) return;

            Arrays.sort(files, new Comparator<File>() {
                @Override
                public int compare(File f1, File f2) {
                    long diff = f1.lastModified() - f2.lastModified();
                    return (diff == 0) ? 0 : (diff < 0 ? -1 : 1);
                }
            });

            for (File f : files) {

                if (f.getName().endsWith(".meta"))
                    continue;

                total -= f.length();

                File meta = new File(f.getAbsolutePath() + ".meta");
                if (meta.exists()) meta.delete();

                f.delete();

                if (total < MAX_DISK_CACHE * 0.8) break;
            }

        } catch (Exception e) {
            success = false;
        } finally {
            long latency = (System.nanoTime() - startTime) / 1_000_000;
            long memoryAfter = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            long memoryUsed = Math.max(0, memoryAfter - memoryBefore);

            RoyalPanopticon.recordExecution(
                    "RoyalCacheManager",
                    latency,
                    success,
                    memoryUsed
            );
        }
    }

    // ==========================================
    // 🔧 UTILS
    // ==========================================

    // [تعديل جراحي في RoyalCacheManager.java]
    // هذه الدالة هي النسخة الجاوية من كود الـ C++ في Guardian
    private static String generateAtomicKey(String input) {
        long hash = 0x811c9dc5L; // نفس الـ Offset الأساسي في C++
        for (int i = 0; i < input.length(); i++) {
            hash ^= input.charAt(i);
            hash *= 0x01000193L; // نفس الـ Prime الثنائي
            hash &= 0xffffffffL; // ضمان البقاء في نطاق 32 بت
        }
        return Long.toHexString(hash);
    }

    // 👑 [إضافة دالة جديدة] بناء هيدرات HTTP احترافية لتفعيل V8 Bytecode Caching
    private static Map<String, String> buildResponseHeaders(String url, String etag, long contentLength) {
        Map<String, String> headers = new HashMap<>();
        // تم حذف Access-Control-Allow-Origin لتجنب تغيير CORS
        headers.put("X-Served-By", "RoyalCacheManager"); // وسم المصدر لمنع التضارب مع SW
        headers.put("Accept-Ranges", "bytes");
        headers.put("Vary", "Accept-Encoding"); // دعم الضغط

        // لا نرسل immutable أو max-age كبير جداً
        if (url.endsWith(".js")
                || url.endsWith(".mjs")
                || url.endsWith(".css")) {

            headers.put("Cache-Control", "public, max-age=300");
        } else {
            headers.put("Cache-Control", "public, max-age=300");
        }

        if (etag != null && !etag.isEmpty()) {
            headers.put("ETag", etag);
        }
        if (contentLength > 0) {
            headers.put("Content-Length", String.valueOf(contentLength));
        }
        return headers;
    }

    private static String getMime(String url) {
        String clean = url.toLowerCase().split("\\?")[0];

        for (Map.Entry<String, String> e : MIME.entrySet()) {
            if (clean.endsWith(e.getKey())) return e.getValue();
        }

        String sys = URLConnection.guessContentTypeFromName(clean);
        return sys != null ? sys : "application/octet-stream";
    }

    private static String getEncoding(String url) {
        String mime = getMime(url);

        if (mime.startsWith("text/")
                || mime.contains("javascript")
                || mime.contains("json")
                || mime.contains("xml")) {
            return "UTF-8";
        }

        return null;
    }

    private static File metaFile(String key) {
        return new File(cacheDir, key + ".meta");
    }

    private static void saveMeta(String key, CacheMeta meta) {
        try (FileOutputStream fos = new FileOutputStream(metaFile(key))) {
            Properties p = new Properties();
            p.put("expiry", String.valueOf(meta.expiry));
            if (meta.etag != null) p.put("etag", meta.etag);
            if (meta.lastModified != null) p.put("lm", meta.lastModified);
            p.put("created", String.valueOf(System.currentTimeMillis()));
            p.store(fos, null);
        } catch (Exception ignored) {}
    }

    // 👑 تحديث وقت انتهاء الصلاحية فقط عند استلام 304 Not Modified
    public static void updateValidationMeta(String url, Map<String, List<String>> newHeaders) {
        String key = generateAtomicKey(url);
        CacheMeta oldMeta = loadMeta(key);

        if (oldMeta != null) {
            CacheMeta updatedMeta = parseHeaders(url, newHeaders);
            if (updatedMeta != null) {
                // دمج البيانات الجديدة مع القديمة
                oldMeta.expiry = updatedMeta.expiry;
                if (updatedMeta.etag != null) oldMeta.etag = updatedMeta.etag;
                if (updatedMeta.lastModified != null) oldMeta.lastModified = updatedMeta.lastModified;

                saveMeta(key, oldMeta);
            }
        }
    }

    private static CacheMeta loadMeta(String key) {
        File f = metaFile(key);
        if (!f.exists()) return null;

        try (FileInputStream fis = new FileInputStream(f)) {
            Properties p = new Properties();
            p.load(fis);

            CacheMeta m = new CacheMeta();
            m.expiry = Long.parseLong(p.getProperty("expiry", "0"));
            m.etag = p.getProperty("etag");
            m.lastModified = p.getProperty("lm");
            m.created = Long.parseLong(p.getProperty("created", "0"));
            return m;

        } catch (Exception e) {
            return null;
        }
    }

    private static CacheMeta parseHeaders(
            String url,
            Map<String, List<String>> headers) {

        if (headers == null) return null;

        CacheMeta meta = new CacheMeta();
        long now = System.currentTimeMillis();

        // 🔥 معالجة Cache-Control
        List<String> cc = headers.get("Cache-Control");
        if (cc != null && !cc.isEmpty()) {
            String lower = cc.get(0).toLowerCase(Locale.US);

            // no-store أو private → لا نخزن
            if (lower.contains("no-store")
                    || lower.contains("private")) {
                return null;
            }

            // no-cache → صلاحية منتهية فوراً (يطلب validation)
            if (lower.contains("no-cache")) {
                meta.expiry = now;
            } else if (lower.contains("max-age")) {
                try {
                    String s = lower.split("max-age=")[1].split(",")[0];
                    long seconds = Long.parseLong(s);
                    meta.expiry = now + (seconds * 1000);
                } catch (Exception ignored) {}
            }
        }

        // 👑 إذا لم يرسل الخادم وقت انتهاء، أو أرسل وقتاً منتهياً (لإجبارنا على التحديث)،
        // سنرفض ذلك ونفرض المدة الزمنية الخاصة بمحركنا بالقوة!
        if (meta.expiry == 0 || meta.expiry <= now) {
            meta.expiry = now + resolveTTL(url);
        }

        // 🔥 ETag
        List<String> et = headers.get("ETag");
        if (et != null) meta.etag = et.get(0);

        // 🔥 Last-Modified
        List<String> lm = headers.get("Last-Modified");
        if (lm != null) meta.lastModified = lm.get(0);

        return meta;
    }

    // ==========================================
    // 📥 DOWNLOAD MANAGER DIRECTORY
    // ==========================================

    /**
     * 🔥 يتولى معالجة تحميل الملفات الكبيرة لتخفيف الضغط عن المحرك الأساسي
     */
    public static void downloadLargeFile(Context context, String url, String userAgent, String contentDisposition, String mimeType) {
        try {
            android.app.DownloadManager.Request request = new android.app.DownloadManager.Request(android.net.Uri.parse(url));
            request.setMimeType(mimeType);

            // حقن الكوكيز لضمان صلاحية التحميل من المواقع التي تتطلب تسجيل دخول
            String cookies = android.webkit.CookieManager.getInstance().getCookie(url);
            if (cookies != null) {
                request.addRequestHeader("cookie", cookies);
            }

            request.addRequestHeader("User-Agent", userAgent);
            request.setDescription("Downloading file...");

            String fileName = android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType);
            request.setTitle(fileName);
            request.allowScanningByMediaScanner();
            request.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, fileName);

            android.app.DownloadManager dm = (android.app.DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm != null) dm.enqueue(request);

        } catch (Exception e) {
            Log.e(TAG, "Royal Download Manager failed", e);
        }
    }

    /**
     * 🚨 [إضافة دالة جديدة] تفريغ طارئ واستباقي للذاكرة عند حدوث OOM Risk
     */
    public static void onMemoryPressure(int level) {
        Log.w(TAG, "🚨 Memory Pressure Triggered (Level: " + level + "). Evicting L1 RAM Cache...");
        
        // 1. تفريغ ذاكرة L1 RAM فوراً
        memoryCache.evictAll();
        
        // 2. تنظيف القرص أيضاً (L2) لتخفيف الضغط
        new Thread(() -> {
            performLRUEviction(); // هذا سيُقلّم حتى 80% من السعة
        }).start();
    }
        }
