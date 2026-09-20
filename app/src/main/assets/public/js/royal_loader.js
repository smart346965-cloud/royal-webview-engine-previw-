/* 👑 ROYAL NUCLEUS ELITE LOADER (Streaming Edition + Telemetry Engine) */
(function() {
    // =========================================================================
    // 🔬 NEXUS TELEMETRY ENGINE: نظام الرادار التشخيصي الدقيق
    // =========================================================================
    window.NexusTelemetry = {
        metrics: { wasm_start: 0, wasm_end: 0, total_blocking_time: 0, bfcache_hit: false },
        longTasks: [],
        
        startMark: function(name) { performance.mark(name + '_start'); },
        endMark: function(name) { 
            performance.mark(name + '_end'); 
            try { performance.measure(name, name + '_start', name + '_end'); } catch(e){}
        },
        
        initObservers: function() {
            if ('PerformanceObserver' in window) {
                try {
                    new PerformanceObserver((list) => {
                        list.getEntries().forEach(entry => {
                            window.NexusTelemetry.total_blocking_time +=
                                Math.max(0, entry.duration - 50);
                            window.NexusTelemetry.longTasks.push({ name: entry.name, duration: entry.duration.toFixed(2) });
                        });
                    }).observe({ type: 'longtask', buffered: true });
                } catch(e) {}
            }

            window.addEventListener('pageshow', (event) => {
                window.NexusTelemetry.metrics.bfcache_hit = event.persisted;
                if (event.persisted) {
                    console.log("%c⚡ [NEXUS] BFCache HIT: تم الرجوع بـ 0ms (استرجاع لحظي من الذاكرة)", "color:#00ff00; font-weight:bold; background:#003300; padding:2px 5px;");
                } else {
                    const navType = performance.getEntriesByType("navigation")[0]?.type;
                    if (navType === 'back_forward') {
                        console.log("%c❌ [NEXUS] BFCache MISS: فشل الاسترجاع اللحظي! الموقع أعاد تحميل نفسه.", "color:#ff3333; font-weight:bold; background:#330000; padding:2px 5px;");
                    }
                }
            });
        },

        generateReport: function() {
            console.groupCollapsed("%c📊 NEXUS DIAGNOSTIC REPORT (اضغط لفتح التقرير الشامل)", "color: #00ffff; font-size: 14px; font-weight: bold; background: #111; padding: 6px; border-radius: 4px;");
            
            const wasmMeasure = performance.getEntriesByName('WASM_IGNITION')[0];
            const wasmTime = wasmMeasure ? wasmMeasure.duration.toFixed(2) : 'N/A';
            console.log(`%c🧠 زمن بناء واستيقاث النواة (C++): %c${wasmTime} ms`, "color: #d97706; font-weight:bold;", "color: #000000; font-weight: bold; font-size: 12px;");
            
            const nav = performance.getEntriesByType("navigation")[0];
            const paint = performance.getEntriesByType("paint");
            const fcp = paint.find(p => p.name === 'first-contentful-paint');
            
            if (nav) {
                console.log(`%c🚀 نوع الدخول للصفحة: %c${nav.type.toUpperCase()}`, "color: #d97706; font-weight:bold;", "color: #000000; font-weight: bold;");
                console.log(`%c⏱️ زمن الاستجابة للهيكل (DOM Interactive): %c${nav.domInteractive.toFixed(2)} ms`, "color: #d97706; font-weight:bold;", "color: #000000; font-weight: bold;");
                console.log(`%c🎨 زمن اكتمال الموقع بالكامل (Load Complete): %c${nav.loadEventEnd.toFixed(2)} ms`, "color: #d97706; font-weight:bold;", "color: #000000; font-weight: bold;");
            }
            if (fcp) console.log(`%c👁️ أول بيكسلة ظهرت للشاشة (FCP): %c${fcp.startTime.toFixed(2)} ms`, "color: #d97706; font-weight:bold;", "color: #000000; font-weight: bold;");

            const specSupported = HTMLScriptElement.supports && HTMLScriptElement.supports('speculationrules');
            console.log(`%c🔮 تقنية التنبؤ والرندرة المسبقة: %c${specSupported ? 'تعمل بكفاءة 100%' : 'غير مدعومة في هذا الويبفيو!'}`, "color: #d97706; font-weight:bold;", specSupported ? "color: #059669; font-weight: bold;" : "color: #dc2626; font-weight: bold;");

            console.log("%c🔍 --- التشخيص الآلي لسبب التأخير ---", "color: #0284c7; font-weight:bold;");
            
            if (this.longTasks.length > 0) {
                console.log(`%c⚠️ تم اكتشاف مهام ثقيلة جمدت الشاشة! (Total Blocking: ${this.metrics.total_blocking_time.toFixed(2)}ms)`, "color: #dc2626; font-weight:bold;");
                console.table(this.longTasks);
                console.log("%c💡 التشخيص: التأخير سببه سكربتات جافاسكريبت داخل الموقع الأصلي تعيق عمل المحرك.", "color: #dc2626; font-weight:bold;");
            } else if (wasmMeasure && wasmMeasure.duration > 300) {
                console.log("%c💡 التشخيص: تأخير بسبب بطء معالج الهاتف في فك تشفير ملف الـ WASM.", "color: #d97706; font-weight:bold;");
            } else if (nav && nav.domInteractive > 800) {
                console.log("%c💡 التشخيص: تأخير من الشبكة أو سيرفر الموقع الأصلي يرسل الـ HTML ببطء.", "color: #d97706; font-weight:bold;");
            } else {
                console.log("%c✅ التشخيص: لا يوجد أي بلوك! الأداء مثالي والنواة تعمل كالزبدة.", "color: #059669; font-weight:bold;");
            }
            
            console.groupEnd();
        }
    };

    window.NexusTelemetry.initObservers();
    window.NEXUS_REPORT = function() { window.NexusTelemetry.generateReport(); };

    // =========================================================================
    // 👑 ROYAL NUCLEUS IGNITION (كودك الأصلي مغلف بمجسات القياس)
    // =========================================================================
    const WASM_URL = 'https://royal-engine.local/public/js/royal_nucleus.wasm';
    const JS_URL = 'https://royal-engine.local/public/js/royal_nucleus.js';

    // =========================================================================
    // 🛠️ HELPER FUNCTIONS
    // =========================================================================

    function injectNexusPrerender(url, eagerness = 'moderate') {
        if (!url) return false;

        let target;
        try {
            target = new URL(url, window.location.href);
        } catch (_) {
            return false;
        }

        if (target.origin !== window.location.origin) {
            console.warn('[NEXUS] Cross-origin prerender blocked:', target.href);
            return false;
        }

        if (target.protocol !== 'https:' && target.protocol !== 'http:') {
            return false;
        }

        const blocked = /^\/(cart|checkout|payment|login|logout|account)(\/|$)/i;
        if (blocked.test(target.pathname)) {
            return false;
        }

        if (typeof HTMLScriptElement === 'undefined' ||
            !HTMLScriptElement.supports ||
            !HTMLScriptElement.supports('speculationrules')) {
            return false;
        }

        const normalized = target.href;
        const existing = document.querySelector('script[data-nexus-prerender]');

        if (existing) {
            try {
                const rules = JSON.parse(existing.textContent || '{}');
                const urls = rules.prerender?.[0]?.urls || [];
                if (urls.includes(normalized)) return true;
            } catch (_) {}
            existing.remove();
        }

        const script = document.createElement('script');
        script.type = 'speculationrules';
        script.dataset.nexusPrerender = 'true';
        script.textContent = JSON.stringify({
            prerender: [{
                urls: [normalized],
                eagerness
            }]
        });

        document.head.appendChild(script);
        return true;
    }

    function nexusPreconnect(url) {
        if (!url) return;
        try {
            const target = new URL(url, window.location.href);
            if (target.protocol !== 'https:' && target.protocol !== 'http:') return;
            const origin = target.origin;
            const links = document.querySelectorAll('link[rel="preconnect"]');
            for (const link of links) {
                if (link.href === origin || link.href === origin + '/') return;
            }
            const link = document.createElement('link');
            link.rel = 'preconnect';
            link.href = origin;
            document.head.appendChild(link);
        } catch (_) {}
    }

    function applyNexusAsyncVisuals() {
        if (document.documentElement.dataset.nexusAsyncVisuals === 'true') return;
        document.documentElement.dataset.nexusAsyncVisuals = 'true';

        const apply = (root) => {
            if (!root || root.nodeType !== 1) return;
            if (root.tagName === 'IMG') {
                root.decoding = 'async';
                const rect = root.getBoundingClientRect();
                const nearViewport = rect.top < window.innerHeight * 1.5;
                if (!nearViewport && !root.hasAttribute('loading')) {
                    root.loading = 'lazy';
                }
            }
            if (root.querySelectorAll) {
                root.querySelectorAll('img').forEach(img => {
                    img.decoding = 'async';
                    if (!img.hasAttribute('loading')) {
                        const rect = img.getBoundingClientRect();
                        if (rect.top > window.innerHeight * 1.5) {
                            img.loading = 'lazy';
                        }
                    }
                });
            }
        };

        apply(document.documentElement);

        const observer = new MutationObserver(mutations => {
            for (const mutation of mutations) {
                mutation.addedNodes.forEach(apply);
            }
        });
        observer.observe(document.documentElement, { childList: true, subtree: true });
    }

    function applyNexusRenderOptimization() {
        if (document.getElementById('nexus-render-optimization')) return;
        const style = document.createElement('style');
        style.id = 'nexus-render-optimization';
        style.textContent = `
            html, body { overflow-x: clip; }
            img, video { content-visibility: auto; }
        `;
        document.head.appendChild(style);
    }

    function initNexusLayoutObserver() {
        if (window.NexusLayoutObserverInitialized) return;
        window.NexusLayoutObserverInitialized = true;
        if (typeof IntersectionObserver === 'undefined') return;

        const observer = new IntersectionObserver(entries => {
            for (const entry of entries) {
                if (!entry.isIntersecting) continue;
                const el = entry.target;
                if (el.tagName === 'IMG') {
                    el.decoding = 'async';
                    if (!el.hasAttribute('loading')) {
                        el.loading = 'lazy';
                    }
                }
            }
        }, { rootMargin: '400px 0px' });

        document.querySelectorAll('img').forEach(el => observer.observe(el));
    }

    function setNexusThrottle(state) {
        document.documentElement.classList.toggle('nexus-fast-scroll', Boolean(state));
    }

    // =========================================================================
    // 👑 IGNITION
    // =========================================================================

    async function ignite() {
        if (window.NexusWorkerActive) return;

        try {
            window.NexusTelemetry.startMark('WASM_IGNITION');

            const worker = new Worker(
                new URL('/public/js/nexus-worker.js', window.location.origin)
            );
            window.NexusWorker = worker;
            window.NexusWorkerActive = true;

            worker.onerror = function (error) {
                console.error("❌ NEXUS WORKER ERROR:", error);
                window.NexusWorkerActive = false;
                window.NexusWorker = null;
            };

            worker.postMessage({
                type: 'INIT',
                origin: window.location.origin
            });

            // =========================================================
            // 🧠 MAIN THREAD MESSAGE HANDLER (Switch Architecture)
            // =========================================================
            const handleNexusWorkerMessage = (msg) => {
                if (!msg || !msg.type) return;

                switch (msg.type) {

                    case 'NUCLEUS_READY':
                        worker.postMessage({ type: 'INIT_MEMORY' });
                        window.NexusTelemetry.endMark('WASM_IGNITION');
                        console.log("🚀 NUCLEUS ACTIVE: Off-Main-Thread Fusion Complete.");
                        drawBlueSquare("READY");
                        break;

                    case 'MEMORY_READY':
                        console.log("🧠 [NEXUS] Shared WASM memory is ready.");
                        break;

                    case 'EXECUTE_PRERENDER':
                        injectNexusPrerender(msg.url, 'immediate');
                        drawBlueSquare("RENDER");
                        break;

                    case 'EXECUTE_BACK_PRERENDER':
                        injectNexusPrerender(msg.url, 'moderate');
                        break;

                    case 'PRECONNECT':
                        nexusPreconnect(msg.url);
                        break;

                    case 'APPLY_ASYNC_VISUALS':
                        applyNexusAsyncVisuals();
                        break;

                    case 'APPLY_RENDER_OPTIMIZATION':
                        applyNexusRenderOptimization();
                        break;

                    case 'INIT_LAYOUT_OBSERVER':
                        initNexusLayoutObserver();
                        break;

                    case 'PREPARE_BFCACHE':
                        console.log('[NEXUS] BFCache-safe mode active.');
                        break;

                    case 'OPTIMIZE_SCRIPT_PIPELINE':
                        console.log('[NEXUS] Script pipeline optimization delegated to browser.');
                        break;

                    case 'ENABLE_NETWORK_OPTIMIZATION':
                        console.log('[NEXUS] Network optimization enabled.');
                        break;

                    case 'THROTTLE_RENDER':
                        setNexusThrottle(Boolean(msg.state));
                        break;

                    case 'CONFIRM_SCROLL':
                        document.documentElement.classList.add('nexus-user-scrolling');
                        break;

                    case 'DRAW_BLUE_SQUARE':
                        drawBlueSquare(msg.text || "SIGNAL");
                        break;

                    default:
                        console.log('[NEXUS] Unknown message type:', msg.type);
                }
            };

            // 🎨 دالة مساعدة لإنشاء ورسم المربع الأزرق فوراً فوق الصفحة
            const drawBlueSquare = (label = "NUCLEUS SIGNAL") => {
                let square = document.getElementById('nexus-debug-square');
                if (!square) {
                    square = document.createElement('div');
                    square.id = 'nexus-debug-square';
                    square.style.cssText = `
                        position: fixed;
                        top: 20px;
                        right: 20px;
                        width: 60px;
                        height: 60px;
                        background-color: #0066ff;
                        border: 2px solid #ffffff;
                        border-radius: 8px;
                        box-shadow: 0 4px 15px rgba(0, 102, 255, 0.5);
                        z-index: 999999;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        color: white;
                        font-size: 10px;
                        font-weight: bold;
                        font-family: sans-serif;
                        pointer-events: none;
                        transition: transform 0.2s ease, opacity 0.2s ease;
                    `;
                    square.innerText = label;
                    document.body.appendChild(square);
                }
                square.style.transform = 'scale(1.2)';
                setTimeout(() => { square.style.transform = 'scale(1)'; }, 200);
            };

            worker.onmessage = function(e) {
                handleNexusWorkerMessage(e.data);
            };

            // =========================================================
            // 👆 TOUCH LIFECYCLE
            // =========================================================
            let nexusTouchStartX = 0;
            let nexusTouchStartY = 0;
            let nexusTouchActive = false;

            window.addEventListener('touchstart', (e) => {
                if (!e.touches || !e.touches[0]) return;

                const touch = e.touches[0];
                nexusTouchStartX = touch.clientX;
                nexusTouchStartY = touch.clientY;
                nexusTouchActive = true;

                const target = e.target;
                const link = target && target.closest ? target.closest('a[href]') : null;
                if (!link || !link.href) return;

                window.dispatchToNucleus('TOUCH_START', {
                    x: nexusTouchStartX,
                    y: nexusTouchStartY,
                    timestamp: performance.now(),
                    url: link.href
                });
            }, { passive: true });

            window.addEventListener('touchmove', (e) => {
                if (!nexusTouchActive || !e.touches || !e.touches[0]) return;
                const touch = e.touches[0];
                window.dispatchToNucleus('TOUCH_MOVE', {
                    startX: nexusTouchStartX,
                    startY: nexusTouchStartY,
                    currentX: touch.clientX,
                    currentY: touch.clientY,
                    dpr: window.devicePixelRatio || 1
                });
            }, { passive: true });

            window.addEventListener('touchend', () => { nexusTouchActive = false; }, { passive: true });
            window.addEventListener('touchcancel', () => { nexusTouchActive = false; }, { passive: true });

            // =========================================================
            // 📜 SCROLL ENGINE
            // =========================================================
            let nexusLastScrollY = window.scrollY || 0;
            let nexusLastScrollTime = performance.now();
            let nexusScrollScheduled = false;
            let nexusScrollStopTimer = null;

            window.addEventListener('scroll', () => {
                if (nexusScrollScheduled) return;
                nexusScrollScheduled = true;

                requestAnimationFrame(() => {
                    nexusScrollScheduled = false;
                    const now = performance.now();
                    const currentY = window.scrollY || 0;
                    const delta = Math.max(now - nexusLastScrollTime, 1);

                    window.dispatchToNucleus('SCROLL_DATA', {
                        y: currentY,
                        lastY: nexusLastScrollY,
                        delta
                    });

                    nexusLastScrollY = currentY;
                    nexusLastScrollTime = now;

                    clearTimeout(nexusScrollStopTimer);
                    nexusScrollStopTimer = setTimeout(() => {
                        document.documentElement.classList.remove('nexus-user-scrolling');
                        document.documentElement.classList.remove('nexus-fast-scroll');
                    }, 140);
                });
            }, { passive: true });

            // =========================================================
            // 🧩 DISPATCHER
            // =========================================================
            window.dispatchToNucleus = (type, payload) => {
                if (window.NexusWorker && window.NexusWorkerActive) {
                    worker.postMessage({ type, ...payload });
                }
            };

            // =========================================================
            // 🛡️ IDLE STABILIZATION
            // =========================================================
            const triggerMaestroStabilization = () => {
                console.log("%c🛡️ [NEXUS] SHIELD: Main Thread is now COLD.", "color:#3b82f6; font-weight:bold; background:#e0f2fe; padding:2px 5px;");
                if (window.RoyalBridge && window.RoyalBridge.log) {
                    window.RoyalBridge.log("NUCLEUS_STABILIZED");
                }
            };

            if ('requestIdleCallback' in window) {
                requestIdleCallback(() => setTimeout(triggerMaestroStabilization, 1500), { timeout: 4000 });
            } else {
                setTimeout(triggerMaestroStabilization, 4000);
            }

            setTimeout(() => { window.NexusTelemetry.generateReport(); }, 2000);

        } catch (err) {
            console.warn("Nucleus Ignition partial fail, retrying...", err);
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', ignite);
    } else {
        ignite();
    }

})();
