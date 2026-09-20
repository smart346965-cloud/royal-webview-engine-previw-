package com.store.app.offline;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.store.app.BuildConfig;
import com.store.app.NetworkMonitor;
import com.store.app.R;
import com.store.app.RoyalNetworkEngine;
import com.store.app.WebEngineManager;
import com.store.app.SystemUI;
import com.store.app.offline.OfflineStateManager;

public class OfflineUIController {
    private static final String TAG = "OfflineUIController";
    private final Activity activity;
    private final WebView webView;
    private final WebEngineManager engineManager;
    private FrameLayout pureOfflineUI;
    private ProgressBar progressBar;
    private OfflineBarController offlineBarController;
    private boolean isOfflineUIVisible = false;
    private boolean isPageLoaded = false;
    private static final long RETRY_HOLD_DURATION = 4000L;
    private Handler retryHandler = new Handler(Looper.getMainLooper());
    private boolean retryInProgress = false;
    private ObjectAnimator retryLoaderAnimator;
    private static final int OFFLINE_BACKGROUND = Color.parseColor("#F3F4F6");
    private static final int OFFLINE_CARD_TOP = Color.parseColor("#FFFFFF");
    private static final int OFFLINE_CARD_BOTTOM = Color.parseColor("#F5F6F9");
    private static final int OFFLINE_PRIMARY_TEXT = Color.parseColor("#17181C");
    private static final int OFFLINE_SECONDARY_TEXT = Color.parseColor("#6B707A");
    private static final int OFFLINE_ACCENT = Color.parseColor("#6674E8");
    public interface OfflineUICallback { void onOfflineUIVisibilityChanged(boolean visible); void onOfflineBarVisibilityChanged(boolean visible); }
    private OfflineUICallback callback;
    private ObjectAnimator floatingAnimator, elevationAnimator;

    public OfflineUIController(Activity activity, WebView webView, WebEngineManager engineManager) { this.activity = activity; this.webView = webView; this.engineManager = engineManager; }

    public void init() {
        Log.i(TAG, "🚀 Initializing OfflineUIController...");
        offlineBarController = new OfflineBarController(activity); offlineBarController.init();
        createPureOfflineUI();
        progressBar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 6, android.view.Gravity.TOP);
        activity.addContentView(progressBar, p);
        progressBar.setVisibility(View.GONE);
        NetworkMonitor.setListener(connected -> { Log.i(TAG, "📡 Network state changed: " + connected); handleNetworkChange(connected); });
        OfflineStateManager.getInstance().bind(webView, this);
        offlineBarController.setCallback(visible -> { if (callback != null) callback.onOfflineBarVisibilityChanged(visible); });
        Log.i(TAG, "✅ OfflineUIController initialized.");
    }

    public void onResume() {
        Log.d(TAG, "🔄 onResume called");
        if (activity == null || activity.isFinishing()) return;
        if (!NetworkMonitor.isInternetAvailable(activity)) { SystemUI.applyHeaderColor(activity, OFFLINE_BACKGROUND); handleOfflineState(); }
    }

    public void destroy() {
        Log.i(TAG, "🧹 Destroying OfflineUIController...");
        NetworkMonitor.setListener(null);
        if (retryHandler != null) { retryHandler.removeCallbacksAndMessages(null); }
        if (retryLoaderAnimator != null) { retryLoaderAnimator.cancel(); retryLoaderAnimator = null; }
        retryInProgress = false;
        if (offlineBarController != null) { offlineBarController.destroy(); offlineBarController = null; }
        pureOfflineUI = null; callback = null;
    }

    private void handleNetworkChange(boolean connected) { if (connected) handleOnlineState(); else handleOfflineState(); }

    private void handleOfflineState() {
        Log.i(TAG, "📡 Network lost. Handling offline state...");
        if (webView == null) return;
        if (webView.getUrl() == null || webView.getUrl().equals("about:blank")) { showOfflineUI(); }
        else if (engineManager != null && !engineManager.isPageValid()) { showOfflineUI(); }
        else { if (offlineBarController != null) offlineBarController.show(); }
    }

    private void handleOnlineState() {
        Log.i(TAG, "🌐 Network restored. Synchronizing UI...");
        if (webView == null) return;
        if (offlineBarController != null && offlineBarController.isVisible()) offlineBarController.hideWithAnimation();
        if (webView.getUrl() == null || webView.getUrl().equals("about:blank")) { webView.loadUrl(BuildConfig.CLIENT_URL); }
        else if (!OfflineStateManager.getInstance().isPageValid()) { webView.reload(); }
    }

    public void forceHideAllInternal() {
        activity.runOnUiThread(() -> { if (isOfflineUIVisible) hideOfflineUI(); if (offlineBarController != null) offlineBarController.hideImmediately(); });
    }

    public void showOnlineBarTransition() { if (offlineBarController != null) offlineBarController.showOnlineTransition(); }

    public void showLoadingOverlay() {
        activity.runOnUiThread(() -> { if (progressBar != null) { if (progressBar.getVisibility() != View.VISIBLE) { progressBar.setAlpha(0f); progressBar.setVisibility(View.VISIBLE); progressBar.animate().alpha(1f).setDuration(180).start(); } } });
    }

    public void hideLoadingOverlay() {
        activity.runOnUiThread(() -> { if (progressBar != null && progressBar.getVisibility() == View.VISIBLE) { progressBar.animate().alpha(0f).setDuration(180).withEndAction(() -> progressBar.setVisibility(View.GONE)).start(); } });
    }

    private void createPureOfflineUI() {
        if (activity == null) return;
        pureOfflineUI = new FrameLayout(activity);
        pureOfflineUI.setBackgroundColor(OFFLINE_BACKGROUND);
        pureOfflineUI.setVisibility(View.GONE);
        ImageView illustration = new ImageView(activity);
        illustration.setImageResource(R.drawable.offline_illustration);
        illustration.setScaleType(ImageView.ScaleType.FIT_CENTER);
        illustration.setAdjustViewBounds(true);
        illustration.setBackgroundColor(Color.TRANSPARENT);
        FrameLayout.LayoutParams illustrationParams = new FrameLayout.LayoutParams(dp(300), dp(430), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        illustrationParams.topMargin = dp(34);
        pureOfflineUI.addView(illustration, illustrationParams);
        LinearLayout bottomCard = new LinearLayout(activity);
        bottomCard.setOrientation(LinearLayout.VERTICAL);
        bottomCard.setGravity(Gravity.CENTER_HORIZONTAL);
        bottomCard.setBackground(createCardDrawable());
        bottomCard.setPadding(dp(22), dp(26), dp(22), dp(26));
        bottomCard.setElevation(dp(16));
        bottomCard.setTranslationZ(dp(0));
        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        cardParams.leftMargin = dp(16); cardParams.rightMargin = dp(16); cardParams.bottomMargin = dp(16);
        pureOfflineUI.addView(bottomCard, cardParams);
        pureOfflineUI.setOnApplyWindowInsetsListener((v, insets) -> {
            int bottomInset = (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) ? insets.getInsets(WindowInsets.Type.systemBars()).bottom : insets.getSystemWindowInsetBottom();
            int professionalSpacing = dp(16);
            FrameLayout.LayoutParams updatedParams = (FrameLayout.LayoutParams) bottomCard.getLayoutParams();
            if (updatedParams.bottomMargin <= 0) { updatedParams.bottomMargin = bottomInset + professionalSpacing; bottomCard.setLayoutParams(updatedParams); }
            return insets;
        });
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) pureOfflineUI.requestApplyInsets();
        TextView titleMsg = new TextView(activity);
        titleMsg.setText("لا يوجد اتصال بالإنترنت");
        titleMsg.setTextColor(OFFLINE_PRIMARY_TEXT);
        titleMsg.setTextSize(20f);
        titleMsg.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        titleMsg.setGravity(Gravity.CENTER);
        titleMsg.setIncludeFontPadding(true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.bottomMargin = dp(8);
        bottomCard.addView(titleMsg, titleParams);
        TextView subMsg = new TextView(activity);
        subMsg.setText("يبدو أنك غير متصل بالشبكة. يرجى التحقق من الواي فاي أو بيانات الهاتف والمحاولة مجدداً.");
        subMsg.setTextColor(OFFLINE_SECONDARY_TEXT);
        subMsg.setTextSize(14.72f);
        subMsg.setGravity(Gravity.CENTER);
        subMsg.setIncludeFontPadding(true);
        subMsg.setLineSpacing(0, 1.6f);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subParams.bottomMargin = dp(22);
        bottomCard.addView(subMsg, subParams);
        FrameLayout btnContainer = new FrameLayout(activity);
        btnContainer.setBackground(createRetryButtonDrawable());
        btnContainer.setClickable(true);
        btnContainer.setFocusable(true);
        btnContainer.setForeground(createRippleDrawable());
        LinearLayout btnContent = new LinearLayout(activity);
        btnContent.setOrientation(LinearLayout.HORIZONTAL);
        btnContent.setGravity(Gravity.CENTER);
        btnContent.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        ImageView retryIcon = new ImageView(activity);
        retryIcon.setImageDrawable(new RetryIconDrawable(Color.WHITE, dp(18)));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(18), dp(18));
        btnContent.addView(retryIcon, iconParams);
        TextView retryText = new TextView(activity);
        retryText.setText("إعادة المحاولة");
        retryText.setTextColor(Color.WHITE);
        retryText.setTextSize(15.68f);
        retryText.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        retryText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams retryTextParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        retryTextParams.setMargins(dp(5), 0, 0, 0);
        btnContent.addView(retryText, retryTextParams);
        LinearLayout dotsLoader = createDotsLoader();
        FrameLayout.LayoutParams dotsParams = new FrameLayout.LayoutParams(dp(30), dp(24), Gravity.CENTER);
        dotsLoader.setVisibility(View.INVISIBLE);
        btnContainer.addView(dotsLoader, dotsParams);
        FrameLayout.LayoutParams contentParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER);
        btnContainer.addView(btnContent, contentParams);
        btnContainer.setOnClickListener(v -> {
            if (!btnContainer.isEnabled() || retryInProgress) return;
            retryInProgress = true;
            btnContainer.setEnabled(false);
            btnContent.animate().alpha(0f).setDuration(160).start();
            dotsLoader.setVisibility(View.VISIBLE);
            dotsLoader.setAlpha(0f);
            dotsLoader.setRotation(0f);
            dotsLoader.animate().alpha(1f).setDuration(180).start();
            retryLoaderAnimator = ObjectAnimator.ofFloat(dotsLoader, View.ROTATION, 0f, 360f);
            retryLoaderAnimator.setDuration(1200);
            retryLoaderAnimator.setRepeatCount(ObjectAnimator.INFINITE);
            retryLoaderAnimator.setInterpolator(new android.view.animation.LinearInterpolator());
            retryLoaderAnimator.start();
            if (webView != null) {
                webView.post(() -> {
                    try {
                        if (webView.getUrl() == null || webView.getUrl().equals("about:blank")) { webView.loadUrl(BuildConfig.CLIENT_URL); }
                        else { webView.reload(); }
                    } catch (Exception e) { Log.e(TAG, "Retry reload failed.", e); }
                });
            }
            retryHandler.postDelayed(() -> {
                if (activity == null || activity.isFinishing()) { resetRetryButton(btnContainer, btnContent, dotsLoader); return; }
                boolean internetAvailable = NetworkMonitor.isInternetAvailable(activity);
                boolean pageValid = false;
                try { pageValid = OfflineStateManager.getInstance().isPageValid(); } catch (Exception e) { Log.e(TAG, "Retry page validation failed.", e); }

                if (internetAvailable && pageValid) {
                    Log.i(TAG, "✅ Retry successful after 4-second hold.");
                    stopRetryLoader(dotsLoader);

                    // 👑 المرحلة الأولى: إعادة تحميل الصفحة أولاً
                    if (webView != null) {
                        if (webView.getUrl() == null || webView.getUrl().equals("about:blank")) {
                            webView.loadUrl(BuildConfig.CLIENT_URL);
                        } else {
                            webView.reload();
                        }
                    }

                    // 👑 المرحلة الثانية: إخفاء الواجهة بعد 3 ثوانٍ (بدلاً من الفورية)
                    retryHandler.postDelayed(() -> {
                        hideOfflineUI();
                    }, 3000);

                    retryInProgress = false;
                    return;
                }
                Log.w(TAG, "⚠️ Retry failed after 4-second validation window.");
                resetRetryButton(btnContainer, btnContent, dotsLoader);
            }, RETRY_HOLD_DURATION);
        });
        LinearLayout.LayoutParams btnLayoutParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        bottomCard.addView(btnContainer, btnLayoutParams);
        bottomCard.setAlpha(0f);
        bottomCard.setTranslationY(dp(24));
        bottomCard.setScaleX(0.98f);
        bottomCard.setScaleY(0.98f);
        activity.addContentView(pureOfflineUI, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        Log.d(TAG, "🍏 Professional Offline UI created.");
    }

    private Drawable createCardDrawable() {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(OFFLINE_CARD_TOP);
        gd.setCornerRadius(dp(28));
        gd.setStroke(dp(1), Color.parseColor("#0A000000"));
        return gd;
    }

    private Drawable createRetryButtonDrawable() {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(OFFLINE_ACCENT);
        gd.setCornerRadius(dp(16));
        return gd;
    }

    private Drawable createRippleDrawable() {
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dp(16));
        return new RippleDrawable(android.content.res.ColorStateList.valueOf(Color.parseColor("#30FFFFFF")), null, mask);
    }

    private LinearLayout createDotsLoader() {
        LinearLayout loader = new LinearLayout(activity);
        loader.setOrientation(LinearLayout.HORIZONTAL);
        loader.setGravity(Gravity.CENTER);
        loader.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        for (int i = 0; i < 3; i++) {
            View dot = new View(activity);
            GradientDrawable dotDrawable = new GradientDrawable();
            dotDrawable.setShape(GradientDrawable.OVAL);
            dotDrawable.setColor(Color.WHITE);
            dot.setBackground(dotDrawable);
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(6), dp(6));
            if (i > 0) dotParams.leftMargin = dp(4);
            loader.addView(dot, dotParams);
        }
        return loader;
    }

    private void stopRetryLoader(LinearLayout dotsLoader) {
        if (retryLoaderAnimator != null) { retryLoaderAnimator.cancel(); retryLoaderAnimator = null; }
        if (dotsLoader != null) {
            dotsLoader.animate().alpha(0f).setDuration(160).withEndAction(() -> { dotsLoader.setVisibility(View.INVISIBLE); dotsLoader.setRotation(0f); }).start();
        }
    }

    private void resetRetryButton(FrameLayout btnContainer, LinearLayout btnContent, LinearLayout dotsLoader) {
        activity.runOnUiThread(() -> {
            if (retryLoaderAnimator != null) { retryLoaderAnimator.cancel(); retryLoaderAnimator = null; }
            if (dotsLoader != null) {
                dotsLoader.animate().alpha(0f).setDuration(160).withEndAction(() -> {
                    dotsLoader.setVisibility(View.INVISIBLE);
                    dotsLoader.setRotation(0f);
                    if (btnContent != null) { btnContent.setAlpha(0f); btnContent.animate().alpha(1f).setDuration(180).start(); }
                    if (btnContainer != null) { btnContainer.setEnabled(true); }
                    retryInProgress = false;
                }).start();
            } else {
                if (btnContent != null) btnContent.setAlpha(1f);
                if (btnContainer != null) btnContainer.setEnabled(true);
                retryInProgress = false;
            }
        });
    }

    private static class RetryIconDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final float size;
        RetryIconDrawable(int color, float size) { this.size = size; paint.setColor(color); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(size * 0.115f); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND); }
        @Override public void draw(Canvas canvas) {
            RectF bounds = new RectF(getBounds());
            float left = bounds.left, top = bounds.top, right = bounds.right, bottom = bounds.bottom;
            float cx = (left + right) / 2f, cy = (top + bottom) / 2f, radius = Math.min(right - left, bottom - top) * 0.34f;
            RectF arcRect = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);
            canvas.drawArc(arcRect, -55f, 285f, false, paint);
            path.reset();
            float arrowX = cx + radius * 0.98f, arrowY = cy - radius * 0.88f;
            path.moveTo(arrowX, arrowY);
            path.lineTo(arrowX - size * 0.28f, arrowY);
            path.moveTo(arrowX, arrowY);
            path.lineTo(arrowX, arrowY + size * 0.28f);
            canvas.drawPath(path, paint);
        }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(android.graphics.ColorFilter colorFilter) { paint.setColorFilter(colorFilter); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
        @Override public int getIntrinsicWidth() { return Math.round(size); }
        @Override public int getIntrinsicHeight() { return Math.round(size); }
    }

    private void syncOfflineSystemUI() { if (activity != null && !activity.isFinishing()) SystemUI.syncWithNativeUI(activity, OFFLINE_BACKGROUND); }

    public void setOfflineUIVisibility(boolean show) { if (show && !isOfflineUIVisible) showOfflineUI(); else if (!show && isOfflineUIVisible) hideOfflineUI(); }
    public void setOfflineBarVisibility(boolean show) { if (offlineBarController != null) { if (show) offlineBarController.show(); else offlineBarController.hideWithAnimation(); } }

    private void showOfflineUI() {
        if (pureOfflineUI == null) return;
        isOfflineUIVisible = true;
        activity.runOnUiThread(() -> {
            if (activity.isFinishing()) return;
            SystemUI.forceNativeStatusBar(activity, OFFLINE_BACKGROUND);
            if (offlineBarController != null) offlineBarController.hideImmediately();
            pureOfflineUI.setVisibility(View.VISIBLE);
            pureOfflineUI.setAlpha(1f);
            View card = pureOfflineUI.getChildAt(1);
            updateOfflineCardBottomInset(card);
            if (card != null) {
                card.setAlpha(0f);
                card.setTranslationY(dp(24));
                card.setScaleX(0.98f);
                card.setScaleY(0.98f);
                card.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setDuration(800).setInterpolator(new android.view.animation.PathInterpolator(0.16f, 1f, 0.3f, 1f)).withEndAction(() -> {
                    floatingAnimator = ObjectAnimator.ofFloat(card, View.TRANSLATION_Y, 0f, -dp(4), 0f);
                    floatingAnimator.setDuration(4000);
                    floatingAnimator.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
                    floatingAnimator.setRepeatCount(ObjectAnimator.INFINITE);
                    floatingAnimator.start();
                    elevationAnimator = ObjectAnimator.ofFloat(card, View.TRANSLATION_Z, dp(16), dp(22), dp(16));
                    elevationAnimator.setDuration(4000);
                    elevationAnimator.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
                    elevationAnimator.setRepeatCount(ObjectAnimator.INFINITE);
                    elevationAnimator.start();
                }).start();
            }
            if (webView != null) webView.setVisibility(View.GONE);
            if (callback != null) callback.onOfflineUIVisibilityChanged(true);
        });
        Log.d(TAG, "🟠 Offline UI shown with synchronized System UI.");
    }

    private void hideOfflineUI() {
        if (pureOfflineUI == null) return;
        isOfflineUIVisible = false;
        activity.runOnUiThread(() -> {
            if (activity.isFinishing()) return;
            View card = pureOfflineUI.getChildAt(1);
            if (card != null) {
                card.animate().cancel();
                if (floatingAnimator != null) { floatingAnimator.cancel(); floatingAnimator = null; }
                if (elevationAnimator != null) { elevationAnimator.cancel(); elevationAnimator = null; }
                card.setTranslationY(dp(0));
                card.setTranslationZ(dp(16));
            }

            // 👑 تعديل بداية hideOfflineUI() لجعل الخروج فاخرًا
            pureOfflineUI.animate()
                    .alpha(0f)
                    .scaleX(0.985f)
                    .scaleY(0.985f)
                    .setDuration(420)
                    .setInterpolator(
                            new android.view.animation.PathInterpolator(
                                    0.22f,
                                    1f,
                                    0.36f,
                                    1f
                            )
                    )
                    .withEndAction(() -> {

                        pureOfflineUI.setVisibility(
                                View.GONE
                        );

                        pureOfflineUI.setAlpha(
                                1f
                        );

                        pureOfflineUI.setScaleX(
                                1f
                        );

                        pureOfflineUI.setScaleY(
                                1f
                        );

                        /*
                         * 👑 WebView يظهر فقط بعد اختفاء
                         * واجهة الأوفلاين بالكامل.
                         */
                        if (webView != null) {

                            webView.setAlpha(
                                    0f
                            );

                            webView.setVisibility(
                                    View.VISIBLE
                            );

                            webView.animate()
                                    .alpha(1f)
                                    .setDuration(420)
                                    .setInterpolator(
                                            new android.view.animation.PathInterpolator(
                                                    0.16f,
                                                    1f,
                                                    0.3f,
                                                    1f
                                            )
                                    )
                                    .start();
                        }

                        /*
                         * 👑 إعادة ملكية Status Bar للـ WebView
                         * بعد اكتمال الانتقال البصري.
                         */
                        SystemUI.scheduleStatusBarSync(
                                activity,
                                webView
                        );
                    })
                    .start();
        });
        if (callback != null) callback.onOfflineUIVisibilityChanged(false);
        Log.d(TAG, "🟢 Offline UI hidden.");
    }

    public boolean isOfflineUIVisible() { return isOfflineUIVisible; }
    public boolean isPageLoaded() { return isPageLoaded; }
    public void setPageLoaded(boolean loaded) { this.isPageLoaded = loaded; }
    public void setCallback(OfflineUICallback callback) {
        this.callback = callback;
        if (offlineBarController != null) {
            offlineBarController.setCallback(visible -> { if (this.callback != null) this.callback.onOfflineBarVisibilityChanged(visible); });
        }
    }

    private void updateOfflineCardBottomInset(View card) {
        if (card == null || pureOfflineUI == null) return;
        pureOfflineUI.post(() -> {
            int bottomInset = 0;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                WindowInsets insets = pureOfflineUI.getRootWindowInsets();
                if (insets != null) bottomInset = insets.getInsets(WindowInsets.Type.systemBars()).bottom;
            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                WindowInsets insets = pureOfflineUI.getRootWindowInsets();
                if (insets != null) bottomInset = insets.getSystemWindowInsetBottom();
            }
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) card.getLayoutParams();
            params.bottomMargin = bottomInset + dp(16);
            card.setLayoutParams(params);
        });
    }

    public void shakeOfflineBar() { if (offlineBarController != null) offlineBarController.shake(); }
    private int dp(float value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
                }
