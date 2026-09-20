/**
 * =========================================================
 * 🔑 Nexus Service Worker Bootstrapper
 * =========================================================
 */
if ('serviceWorker' in navigator) {
    window.addEventListener('load', () => {
        // نطلب ملف السيرفر من نفس دومين المتجر!
        // لا تقلق، الـ WebViewClient النيتف الخاص بنا سيعترض هذا الطلب ويعطيه الملف من داخل الهاتف
        navigator.serviceWorker.register('/nexus-service-worker.js', { scope: '/' })
            .then((registration) => {
                console.log('[Nexus Bootstrapper] ✅ ServiceWorker registered successfully with scope: ', registration.scope);
            })
            .catch((error) => {
                console.error('[Nexus Bootstrapper] ❌ ServiceWorker registration failed: ', error);
            });
    });
}
