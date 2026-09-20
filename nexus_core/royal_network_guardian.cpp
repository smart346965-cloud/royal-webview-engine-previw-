#include <emscripten/emscripten.h>
#include <emscripten/bind.h>

#include <string>
#include <unordered_map>
#include <chrono>
#include <vector>
#include <algorithm>
#include <unordered_set>
#include <cctype>

using namespace emscripten;


/**
 * 🛡️ ROYAL NETWORK GUARDIAN
 *
 * Worker-safe Network Decision Engine.
 *
 * ملاحظة:
 * الـGuardian لا يحاول تجاوز HTTP Cache-Control
 * ولا يزوّر حالة الشبكة.
 *
 * وظيفته:
 * 1. تحليل الطلب.
 * 2. تحديد استراتيجية الكاش.
 * 3. حماية APIs.
 * 4. كشف الدومينات الثقيلة.
 * 5. إرسال أوامر DOM إلى Main Thread عند الحاجة.
 */
class RoyalNetworkGuardian {

private:

    struct CacheRule {
        long long ttl;
        bool persistent_hint;
        bool code_caching;
    };

    std::unordered_map<std::string, CacheRule> registry;

    long long session_start_time;

    std::unordered_set<std::string> parasitic_registry;

    bool is_nucleus_stabilized = false;


    void fast_lower(std::string& s) const {
        std::transform(
            s.begin(),
            s.end(),
            s.begin(),
            [](unsigned char c) {
                return static_cast<char>(std::tolower(c));
            }
        );
    }


    bool contains_any(
        const std::string& value,
        const std::vector<std::string>& patterns
    ) const {

        for (const auto& pattern : patterns) {
            if (value.find(pattern) != std::string::npos) {
                return true;
            }
        }

        return false;
    }


    void dispatch_main_thread_command(
        const std::string& type,
        const std::string& value = ""
    ) {

        EM_ASM_({
            const type = UTF8ToString($0);
            const value = UTF8ToString($1);

            if (
                typeof self !== 'undefined' &&
                typeof self.postMessage === 'function'
            ) {

                const message = {
                    type: type
                };

                if (value.length > 0) {
                    message.value = value;
                }

                self.postMessage(message);
            }

        }, type.c_str(), value.c_str());
    }


public:

    RoyalNetworkGuardian() {

        session_start_time =
            std::chrono::system_clock::now()
            .time_since_epoch()
            .count();


        /**
         * Static assets.
         *
         * TTL هنا قرار داخلي للنواة.
         * التنفيذ النهائي للكاش يبقى للـService Worker
         * وHTTP headers.
         */
        registry[".js"] = {
            7LL * 24 * 60 * 60 * 1000,
            true,
            true
        };

        registry[".css"] = {
            7LL * 24 * 60 * 60 * 1000,
            true,
            false
        };

        registry[".woff2"] = {
            30LL * 24 * 60 * 60 * 1000,
            true,
            false
        };

        registry[".png"] = {
            7LL * 24 * 60 * 60 * 1000,
            true,
            false
        };

        registry[".jpg"] = {
            7LL * 24 * 60 * 60 * 1000,
            true,
            false
        };

        registry[".webp"] = {
            7LL * 24 * 60 * 60 * 1000,
            true,
            false
        };

        registry["html"] = {
            5LL * 60 * 1000,
            false,
            false
        };


        init_shield_registry();
    }


    /**
     * 🛡️ قائمة الدومينات الثقيلة.
     */
    void init_shield_registry() {

        parasitic_registry = {

            "gorgias.chat",
            "connect.facebook.net",
            "google-analytics.com",
            "googletagmanager.com",
            "klaviyo.com",
            "luckyorange.com",
            "hotjar.com",
            "snapchat.com",
            "tiktok.com",
            "ads-twitter.com"
        };
    }


    /**
     * ⚡ قرار استراتيجية الطلب.
     *
     * القيم:
     *
     * NETWORK_ONLY
     * CACHE_FIRST
     * STALE_WHILE_REVALIDATE
     */
    val evaluate_request_strategy(
        std::string url
    ) {

        fast_lower(url);


        // الطلبات الحساسة لا تدخل الكاش.
        if (
            url.find("/api/") != std::string::npos ||
            url.find("graphql") != std::string::npos ||
            url.find("token") != std::string::npos ||
            url.find("authorization") != std::string::npos ||
            url.find("cookie") != std::string::npos ||
            url.find("/login") != std::string::npos ||
            url.find("/logout") != std::string::npos ||
            url.find("/checkout") != std::string::npos ||
            url.find("/payment") != std::string::npos
        ) {

            return val("NETWORK_ONLY");
        }


        size_t dot_pos = url.find_last_of('.');

        if (dot_pos != std::string::npos) {

            std::string ext =
                url.substr(dot_pos);

            auto it = registry.find(ext);

            if (it != registry.end()) {

                if (it->second.persistent_hint) {
                    return val("CACHE_FIRST");
                }
            }
        }


        return val("STALE_WHILE_REVALIDATE");
    }


    /**
     * 🧬 FNV-1a Atomic Key
     */
    std::string compute_atomic_key(
        const std::string& url
    ) const {

        unsigned int hash = 0x811c9dc5u;

        for (unsigned char c : url) {

            hash ^= static_cast<unsigned int>(c);

            hash *= 0x01000193u;
        }

        char hex[9];

        snprintf(
            hex,
            sizeof(hex),
            "%08x",
            hash
        );

        return std::string(hex);
    }


    /**
     * 🌐 Network Health
     */
    bool should_throttle_network(
        double current_latency
    ) const {

        return current_latency > 500.0;
    }


    /**
     * 👑 Critical assets
     */
    bool is_critical_asset(
        const std::string& url
    ) const {

        return (
            url.find("main.js") != std::string::npos ||
            url.find("style.css") != std::string::npos ||
            url.find("theme.css") != std::string::npos
        );
    }


    /**
     * 🖼️ Async Visuals
     *
     * DOM implementation is delegated to Main Thread.
     */
    void enforce_async_visuals() {

        dispatch_main_thread_command(
            "APPLY_ASYNC_VISUALS"
        );
    }


    /**
     * ⚡ Bytecode optimization
     *
     * لا نحاول اختراع V8 cache API.
     *
     * Chrome/V8 يدير code caching داخلياً.
     *
     * هذه الدالة الآن ترسل إشارة اختيارية للـMain Thread
     * إذا كان لديك Service Worker يريد التعامل معها.
     */
    void trigger_bytecode_opt() {

        dispatch_main_thread_command(
            "OPTIMIZE_SCRIPT_PIPELINE"
        );
    }


    /**
     * 🌐 Preconnect
     *
     * لا ننفذ DOM من Worker.
     */
    void maintain_hot_socket(
        const std::string& domain
    ) {

        if (domain.empty()) return;

        dispatch_main_thread_command(
            "PRECONNECT",
            domain
        );
    }


    /**
     * 🧠 Stubborn strategy
     *
     * الاسم محفوظ للتوافق مع الـAPI.
     *
     * لكننا لم نعد ندعي أن النواة تستطيع
     * تجاوز HTTP Cache-Control.
     */
    val get_stubborn_strategy(
        std::string url
    ) {

        fast_lower(url);

        size_t dot_pos =
            url.find_last_of('.');


        if (dot_pos != std::string::npos) {

            std::string ext =
                url.substr(dot_pos);

            auto it = registry.find(ext);

            if (
                it != registry.end() &&
                it->second.persistent_hint
            ) {

                return val("CACHE_FIRST");
            }
        }


        return val("DEFAULT_STRATEGY");
    }


    /**
     * ⚡ V8 code caching
     *
     * Chrome يديره داخلياً.
     * Service Worker لا يملك API لحفظ V8 bytecode مباشرة.
     */
    void force_bytecode_persistence() {

        dispatch_main_thread_command(
            "OPTIMIZE_SCRIPT_PIPELINE"
        );
    }


    /**
     * 🚀 Network Turbo
     *
     * ممنوع تزوير navigator.connection.
     *
     * بدلاً من ذلك نرسل إشارة للـMain Thread
     * لتطبيق تحسينات fetch/preconnect الآمنة.
     */
    void activate_network_turbo() {

        dispatch_main_thread_command(
            "ENABLE_NETWORK_OPTIMIZATION"
        );
    }


    /**
     * 🛡️ Domain Isolation
     */
    bool should_isolate_domain(
        std::string url
    ) {

        fast_lower(url);


        for (const auto& domain :
             parasitic_registry) {

            if (
                url.find(domain) !=
                std::string::npos
            ) {

                return true;
            }
        }


        return false;
    }


    /**
     * 👑 Stabilization
     */
    void mark_stabilized() {

        is_nucleus_stabilized = true;

        EM_ASM({
            if (
                typeof console !== 'undefined'
            ) {
                console.log(
                    "🛡️ NUCLEUS: Stabilized. ",
                    "Guardian is now in adaptive mode."
                );
            }
        });
    }


    bool is_stabilized() const {

        return is_nucleus_stabilized;
    }
};


EMSCRIPTEN_BINDINGS(royal_guardian_module) {

    class_<RoyalNetworkGuardian>(
        "RoyalNetworkGuardian"
    )

    .constructor()

    .function(
        "evaluate_request_strategy",
        &RoyalNetworkGuardian::evaluate_request_strategy
    )

    .function(
        "compute_atomic_key",
        &RoyalNetworkGuardian::compute_atomic_key
    )

    .function(
        "should_throttle_network",
        &RoyalNetworkGuardian::should_throttle_network
    )

    .function(
        "is_critical_asset",
        &RoyalNetworkGuardian::is_critical_asset
    )

    .function(
        "enforce_async_visuals",
        &RoyalNetworkGuardian::enforce_async_visuals
    )

    .function(
        "trigger_bytecode_opt",
        &RoyalNetworkGuardian::trigger_bytecode_opt
    )

    .function(
        "maintain_hot_socket",
        &RoyalNetworkGuardian::maintain_hot_socket
    )

    .function(
        "get_stubborn_strategy",
        &RoyalNetworkGuardian::get_stubborn_strategy
    )

    .function(
        "force_bytecode_persistence",
        &RoyalNetworkGuardian::force_bytecode_persistence
    )

    .function(
        "activate_network_turbo",
        &RoyalNetworkGuardian::activate_network_turbo
    )

    .function(
        "should_isolate_domain",
        &RoyalNetworkGuardian::should_isolate_domain
    )

    .function(
        "mark_stabilized",
        &RoyalNetworkGuardian::mark_stabilized
    )

    .function(
        "is_stabilized",
        &RoyalNetworkGuardian::is_stabilized
    );
}
