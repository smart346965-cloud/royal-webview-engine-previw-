package com.store.app;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebView;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.FragmentActivity;

public class SystemUI {

    private static final String TAG = "SystemUI_Engine";

    private static int currentHeaderColor = Integer.MIN_VALUE;
    private static String currentHeaderUrl = null;

    private static long syncGeneration = 0L;

    private static final Handler SYNC_HANDLER = new Handler(Looper.getMainLooper());
    private static Runnable syncTask;

    private static final long NAVIGATION_BAR_HIDE_DELAY = 3500L;
    private static final long SYSTEM_BARS_HIDE_DELAY = 3000L;

    private static final Handler NAV_HANDLER = new Handler(Looper.getMainLooper());

    private static Runnable navigationHideTask;
    private static Runnable systemBarsHideTask;

    private static int detectedNavigationMode = -1;
    private static boolean navigationBarControllerReady = false;

    private static int detectNavigationMode(android.content.Context context) {
        if (context == null) return 2;
        try {
            int resourceId = context.getResources().getIdentifier("config_navBarInteractionMode", "integer", "android");
            if (resourceId != 0) {
                int mode = context.getResources().getInteger(resourceId);
                if (mode >= 0 && mode <= 2) return mode;
            }
        } catch (Throwable t) {
            Log.w(TAG, "Navigation mode detection failed.", t);
        }
        return 2;
    }

    public static void hideSystemBars(android.app.Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        activity.runOnUiThread(() -> {
            Window window = activity.getWindow();
            if (window == null) return;
            WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
            if (controller == null) return;
            controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_SWIPE);
            controller.hide(androidx.core.view.WindowInsetsCompat.Type.navigationBars());
        });
    }

    public static void showSystemBarsOnInteraction(android.app.Activity activity) {}

    private static void scheduleSystemBarsHide(android.app.Activity activity) {}

    public static void cancelSystemBarsHide() {
        if (systemBarsHideTask != null) {
            NAV_HANDLER.removeCallbacks(systemBarsHideTask);
            systemBarsHideTask = null;
        }
    }

    public static void initializeNavigationBarController(android.app.Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        activity.runOnUiThread(() -> {
            Window window = activity.getWindow();
            if (window == null) return;
            detectedNavigationMode = detectNavigationMode(activity);
            navigationBarControllerReady = true;
            WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
            if (controller == null) return;
            window.setNavigationBarColor(Color.TRANSPARENT);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                window.setNavigationBarContrastEnforced(false);
            }
            if (detectedNavigationMode == 2) {
                cancelNavigationBarHide();
                controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_SWIPE);
                return;
            }
        });
    }

    public static void scheduleNavigationBarHide(android.app.Activity activity) {
        cancelNavigationBarHide();
        if (activity == null || activity.isFinishing() || !navigationBarControllerReady) return;
        navigationHideTask = () -> {
            if (activity.isFinishing()) return;
            Window window = activity.getWindow();
            if (window == null) return;
            WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
            if (controller != null) {
                controller.hide(androidx.core.view.WindowInsetsCompat.Type.navigationBars());
            }
        };
        NAV_HANDLER.postDelayed(navigationHideTask, NAVIGATION_BAR_HIDE_DELAY);
    }

    public static void cancelNavigationBarHide() {
        if (navigationHideTask != null) {
            NAV_HANDLER.removeCallbacks(navigationHideTask);
            navigationHideTask = null;
        }
    }

    private static void enforceStableNavigationBarPolicy(android.app.Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        Window window = activity.getWindow();
        if (window == null) return;
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
        if (controller == null) return;
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_SWIPE);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false);
        }
    }

    public static void refreshNavigationBar(android.app.Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        activity.runOnUiThread(() -> {
            detectedNavigationMode = detectNavigationMode(activity);
            hideSystemBars(activity);
        });
    }

    public static void lockStatusBarIcons() {}
    public static void unlockStatusBarIcons() {}

    public static void applyKingMode(FragmentActivity activity, WebView webView, int initialColor) {
        if (activity == null) return;
        Window window = activity.getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false);
            window.setStatusBarContrastEnforced(false);
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
        if (controller != null) {
            controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_SWIPE);
        }
        initializeNavigationBarController(activity);
        enforceStableNavigationBarPolicy(activity);
        hideSystemBars(activity);
        applyHeaderColor(activity, initialColor);
    }

    public static void applyHeaderColor(android.app.Activity activity, int targetColor) {
        applyHeaderColorInternal(activity, targetColor, true);
    }

    private static void applyHeaderColorInternal(android.app.Activity activity, int targetColor, boolean updateIcons) {
        if (activity == null || activity.isFinishing()) return;
        activity.runOnUiThread(() -> {
            Window window = activity.getWindow();
            if (window == null) return;
            window.setStatusBarColor(Color.TRANSPARENT);
            int defaultBg = getDefaultSystemColor(activity);
            int solidColor = compositeColorWithBackground(targetColor, defaultBg);
            View topSurface = activity.findViewById(android.R.id.content).findViewWithTag("TOP_VISUAL_SURFACE");
            if (topSurface != null) {
                topSurface.setBackgroundColor(solidColor);
            }
            if (updateIcons) {
                applyStatusBarIconAppearance(window, solidColor);
                WindowInsetsControllerCompat navigationController = WindowCompat.getInsetsController(window, window.getDecorView());
                if (navigationController != null) {
                    navigationController.setAppearanceLightNavigationBars(isColorLight(getDefaultSystemColor(activity)));
                }
            }
            currentHeaderColor = solidColor;
        });
    }

    private static void setStatusBarIconsInternal(Window window, boolean lightBackground) {
        if (window == null) return;
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
        if (controller != null) {
            controller.setAppearanceLightStatusBars(lightBackground);
        }
    }

    public static void setStatusBarIcons(Window window, boolean lightBackground) {
        setStatusBarIconsInternal(window, lightBackground);
    }

    public static void setDynamicIcons(Window window, boolean isLightBackground) {
        setStatusBarIconsInternal(window, isLightBackground);
    }

    public static void forceNativeStatusBar(android.app.Activity activity, int color) {
        if (activity == null || activity.isFinishing()) return;
        syncGeneration++;
        cancelStatusBarSync();
        applyHeaderColor(activity, color);
    }

    public static void syncWithNativeUI(android.app.Activity activity, int uiColor) {
        forceNativeStatusBar(activity, uiColor);
    }

    public static void makeStatusBarTransparent(android.app.Activity activity, int underlyingColor) {
        applyHeaderColor(activity, underlyingColor);
    }

    public static void updateStatusBarColor(android.app.Activity activity, int targetColor) {
        applyHeaderColor(activity, targetColor);
    }

    public static void restoreHeaderOnResume(android.app.Activity activity) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        if (currentHeaderColor == Integer.MIN_VALUE) {
            applyHeaderColor(activity, getDefaultSystemColor(activity));
        }
    }

    public static boolean isColorLight(int color) {
        double red = Color.red(color) / 255.0;
        double green = Color.green(color) / 255.0;
        double blue = Color.blue(color) / 255.0;
        double r = (red <= 0.04045) ? red / 12.92 : Math.pow((red + 0.055) / 1.055, 2.4);
        double g = (green <= 0.04045) ? green / 12.92 : Math.pow((green + 0.055) / 1.055, 2.4);
        double b = (blue <= 0.04045) ? blue / 12.92 : Math.pow((blue + 0.055) / 1.055, 2.4);
        double luminance = (0.2126 * r) + (0.7152 * g) + (0.0722 * b);
        double blackContrast = (luminance + 0.05) / 0.05;
        double whiteContrast = 1.05 / (luminance + 0.05);
        return blackContrast >= whiteContrast;
    }

    private static void applyStatusBarIconAppearance(Window window, int backgroundColor) {
        if (window == null) return;
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
        if (controller == null) return;
        boolean lightBackground = isColorLight(backgroundColor);
        controller.setAppearanceLightStatusBars(lightBackground);
    }

    public static int compositeColorWithBackground(int foregroundColor, int backgroundColor) {
        int alpha = Color.alpha(foregroundColor);
        if (alpha == 255) return foregroundColor;
        if (alpha == 0) return backgroundColor;
        float a = alpha / 255.0f;
        int r = (int) (Color.red(foregroundColor) * a + Color.red(backgroundColor) * (1 - a));
        int g = (int) (Color.green(foregroundColor) * a + Color.green(backgroundColor) * (1 - a));
        int b = (int) (Color.blue(foregroundColor) * a + Color.blue(backgroundColor) * (1 - a));
        return Color.rgb(r, g, b);
    }

    private static void syncStatusBarWithWeb(android.app.Activity activity, WebView webView, long requestGeneration, String requestedUrl) {
        if (activity == null || webView == null) return;
        String defaultHex = (currentHeaderColor != Integer.MIN_VALUE)
                ? String.format("#%06X", (0xFFFFFF & currentHeaderColor))
                : (isDarkMode(activity) ? "#12141C" : "#FFFFFF");
        String jsScript =
                "(function() {" +
                "  function normalizeColor(colorStr) {" +
                "    if (!colorStr) return null;" +
                "    try {" +
                "      var canvas = document.createElement('canvas');" +
                "      canvas.width = 1; canvas.height = 1;" +
                "      var ctx = canvas.getContext('2d');" +
                "      ctx.fillStyle = colorStr;" +
                "      return ctx.fillStyle;" +
                "    } catch(e) { return colorStr; }" +
                "  }" +
                "  function isBlackOrTransparent(colorStr) {" +
                "    if (!colorStr) return true;" +
                "    var c = colorStr.toLowerCase().replace(/\\s+/g, '');" +
                "    return c === 'transparent' || c === 'rgba(0,0,0,0)' || c === '#000000' || c === '#000' || c === 'rgb(0,0,0)';" +
                "  }" +
                "  function extractColor() {" +
                "    var metas = document.querySelectorAll('meta[name=\"theme-color\"]');" +
                "    for (var i = 0; i < metas.length; i++) {" +
                "      var m = metas[i];" +
                "      if (!m.media || window.matchMedia(m.media).matches) {" +
                "        if (m.content) {" +
                "          var normalized = normalizeColor(m.content);" +
                "          if (normalized && !isBlackOrTransparent(normalized)) return normalized;" +
                "        }" +
                "      }" +
                "    }" +
                "    var probeY = Math.max(1, Math.min(24, window.innerHeight * 0.03));" +
                "    var el = document.elementFromPoint(window.innerWidth / 2, probeY);" +
                "    if (!el) {" +
                "      el = document.querySelector('header, nav, [role=\"banner\"], [data-header], .header, .navbar') || document.body;" +
                "    }" +
                "    while (el && el !== document.documentElement) {" +
                "      var st = window.getComputedStyle(el);" +
                "      var bg = st.backgroundColor;" +
                "      if (bg && !isBlackOrTransparent(bg)) {" +
                "        return normalizeColor(bg);" +
                "      }" +
                "      el = el.parentElement;" +
                "    }" +
                "    var bodyBg = window.getComputedStyle(document.body).backgroundColor;" +
                "    if (bodyBg && !isBlackOrTransparent(bodyBg)) {" +
                "      return normalizeColor(bodyBg);" +
                "    }" +
                "    return '" + defaultHex + "';" +
                "  }" +
                "  return extractColor();" +
                "})();";
        webView.evaluateJavascript(jsScript, value -> {
            if (requestGeneration != syncGeneration) {
                return;
            }
            if (activity.isFinishing()) {
                return;
            }
            String currentUrl = webView.getUrl();
            if (requestedUrl != null
                    && currentUrl != null
                    && !requestedUrl.equals(currentUrl)) {
                return;
            }
            if (value == null
                    || value.equals("null")
                    || value.equals("\"null\"")) {
                return;
            }
            int parsedColor =
                    parseColorString(activity, value);
            applyHeaderColor(
                    activity,
                    parsedColor
            );
        });
    }

    public static void syncStatusBarWithWebEarly(android.app.Activity activity, WebView webView) {
        scheduleStatusBarSync(activity, webView);
    }

    public static void scheduleStatusBarSync(android.app.Activity activity, WebView webView) {
        if (activity == null
                || activity.isFinishing()
                || webView == null) {
            return;
        }
        cancelStatusBarSync();
        syncGeneration++;
        final long scheduledGeneration = syncGeneration;
        final String scheduledUrl = webView.getUrl();
        syncTask = () -> {
            if (activity.isFinishing()
                    || webView.getVisibility() != View.VISIBLE
                    || scheduledGeneration != syncGeneration) {
                return;
            }
            String currentUrl = webView.getUrl();
            if (scheduledUrl != null
                    && currentUrl != null
                    && !scheduledUrl.equals(currentUrl)) {
                return;
            }
            syncStatusBarWithWeb(
                    activity,
                    webView,
                    scheduledGeneration,
                    scheduledUrl
            );
        };
        SYNC_HANDLER.postDelayed(
                syncTask,
                80L
        );
    }

    public static void cancelStatusBarSync() {
        if (syncTask != null) {
            SYNC_HANDLER.removeCallbacks(syncTask);
            syncTask = null;
        }
    }

    public static void onHeaderColorChanged(android.app.Activity activity, String colorStr) {
        if (activity == null || colorStr == null) return;
        final String trimmed = colorStr.replace("\"", "").trim();
        if (trimmed.isEmpty() || trimmed.equalsIgnoreCase("null")) {
            return;
        }
        activity.runOnUiThread(() -> {
            try {
                int parsedColor = parseColorString(activity, trimmed);
                cancelStatusBarSync();
                applyHeaderColor(
                        activity,
                        parsedColor
                );
            } catch (Throwable t) {
                Log.w(TAG, "Instant header color update failed: " + trimmed, t);
            }
        });
    }

    public static void applyInstantHeaderColor(android.app.Activity activity, String colorStr) {
        if (activity == null || activity.isFinishing() || colorStr == null) {
            return;
        }
        final String trimmed = colorStr.replace("\"", "").trim();
        if (trimmed.isEmpty() || trimmed.equalsIgnoreCase("null")) {
            return;
        }
        activity.runOnUiThread(() -> {
            try {
                int parsedColor = parseColorString(activity, trimmed);
                cancelStatusBarSync();
                applyHeaderColor(activity, parsedColor);
            } catch (Throwable t) {
                Log.w(TAG, "applyInstantHeaderColor failed: " + trimmed, t);
            }
        });
    }

    public static int parseColorString(android.content.Context context, String colorStr) {
        int defaultColor = getDefaultSystemColor(context);
        if (colorStr == null) return defaultColor;
        colorStr = colorStr.replace("\"", "").trim();
        try {
            if (colorStr.startsWith("#")) {
                return Color.parseColor(colorStr);
            } else if (colorStr.startsWith("rgb")) {
                String[] parts = colorStr.substring(colorStr.indexOf("(") + 1, colorStr.indexOf(")")).split(",");
                int r = Integer.parseInt(parts[0].trim());
                int g = Integer.parseInt(parts[1].trim());
                int b = Integer.parseInt(parts[2].trim());
                if (parts.length >= 4) {
                    float a = Float.parseFloat(parts[3].trim());
                    int alphaInt = Math.round(a * 255);
                    return Color.argb(alphaInt, r, g, b);
                }
                return Color.rgb(r, g, b);
            }
        } catch (Exception e) {
            Log.w(TAG, "Color parsing fallback triggered for: " + colorStr, e);
        }
        return defaultColor;
    }

    public static boolean isDarkMode(android.content.Context context) {
        if (context == null) return false;
        int nightModeFlags = context.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    public static int getDefaultSystemColor(android.content.Context context) {
        return isDarkMode(context) ? Color.parseColor("#12141C") : Color.WHITE;
    }

    public static void notifyNavigationUserInteraction(android.app.Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        activity.runOnUiThread(() -> {
            scheduleNavigationBarHide(activity);
        });
    }

    public static void onNavigationBarVisibilityChanged(android.app.Activity activity, boolean visible) {
        if (activity == null || activity.isFinishing()) return;
        if (visible) {
            Window window = activity.getWindow();
            if (window != null) {
                WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
                if (controller != null) {
                    controller.show(androidx.core.view.WindowInsetsCompat.Type.navigationBars());
                }
            }
            scheduleNavigationBarHide(activity);
        } else {
            cancelNavigationBarHide();
        }
    }
        }
