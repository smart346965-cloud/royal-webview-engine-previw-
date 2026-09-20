package com.store.app;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcel;
import android.util.Log;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.ImageView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 👑 ROYAL SESSION SENTINEL
 *
 * مسؤول عن:
 *
 * - إدارة حالة الجلسة.
 * - حفظ واستعادة WebView state.
 * - حفظ Snapshot بصري للجلسة.
 * - إظهار Ghost Snapshot أثناء الاستعادة.
 * - إدارة دورة حياة موارد الجلسة.
 *
 * ملاحظة:
 * هذا الملف لا يدير شبكة Chromium.
 * لا ينفذ TLS/DNS يدويًا.
 * لا ينشئ Spare Renderer وهميًا.
 * لا يدير Cookies يدويًا.
 * لا يدعي تنفيذ Freeze-Dried Tabs حقيقية.
 *
 * Network / speculative loading:
 * يتم التعامل معها في طبقات WebKit / Navigation / Prediction المناسبة.
 */
public final class RoyalSessionSentinel {

    private static final String TAG = "RoyalSentinel";

    // ============================================================
    // FILES
    // ============================================================

    private static final String STATE_FILE =
            "royal_web_state.bin";

    private static final String SNAPSHOT_FILE =
            "ghost_snapshot.webp";

    private static final String META_FILE =
            "session_meta.properties";

    private static final String WARMUP_SCRIPT_FILE =
            "warmup_script.js";

    // ============================================================
    // CONFIG
    // ============================================================

    private static final long MAX_SESSION_AGE_MS =
            30L * 60L * 1000L;

    private static final int GHOST_TRANSITION_DURATION_MS = 250;

    // ============================================================
    // EXECUTION
    // ============================================================

    private static final ExecutorService diskExecutor =
            Executors.newSingleThreadExecutor();

    private static final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    // ============================================================
    // SESSION STATE
    // ============================================================

    private static volatile boolean sessionWarmed = false;

    private static volatile boolean isResurrecting = false;

    private static volatile boolean isFreezeDried = false;

    private static volatile long sessionStartTime = 0L;

    private static volatile Bundle frozenState = null;

    private static volatile String lastUrl = null;

    private static volatile int lastScrollX = 0;

    private static volatile int lastScrollY = 0;

    // ============================================================
    // ACTIVITY / OVERLAY
    // ============================================================

    private static WeakReference<Activity> activityReference =
            new WeakReference<>(null);

    private static ImageView ghostOverlay;

    // ============================================================
    // CONSTRUCTOR
    // ============================================================

    private RoyalSessionSentinel() {
    }

    // ============================================================
    // SESSION WARMUP
    // ============================================================

    /**
     * Initializes session bookkeeping only.
     *
     * Network warmup is intentionally NOT performed here.
     */
    public static void warmupSession(
            Context context,
            String targetUrl
    ) {

        if (context == null) {
            return;
        }

        if (sessionWarmed) {
            return;
        }

        sessionStartTime =
                System.currentTimeMillis();

        sessionWarmed = true;

        Log.i(
                TAG,
                "🔥 Session Sentinel initialized."
        );
    }

    // ============================================================
    // SESSION VALIDITY
    // ============================================================

    public static boolean isSessionValid() {

        if (!sessionWarmed) {
            return false;
        }

        if (sessionStartTime <= 0L) {
            return false;
        }

        long age =
                System.currentTimeMillis()
                        - sessionStartTime;

        return age >= 0L
                && age <= MAX_SESSION_AGE_MS;
    }

    // ============================================================
    // FREEZE
    // ============================================================

    /**
     * Saves WebView state and creates a visual snapshot.
     *
     * WebView operations are executed on UI thread.
     * Disk operations are executed on diskExecutor.
     */
    public static void freeze(
            WebView webView
    ) {

        if (webView == null) {
            return;
        }

        runOnMainThread(() -> {

            if (webView.getUrl() == null) {
                return;
            }

            if (webView.getWidth() <= 0
                    || webView.getHeight() <= 0) {
                return;
            }

            Log.i(
                    TAG,
                    "❄️ Freezing WebView session state..."
            );

            // ----------------------------------------------------
            // UI THREAD ONLY
            // ----------------------------------------------------

            final Bundle webState =
                    new Bundle();

            webView.saveState(webState);

            final Bitmap snapshot =
                    captureWebView(webView);

            final String url =
                    webView.getUrl();

            final int scrollX =
                    webView.getScrollX();

            final int scrollY =
                    webView.getScrollY();

            // ----------------------------------------------------
            // MEMORY STATE
            // ----------------------------------------------------

            frozenState = webState;

            lastUrl = url;

            lastScrollX = scrollX;

            lastScrollY = scrollY;

            isFreezeDried = true;

            // ----------------------------------------------------
            // DISK THREAD
            // ----------------------------------------------------

            diskExecutor.execute(() -> {

                try {

                    File dir =
                            webView.getContext()
                                    .getCacheDir();

                    File stateFile =
                            new File(
                                    dir,
                                    STATE_FILE
                            );

                    File snapshotFile =
                            new File(
                                    dir,
                                    SNAPSHOT_FILE
                            );

                    File metaFile =
                            new File(
                                    dir,
                                    META_FILE
                            );

                    saveBundleToDisk(
                            webState,
                            stateFile
                    );

                    if (snapshot != null) {

                        try (
                                FileOutputStream fos =
                                        new FileOutputStream(
                                                snapshotFile
                                        )
                        ) {

                            snapshot.compress(
                                    Bitmap.CompressFormat.WEBP,
                                    85,
                                    fos
                            );

                            fos.flush();
                        }

                        snapshot.recycle();
                    }

                    saveMetadata(
                            metaFile,
                            url,
                            scrollX,
                            scrollY
                    );

                    Log.i(
                            TAG,
                            "✅ WebView session state persisted."
                    );

                } catch (Exception e) {

                    Log.e(
                            TAG,
                            "❌ Freeze persistence failed",
                            e
                    );
                }
            });
        });
    }

    // ============================================================
    // RESURRECT
    // ============================================================

    public static boolean resurrect(
            WebView webView,
            Activity activity
    ) {

        if (webView == null
                || activity == null) {

            return false;
        }

        if (!isSessionValid()) {

            Log.w(
                    TAG,
                    "⚠️ Session expired."
            );

            return false;
        }

        activityReference =
                new WeakReference<>(activity);

        // --------------------------------------------------------
        // MEMORY RESTORE
        // --------------------------------------------------------

        if (isFreezeDried
                && frozenState != null) {

            Log.i(
                    TAG,
                    "⚡ Restoring WebView state from memory."
            );

            showGhostOverlay(
                    activity,
                    null
            );

            runOnMainThread(() -> {

                try {

                    webView.restoreState(
                            frozenState
                    );

                    isResurrecting = true;

                    Log.i(
                            TAG,
                            "✅ WebView state restored."
                    );

                } catch (Exception e) {

                    Log.e(
                            TAG,
                            "❌ Memory restore failed",
                            e
                    );

                    isResurrecting = false;

                    hideGhostOverlay();
                }
            });

            return true;
        }

        // --------------------------------------------------------
        // DISK RESTORE
        // --------------------------------------------------------

        File dir =
                activity.getCacheDir();

        File stateFile =
                new File(
                        dir,
                        STATE_FILE
                );

        File snapshotFile =
                new File(
                        dir,
                        SNAPSHOT_FILE
                );

        File metaFile =
                new File(
                        dir,
                        META_FILE
                );

        if (!isStoredSessionValid(metaFile)) {

            Log.w(
                    TAG,
                    "⚠️ Stored session is missing or expired."
            );

            return false;
        }

        if (!stateFile.exists()) {

            Log.w(
                    TAG,
                    "⚠️ No stored WebView state."
            );

            return false;
        }

        isResurrecting = true;

        if (snapshotFile.exists()) {

            showGhostOverlay(
                    activity,
                    snapshotFile
            );
        }

        diskExecutor.execute(() -> {

            try {

                final Bundle restoredBundle =
                        loadBundleFromDisk(
                                stateFile
                        );

                if (restoredBundle == null) {

                    throw new IOException(
                            "Restored Bundle is null"
                    );
                }

                runOnMainThread(() -> {

                    try {

                        webView.restoreState(
                                restoredBundle
                        );

                        frozenState =
                                restoredBundle;

                        isFreezeDried = true;

                        Log.i(
                                TAG,
                                "⚡ WebView state restored from disk."
                        );

                    } catch (Exception e) {

                        Log.e(
                                TAG,
                                "❌ Disk restore failed",
                                e
                        );

                        isResurrecting = false;

                        hideGhostOverlay();
                    }
                });

            } catch (Exception e) {

                Log.e(
                        TAG,
                        "❌ Failed reading stored session",
                        e
                );

                runOnMainThread(() -> {

                    isResurrecting = false;

                    hideGhostOverlay();
                });
            }
        });

        return true;
    }

    // ============================================================
    // PAGE READY
    // ============================================================

    public static void notifyPageReady() {

        if (!isResurrecting) {
            return;
        }

        mainHandler.postDelayed(
                () -> {

                    Log.i(
                            TAG,
                            "🎯 Restored page ready."
                    );

                    hideGhostOverlay();

                    isResurrecting = false;

                },
                80L
        );
    }

    // ============================================================
    // SNAPSHOT
    // ============================================================

    private static Bitmap captureWebView(
            WebView webView
    ) {

        if (webView == null) {
            return null;
        }

        try {

            int width =
                    webView.getWidth();

            int height =
                    webView.getHeight();

            if (width <= 0
                    || height <= 0) {

                return null;
            }

            Bitmap bitmap =
                    Bitmap.createBitmap(
                            width,
                            height,
                            Bitmap.Config.ARGB_8888
                    );

            Canvas canvas =
                    new Canvas(bitmap);

            webView.draw(canvas);

            return bitmap;

        } catch (Exception e) {

            Log.w(
                    TAG,
                    "⚠️ Snapshot capture failed",
                    e
            );

            return null;
        }
    }

    // ============================================================
    // GHOST OVERLAY
    // ============================================================

    private static void showGhostOverlay(
            Activity activity,
            File snapshotFile
    ) {

        if (activity == null) {
            return;
        }

        activityReference =
                new WeakReference<>(activity);

        mainHandler.post(() -> {

            Activity currentActivity =
                    activityReference.get();

            if (currentActivity == null
                    || currentActivity.isFinishing()
                    || currentActivity.isDestroyed()) {

                return;
            }

            try {

                if (ghostOverlay == null
                        || ghostOverlay.getContext()
                        != currentActivity) {

                    removeGhostOverlayInternal();

                    ghostOverlay =
                            new ImageView(
                                    currentActivity
                            );

                    ghostOverlay.setScaleType(
                            ImageView.ScaleType.FIT_XY
                    );

                    ghostOverlay.setBackgroundColor(
                            Color.WHITE
                    );

                    ViewGroup decor =
                            (ViewGroup)
                                    currentActivity
                                            .getWindow()
                                            .getDecorView();

                    decor.addView(
                            ghostOverlay,
                            new ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                            )
                    );
                }

                ghostOverlay.setAlpha(1f);

                ghostOverlay.setVisibility(
                        ImageView.VISIBLE
                );

                if (snapshotFile != null
                        && snapshotFile.exists()) {

                    diskExecutor.execute(() -> {

                        Bitmap bitmap =
                                BitmapFactory.decodeFile(
                                        snapshotFile
                                                .getAbsolutePath()
                                );

                        if (bitmap == null) {
                            return;
                        }

                        mainHandler.post(() -> {

                            Activity owner =
                                    activityReference.get();

                            if (owner == null
                                    || owner.isFinishing()
                                    || owner.isDestroyed()) {

                                bitmap.recycle();

                                return;
                            }

                            if (ghostOverlay != null) {

                                ghostOverlay.setImageBitmap(
                                        bitmap
                                );
                            } else {

                                bitmap.recycle();
                            }
                        });
                    });
                }

            } catch (Exception e) {

                Log.w(
                        TAG,
                        "⚠️ Ghost overlay failed",
                        e
                );
            }
        });
    }

    // ============================================================
    // HIDE GHOST
    // ============================================================

    public static void hideGhostOverlay() {

        mainHandler.post(
                RoyalSessionSentinel::hideGhostOverlayInternal
        );
    }

    private static void hideGhostOverlayInternal() {

        if (ghostOverlay == null) {
            return;
        }

        if (ghostOverlay.getVisibility()
                != ImageView.VISIBLE) {

            return;
        }

        ghostOverlay.animate()
                .alpha(0f)
                .setDuration(
                        GHOST_TRANSITION_DURATION_MS
                )
                .withEndAction(() -> {

                    if (ghostOverlay != null) {

                        ghostOverlay.setVisibility(
                                ImageView.GONE
                        );

                        ghostOverlay.setImageDrawable(
                                null
                        );
                    }
                })
                .start();
    }

    private static void removeGhostOverlayInternal() {

        if (ghostOverlay == null) {
            return;
        }

        ViewGroup parent =
                (ViewGroup)
                        ghostOverlay.getParent();

        if (parent != null) {

            parent.removeView(
                    ghostOverlay
            );
        }

        ghostOverlay.setImageDrawable(
                null
        );

        ghostOverlay = null;
    }

    // ============================================================
    // DISK STATE
    // ============================================================

    private static void saveBundleToDisk(
            Bundle bundle,
            File file
    ) throws IOException {

        if (bundle == null) {
            throw new IOException(
                    "Bundle is null"
            );
        }

        Parcel parcel =
                Parcel.obtain();

        try {

            bundle.writeToParcel(
                    parcel,
                    0
            );

            byte[] bytes =
                    parcel.marshall();

            try (
                    FileOutputStream fos =
                            new FileOutputStream(
                                    file
                            )
            ) {

                fos.write(bytes);

                fos.flush();
            }

        } finally {

            parcel.recycle();
        }
    }

    private static Bundle loadBundleFromDisk(
            File file
    ) throws IOException {

        if (!file.exists()) {
            return null;
        }

        long length =
                file.length();

        if (length <= 0
                || length > Integer.MAX_VALUE) {

            throw new IOException(
                    "Invalid state file size"
            );
        }

        byte[] bytes =
                new byte[(int) length];

        try (
                FileInputStream fis =
                        new FileInputStream(file)
        ) {

            int offset = 0;

            while (offset < bytes.length) {

                int read =
                        fis.read(
                                bytes,
                                offset,
                                bytes.length - offset
                        );

                if (read < 0) {
                    break;
                }

                offset += read;
            }

            if (offset != bytes.length) {

                throw new IOException(
                        "Incomplete state file"
                );
            }
        }

        Parcel parcel =
                Parcel.obtain();

        try {

            parcel.unmarshall(
                    bytes,
                    0,
                    bytes.length
            );

            parcel.setDataPosition(0);

            Bundle bundle =
                    new Bundle();

            bundle.readFromParcel(
                    parcel
            );

            return bundle;

        } finally {

            parcel.recycle();
        }
    }

    // ============================================================
    // METADATA
    // ============================================================

    private static void saveMetadata(
            File file,
            String url,
            int scrollX,
            int scrollY
    ) throws IOException {

        Properties properties =
                new Properties();

        properties.setProperty(
                "url",
                url != null ? url : ""
        );

        properties.setProperty(
                "x",
                String.valueOf(scrollX)
        );

        properties.setProperty(
                "y",
                String.valueOf(scrollY)
        );

        properties.setProperty(
                "time",
                String.valueOf(
                        System.currentTimeMillis()
                )
        );

        try (
                FileOutputStream fos =
                        new FileOutputStream(
                                file
                        )
        ) {

            properties.store(
                    fos,
                    "Royal Session Metadata"
            );

            fos.flush();
        }
    }

    private static boolean isStoredSessionValid(
            File metadataFile
    ) {

        if (!metadataFile.exists()) {
            return false;
        }

        try (
                FileInputStream fis =
                        new FileInputStream(
                                metadataFile
                        )
        ) {

            Properties properties =
                    new Properties();

            properties.load(fis);

            String timeString =
                    properties.getProperty(
                            "time"
                    );

            if (timeString == null) {
                return false;
            }

            long savedTime =
                    Long.parseLong(
                            timeString
                    );

            long age =
                    System.currentTimeMillis()
                            - savedTime;

            return age >= 0L
                    && age <= MAX_SESSION_AGE_MS;

        } catch (Exception e) {

            Log.w(
                    TAG,
                    "⚠️ Stored session metadata invalid.",
                    e
            );

            return false;
        }
    }

    // ============================================================
    // WARMUP SCRIPT
    // ============================================================

    /**
     * Retained only as an optional local asset helper.
     *
     * This does NOT claim to warm V8 snapshots.
     */
    public static File prepareWarmupScript(
            Context context
    ) {

        if (context == null) {
            return null;
        }

        try {

            File scriptFile =
                    new File(
                            context.getCacheDir(),
                            WARMUP_SCRIPT_FILE
                    );

            if (!scriptFile.exists()) {

                try (
                        FileWriter writer =
                                new FileWriter(
                                        scriptFile
                                )
                ) {

                    writer.write(
                            "// Royal local helper script\n"
                    );
                }
            }

            return scriptFile;

        } catch (Exception e) {

            Log.w(
                    TAG,
                    "⚠️ Warmup script creation failed",
                    e
            );

            return null;
        }
    }

    // ============================================================
    // CLEANUP
    // ============================================================

    public static void cleanup() {

        runOnMainThread(() -> {

            Log.i(
                    TAG,
                    "🧹 Cleaning Session Sentinel..."
            );

            removeGhostOverlayInternal();

            activityReference.clear();

            frozenState = null;

            lastUrl = null;

            lastScrollX = 0;

            lastScrollY = 0;

            isFreezeDried = false;

            isResurrecting = false;

            sessionWarmed = false;

            sessionStartTime = 0L;

            Log.i(
                    TAG,
                    "✅ Session Sentinel cleaned."
            );
        });
    }

    // ============================================================
    // THREAD HELPER
    // ============================================================

    private static void runOnMainThread(
            Runnable runnable
    ) {

        if (Looper.myLooper()
                == Looper.getMainLooper()) {

            runnable.run();

        } else {

            mainHandler.post(
                    runnable
            );
        }
    }

    // ============================================================
    // STATUS
    // ============================================================

    public static boolean isSessionWarmed() {

        return sessionWarmed;
    }

    public static boolean isFreezeDried() {

        return isFreezeDried;
    }

    public static boolean hasFrozenState() {

        return frozenState != null;
    }

    public static long getSessionAge() {

        if (sessionStartTime <= 0L) {
            return 0L;
        }

        return System.currentTimeMillis()
                - sessionStartTime;
    }

    public static String getLastUrl() {

        return lastUrl;
    }
    }
