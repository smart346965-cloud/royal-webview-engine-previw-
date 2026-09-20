/**
 * 🧠 NEXUS NUCLEUS WORKER - ELITE EDITION
 * =========================================================
 * المضيف المستقل لنواة الـ C++ (Off-Main-Thread Architecture)
 */

// 1. استيراد شفرة الربط التي تم طبخها في GitHub Actions
importScripts('royal_nucleus.js');

let WasmModule = null;
let Nexus = null;
let sharedWasmMemoryView = null;
let initialized = false;
let initializing = false;

let scrollIntentConfirmed = false;
let scrollThrottleActive = false;

// 🔥 قائمة الأوامر التي تحتاج تمريرها إلى Main Thread
const DOM_COMMANDS = new Set([
    'APPLY_RENDER_OPTIMIZATION',
    'INIT_LAYOUT_OBSERVER',
    'EXECUTE_BACK_PRERENDER',
    'PREPARE_BFCACHE',
    'APPLY_ASYNC_VISUALS',
    'OPTIMIZE_SCRIPT_PIPELINE',
    'ENABLE_NETWORK_OPTIMIZATION',
    'PRECONNECT'
]);

/**
 * 🚀 مرحلة الانصهار (Fusion) داخل الـ Worker
 */
async function initNucleus(origin = '') {
    if (initialized || initializing) {
        return;
    }

    initializing = true;

    try {
        WasmModule = await createRoyalNucleusModule({
            print: (text) => console.log('🛰️ WORKER_WASM:', text),
            printErr: (text) => console.error('⚠️ WORKER_WASM_ERR:', text),
            locateFile: (path) => path
        });

        const Maestro = new WasmModule.RoyalNucleus();

        Nexus = {
            Maestro,
            Predictor: Maestro.getPredictor(),
            Guardian: Maestro.getGuardian(),
            Ignition: new WasmModule.RoyalIgnitionCore(),
            Core: new WasmModule.RoyalCoreEngine(),
            Network: new WasmModule.RoyalNetworkCore()
        };

        if (origin) {
            Nexus.Core.set_origin(origin);
        }

        initialized = true;

        self.postMessage({
            type: 'NUCLEUS_READY'
        });

        console.log('🏆 NUCLEUS WORKER: Maestro is alive.');

    } catch (e) {
        console.error('❌ WORKER_INIT_FAILED:', e);

        WasmModule = null;
        Nexus = null;

        self.postMessage({
            type: 'NUCLEUS_ERROR',
            error: String(e)
        });

    } finally {
        initializing = false;
    }
}

/**
 * 📨 معالج الرسائل المركزي
 */
function handleWorkerMessage(data) {
    if (!data || !data.type) return;

    // ---------------------------------------------------------
    // INIT
    // ---------------------------------------------------------
    if (data.type === 'INIT') {
        initNucleus(data.origin || '');
        return;
    }

    // لا ننفذ أي أمر قبل جاهزية WASM
    if (!initialized || !Nexus) {
        return;
    }

    // ---------------------------------------------------------
    // INIT_MEMORY
    // ---------------------------------------------------------
    if (data.type === 'INIT_MEMORY') {
        if (!Nexus || !Nexus.Core || !WasmModule || !WasmModule.HEAPF32) {
            console.error('❌ WASM memory is not ready.');
            return;
        }

        try {
            const ptr = Nexus.Core.get_shared_buffer_ptr();

            if (!ptr) {
                console.error('❌ Invalid shared buffer pointer.');
                return;
            }

            sharedWasmMemoryView = new Float32Array(
                WasmModule.HEAPF32.buffer,
                ptr,
                10
            );

            self.postMessage({
                type: 'MEMORY_READY'
            });

            console.log('⚡ WORKER: Zero-Allocation Memory Pool Linked.');

        } catch (error) {
            console.error('❌ MEMORY_LINK_FAILED:', error);
        }

        return;
    }

    // ---------------------------------------------------------
    // TOUCH_START
    // ---------------------------------------------------------
    if (data.type === 'TOUCH_START') {
        if (!Nexus || !Nexus.Predictor || !Nexus.Core) {
            return;
        }

        const url = typeof data.url === 'string' ? data.url : '';
        if (!url) {
            return;
        }

        let willClick = false;

        try {
            willClick = Nexus.Predictor.analyze_pointer_intent(
                Number(data.x) || 0,
                Number(data.y) || 0,
                Number(data.timestamp) || Date.now()
            );
        } catch (error) {
            console.warn('⚠️ Predictor unavailable:', error);
            return;
        }

        if (!willClick) {
            return;
        }

        if (!Nexus.Core.evaluate_speculation(url)) {
            return;
        }

        if (sharedWasmMemoryView) {
            sharedWasmMemoryView[0] = Number(data.x) || 0;
            sharedWasmMemoryView[1] = Number(data.y) || 0;
            sharedWasmMemoryView[2] = Number(data.x) || 0;
            sharedWasmMemoryView[3] = Number(data.y) || 0;
        }

        scrollIntentConfirmed = false;

        self.postMessage({
            type: 'EXECUTE_PRERENDER',
            url
        });

        return;
    }

    // ---------------------------------------------------------
    // TOUCH_MOVE
    // ---------------------------------------------------------
    if (data.type === 'TOUCH_MOVE') {
        if (!Nexus || !Nexus.Core) {
            return;
        }

        const intentional = Nexus.Core.detect_scroll_slop(
            Number(data.startX) || 0,
            Number(data.startY) || 0,
            Number(data.currentX) || 0,
            Number(data.currentY) || 0,
            Math.max(Number(data.dpr) || 1, 1)
        );

        if (intentional && !scrollIntentConfirmed) {
            scrollIntentConfirmed = true;

            self.postMessage({
                type: 'CONFIRM_SCROLL'
            });
        }

        return;
    }

    // ---------------------------------------------------------
    // SCROLL_DATA
    // ---------------------------------------------------------
    if (data.type === 'SCROLL_DATA') {
        if (!Nexus || !Nexus.Core) {
            return;
        }

        const delta = Number(data.delta);
        if (!Number.isFinite(delta) || delta <= 0) {
            return;
        }

        const fast = Nexus.Core.analyze_scroll_velocity(
            Number(data.y) || 0,
            Number(data.lastY) || 0,
            delta
        );

        if (fast && !scrollThrottleActive) {
            scrollThrottleActive = true;

            self.postMessage({
                type: 'THROTTLE_RENDER',
                state: true
            });
        }

        if (!fast && scrollThrottleActive) {
            scrollThrottleActive = false;

            self.postMessage({
                type: 'THROTTLE_RENDER',
                state: false
            });
        }

        return;
    }

    // ---------------------------------------------------------
    // 🔥 DOM_COMMANDS: تمريرها إلى Main Thread
    // ---------------------------------------------------------
    if (DOM_COMMANDS.has(data.type)) {
        self.postMessage({
            type: data.type,
            url: data.value || data.url || null
        });
        return;
    }
}

// =========================================================
// 🚀 مستمع الرسائل الرئيسي
// =========================================================
self.onmessage = function (e) {
    handleWorkerMessage(e.data);
};
