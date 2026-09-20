/**
 * =========================================================
 * ⚙️ NEXUS CAPABILITIES ENGINE (V1.0 - The Hardware Bridge)
 * File: nexus-capabilities.js
 * =========================================================
 * Architecture: Lazy Detection, Gesture-Bound Execution, Silent Fallbacks.
 * Philosophy: Detect silently, activate ONLY on user intent.
 */

(function () {
    'use strict';

    if (window.__NEXUS_CAPABILITIES__) return;
    window.__NEXUS_CAPABILITIES__ = true;

    // =========================================================
    // 🎨 0. STYLES (UI أنيميشن احترافية)
    // =========================================================
    const uiStyles = document.createElement('style');
    uiStyles.textContent = `
        .nx-overlay {
            position: fixed;
            inset: 0;
            background: rgba(0,0,0,0.4);
            backdrop-filter: blur(0px);
            opacity: 0;
            display: flex;
            align-items: center;
            justify-content: center;
            z-index: 2147483646;
            transition: all 0.5s cubic-bezier(0.4, 0, 0.2, 1);
            -webkit-tap-highlight-color: transparent;
        }
        .nx-overlay.active {
            opacity: 1;
            backdrop-filter: blur(8px);
        }
        .nx-box {
            width: 85%;
            max-width: 320px;
            background: rgba(255,255,255,0.95);
            border-radius: 24px;
            padding: 24px;
            text-align: center;
            box-shadow: 0 20px 40px rgba(0,0,0,0.15);
            transform: scale(0.9) translate3d(0, 20px, 0);
            opacity: 0;
            filter: blur(10px);
            transition: all 0.6s cubic-bezier(0.16, 1, 0.3, 1);
        }
        .nx-overlay.active .nx-box {
            transform: scale(1) translate3d(0, 0, 0);
            opacity: 1;
            filter: blur(0px);
        }

        /* إلغاء الومضة الزرقاء عن كل ما هو قابل للتفاعل */
        .nx-overlay, .nx-box, button, [data-nexus-action] {
            -webkit-tap-highlight-color: transparent !important;
            outline: none !important;
        }

        /* حاوية الكاميرا الاحترافية */
        .nx-camera-container {
            position: fixed;
            inset: 0;
            background: #000;
            z-index: 2147483647;
            display: flex;
            flex-direction: column;
            transform: translate3d(0, 100%, 0);
            transition: transform 0.6s cubic-bezier(0.16, 1, 0.3, 1);
            border-radius: 20px 20px 0 0;
            margin-top: 40px;
            overflow: hidden;
        }

        .nx-camera-container.active {
            transform: translate3d(0, 0, 0);
        }

        /* زر التصوير الاحترافي */
        .nx-capture-btn {
            position: absolute;
            bottom: 40px;
            left: 50%;
            transform: translateX(-50%);
            width: 72px;
            height: 72px;
            background: #fff;
            border-radius: 50%;
            border: 4px solid rgba(0,0,0,0.1);
            display: flex;
            align-items: center;
            justify-content: center;
            cursor: pointer;
            box-shadow: 0 0 20px rgba(0,0,0,0.2);
            z-index: 11;
        }
        .nx-capture-btn::after {
            content: '';
            width: 54px;
            height: 54px;
            background: transparent;
            border: 2px solid #000;
            border-radius: 50%;
        }
        .nx-capture-btn:active {
            transform: translateX(-50%) scale(0.9);
            background: #eee;
        }
    `;
    document.head.appendChild(uiStyles);

    // =========================================================
    // 🔬 1. DETECTOR (فحص قدرات عتاد الهاتف بصمت)
    // =========================================================
    const Hardware = {
        camera: !!(navigator.mediaDevices && navigator.mediaDevices.getUserMedia),
        audio: !!(navigator.mediaDevices && navigator.mediaDevices.getUserMedia),
        gps: 'geolocation' in navigator,
        share: 'share' in navigator,
        biometric: !!window.PublicKeyCredential,
        bluetooth: 'bluetooth' in navigator,
        nfc: 'NDEFReader' in window
    };

    // =========================================================
    // 💾 1.5 MEMORY (ذاكرة الرفض)
    // =========================================================
    const Memory = {
        denied: {},
        setDenied(type) {
            this.denied[type] = true;
        },
        isDenied(type) {
            return this.denied[type];
        }
    };

    // =========================================================
    // 📝 1.6 CAPABILITY COPY / UX TEMPLATES
    // =========================================================

    const CapabilityCopy = {

        camera: {
            title: "📷 تفعيل الكاميرا",
            desc: "نحتاج إلى استخدام كاميرا جهازك لتنفيذ هذه العملية."
        },

        microphone: {
            title: "🎤 تفعيل الميكروفون",
            desc: "نحتاج إلى استخدام الميكروفون لتنفيذ هذه العملية."
        },

        location: {
            title: "📍 الوصول إلى موقعك",
            desc: "نحتاج إلى موقعك الحالي لتقديم خدمة الموقع أو تحديد أقرب نتيجة."
        },

        biometric: {
            title: "🔐 استخدام بصمة الجهاز",
            desc: "يمكن استخدام بصمة الجهاز أو قفل الشاشة للتحقق من هويتك بأمان."
        },

        notification: {
            title: "🔔 تفعيل الإشعارات",
            desc: "فعّل الإشعارات حتى يصلك أهم التحديثات والتنبيهات من التطبيق."
        },

        bluetooth: {
            title: "📡 الوصول إلى Bluetooth",
            desc: "نحتاج إلى Bluetooth للاتصال بجهاز قريب يدعم هذه الخدمة."
        },

        nfc: {
            title: "📳 استخدام NFC",
            desc: "نحتاج إلى NFC لقراءة أو التفاعل مع بطاقة أو جهاز قريب."
        },

        download: {
            title: "⬇️ تنزيل الملف",
            desc: "سيتم حفظ الملف على جهازك ويمكنك الوصول إليه من مجلد التنزيلات."
        },

        upload: {
            title: "📁 اختيار ملف",
            desc: "اختر صورة أو ملفاً من جهازك لإرساله إلى الموقع."
        },

        share: {
            title: "↗️ مشاركة",
            desc: "سيتم فتح خيارات المشاركة الموجودة على جهازك."
        },

        contacts: {
            title: "👤 الوصول إلى جهات الاتصال",
            desc: "نحتاج إلى جهات الاتصال لتنفيذ العملية التي طلبتها."
        },

        cameraAndMicrophone: {
            title: "🎥 الكاميرا والميكروفون",
            desc: "هذه الصفحة تطلب استخدام الكاميرا والميكروفون. لن يتم تشغيلهما إلا بعد موافقتك."
        }
    };

    // =========================================================
    // 🛡️ 2. PERMISSION INTELLIGENCE LAYER (نظام صلاحيات ذكي)
    // =========================================================
    const Permissions = {
        async query(name) {
            if (!navigator.permissions) return "unknown";
            try {
                const result = await navigator.permissions.query({ name });
                return result.state; // granted | denied | prompt
            } catch {
                return "unknown";
            }
        }
    };

    // =========================================================
    // 🧠 2.5 PERMISSION UX HANDLER (إدارة الرفض والموافقة)
    // =========================================================
    const UX = {
        showPrePermission: function ({ title, desc, onAccept, onCancel }) {
            const overlay = document.createElement('div');
            overlay.className = 'nx-overlay';

            const box = document.createElement('div');
            box.className = 'nx-box';

            box.innerHTML = `
                <div style="font-size:19px;font-weight:800;color:#111;margin-bottom:10px">${title}</div>
                <div style="font-size:14px;color:rgba(0,0,0,0.6);line-height:1.5;margin-bottom:22px">${desc}</div>
                <button id="nx-allow" style="width:100%;padding:14px;margin-bottom:10px;border:none;border-radius:15px;background:#111;color:#fff;font-size:15px;font-weight:600;cursor:pointer;-webkit-tap-highlight-color:transparent">نعم، متابعة</button>
                <button id="nx-cancel" style="width:100%;padding:14px;border:none;border-radius:15px;background:rgba(0,0,0,0.05);color:#555;font-size:15px;font-weight:600;cursor:pointer;-webkit-tap-highlight-color:transparent">ليس الآن</button>
            `;

            overlay.appendChild(box);
            document.body.appendChild(overlay);

            requestAnimationFrame(() => {
                requestAnimationFrame(() => {
                    overlay.classList.add('active');
                });
            });

            const close = (callback) => {
                overlay.style.opacity = '0';
                overlay.style.backdropFilter = 'blur(0px)';
                box.style.transform = 'translate3d(0, 30px, 0) scale(0.95)';
                box.style.filter = 'blur(10px)';
               
                setTimeout(() => {
                    overlay.remove();
                    if (callback) callback();
                }, 500);
            };

            box.querySelector('#nx-allow').onclick = (e) => {
                e.preventDefault();
                close(onAccept);
            };

            box.querySelector('#nx-cancel').onclick = (e) => {
                e.preventDefault();
                close(onCancel);
            };
        },

        showCapability: function (type, onAccept, onCancel) {

            const config =
                CapabilityCopy[type] ||
                CapabilityCopy.camera;

            this.showPrePermission({
                title: config.title,
                desc: config.desc,
                onAccept: onAccept,
                onCancel: onCancel
            });
        },

        showPermissionHelp: function (type) {
            alert(`⚠️ يرجى تفعيل إذن ${type} من إعدادات التطبيق`);
        }
    };

    // =========================================================
    // 🛡️ 3. PERMISSION & EXECUTOR (المنفذ الآمن)
    // =========================================================
    const Executor = {
       
        cameraStream: null,
        audioStream: null,

        // 📷 فتح الكاميرا
        openCamera: async function () {
            if (this.cameraStream) return true;
            if (!Hardware.camera) return false;

            if (Memory.isDenied("camera")) {
                UX.showPermissionHelp("الكاميرا");
                return false;
            }

            try {
                const stream = await navigator.mediaDevices.getUserMedia({ 
                    video: { facingMode: { ideal: "environment" } } 
                });
                this.cameraStream = stream;

                const container = document.createElement('div');
                container.className = 'nx-camera-container';

                const video = document.createElement('video');
                video.autoplay = true;
                video.muted = true;
                video.playsInline = true;
                video.srcObject = stream;
                Object.assign(video.style, { width: "100%", height: "100%", objectFit: "cover" });

                // زر الإغلاق
                const closeBtn = document.createElement('button');
                closeBtn.innerHTML = "✕";
                Object.assign(closeBtn.style, {
                    position: "absolute", top: "20px", right: "20px", width: "42px", height: "42px",
                    borderRadius: "50%", background: "rgba(255,255,255,0.2)", backdropFilter: "blur(10px)",
                    color: "#fff", border: "none", fontSize: "18px", zIndex: "12"
                });

                // 📸 زر التصوير الدائري
                const captureBtn = document.createElement('div');
                captureBtn.className = 'nx-capture-btn';

                // وظيفة التقاط الصورة
                const takePhoto = () => {
                    const canvas = document.createElement('canvas');
                    canvas.width = video.videoWidth;
                    canvas.height = video.videoHeight;
                    const ctx = canvas.getContext('2d');
                    ctx.drawImage(video, 0, 0);

                    canvas.toBlob((blob) => {
                        const file = new File([blob], `nexus-photo-${Date.now()}.jpg`, { type: "image/jpeg" });
                        
                        // 🚀 إرسال الحدث للمتجر
                        window.dispatchEvent(new CustomEvent('nexus:photo-captured', {
                            detail: { 
                                blob: blob, 
                                file: file,
                                base64: canvas.toDataURL('image/jpeg')
                            }
                        }));
                        
                        // تأثير بصري عند التصوير (Flash)
                        container.style.background = '#fff';
                        setTimeout(() => { container.style.background = '#000'; }, 50);
                        closeCamera();
                    }, 'image/jpeg', 0.9);
                };

                const closeCamera = () => {
                    container.classList.remove('active');
                    setTimeout(() => {
                        stream.getTracks().forEach(track => track.stop());
                        container.remove();
                        this.cameraStream = null;
                    }, 600);
                };

                closeBtn.onclick = closeCamera;
                captureBtn.onclick = takePhoto;

                container.appendChild(closeBtn);
                container.appendChild(video);
                container.appendChild(captureBtn);
                document.body.appendChild(container);

                requestAnimationFrame(() => container.classList.add('active'));
                return true;

            } catch (err) {

                console.warn(
                    "Camera error:",
                    err
                );

                if (
                    err &&
                    (
                        err.name === "NotAllowedError" ||
                        err.name === "PermissionDeniedError"
                    )
                ) {

                    Memory.setDenied("camera");

                    UX.showPermissionHelp(
                        "الكاميرا"
                    );
                }

                return false;
            }
        },

        // 🎤 فتح الميكروفون
        openMicrophone: async function () {
            if (this.audioStream) return true;
            if (!Hardware.audio) return false;

            if (Memory.isDenied("microphone")) {
                UX.showPermissionHelp("الميكروفون");
                return false;
            }

            const state = await Permissions.query("microphone");

            if (state === "denied") {
                Memory.setDenied("microphone");
                UX.showPermissionHelp("الميكروفون");
                return false;
            }

            try {
                const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
                this.audioStream = stream;

                const container = document.createElement('div');
                container.className = 'nx-overlay';

                const box = document.createElement('div');
                box.className = 'nx-box';

                box.innerHTML = `
                    <div style="font-size:18px;font-weight:700;margin-bottom:10px">🎤 الميكروفون يعمل الآن</div>
                    <div style="font-size:14px;opacity:0.7;margin-bottom:20px">يمكنك التحدث الآن...</div>
                    <button id="nx-stop" style="width:100%;padding:14px;border:none;border-radius:15px;background:#111;color:#fff;font-size:15px;font-weight:600;cursor:pointer;-webkit-tap-highlight-color:transparent">إيقاف</button>
                `;

                container.appendChild(box);
                document.body.appendChild(container);

                requestAnimationFrame(() => {
                    requestAnimationFrame(() => {
                        container.classList.add('active');
                    });
                });

                const closeMic = () => {
                    container.classList.remove('active');
                    setTimeout(() => {
                        stream.getTracks().forEach(track => track.stop());
                        container.remove();
                        this.audioStream = null;
                    }, 500);
                };

                const stopBtn = box.querySelector('#nx-stop');
                stopBtn.ontouchstart = (e) => { e.preventDefault(); closeMic(); };
                stopBtn.onclick = closeMic;

                return true;

            } catch (err) {

                console.warn(
                    "Microphone error:",
                    err
                );

                if (
                    err &&
                    (
                        err.name === "NotAllowedError" ||
                        err.name === "PermissionDeniedError"
                    )
                ) {

                    Memory.setDenied(
                        "microphone"
                    );

                    UX.showPermissionHelp(
                        "الميكروفون"
                    );
                }

                return false;
            }
        },

        // 🔐 البصمة (WebAuthn)
        startBiometric: async function () {
            if (!Hardware.biometric) return false;

            if (Memory.isDenied("biometric")) {
                UX.showPermissionHelp("البصمة");
                return false;
            }

            const state = await Permissions.query("publickey");

            if (state === "denied") {
                Memory.setDenied("biometric");
                UX.showPermissionHelp("البصمة");
                return false;
            }

            try {
                console.log("🔐 Starting WebAuthn...");
                alert("سيتم تسجيل الدخول بالبصمة قريباً");
                return true;
            } catch (e) {
                return false;
            }
        },

        // 📍 تحديد الموقع (يعمل فقط عند النقر)
        requestLocation: async function (onSuccess, onError) {
            if (!Hardware.gps) {
                if (onError) onError("GPS not supported");
                return;
            }

            if (Memory.isDenied("location")) {
                UX.showPermissionHelp("الموقع");
                if (onError) onError("Permission denied");
                return;
            }

            const state = await Permissions.query("geolocation");

            if (state === "denied") {
                Memory.setDenied("location");
                UX.showPermissionHelp("الموقع");
                if (onError) onError("Permission denied");
                return;
            }

            navigator.geolocation.getCurrentPosition(
                (position) => {
                    if (onSuccess) onSuccess(position.coords);
                },
                (error) => {
                    console.warn("📍 Location Denied/Failed:", error.message);
                    if (error.code === 1) {
                        Memory.setDenied("location");
                        UX.showPermissionHelp("الموقع");
                    }
                    if (onError) onError(error.message);
                },
                { enableHighAccuracy: true, timeout: 5000, maximumAge: 0 }
            );
        },

        // 🔗 مشاركة الملفات/الروابط (Native Android Share)
        shareContent: async function (title, text, url) {
            if (!Hardware.share) return false;
            try {
                await navigator.share({ title, text, url });
                return true;
            } catch (err) {

                if (
                    err &&
                    err.name === "AbortError"
                ) {
                    return false;
                }

                console.warn(
                    "Share failed:",
                    err
                );

                return false;
            }
        },

        // 🔐 البصمة (WebAuthn) - Legacy
        initBiometric: async function () {
            if (!Hardware.biometric) return false;
            console.log("🔐 Biometric hardware is ready for WebAuthn.");
            return true;
        }
    };

    // =========================================================
    // 🧠 4. CONTEXT ANALYZER & EVENT BINDING (ربط العتاد بالـ DOM)
    // =========================================================
    const ContextBinder = {

        init: function () {

            const handleIntent = (e) => {

                const target =
                    e.target.closest(
                        '[data-nexus-action]'
                    );

                if (!target) {
                    return;
                }

                if (e.type === 'touchstart') {
                    e.preventDefault();
                }

                const action =
                    target.getAttribute(
                        'data-nexus-action'
                    );

                // =====================================================
                // CAMERA
                // =====================================================

                if (action === 'camera') {

                    UX.showCapability(
                        "camera",
                        () =>
                            Executor.openCamera()
                    );

                    return;
                }

                // =====================================================
                // MICROPHONE
                // =====================================================

                if (action === 'microphone') {

                    UX.showCapability(
                        "microphone",
                        () =>
                            Executor.openMicrophone()
                    );

                    return;
                }

                // =====================================================
                // CAMERA + MICROPHONE
                // =====================================================

                if (
                    action ===
                    'camera-microphone'
                ) {

                    UX.showCapability(
                        "cameraAndMicrophone",
                        async () => {

                            try {

                                await navigator.mediaDevices
                                    .getUserMedia({
                                        video: true,
                                        audio: true
                                    });

                            } catch (error) {

                                console.warn(
                                    "Camera/Microphone error:",
                                    error
                                );
                            }
                        }
                    );

                    return;
                }

                // =====================================================
                // BIOMETRIC
                // =====================================================

                if (action === 'biometric') {

                    UX.showCapability(
                        "biometric",
                        () =>
                            Executor.startBiometric()
                    );

                    return;
                }

                // =====================================================
                // LOCATION
                // =====================================================

                if (action === 'location') {

                    UX.showCapability(
                        "location",
                        () => {

                            const originalText =
                                target.innerText;

                            target.disabled = true;
                            target.innerText =
                                "جاري...";

                            Executor.requestLocation(

                                (coords) => {

                                    target.innerText =
                                        "تم التحديد ✓";

                                    target.disabled =
                                        false;

                                    window.dispatchEvent(
                                        new CustomEvent(
                                            'nexus:location-ready',
                                            {
                                                detail: {
                                                    latitude:
                                                        coords.latitude,

                                                    longitude:
                                                        coords.longitude,

                                                    accuracy:
                                                        coords.accuracy
                                                }
                                            }
                                        )
                                    );
                                },

                                () => {

                                    target.innerText =
                                        originalText;

                                    target.disabled =
                                        false;
                                }
                            );
                        }
                    );

                    return;
                }

                // =====================================================
                // SHARE
                // =====================================================

                if (action === 'share') {

                    const urlToShare =
                        target.getAttribute(
                            'data-url'
                        ) ||
                        window.location.href;

                    const titleToShare =
                        target.getAttribute(
                            'data-title'
                        ) ||
                        document.title;

                    UX.showCapability(
                        "share",
                        () =>
                            Executor.shareContent(
                                titleToShare,
                                "",
                                urlToShare
                            )
                    );

                    return;
                }

                // =====================================================
                // DOWNLOAD
                // =====================================================

                if (action === 'download') {

                    const url =
                        target.getAttribute(
                            'data-url'
                        );

                    const filename =
                        target.getAttribute(
                            'data-filename'
                        ) ||
                        "الملف";

                    UX.showCapability(
                        "download",
                        () => {

                            window.dispatchEvent(
                                new CustomEvent(
                                    'nexus:download-confirmed',
                                    {
                                        detail: {
                                            url,
                                            filename
                                        }
                                    }
                                )
                            );
                        }
                    );

                    return;
                }
            };

            document.addEventListener(
                'touchstart',
                handleIntent,
                {
                    passive: false
                }
            );

            document.addEventListener(
                'click',
                handleIntent
            );
        }
    };

    // =========================================================
    // 🚀 5. IGNITION (التشغيل الصامت في وقت الفراغ)
    // =========================================================
    function bootCapabilities() {
        const requestIdle = window.requestIdleCallback || function (cb) { setTimeout(cb, 500); };
       
        requestIdle(() => {
            try {
                ContextBinder.init();
            } catch (e) {
                console.error("Nexus Capabilities Boot Error", e);
            }
        });
    }

    // 🔗 كشف العضلة للمايسترو (index.js)
    window.NexusCapabilities = { init: bootCapabilities };

})();
