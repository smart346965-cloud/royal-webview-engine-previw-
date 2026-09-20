/**
 * =========================================================
 * 🔮 ROYAL SPECULATOR V7
 * Native Visibility Sensor
 * =========================================================
 *
 * JS responsibilities:
 *  - Observe links entering the predictive viewport.
 *  - Filter unsafe/sensitive routes.
 *  - Send eligible URLs to RoyalJsBridge.
 *
 * Native responsibilities:
 *  - Final origin validation.
 *  - Preconnect.
 *  - Chromium prerender.
 *
 * No:
 *  - RoyalWasm
 *  - speculationrules
 *  - eagerness: immediate
 *  - scroll manipulation
 *  - requestAnimationFrame
 */

(function () {
    'use strict';

    const ROOT_MARGIN = '300px 0px 300px 0px';

    const MAX_VISIBLE_PREDICTIONS = 8;

    const VISIBILITY_COOLDOWN = 2000;

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
        predicted: new Map(),
        visiblePredictions: 0
    };

    function normalizeUrl(url) {
        try {
            const parsed = new URL(
                url,
                window.location.href
            );

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
            const parsed = new URL(
                url,
                window.location.href
            );

            const path =
                parsed.pathname
                    .toLowerCase()
                    .replace(/\/+/g, '/')
                    .replace(/\/$/, '');

            for (
                let i = 0;
                i < SENSITIVE_SEGMENTS.length;
                i++
            ) {
                const segment =
                    SENSITIVE_SEGMENTS[i];

                if (
                    path === segment ||
                    path.startsWith(segment + '/')
                ) {
                    return true;
                }
            }

            const params =
                parsed.searchParams;

            for (
                let i = 0;
                i < SENSITIVE_QUERY_KEYS.length;
                i++
            ) {
                if (
                    params.has(
                        SENSITIVE_QUERY_KEYS[i]
                    )
                ) {
                    return true;
                }
            }

            return false;

        } catch (_) {
            return true;
        }
    }

    function isEligibleLink(link) {
        if (
            !link ||
            !link.href
        ) {
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
            (
                link.getAttribute('target') ||
                ''
            ).toLowerCase();

        if (
            target &&
            target !== '_self'
        ) {
            return false;
        }

        const url =
            normalizeUrl(link.href);

        if (!url) {
            return false;
        }

        if (isSensitiveUrl(url)) {
            return false;
        }

        try {
            const parsed =
                new URL(url);

            /*
             * JS يمنع التنبؤ بالروابط الخارجية.
             * Native يعيد فحص origin كطبقة أمان ثانية.
             */
            if (
                parsed.origin !==
                window.location.origin
            ) {
                return false;
            }

        } catch (_) {
            return false;
        }

        return url;
    }

    function sendToNative(link) {

        if (
            state.visiblePredictions >=
            MAX_VISIBLE_PREDICTIONS
        ) {
            return;
        }

        const url =
            isEligibleLink(link);

        if (!url) {
            return;
        }

        const now =
            Date.now();

        const previous =
            state.predicted.get(url);

        if (
            previous &&
            now - previous <
            VISIBILITY_COOLDOWN
        ) {
            return;
        }

        const bridge =
            window.RoyalJsBridge;

        if (
            !bridge ||
            typeof bridge.predict !==
                'function'
        ) {
            return;
        }

        state.predicted.set(
            url,
            now
        );

        state.visiblePredictions++;

        /* 🟢 تحرير سعة الجافاسكريبت تلقائياً بعد ثانيتين لاستقبال روابط جديدة */
        setTimeout(function () {
            state.visiblePredictions = Math.max(0, state.visiblePredictions - 1);
        }, VISIBILITY_COOLDOWN);

        try {

            bridge.predict(url);

            link.setAttribute(
                'data-royal-visible',
                'true'
            );

        } catch (_) {

            state.visiblePredictions =
                Math.max(
                    0,
                    state.visiblePredictions - 1
                );
        }
    }

    const ViewportPredictor = {

        init: function () {

            const observer =
                new IntersectionObserver(
                    function (entries) {

                        entries.forEach(
                            function (entry) {

                                if (
                                    !entry.isIntersecting
                                ) {
                                    return;
                                }

                                const element =
                                    entry.target;

                                if (
                                    element.tagName !==
                                    'A'
                                ) {
                                    return;
                                }

                                sendToNative(
                                    element
                                );
                            }
                        );
                    },
                    {
                        root: null,
                        rootMargin:
                            ROOT_MARGIN,
                        threshold: 0
                    }
                );

            this.scanDOM =
                function () {

                    const links =
                        document.querySelectorAll(
                            'a[href]:not([data-royal-observed])'
                        );

                    links.forEach(
                        function (link) {

                            if (
                                !isEligibleLink(
                                    link
                                )
                            ) {
                                link.setAttribute(
                                    'data-royal-observed',
                                    'true'
                                );

                                return;
                            }

                            link.setAttribute(
                                'data-royal-observed',
                                'true'
                            );

                            observer.observe(
                                link
                            );
                        }
                    );
                };

            this.scanDOM();

            let scanTimer = null;

            const mutationObserver =
                new MutationObserver(
                    function () {

                        if (scanTimer !== null) {
                            return;
                        }

                        scanTimer =
                            setTimeout(
                                function () {

                                    scanTimer =
                                        null;

                                    ViewportPredictor
                                        .scanDOM();

                                },
                                250
                            );
                    }
                );

            if (document.body) {
                mutationObserver.observe(
                    document.body,
                    {
                        childList: true,
                        subtree: true
                    }
                );
            }
        }
    };

    function startEngine() {

        ViewportPredictor.init();

        console.log(
            '🔮 ROYAL SPECULATOR V7: Native Visibility Sensor Active'
        );
    }

    window.RoyalSpeculator = {
        init: startEngine
    };

})();
