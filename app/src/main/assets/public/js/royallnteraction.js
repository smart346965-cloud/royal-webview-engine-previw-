/**
 * =========================================================
 * ⚡ ROYAL INTERACTION ENGINE V7
 * Native Compositor Friendly Prediction Sensor
 * =========================================================
 *
 * JS:
 *  - Passive touch sensing only.
 *  - High-confidence intent detection.
 *  - Sensitive-route filtering.
 *  - Sends eligible URLs to RoyalJsBridge.
 *
 * Native:
 *  - Final origin validation.
 *  - Preconnect.
 *  - Chromium prerender.
 *
 * IMPORTANT:
 *  - No preventDefault()
 *  - No scrollTo()
 *  - No requestAnimationFrame()
 *  - No RoyalWasm
 *  - No scroll manipulation
 */

(function () {
    'use strict';

    const INTENT_DELAY = 90;
    const MOVE_CANCEL_DISTANCE = 12;
    const HOVER_DELAY = 120;
    const PREDICTION_COOLDOWN = 1200;

    const SENSITIVE_SEGMENTS = [
        '/cart',
        '/checkout',
        '/login',
        '/logout',
        '/account',
        '/register',
        '/signup',
        '/sign-up',
        '/signin',
        '/sign-in',
        '/password',
        '/reset-password',
        '/forgot-password',
        '/verify',
        '/verification',
        '/payment',
        '/payments',
        '/order',
        '/orders',
        '/wishlist',
        '/favorites',
        '/compare',
        '/admin',
        '/dashboard'
    ];

    const SENSITIVE_QUERY_KEYS = [
        'token',
        'access_token',
        'refresh_token',
        'auth',
        'authorization',
        'session',
        'session_id',
        'checkout',
        'payment',
        'code'
    ];

    const state = {
        touchTimer: null,
        touchLink: null,
        touchStartX: 0,
        touchStartY: 0,
        touchActive: false,

        lastPredictedUrl: '',
        lastPredictionTime: 0,

        hoverTimer: null,
        hoverLink: null
    };

    function getBridge() {
        if (
            window.RoyalJsBridge &&
            typeof window.RoyalJsBridge.predict === 'function'
        ) {
            return window.RoyalJsBridge;
        }

        return null;
    }

    function normalizeUrl(url) {
        try {
            const parsed = new URL(url, window.location.href);

            if (
                parsed.protocol !== 'https:' &&
                parsed.protocol !== 'http:'
            ) {
                return null;
            }

            parsed.hash = '';

            return parsed.href;
        } catch (_) {
            return null;
        }
    }

    function isSensitiveUrl(url) {
        try {
            const parsed = new URL(url, window.location.href);

            const path =
                parsed.pathname
                    .toLowerCase()
                    .replace(/\/+/g, '/')
                    .replace(/\/$/, '');

            for (let i = 0; i < SENSITIVE_SEGMENTS.length; i++) {
                const sensitive =
                    SENSITIVE_SEGMENTS[i];

                if (
                    path === sensitive ||
                    path.startsWith(sensitive + '/')
                ) {
                    return true;
                }
            }

            const params = parsed.searchParams;

            for (let i = 0; i < SENSITIVE_QUERY_KEYS.length; i++) {
                if (params.has(SENSITIVE_QUERY_KEYS[i])) {
                    return true;
                }
            }

            return false;

        } catch (_) {
            return true;
        }
    }

    function isEligibleLink(link) {
        if (!link || !link.href) {
            return false;
        }

        if (
            link.hasAttribute('download') ||
            link.hasAttribute('data-no-prefetch') ||
            link.hasAttribute('data-no-prerender')
        ) {
            return false;
        }

        const target =
            (link.getAttribute('target') || '')
                .toLowerCase();

        if (
            target &&
            target !== '_self'
        ) {
            return false;
        }

        const url = normalizeUrl(link.href);

        if (!url) {
            return false;
        }

        if (isSensitiveUrl(url)) {
            return false;
        }

        try {
            const parsed = new URL(url);

            if (
                parsed.origin !== window.location.origin
            ) {
                return false;
            }
        } catch (_) {
            return false;
        }

        return url;
    }

    function sendPrediction(link) {
        const url = isEligibleLink(link);

        if (!url) {
            return false;
        }

        const now = Date.now();

        if (
            state.lastPredictedUrl === url &&
            now - state.lastPredictionTime <
                PREDICTION_COOLDOWN
        ) {
            return false;
        }

        const bridge = getBridge();

        if (!bridge) {
            return false;
        }

        state.lastPredictedUrl = url;
        state.lastPredictionTime = now;

        try {
            bridge.predict(url);

            link.setAttribute(
                'data-royal-predicted',
                'true'
            );

            return true;

        } catch (_) {
            return false;
        }
    }

    function clearTouchIntent() {
        if (state.touchTimer !== null) {
            clearTimeout(state.touchTimer);
            state.touchTimer = null;
        }

        state.touchLink = null;
        state.touchActive = false;
    }

    function onTouchStart(event) {
        if (
            !event.touches ||
            event.touches.length === 0
        ) {
            return;
        }

        const target = event.target;

        if (
            !target ||
            !target.closest
        ) {
            return;
        }

        const link =
            target.closest('a[href]');

        if (!link) {
            return;
        }

        if (!isEligibleLink(link)) {
            return;
        }

        const touch = event.touches[0];

        state.touchActive = true;
        state.touchLink = link;
        state.touchStartX = touch.clientX;
        state.touchStartY = touch.clientY;

        if (state.touchTimer !== null) {
            clearTimeout(state.touchTimer);
        }

        /*
         * لا نتنبأ عند أول millisecond.
         * ننتظر ثبات اللمس 90ms لإثبات نية المستخدم.
         */
        state.touchTimer = setTimeout(function () {

            if (
                !state.touchActive ||
                !state.touchLink
            ) {
                return;
            }

            sendPrediction(state.touchLink);

            state.touchTimer = null;

        }, INTENT_DELAY);
    }

    function onTouchMove(event) {
        if (
            !state.touchActive ||
            !state.touchLink ||
            !event.touches ||
            event.touches.length === 0
        ) {
            return;
        }

        const touch = event.touches[0];

        const dx =
            touch.clientX -
            state.touchStartX;

        const dy =
            touch.clientY -
            state.touchStartY;

        const distance =
            Math.sqrt(
                dx * dx +
                dy * dy
            );

        /*
         * المستخدم بدأ سحباً وليس نقراً.
         * نلغي نية التنبؤ فقط.
         *
         * لا preventDefault().
         * لا scrollTo().
         * لا requestAnimationFrame().
         */
        if (
            distance >
            MOVE_CANCEL_DISTANCE
        ) {
            clearTouchIntent();
        }
    }

    function onTouchEnd() {
        clearTouchIntent();
    }

    function onTouchCancel() {
        clearTouchIntent();
    }

    function onPointerOver(event) {
        /*
         * يعمل كإشارة إضافية لأجهزة المؤشر.
         * لا يتحكم أبداً في التنقل.
         */
        if (
            event.pointerType === 'touch'
        ) {
            return;
        }

        const target = event.target;

        if (
            !target ||
            !target.closest
        ) {
            return;
        }

        const link =
            target.closest('a[href]');

        if (!link) {
            return;
        }

        if (!isEligibleLink(link)) {
            return;
        }

        if (state.hoverTimer !== null) {
            clearTimeout(state.hoverTimer);
        }

        state.hoverLink = link;

        state.hoverTimer = setTimeout(function () {

            if (state.hoverLink === link) {
                sendPrediction(link);
            }

            state.hoverTimer = null;

        }, HOVER_DELAY);
    }

    function onPointerOut(event) {
        if (
            event.pointerType === 'touch'
        ) {
            return;
        }

        if (state.hoverTimer !== null) {
            clearTimeout(state.hoverTimer);
            state.hoverTimer = null;
        }

        state.hoverLink = null;
    }

    const PredictionSensor = {

        init: function () {

            /*
             * Passive touch listeners:
             * Chromium يحتفظ بالـ native compositor scroll path.
             */
            document.addEventListener(
                'touchstart',
                onTouchStart,
                {
                    passive: true,
                    capture: true
                }
            );

            document.addEventListener(
                'touchmove',
                onTouchMove,
                {
                    passive: true,
                    capture: true
                }
            );

            document.addEventListener(
                'touchend',
                onTouchEnd,
                {
                    passive: true,
                    capture: true
                }
            );

            document.addEventListener(
                'touchcancel',
                onTouchCancel,
                {
                    passive: true,
                    capture: true
                }
            );

            /*
             * Pointer hover للأجهزة التي تستخدم مؤشر.
             */
            document.addEventListener(
                'pointerover',
                onPointerOver,
                {
                    passive: true,
                    capture: true
                }
            );

            document.addEventListener(
                'pointerout',
                onPointerOut,
                {
                    passive: true,
                    capture: true
                }
            );
        }
    };

    function startRoyalInteraction() {

        PredictionSensor.init();

        console.log(
            '⚡ ROYAL INTERACTION V7: Passive Native-Compositor Prediction Active'
        );
    }

    window.RoyalInteraction = {
        init: startRoyalInteraction
    };

})();
