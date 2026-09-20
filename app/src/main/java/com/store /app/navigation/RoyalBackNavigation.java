package com.store.app.navigation;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.util.Log;
import android.webkit.WebView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.store.app.BuildConfig;
import com.store.app.SystemUI;
import com.store.app.WebEngineManager;

public class RoyalBackNavigation {

    private static final String TAG = "RoyalBackNavigation";

    private final AppCompatActivity activity;
    private final WebView activeWebView;
    private final WebEngineManager engineManager;
    private final ProgressBar progressBar;

    private boolean doubleBackToExitPressedOnce = false;
    private final Handler backPressHandler = new Handler(Looper.getMainLooper());
    private final Runnable resetBackPressFlag = () -> doubleBackToExitPressedOnce = false;

    public RoyalBackNavigation(AppCompatActivity activity,
                               WebView activeWebView,
                               WebEngineManager engineManager,
                               ProgressBar progressBar) {
        this.activity = activity;
        this.activeWebView = activeWebView;
        this.engineManager = engineManager;
        this.progressBar = progressBar;
    }

    // 👑 تحديد الصفحة الرئيسية بشكل آمن
    private boolean isAtHomePage() {
        if (activeWebView == null) return true;

        String currentUrl = activeWebView.getUrl();
        if (currentUrl == null || currentUrl.trim().isEmpty()) return true;

        currentUrl = currentUrl.trim();
        if (currentUrl.equalsIgnoreCase("about:blank")) return true;

        try {
            Uri current = Uri.parse(currentUrl);
            Uri home = Uri.parse(BuildConfig.CLIENT_URL);

            String currentHost = current.getHost();
            String homeHost = home.getHost();

            if (currentHost == null || homeHost == null) {
                return currentUrl.equalsIgnoreCase(BuildConfig.CLIENT_URL);
            }

            boolean sameHost = currentHost.equalsIgnoreCase(homeHost);
            if (!sameHost) return false;

            String currentPath = current.getPath();
            String homePath = home.getPath();

            if (currentPath == null || currentPath.isEmpty()) currentPath = "/";
            if (homePath == null || homePath.isEmpty()) homePath = "/";

            return currentPath.equals(homePath);

        } catch (Exception e) {
            Log.w(TAG, "Home page detection failed: " + e.getMessage());
            return currentUrl.equalsIgnoreCase(BuildConfig.CLIENT_URL);
        }
    }

    // 👑 تفعيل منطق زر الرجوع
    public void setupBackNavigation() {
        activity.getOnBackPressedDispatcher().addCallback(
                activity,
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {

                        // 👑 إبلاغ SystemUI بتفاعل المستخدم مع زر الرجوع
                        // يظهر Navigation Bar ويعيد مؤقت الـ 5 ثوانٍ
                        SystemUI.notifyNavigationUserInteraction(activity);

                        try {
                            if (activeWebView == null) {
                                performRoyalExit();
                                return;
                            }

                            if (isAtHomePage()) {
                                handleHomeBackPress();
                                return;
                            }

                            if (activeWebView.canGoBack()) {
                                if (progressBar != null) progressBar.setVisibility(View.GONE);

                                boolean navigated = false;
                                if (engineManager != null) {
                                    try {
                                        navigated = engineManager.safeGoBack();
                                    } catch (Exception e) {
                                        Log.w(TAG, "safeGoBack failed: " + e.getMessage());
                                    }
                                }

                                if (!navigated) {
                                    String currentUrl = activeWebView.getUrl();
                                    if (currentUrl != null
                                            && !currentUrl.trim().isEmpty()
                                            && !currentUrl.equalsIgnoreCase("about:blank")) {
                                        if (!isAtHomePage()) {
                                            activeWebView.goBack();
                                        } else {
                                            Log.i(TAG, "🛡️ Back blocked: returned to HOME.");
                                        }
                                    } else {
                                        Log.i(TAG, "🛡️ Back blocked: invalid/blank URL.");
                                    }
                                }

                                doubleBackToExitPressedOnce = false;
                                backPressHandler.removeCallbacks(resetBackPressFlag);
                                return;
                            }

                            handleHomeBackPress();

                        } catch (Throwable e) {
                            Log.e(TAG, "❌ Back navigation protected failure", e);
                            handleHomeBackPress();
                        }
                    }
                }
        );

        Log.i(TAG, "✅ Royal Back Navigation armed: HOME is protected from goBack().");
    }

    // 👑 معالجة زر الرجوع في الصفحة الرئيسية
    private void handleHomeBackPress() {
        if (doubleBackToExitPressedOnce) {
            doubleBackToExitPressedOnce = false;
            backPressHandler.removeCallbacks(resetBackPressFlag);
            Log.i(TAG, "🚪 Second BACK press on HOME → exiting application.");
            performRoyalExit();
            return;
        }

        doubleBackToExitPressedOnce = true;
        Toast.makeText(activity, "اضغط مرة أخرى للخروج", Toast.LENGTH_SHORT).show();
        backPressHandler.removeCallbacks(resetBackPressFlag);
        backPressHandler.postDelayed(resetBackPressFlag, 2000);
        Log.i(TAG, "👑 First BACK press on HOME → exit warning shown.");
    }

    // 👑 خروج آمن
    private void performRoyalExit() {
        try {
            backPressHandler.removeCallbacks(resetBackPressFlag);
            doubleBackToExitPressedOnce = false;
            activity.moveTaskToBack(true);
            Log.i(TAG, "👑 Royal exit completed.");
        } catch (Exception e) {
            Log.e(TAG, "Royal exit failed: " + e.getMessage());
            try {
                activity.finish();
            } catch (Exception ignored) {}
        }
    }
                                              }
