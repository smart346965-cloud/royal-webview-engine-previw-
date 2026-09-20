#include <emscripten/emscripten.h>
#include <emscripten/bind.h>
#include <cmath>
#include <string>

using namespace emscripten;

/**
 * 👑 ROYAL INTEL PREDICTION
 *
 * Worker-safe edition.
 *
 * القاعدة:
 * - الحسابات الداخلية تعمل داخل Worker.
 * - أي عملية DOM / Speculation Rules ترسل أمرًا إلى Main Thread.
 * - لا يوجد document/window داخل Worker.
 */
class RoyalIntelPrediction {
private:
    float last_touch_x = 0.0f;
    float last_touch_y = 0.0f;
    long long last_touch_time = 0;
    bool is_preloading_active = false;

    // إرسال أمر إلى Main Thread فقط.
    void dispatch_main_thread_command(
        const std::string& type,
        const std::string& url = ""
    ) {
        EM_ASM_({
            const type = UTF8ToString($0);
            const url = UTF8ToString($1);

            if (typeof self !== 'undefined' &&
                typeof self.postMessage === 'function') {

                const message = { type: type };

                if (url.length > 0) {
                    message.url = url;
                }

                self.postMessage(message);
            }
        }, type.c_str(), url.c_str());
    }

public:
    RoyalIntelPrediction() {}

    /**
     * 👆 تحليل نية النقر
     *
     * Worker-safe بالكامل.
     */
    bool analyze_pointer_intent(
        float x,
        float y,
        long long timestamp
    ) {
        float dx = std::abs(x - last_touch_x);
        float dy = std::abs(y - last_touch_y);

        long long dt = timestamp - last_touch_time;

        // أول حدث لا يجب أن يعتبر نية نقر.
        if (last_touch_time == 0) {
            last_touch_x = x;
            last_touch_y = y;
            last_touch_time = timestamp;
            return false;
        }

        bool intent =
            dx < 5.0f &&
            dy < 5.0f &&
            dt > 100 &&
            dt < 2000;

        last_touch_x = x;
        last_touch_y = y;
        last_touch_time = timestamp;

        if (intent) {
            is_preloading_active = true;
        }

        return intent;
    }

    /**
     * 🔮 طلب حقن Speculation Rules
     *
     * لا يلمس DOM.
     * Worker -> Main Thread.
     */
    void inject_speculation_atomic(
        const std::string& url
    ) {
        if (url.empty()) return;

        dispatch_main_thread_command(
            "EXECUTE_PRERENDER",
            url
        );
    }

    /**
     * 👻 Ghost Rendering
     *
     * الاسم محفوظ حتى لا نكسر الـAPI.
     *
     * التنفيذ الحقيقي يتم في Main Thread.
     */
    void ghost_render_sequence(
        const std::string& url
    ) {
        if (url.empty()) return;

        dispatch_main_thread_command(
            "EXECUTE_PRERENDER",
            url
        );
    }

    /**
     * 🌪️ GPU / Rendering Optimization
     *
     * لا يمكن التحكم بخيط Compositor مباشرة من C++ Worker.
     * نرسل الأمر للـMain Thread ليطبق التحسينات الآمنة.
     */
    void offload_rendering_to_gpu() {
        dispatch_main_thread_command(
            "APPLY_RENDER_OPTIMIZATION"
        );
    }

    /**
     * 👆 Layout Pre-computation
     *
     * IntersectionObserver هو DOM API،
     * لذلك يجب أن يعمل في Main Thread.
     */
    void precompute_page_layout() {
        dispatch_main_thread_command(
            "INIT_LAYOUT_OBSERVER"
        );
    }

    /**
     * 🔄 Back-Step Oracle
     *
     * يرسل طلب prerender للصفحة السابقة إلى Main Thread.
     */
    void predict_back_step(
        const std::string& previous_url
    ) {
        if (previous_url.empty()) return;

        dispatch_main_thread_command(
            "EXECUTE_BACK_PRERENDER",
            previous_url
        );
    }

    /**
     * ⚡ BFCache Optimization
     *
     * لا نحاول "إجبار" BFCache.
     * فقط نرسل طلب تنظيف handlers إن كان هناك كود
     * خارجي يحتاج ذلك.
     */
    void lock_current_dom_state() {
        dispatch_main_thread_command(
            "PREPARE_BFCACHE"
        );
    }

    /**
     * حالة المحرك.
     */
    bool is_preloading() const {
        return is_preloading_active;
    }

    void reset_preloading() {
        is_preloading_active = false;
    }
};


EMSCRIPTEN_BINDINGS(royal_intel_module) {

    class_<RoyalIntelPrediction>("RoyalIntelPrediction")
        .constructor()

        .function(
            "analyze_pointer_intent",
            &RoyalIntelPrediction::analyze_pointer_intent
        )

        .function(
            "inject_speculation_atomic",
            &RoyalIntelPrediction::inject_speculation_atomic
        )

        .function(
            "ghost_render_sequence",
            &RoyalIntelPrediction::ghost_render_sequence
        )

        .function(
            "offload_rendering_to_gpu",
            &RoyalIntelPrediction::offload_rendering_to_gpu
        )

        .function(
            "precompute_page_layout",
            &RoyalIntelPrediction::precompute_page_layout
        )

        .function(
            "predict_back_step",
            &RoyalIntelPrediction::predict_back_step
        )

        .function(
            "lock_current_dom_state",
            &RoyalIntelPrediction::lock_current_dom_state
        )

        .function(
            "is_preloading",
            &RoyalIntelPrediction::is_preloading
        )

        .function(
            "reset_preloading",
            &RoyalIntelPrediction::reset_preloading
        );
}
