/**
 * =========================================================
 * 👑 ROYAL ENGINE CORE (V2.0 - The Lean Orchestrator)
 * File: index.js
 * =========================================================
 * Architecture: Direct Execution, Phased Scheduling, Zero Overhead.
 * Philosophy: Keep it simple, protect the CPU, manage the time.
 */

const RoyalEngine = (function () {
    'use strict';

    // 🛡️ حماية من التكرار
    if (window.__ROYAL_ORCHESTRATOR_ACTIVE__) return window.__ROYAL_ORCHESTRATOR_ACTIVE__;
    window.__ROYAL_ORCHESTRATOR_ACTIVE__ = true;

    // console.log("👑 ROYAL ENGINE: Orchestrator Online. Awaiting DOM...");

    // =========================================================
    // 🛡️ 1. SAFETY WRAPPER (صندوق الرمل لحماية المتجر)
    // =========================================================
    function safeExecute(moduleName, fn) {
        if (typeof fn !== 'function') return;
        try {
            // const t0 = performance.now();
            fn();
            // const t1 = performance.now();
            // console.log(`✅ [${moduleName}] executed in ${(t1 - t0).toFixed(2)}ms`);
        } catch (error) {
            console.error(`🚨 [ROYAL CRASH] Module: ${moduleName} failed!`, error);
        }
    }

    // =========================================================
    // ⏱️ 2. PHASED EXECUTION (خريطة المحركات الشاملة)
    // =========================================================
    const moduleState = {}; // ذاكرة ديناميكية تسجل من اشتغل ومن لا يزال نائماً

    // 👑 خريطة المحركات (The Master Roster): هنا نحدد ترتيب وأولوية كل ملف في مشروعك!
    const SystemModules = {
        // ⚡ المرحلة 1: العضلات الحرجة 
        DOM_READY:[
            'RoyalChameleon',   // 1. تلوين شريط الهاتف فوراً
            // ❌ تم حذف 'RoyalInteraction' من هنا لنقله للودر
            'NexusUIEngine'     // 2. تجهيز واجهات الإشعارات (Bar & Sheet)
        ],
        // 🧘‍♂️ المرحلة 2: العضلات الثقيلة 
        IDLE:[
            // ❌ تم حذف 'RoyalSpeculator' من هنا لنقله للودر
            'NexusTracker',      // 1. تتبع سلوك المستخدم (السلة والاهتمام)
            'NexusCapabilities', // 2. تجهيز عتاد الهاتف (GPS, Camera)
            'RoyalOneSignal'     // 3. تهيئة الإشعارات الخارجية
        ]
    };

    // دالة ديناميكية لتشغيل أي قائمة من العضلات بأمان
    function runModuleGroup(groupName) {
        const modules = SystemModules[groupName];
        if (!modules) return;

        modules.forEach(modName => {
            // إذا كانت العضلة موجودة في الـ Window ولم تعمل مسبقاً
            if (window[modName] && !moduleState[modName]) {
                safeExecute(modName, window[modName].init);
                moduleState[modName] = true; // تسجيل أنها عملت لكي لا تتكرر
            }
        });
    }

    function runDomReadyModules() {
        runModuleGroup('DOM_READY');
    }

    function runIdleModules() {
        runModuleGroup('IDLE');
    }

    // =========================================================
    // 🚀 3. BOOT SEQUENCE (تسلسل الإقلاع)
    // =========================================================
    function boot() {
        // 1. تشغيل عضلات التفاعل فوراً
        runDomReadyModules();

        // 2. تشغيل عضلات التنبؤ في وقت الفراغ
        const requestIdle = window.requestIdleCallback || function (cb) { setTimeout(cb, 200); };
        requestIdle(runIdleModules);
    }

    // ننتظر حتى يكتمل بناء هيكل الصفحة (DOM) قبل الإقلاع
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', boot);
    } else {
        boot();
    }

    // =========================================================
    // 🔄 4. SPA ROUTE MONITOR (مراقب التنقل الداخلي)
    // =========================================================
    // متاجر React لا تقوم بتحديث الصفحة، لذلك يجب أن نراقب التغييرات لإعادة تشغيل العضلات
    let lastUrl = location.href;
    let routeChangeScheduled = false;

    const routeObserver = new MutationObserver(() => {
        if (location.href === lastUrl) return;

        lastUrl = location.href;

        if (routeChangeScheduled) return;
        routeChangeScheduled = true;

        setTimeout(() => {
            routeChangeScheduled = false;

            console.log("🔄 ROYAL ENGINE: SPA Route Changed. Re-igniting modules...");

            window.dispatchEvent(new CustomEvent('royal:route-change', {
                detail: { url: lastUrl }
            }));

            requestAnimationFrame(() => {
                runDomReadyModules();

                const requestIdle = window.requestIdleCallback || function (cb) { setTimeout(cb, 200); };
                requestIdle(runIdleModules);
            });

        }, 150); // debounce time
    });

    // نراقب الـ body لأن React يغير محتواه عند الانتقال بين الصفحات
    routeObserver.observe(document.body, { childList: true, subtree: true });

})();
