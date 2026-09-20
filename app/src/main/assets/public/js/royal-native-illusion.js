/**
 * =========================================================
 * 🎭 ROYAL NATIVE ILLUSION (The Web Exterminator V2 - Safe Mode)
 * File: royal-native-illusion.js
 * =========================================================
 * Priority: Absolute First (Inject before DOM is fully parsed).
 * Role: Eradicate web behaviors (Zoom, Highlight, Context Menu) SAFELY.
 */

(function () {
    'use strict';

    // 🛡️ حماية من التكرار
    if (document.getElementById('royal-native-illusion-shield')) return;
    // console.log("🎭 ROYAL ENGINE: Native Illusion Shield Deployed.");

    // =========================================================
    // 🛡️ 1. THE VIEWPORT LOCK (إبادة الزوم بالإصبعين من الجذور)
    // =========================================================
    // هذه هي الطريقة الرسمية والآمنة 100% لمنع الـ Pinch-to-Zoom في كل المتصفحات
    function lockViewport() {
        let viewport = document.querySelector('meta[name="viewport"]');
        const lockContent = 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover';
       
        if (viewport) {
            viewport.content = lockContent;
        } else {
            viewport = document.createElement('meta');
            viewport.name = 'viewport';
            viewport.content = lockContent;
            document.head.appendChild(viewport);
        }
    }

    // =========================================================
    // 🎨 2. THE VISUAL SHIELD (الدرع البصري - CSS)
    // =========================================================
    const style = document.createElement('style');
    style.id = 'royal-native-illusion-shield';
    style.textContent = `
        /* إبادة الوميض الأزرق عند النقر */
        * { -webkit-tap-highlight-color: transparent !important; }

        /* إبادة تحديد النصوص العشوائي (نستثني حقول الإدخال) */
        *:not(input):not(textarea):not([contenteditable="true"]) {
            -webkit-user-select: none !important;
            user-select: none !important;
        }

        /* السماح بالكتابة داخل حقول الإدخال */
        input, textarea, [contenteditable="true"] {
            -webkit-user-select: auto !important;
            user-select: auto !important;
        }

        /* إبادة تأخير النقر (300ms Delay) */
        body { touch-action: manipulation; }
        a, button,[role="button"], input, select {
            touch-action: manipulation !important;
        }

        /* إبادة الاهتزاز الجانبي وتأثير الارتداد المزعج */
        html {
            overflow-x: hidden !important;
            overscroll-behavior-y: none !important; /* يمنع السحب خارج إطار التطبيق */
        }

        body {
            overflow-x: hidden !important;
            position: relative;
            width: 100%;
            -webkit-font-smoothing: antialiased !important;
            -moz-osx-font-smoothing: grayscale !important;
        }

        /* إبادة سحب الصور (Image Dragging) وقائمة السياق البصرية */
        img {
            -webkit-user-drag: none !important;
            -webkit-touch-callout: none !important;
        }
        a {
            -webkit-touch-callout: none !important;
        }

        /*
         * 10. إبادة سحب الصور (Image Dragging)
         */
        img {
            -webkit-user-drag: none !important;
        }

        /*
         * 🛑 11. إبادة شريط ترجمة جوجل بصرياً (Zero CPU Cost)
         * نخفي أي عنصر يحاول جوجل حقنه، ونمنعه من إزاحة الـ body للأسفل
         */
        .goog-te-banner-frame,
        .goog-te-menu-frame,
        .goog-te-balloon-frame,
        #goog-gt-tt {
            display: none !important;
            opacity: 0 !important;
            visibility: hidden !important;
        }
        body {
            top: 0 !important;
        }
    `;

    // =========================================================
    // 🚀 3. IMMEDIATE INJECTION (الحقن الفوري)
    // =========================================================
    if (document.head) {
        document.head.insertBefore(style, document.head.firstChild);
        lockViewport();
    } else {
        const observer = new MutationObserver((mutations, obs) => {
            if (document.head) {
                document.head.insertBefore(style, document.head.firstChild);
                lockViewport();
                obs.disconnect();
            }
        });
        observer.observe(document.documentElement, { childList: true });
    }

    // =========================================================
    // 🛡️ 4. BEHAVIORAL EXTERMINATORS (إبادة السلوكيات الخبيثة بالـ JS)
    // =========================================================

    // أ. إبادة قائمة السياق (Context Menu / Save Image) بأمان تام
    // نمنعها فقط على الصور والروابط لكي لا نكسر حقول الإدخال
    document.addEventListener('contextmenu', function (e) {
        const target = e.target;
        // إذا كان العنصر جزءاً من نظام نيكسوس، لا تلمسه واتركه يعمل
        if (target.closest('[data-nexus-action]')) return;

        if (target.tagName === 'IMG' || target.closest('a')) {
            e.preventDefault();
        }
    }, { passive: false });

    // ب. إبادة التقريب المزدوج (Double-Tap Zoom) كإجراء احتياطي
    let lastTouchEnd = 0;
    document.addEventListener('touchend', function (e) {
        const now = Date.now();
        if (now - lastTouchEnd <= 300) {
            // نمنع التقريب المزدوج فقط إذا لم يكن المستخدم يكتب في حقل إدخال
            const tag = e.target.tagName.toLowerCase();
            if (tag !== 'input' && tag !== 'textarea') {
                e.preventDefault();
            }
        }
        lastTouchEnd = now;
    }, { passive: false });

    // ج. إبادة الترجمة من الجذور (تعديل جينات الـ HTML والـ Meta)
    const metaTranslate = document.createElement('meta');
    metaTranslate.name = 'google';
    metaTranslate.content = 'notranslate';
    if (document.head) document.head.appendChild(metaTranslate);

    // 👑 السحر هنا: إجبار المتصفح على قراءة الصفحة كـ "غير قابلة للترجمة"
    if (document.documentElement) {
        document.documentElement.setAttribute('translate', 'no');
        document.documentElement.classList.add('notranslate');
    }

    // منع double-tap zoom الحقيقي

})();
