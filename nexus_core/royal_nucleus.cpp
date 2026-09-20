// royal_nucleus.cpp
#include <emscripten/emscripten.h>
#include <emscripten/bind.h>

#include <string>
#include <chrono>
#include <iostream>
#include <vector>
#include <unordered_set>
#include <unordered_map>
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cctype>
#include <cstdio>

using namespace emscripten;

// 👑 [التحام النواة]: صهر محركات التنبؤ والحماية في كتلة ثنائية واحدة
#include "royal_intel_prediction.cpp"
#include "royal_network_guardian.cpp"

// =========================================================
// 🚀 ROYAL IGNITION CORE (ملف royal_ignition_core.cpp)
// =========================================================
class RoyalIgnitionCore {
private:
    bool engine_warmed = false;
    std::chrono::steady_clock::time_point ignition_timestamp;

public:
    RoyalIgnitionCore() {
        ignition_timestamp = std::chrono::steady_clock::now();

        EM_ASM({
            console.log("⚡ NUCLEUS (Worker): Engine ignition sequence started.");
        });
    }

    /**
     * 🚀 منطق تسخين السوكيت الاحترافي (Socket Priming Logic)
     * بدلاً من Thread جافا ثقيل، النواة تدير عملية الربط بذكاء
     */
    bool perform_socket_priming(const std::string& domain) {
        // في عالم الـ Wasm، سنستخدم مكتبة fetch المتقدمة في كروميوم
        // أو نقوم بضبط إعدادات النواة لرفع أولوية الاتصال القادم لهذا الدومين
        EM_ASM_({
            // حقن طلب خفيف جداً (HEAD Request) في خيط خلفي داخل المتصفح
            fetch(UTF8ToString($0), {method: 'HEAD', mode: 'no-cors', cache: 'force-cache'})
                .then(() => console.log("🌐 Socket Primed at Nucleus Level"))
                .catch(e => console.error("Priming failed", e));
        }, domain.c_str());

        return true;
    }

    /**
     * 🧠 استراتيجية التجهيز الذكي (Intelligent Pre-warming)
     * تحسب الوقت المثالي لإطلاق الإشارة بناءً على زمن إقلاع التطبيق
     */
    double calculate_ignition_readiness() {
        auto now = std::chrono::steady_clock::now();

        const double delta =
            std::chrono::duration<double, std::milli>(
                now - ignition_timestamp
            ).count();

        return std::min(delta / 500.0, 1.0);
    }

    void set_engine_warmed(bool state) {
        engine_warmed = state;
    }
};

// =========================================================
// 🧠 ROYAL CORE ENGINE (ملف royal_core.cpp)
// =========================================================
class RoyalCoreEngine {
private:
    std::unordered_set<std::string> prefetch_cache;
    const size_t MAX_PREFETCH = 5;
    std::string app_origin;

    // دالة داخلية سريعة للتحقق من الممنوعات (محدثة)
    bool is_blacklisted(const std::string& url) {

        if (url.empty()) {
            return true;
        }

        std::string clean = url;

        std::transform(
            clean.begin(),
            clean.end(),
            clean.begin(),
            [](unsigned char c) {
                return static_cast<char>(std::tolower(c));
            }
        );

        if (
            clean.find("javascript:") != std::string::npos ||
            clean.find("data:") != std::string::npos ||
            clean.find("blob:") != std::string::npos
        ) {
            return true;
        }

        if (
            clean.find("/cart") != std::string::npos ||
            clean.find("/checkout") != std::string::npos ||
            clean.find("/payment") != std::string::npos ||
            clean.find("/login") != std::string::npos ||
            clean.find("/logout") != std::string::npos ||
            clean.find("/account") != std::string::npos
        ) {
            return true;
        }

        return false;
    }

    // دالة جديدة لاستخراج origin
    std::string extract_origin(const std::string& value) const {
        size_t scheme = value.find("://");
        if (scheme == std::string::npos) return "";

        size_t host_start = scheme + 3;
        size_t path_start = value.find_first_of("/?#", host_start);

        if (path_start == std::string::npos) {
            return value;
        }

        return value.substr(0, path_start);
    }

    bool is_same_origin_url(const std::string& url) const {
        if (app_origin.empty() || url.empty()) return false;
        return extract_origin(url) == extract_origin(app_origin);
    }

    // [تعديل جراحي في royal_core.cpp لدعم Zero-Allocation]
    float shared_buffer[10]; // حوض ذاكرة ثابت لاستقبال إحداثيات اللمس

public:
    RoyalCoreEngine() : app_origin("") {}

    /**
     * 👑 تثبيت الرابط الأساسي للمتجر عند الإقلاع لضمان الحماية
     */
    void set_origin(const std::string& origin) {
        this->app_origin = origin;
    }

    /**
     * 🧠 دالة التنبؤ الصاروخية (تصفية وتحليل الروابط بسرعة النواة)
     * تعيد true إذا كان الرابط مؤهلاً للتحميل المسبق فوراً
     */
    bool evaluate_speculation(const std::string& url) {
        if (url.empty() || is_blacklisted(url)) return false;

        // حماية صارمة باستخدام is_same_origin_url
        if (!app_origin.empty() && !is_same_origin_url(url)) {
            return false;
        }

        // إذا كان الرابط مسجلاً مسبقاً لا داعي لتكرار العملية
        if (prefetch_cache.find(url) != prefetch_cache.end()) {
            return false;
        }

        // حماية الرام: إذا وصلنا للحد الأقصى، نرفض مؤقتاً لحين تفريغ العناصر
        if (prefetch_cache.size() >= MAX_PREFETCH) {
            return false;
        }

        prefetch_cache.insert(url);
        return true;
    }

    /**
     * 🧹 تفريغ الذاكرة الذكي (Garbage Collection) عند خروج الروابط من الشاشة
     */
    void remove_speculation(const std::string& url) {
        prefetch_cache.erase(url);
    }

    /**
     * 🌊 محرك احتساب سرعة التمرير ومكافحة التقطيع (Velocity Vector)
     * يحلل حركة الإصبع أو السكرول ويعيد true إذا كانت السرعة تستدعي التنبؤ الفوري للأسفل
     */
    bool analyze_scroll_velocity(
        int current_y,
        int last_y,
        double delta_time
    ) {
        if (delta_time <= 0.0) return false;

        const double velocity =
            std::abs(static_cast<double>(current_y - last_y)) / delta_time;

        return velocity > 1.8;
    }

    /**
     * 👆 محرك قياس الحركة المصدق (Calibrated Slop Detection)
     * يحسب المسافة بناءً على كثافة بكسلات الجهاز لضمان "خفة" السكرول
     */
    bool detect_scroll_slop(float start_x, float start_y, float current_x, float current_y, float dpr) {
        float dx = current_x - start_x;
        float dy = current_y - start_y;
        float distance = std::sqrt(dx * dx + dy * dy);
        
        // 📏 العتبة الديناميكية: 8 بكسل فيزيائي (تعديل من 12 لزيادة الاستجابة)
        return distance > (8.0f * dpr); 
    }

    /**
     * 🌐 وعي عزل المواقع (Site Isolation Engine)
     * تحلل ما إذا كان التنقل داخل نفس الأصل (Same-Site) أو أصل مختلف (Cross-Site)
     * لإعداد عملية رندر جديدة (Render Process) في الذاكرة مسبقاً عند الحاجة
     */
    bool is_same_site_navigation(const std::string& target_url) const {
        if (target_url.empty() || app_origin.empty()) return false;
        return is_same_origin_url(target_url);
    }

    /**
     * ⚡ فحص جاهزية CommitNavigation
     * يقيس مدى جاهزية عملية الرندر للبدء في اعتماد التنقل فور وصول أول بيانات الهيدر من الشبكة
     */
    bool check_commit_navigation_readiness(int response_status, double header_ttfb_ms) const {
        // تكون الاستجابة جاهزة للـ Commit المباشر (0ms) إذا كان الرمز 200 والـ TTFB أقل من 250ms
        if (response_status == 200 && header_ttfb_ms < 250.0) {
            return true;
        }
        return false;
    }

    /**
     * 🛰️ فحص صحة الرابط المعلق (Pending URL Validator)
     * يتأكد من أن الرابط الذي حاول المستخدم فتحه أوفلاين هو رابط آمن للتحميل التلقائي
     */
    bool is_safe_for_auto_reload(const std::string& url) {

        if (url.empty()) {
            return false;
        }

        std::string clean = url;

        std::transform(
            clean.begin(),
            clean.end(),
            clean.begin(),
            [](unsigned char c) {
                return static_cast<char>(std::tolower(c));
            }
        );

        if (
            clean.find("/checkout") != std::string::npos ||
            clean.find("/payment") != std::string::npos ||
            clean.find("/logout") != std::string::npos
        ) {
            return false;
        }

        return true;
    }

    // عرض عنوان الذاكرة للجافا سكريبت
    uintptr_t get_shared_buffer_ptr() {
        return reinterpret_cast<uintptr_t>(shared_buffer);
    }

    /**
     * ⚡ معالجة النبضة الخام (Raw Pulse Processing)
     * تقرأ البيانات من الذاكرة المشتركة مباشرة
     */
    bool process_raw_touch() {
        // shared_buffer[0] = x, shared_buffer[1] = y...
        return detect_scroll_slop(shared_buffer[0], shared_buffer[1], shared_buffer[2], shared_buffer[3], 1.0f);
    }

    /**
     * 🧼 جلب القائمة الحالية للروابط النشطة بداخل الذاكرة
     */
    std::vector<std::string> get_active_prefetch_list() {
        return std::vector<std::string>(prefetch_cache.begin(), prefetch_cache.end());
    }
};

// =========================================================
// 🌐 ROYAL NETWORK CORE (ملف royal_network_core.cpp)
// =========================================================
class RoyalNetworkCore {
private:
    std::unordered_set<std::string> cache_extensions;
    std::unordered_map<std::string, std::string> mime_types;
    
    // تحويل الـ Regex الثقيل إلى دوال مطابقة نصية فائقة السرعة (String Manipulation)
    // النواة تقوم بمسح النص بلمح البصر دون عمل Allocation إضافي في الذاكرة
    bool contains_substring(const std::string& str, const std::string& sub) const {
        return str.find(sub) != std::string::npos;
    }

public:
    RoyalNetworkCore() {
        // حقن الامتدادات المدعومة مسبقاً في الذاكرة الثنائية
        cache_extensions = {
            ".png", ".jpg", ".jpeg", ".webp", ".avif", ".gif", ".ico", ".svg",
            ".css", ".js", ".mjs", ".woff", ".woff2", ".ttf", ".otf",
            ".mp4", ".webm", ".mp3", ".wav", ".pdf", ".doc", ".docx"
        };

        // خارطة الميم تايبس (MIME Types Map) بسرعة النواة
        mime_types = {
            {".webp", "image/webp"},
            {".avif", "image/avif"},
            {".woff2", "font/woff2"},
            {".mjs", "application/javascript"},
            {".svg", "image/svg+xml"},
            {".html", "text/html"}
        };
    }

    /**
     * 🧠 دالة حساب الهاش السريعة المتوافقة تماماً مع الأندرويد
     * يتم حساب الـ Hash للرابط داخل النواة مباشرة لحماية مسار التخزين
     */
    std::string generate_fast_hash_key(const std::string& input) const {
        // محاكاة سريعة ومحمية خالية من الـ Crashes
        unsigned long hash = 5381;
        for (char c : input) {
            hash = ((hash << 5) + hash) + c;
        }
        
        char hex_string[17];
        snprintf(hex_string, sizeof(hex_string), "%016lx", hash);
        return std::string(hex_string);
    }

    /**
     * 🛡️ حارس البوابة (Is Cacheable): فحص الأمان لفرز البيانات الديناميكية والـ APIs
     * تعيد true إذا كان الرابط قابلاً للتخزين بالقوة وبأعلى معيار أمان
     */
    bool is_url_cacheable(const std::string& url) const {
        if (url.empty()) return false;

        // تنظيف الرابط من معاملات الاستعلام (Query Parameters) بسرعة النواة
        std::string clean_url = url.substr(0, url.find('?'));
        std::transform(clean_url.begin(), clean_url.end(), clean_url.begin(), ::tolower);

        // حظر الـ APIs والبيانات الحساسة والعمليات التجارية فوراً
        if (contains_substring(clean_url, "/api/") || 
            contains_substring(clean_url, "graphql") || 
            contains_substring(clean_url, "/wp-json/") || 
            contains_substring(clean_url, "/rest/") ||
            contains_substring(clean_url, "login") || 
            contains_substring(clean_url, "logout") || 
            contains_substring(clean_url, "signin") ||
            contains_substring(clean_url, "/account") || 
            contains_substring(clean_url, "/profile") ||
            contains_substring(clean_url, "/cart") || 
            contains_substring(clean_url, "/checkout") || 
            contains_substring(clean_url, "/payment")) {
            return false;
        }

        // منع صفحات المعالجة الخلفية الديناميكية
        if (clean_url.length() >= 4) {
            std::string ext4 = clean_url.substr(clean_url.length() - 4);
            if (ext4 == ".php" || ext4 == ".jsp" || ext4 == ".asp") return false;
        }
        if (clean_url.length() >= 5) {
            if (clean_url.substr(clean_url.length() - 5) == ".aspx") return false;
        }

        // فحص الامتدادات المعروفة
        size_t dot_pos = clean_url.find_last_of('.');
        if (dot_pos != std::string::npos) {
            std::string ext = clean_url.substr(dot_pos);
            if (cache_extensions.find(ext) != cache_extensions.end()) {
                return true;
            }
        }

        // 👑 ذكاء اصطياد روابط المتاجر الهيكلية (No Extension Check)
        // إذا كان الرابط لا يحتوي على نقطة في نهايته، فهو صفحة HTML هيكلية للمتجر
        if (clean_url.find_last_of('.') == std::string::npos || 
            clean_url.find_last_of('.') < clean_url.find_last_of('/')) {
            return true;
        }

        return false;
    }

    /**
     * 👑 الفرض الصارم للـ TTL (Time-To-Live Allocation)
     * يعيد مدة صلاحية الملف بالملي ثانية بناءً على نوع المورد
     */
    long long resolve_resource_ttl(const std::string& url) const {
        std::string clean_url = url.substr(0, url.find('?'));
        std::transform(clean_url.begin(), clean_url.end(), clean_url.begin(), ::tolower);

        if (clean_url.length() >= 3) {
            std::string ext3 = clean_url.substr(clean_url.length() - 3);
            if (ext3 == ".js" || ext3 == ".css") return 6LL * 60 * 60 * 1000; // 6 ساعات
        }

        if (contains_substring(clean_url, ".woff2") || contains_substring(clean_url, ".woff")) {
            return 30LL * 24 * 60 * 60 * 1000; // 30 يوم للخطوط الثابتة
        }

        if (contains_substring(clean_url, ".png") || contains_substring(clean_url, ".jpg") || 
            contains_substring(clean_url, ".jpeg") || contains_substring(clean_url, ".webp") || 
            contains_substring(clean_url, ".avif")) {
            return 7LL * 24 * 60 * 60 * 1000; // 7 أيام للصور
        }

        // صفحات المتجر الهيكلية تأخذ 5 دقائق كحد أقصى لضمان تحديث الأسعار والـ مخزون فوريّاً
        if (clean_url.find_last_of('.') == std::string::npos || contains_substring(clean_url, ".html")) {
            return 5LL * 60 * 1000; // 5 دقائق فقط
        }

        return 60LL * 60 * 1000; // ساعة للملفات الأخرى
    }

    /**
     * 🌐 جلب الـ MIME Type الفوري للمورد لمنع تعطل عرض الصفحة
     */
    std::string resolve_mime_type(const std::string& url) const {
        std::string clean_url = url.substr(0, url.find('?'));
        std::transform(clean_url.begin(), clean_url.end(), clean_url.begin(), ::tolower);

        size_t dot_pos = clean_url.find_last_of('.');
        if (dot_pos != std::string::npos) {
            std::string ext = clean_url.substr(dot_pos);
            auto it = mime_types.find(ext);
            if (it != mime_types.end()) {
                return it->second;
            }
        }
        return "application/octet-stream";
    }
};

// =========================================================
// 🏛️ THE MAESTRO: ROYAL NUCLEUS (The Commander Core)
// =========================================================
class RoyalNucleus {
private:
    RoyalIntelPrediction* predictor_ptr = nullptr;
    RoyalNetworkGuardian* guardian_ptr = nullptr;

public:
    // [تعديل جراحي آمن للبيئة - Off-Main-Thread Fusion]
    RoyalNucleus()
        : predictor_ptr(nullptr),
          guardian_ptr(nullptr) {

        predictor_ptr = new RoyalIntelPrediction();
        guardian_ptr = new RoyalNetworkGuardian();

        EM_ASM({
            if (typeof console !== 'undefined') {
                console.log(
                    "👑 ROYAL NUCLEUS: Maestro fused with Intel & Guardian."
                );
            }

            if (
                typeof self !== 'undefined' &&
                typeof self.postMessage === 'function'
            ) {
                self.postMessage({
                    type: 'DRAW_BLUE_SQUARE',
                    text: 'NUCLEUS'
                });
            }
        });
    }

    // دوال الجلب الصريحة (Explicit Getters)
    RoyalIntelPrediction* getPredictor() const {
        return predictor_ptr;
    }

    RoyalNetworkGuardian* getGuardian() const {
        return guardian_ptr;
    }

    ~RoyalNucleus() {
        delete predictor_ptr;
        delete guardian_ptr;

        predictor_ptr = nullptr;
        guardian_ptr = nullptr;
    }
};

// =========================================================
// 🌉 EMSCRIPTEN BINDINGS (دمج جميع Bindings في Nucleus واحد)
// =========================================================
EMSCRIPTEN_BINDINGS(royal_nucleus_module) {
    // كلاس Ignition
    class_<RoyalIgnitionCore>("RoyalIgnitionCore")
        .constructor()
        .function("perform_socket_priming", &RoyalIgnitionCore::perform_socket_priming)
        .function("calculate_ignition_readiness", &RoyalIgnitionCore::calculate_ignition_readiness)
        .function("set_engine_warmed", &RoyalIgnitionCore::set_engine_warmed);
    
    // كلاس الـ Core
    class_<RoyalCoreEngine>("RoyalCoreEngine")
        .constructor()
        .function("set_origin", &RoyalCoreEngine::set_origin)
        .function("evaluate_speculation", &RoyalCoreEngine::evaluate_speculation)
        .function("remove_speculation", &RoyalCoreEngine::remove_speculation)
        .function("analyze_scroll_velocity", &RoyalCoreEngine::analyze_scroll_velocity)
        .function("detect_scroll_slop", &RoyalCoreEngine::detect_scroll_slop)
        .function("get_active_prefetch_list", &RoyalCoreEngine::get_active_prefetch_list)
        .function("get_shared_buffer_ptr", &RoyalCoreEngine::get_shared_buffer_ptr)
        .function("process_raw_touch", &RoyalCoreEngine::process_raw_touch)
        .function("is_same_site_navigation", &RoyalCoreEngine::is_same_site_navigation)
        .function("check_commit_navigation_readiness", &RoyalCoreEngine::check_commit_navigation_readiness)
        .function("is_safe_for_auto_reload", &RoyalCoreEngine::is_safe_for_auto_reload);
    
    // كلاس الـ Network
    class_<RoyalNetworkCore>("RoyalNetworkCore")
        .constructor()
        .function("generate_fast_hash_key", &RoyalNetworkCore::generate_fast_hash_key)
        .function("is_url_cacheable", &RoyalNetworkCore::is_url_cacheable)
        .function("resolve_resource_ttl", &RoyalNetworkCore::resolve_resource_ttl)
        .function("resolve_mime_type", &RoyalNetworkCore::resolve_mime_type);

    // 🌉 [جسر العمالقة]: ربط دوال الوصول للمايسترو بمحركاته الفرعية
    class_<RoyalNucleus>("RoyalNucleus")
        .constructor()
        .function("getPredictor", &RoyalNucleus::getPredictor, allow_raw_pointers())
        .function("getGuardian", &RoyalNucleus::getGuardian, allow_raw_pointers());
    }
