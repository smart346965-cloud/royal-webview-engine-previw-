/**
 * =========================================================
 * 🎨 ROYAL CHAMELEON ENGINE (V2.0 - The Flawless Illusion)
 * File: royal-chameleon.js
 * =========================================================
 * Architecture: Dynamic Theme-Color, Luminance Detection, Scroll Sync.
 * Fixes: Null Pointer Protection, CPU-Safe SPA Observer, Scroll Throttling.
 */

(function () {
    'use strict';

    const MODULE_NAME = 'RoyalChameleon';

    // 🛡️ حماية من التكرار
    if (window.__ROYAL_CHAMELEON__) return;
    window.__ROYAL_CHAMELEON__ = true;

    const Chameleon = {
        init: function (context) {
            const { Logger } = context;

            // 👑 تأجيل التقاط الألوان حتى يكتمل تحميل الصفحة وتهدأ الموارد
            const requestIdle = window.requestIdleCallback || function (cb) { setTimeout(cb, 2000); };
            requestIdle(() => this.captureBrandColors());

            Logger.info(`[${MODULE_NAME}] Brand Identity Sniper Online.`);
        },

        /**
         * 🎨 قناص الهوية المزدوج: يستخرج اللون الأساسي (للأزرار) والثانوي (للخلفيات)
         */
        captureBrandColors: function () {
            try {
                // إذا تم الحفظ مسبقاً، لا ترهق المعالج مرة أخرى
                if (localStorage.getItem('royal_brand_identity')) return;

                function getValidColor(el) {
                    if (!el) return null;
                    const bg = getComputedStyle(el).backgroundColor;
                    return (bg && bg !== 'rgba(0, 0, 0, 0)' && bg !== 'transparent') ? bg : null;
                }

                // 🥇 1. اللون الأساسي (Primary): نستخرجه من أزرار الشراء أو الإضافة للسلة
                let primaryColor = 
                    getValidColor(document.querySelector('button.add-to-cart, .btn-primary, [role="button"]')) || 
                    getValidColor(document.querySelector('button, .btn')) || 
                    'rgb(15, 23, 42)'; // لون افتراضي (كحلي فخم)

                // 🥈 2. اللون الثانوي (Secondary): نستخرجه من الهيدر أو خلفية الصفحة
                let secondaryColor = 
                    getValidColor(document.querySelector('header, .header, .navbar, [role="banner"]')) || 
                    getValidColor(document.body) || 
                    'rgb(248, 250, 252)'; // لون افتراضي (فضي فاتح)

                // 🧠 حفظ الهوية المزدوجة ليستخدمها ملف الأوفلاين
                localStorage.setItem('royal_brand_identity', JSON.stringify({
                    primary: primaryColor,
                    secondary: secondaryColor
                }));

            } catch (e) {
                // صامت تماماً
            }
        }
    };

    // =========================================================
    // 📦 REGISTER MODULE (تسجيل العضلة في المايسترو)
    // =========================================================
    window.__ROYAL_MODULES__ = window.__ROYAL_MODULES__ ||[];

    window.__ROYAL_MODULES__.push({
        name: MODULE_NAME,
        stage: 'IDLE',
        priority: 25,

        init: function (context) {
            Chameleon.init(context);
        },

        hooks: {
            afterInit: () => {
                // console.log(`[${MODULE_NAME}] is active.`);
            }
        }
    });

    // 🔗 كشف العضلة للمايسترو (index.js)
    window.RoyalChameleon = { init: function() { Chameleon.init({ Logger: console }); } };

})();
