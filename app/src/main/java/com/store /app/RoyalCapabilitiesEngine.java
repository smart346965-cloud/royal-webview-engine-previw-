package com.store.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class RoyalCapabilitiesEngine {

    private static final String TAG = "RoyalCapabilities";

    public static final int FILECHOOSER_RESULTCODE = 101;
    private static final int LOCATION_PERMISSION_CODE = 102;
    private static final int MEDIA_PERMISSION_CODE = 103;
    private static final int NOTIFICATION_PERMISSION_CODE = 105;
    private static final int DOWNLOAD_PERMISSION_CODE = 106;

    private final Activity activity;

    private ValueCallback<Uri[]> filePathCallback;

    private PermissionRequest pendingWebPermissionRequest;
    private String[] pendingWebPermissionResources;

    private GeolocationPermissions.Callback pendingLocationCallback;
    private String pendingLocationOrigin;

    private String pendingDownloadUrl;
    private String pendingDownloadUserAgent;
    private String pendingDownloadContentDisposition;
    private String pendingDownloadMimeType;

    /*
     * Temporary payment/auth popup.
     * It is created only when a payment page explicitly requests
     * a secondary window through window.open().
     */
    private FrameLayout paymentPopupContainer;
    private WebView paymentPopupWebView;
    private boolean paymentPopupCommitted;

    public RoyalCapabilitiesEngine(Activity activity) {
        this.activity = activity;
    }

    // =========================================================
    // 1. DOWNLOAD MANAGER
    // =========================================================

    public void attachDownloadManager(WebView webView) {
        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(
                    String url,
                    String userAgent,
                    String contentDisposition,
                    String mimetype,
                    long contentLength
            ) {
                if (url == null || url.trim().isEmpty()) return;
                pendingDownloadUrl = url;
                pendingDownloadUserAgent = userAgent;
                pendingDownloadContentDisposition = contentDisposition;
                pendingDownloadMimeType = mimetype;
                String fileName = URLUtil.guessFileName(url, contentDisposition, mimetype);
                showDownloadConfirmation(fileName, mimetype, contentLength);
            }
        });
    }

    private void showDownloadConfirmation(String fileName, String mimeType, long contentLength) {
        if (activity.isFinishing() || activity.isDestroyed()) { clearPendingDownload(); return; }
        String sizeText = formatFileSize(contentLength);
        String message;
        if (sizeText != null) {
            message = "هل تريد تنزيل هذا الملف؟\n\n" + "الملف: " + fileName + "\n" + "الحجم: " + sizeText;
        } else {
            message = "هل تريد تنزيل هذا الملف؟\n\n" + "الملف: " + fileName;
        }
        new AlertDialog.Builder(activity)
                .setTitle("تنزيل ملف")
                .setMessage(message)
                .setNegativeButton("إلغاء", (dialog, which) -> clearPendingDownload())
                .setPositiveButton("تنزيل", (dialog, which) -> startPendingDownload(fileName, mimeType))
                .setOnCancelListener(dialog -> clearPendingDownload())
                .show();
    }

    private void startPendingDownload(String fileName, String mimeType) {
        if (pendingDownloadUrl == null) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, DOWNLOAD_PERMISSION_CODE);
                return;
            }
        }
        enqueueDownload(fileName, mimeType);
    }

    private void enqueueDownload(String fileName, String mimeType) {
        try {
            Uri uri = Uri.parse(pendingDownloadUrl);
            DownloadManager.Request request = new DownloadManager.Request(uri);
            if (mimeType != null && !mimeType.isEmpty()) request.setMimeType(mimeType);
            String cookies = CookieManager.getInstance().getCookie(pendingDownloadUrl);
            if (cookies != null && !cookies.isEmpty()) request.addRequestHeader("Cookie", cookies);
            if (pendingDownloadUserAgent != null) request.addRequestHeader("User-Agent", pendingDownloadUserAgent);
            request.setTitle(fileName);
            request.setDescription("جاري تنزيل الملف");
            request.allowScanningByMediaScanner();
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
            DownloadManager manager = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager == null) {
                Toast.makeText(activity, "تعذر تشغيل مدير التنزيلات", Toast.LENGTH_LONG).show();
                clearPendingDownload();
                return;
            }
            manager.enqueue(request);
            Toast.makeText(activity, "بدأ تنزيل الملف", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Download failed", e);
            Toast.makeText(activity, "تعذر بدء تنزيل الملف", Toast.LENGTH_LONG).show();
        } finally {
            clearPendingDownload();
        }
    }

    private void continuePendingDownloadAfterPermission(int[] grantResults) {
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            String fileName = URLUtil.guessFileName(pendingDownloadUrl, pendingDownloadContentDisposition, pendingDownloadMimeType);
            enqueueDownload(fileName, pendingDownloadMimeType);
        } else {
            Toast.makeText(activity, "لم يتم السماح بحفظ الملف", Toast.LENGTH_SHORT).show();
            clearPendingDownload();
        }
    }

    // =========================================================
    // 2. PAYMENT POPUP WEBVIEW
    // =========================================================

    private boolean isPaymentOrAuthenticationHost(String host) {
        if (host == null) {
            return false;
        }

        String normalized =
                host.toLowerCase(
                        Locale.ROOT
                );

        return normalized.equals("paypal.com")
                || normalized.endsWith(".paypal.com")
                || normalized.equals("stripe.com")
                || normalized.endsWith(".stripe.com")
                || normalized.equals("checkout.com")
                || normalized.endsWith(".checkout.com")
                || normalized.equals("tap.company")
                || normalized.endsWith(".tap.company")
                || normalized.equals("moyasar.com")
                || normalized.endsWith(".moyasar.com")
                || normalized.contains("3ds")
                || normalized.contains("secure");
    }

    private boolean launchExternalHttpInCustomTab(Uri uri) {
        if (activity == null || uri == null) {
            return true;
        }

        try {
            CustomTabsIntent customTabsIntent =
                    new CustomTabsIntent.Builder()
                            .setShowTitle(true)
                            .build();

            customTabsIntent.launchUrl(
                    activity,
                    uri
            );

            return true;

        } catch (Throwable t) {
            Log.w(
                    TAG,
                    "Custom Tab launch failed; using browser fallback.",
                    t
            );

            try {
                Intent intent =
                        new Intent(
                                Intent.ACTION_VIEW,
                                uri
                        );

                activity.startActivity(intent);
            } catch (Throwable ignored) {
            }

            return true;
        }
    }

    private WebView createPaymentPopup(WebView parentWebView) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return null;

        closePaymentPopup();

        paymentPopupCommitted = false;

        paymentPopupContainer = new FrameLayout(activity);
        paymentPopupContainer.setBackgroundColor(android.graphics.Color.WHITE);
        paymentPopupContainer.setElevation(32f);

        paymentPopupWebView = new WebView(activity);

        android.webkit.WebSettings popupSettings = paymentPopupWebView.getSettings();
        popupSettings.setJavaScriptEnabled(true);
        popupSettings.setDomStorageEnabled(true);
        popupSettings.setDatabaseEnabled(true);
        popupSettings.setJavaScriptCanOpenWindowsAutomatically(true);
        popupSettings.setSupportMultipleWindows(true);
        popupSettings.setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(paymentPopupWebView, true);
        }

        String parentUserAgent = parentWebView != null ? parentWebView.getSettings().getUserAgentString() : null;
        if (parentUserAgent != null && !parentUserAgent.trim().isEmpty()) {
            popupSettings.setUserAgentString(parentUserAgent);
        }

        paymentPopupWebView.setWebViewClient(
                new WebViewClient() {

                    @Override
                    public boolean shouldOverrideUrlLoading(
                            WebView view,
                            android.webkit.WebResourceRequest request
                    ) {
                        if (request == null
                                || request.getUrl() == null) {
                            return false;
                        }

                        Uri uri = request.getUrl();
                        String scheme = uri.getScheme();

                        if (scheme == null) {
                            return true;
                        }

                        scheme = scheme.toLowerCase(
                                Locale.ROOT
                        );

                        if ("http".equals(scheme)
                                || "https".equals(scheme)) {

                            if (isPaymentOrAuthenticationHost(
                                    uri.getHost()
                            )) {
                                paymentPopupCommitted = true;
                                return false;
                            }

                            closePaymentPopup();
                            return launchExternalHttpInCustomTab(
                                    uri
                            );
                        }

                        return launchPaymentExternalUri(uri);
                    }

                    @SuppressWarnings("deprecation")
                    @Override
                    public boolean shouldOverrideUrlLoading(
                            WebView view,
                            String url
                    ) {
                        if (url == null
                                || url.trim().isEmpty()) {
                            return false;
                        }

                        Uri uri = Uri.parse(url);
                        String scheme = uri.getScheme();

                        if (scheme == null) {
                            return true;
                        }

                        scheme = scheme.toLowerCase(
                                Locale.ROOT
                        );

                        if ("http".equals(scheme)
                                || "https".equals(scheme)) {

                            if (isPaymentOrAuthenticationHost(
                                    uri.getHost()
                            )) {
                                paymentPopupCommitted = true;
                                return false;
                            }

                            closePaymentPopup();
                            return launchExternalHttpInCustomTab(uri);
                        }

                        return launchPaymentExternalUri(uri);
                    }
                }
        );

        paymentPopupWebView.setWebChromeClient(buildChromeClient(null));

        paymentPopupContainer.addView(paymentPopupWebView,
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        FrameLayout.LayoutParams containerParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        containerParams.gravity = Gravity.CENTER;

        activity.addContentView(paymentPopupContainer, containerParams);
        paymentPopupContainer.setVisibility(View.VISIBLE);
        paymentPopupWebView.setVisibility(View.VISIBLE);

        return paymentPopupWebView;
    }

    private boolean launchPaymentExternalUri(Uri uri) {
        if (activity == null || uri == null) return true;
        String scheme = uri.getScheme();
        if (scheme == null) return true;
        scheme = scheme.toLowerCase(Locale.ROOT);
        if ("http".equals(scheme) || "https".equals(scheme)) return false;
        if ("intent".equals(scheme)) return launchIntentUri(uri.toString());
        try {
            Intent externalIntent = new Intent(Intent.ACTION_VIEW, uri);
            if (externalIntent.resolveActivity(activity.getPackageManager()) != null) {
                activity.startActivity(externalIntent);
            } else {
                Log.w(TAG, "No application can handle payment URI: " + uri);
            }
        } catch (Exception e) {
            Log.w(TAG, "Payment external URI failed: " + uri, e);
        }
        return true;
    }

    private boolean launchIntentUri(String intentUri) {
        if (activity == null || intentUri == null) return true;
        try {
            Intent parsedIntent = Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME);
            if (parsedIntent.resolveActivity(activity.getPackageManager()) != null) {
                activity.startActivity(parsedIntent);
                return true;
            }
            String fallbackUrl = parsedIntent.getStringExtra("browser_fallback_url");
            if (fallbackUrl != null && !fallbackUrl.trim().isEmpty() && webViewCanLoadHttps(fallbackUrl)) {
                CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder().build();
                customTabsIntent.launchUrl(activity, Uri.parse(fallbackUrl));
                return true;
            }
        } catch (Exception e) {
            Log.w(TAG, "Unable to parse payment intent URI.", e);
        }
        return true;
    }

    private boolean webViewCanLoadHttps(String url) {
        try {
            Uri uri = Uri.parse(url);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null;
        } catch (Exception e) {
            return false;
        }
    }

    private void closePaymentPopup() {
        if (paymentPopupWebView != null) {
            try {
                paymentPopupWebView.stopLoading();
                paymentPopupWebView.onPause();
                paymentPopupWebView.destroy();
            } catch (Throwable ignored) {}
            paymentPopupWebView = null;
        }
        if (paymentPopupContainer != null) {
            ViewGroup parent = (ViewGroup) paymentPopupContainer.getParent();
            if (parent != null) parent.removeView(paymentPopupContainer);
            paymentPopupContainer = null;
        }
        paymentPopupCommitted = false;
    }

    // =========================================================
    // 3. WEB CHROME CLIENT
    // =========================================================

    public WebChromeClient buildChromeClient(ProgressBar progressBar) {
        return new WebChromeClient() {

            @Override
            public boolean onCreateWindow(
                    WebView view,
                    boolean isDialog,
                    boolean isUserGesture,
                    Message resultMsg
            ) {
                if (resultMsg == null || resultMsg.obj == null) return false;
                if (!isUserGesture) {
                    Log.w(TAG, "Blocked non-user-gesture popup request.");
                    return false;
                }
                WebView popupWebView = createPaymentPopup(view);
                if (popupWebView == null) return false;
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(popupWebView);
                resultMsg.sendToTarget();
                Log.i(TAG, "✅ Payment popup WebView created.");
                return true;
            }

            @Override
            public void onCloseWindow(WebView window) {
                if (window == paymentPopupWebView) {
                    closePaymentPopup();
                    Log.i(TAG, "✅ Payment popup WebView closed.");
                    return;
                }
                super.onCloseWindow(window);
            }

            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (progressBar == null) return;
                progressBar.setProgress(newProgress);
                if (newProgress >= 100) {
                    progressBar.animate().alpha(0f).setDuration(150)
                            .withEndAction(() -> progressBar.setVisibility(View.GONE)).start();
                } else {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setAlpha(1f);
                }
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback, FileChooserParams fileChooserParams) {
                cancelFileChooser();
                filePathCallback = callback;
                Intent contentSelectionIntent = new Intent(Intent.ACTION_GET_CONTENT);
                contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE);
                String acceptType = "*/*";
                if (fileChooserParams != null) {
                    String[] types = fileChooserParams.getAcceptTypes();
                    if (types != null && types.length > 0) {
                        StringBuilder builder = new StringBuilder();
                        for (String type : types) {
                            if (type == null || type.trim().isEmpty()) continue;
                            if (builder.length() > 0) builder.append(",");
                            builder.append(type);
                        }
                        if (builder.length() > 0) acceptType = builder.toString();
                    }
                }
                contentSelectionIntent.setType(acceptType);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    boolean multiple = fileChooserParams != null &&
                            fileChooserParams.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE;
                    contentSelectionIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple);
                }
                Intent chooser = new Intent(Intent.ACTION_CHOOSER);
                chooser.putExtra(Intent.EXTRA_INTENT, contentSelectionIntent);
                chooser.putExtra(Intent.EXTRA_TITLE, "اختر ملفاً أو صورة");
                try {
                    activity.startActivityForResult(chooser, FILECHOOSER_RESULTCODE);
                    return true;
                } catch (Exception e) {
                    Log.e(TAG, "File chooser failed", e);
                    cancelFileChooser();
                    return false;
                }
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                if (origin == null || callback == null) return;
                if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    callback.invoke(origin, true, true);
                    return;
                }
                pendingLocationOrigin = origin;
                pendingLocationCallback = callback;
                ActivityCompat.requestPermissions(activity,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                        LOCATION_PERMISSION_CODE);
            }

            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                    request.deny();
                    return;
                }
                activity.runOnUiThread(() -> {
                    if (request == null) return;
                    String[] requested = request.getResources();
                    if (requested == null || requested.length == 0) {
                        request.deny();
                        return;
                    }
                    List<String> allowedResources = new ArrayList<>();
                    List<String> androidPermissions = new ArrayList<>();
                    for (String resource : requested) {
                        if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) {
                            allowedResources.add(resource);
                            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                                androidPermissions.add(Manifest.permission.CAMERA);
                            }
                        } else if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) {
                            allowedResources.add(resource);
                            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                androidPermissions.add(Manifest.permission.RECORD_AUDIO);
                            }
                        } else {
                            Log.w(TAG, "Blocked WebView resource: " + resource);
                        }
                    }
                    if (allowedResources.isEmpty()) {
                        request.deny();
                        return;
                    }
                    if (androidPermissions.isEmpty()) {
                        grantSpecificResources(request, allowedResources);
                        return;
                    }
                    cancelPendingWebPermission();
                    pendingWebPermissionRequest = request;
                    pendingWebPermissionResources = allowedResources.toArray(new String[0]);
                    Set<String> uniquePermissions = new HashSet<>(androidPermissions);
                    ActivityCompat.requestPermissions(activity, uniquePermissions.toArray(new String[0]), MEDIA_PERMISSION_CODE);
                });
            }

            @Override
            public void onPermissionRequestCanceled(PermissionRequest request) {
                if (request != null && request == pendingWebPermissionRequest) {
                    clearPendingWebPermission();
                }
            }
        };
    }

    private void grantSpecificResources(PermissionRequest request, List<String> resources) {
        if (request == null || resources == null || resources.isEmpty()) return;
        try {
            request.grant(resources.toArray(new String[0]));
        } catch (Exception e) {
            Log.e(TAG, "Unable to grant WebView resources", e);
            try { request.deny(); } catch (Exception ignored) {}
        }
    }

    public void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return;
        ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_CODE);
    }

    public void handlePermissionResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        if (requestCode == LOCATION_PERMISSION_CODE) {
            boolean granted = false;
            for (int result : grantResults) {
                if (result == PackageManager.PERMISSION_GRANTED) { granted = true; break; }
            }
            if (pendingLocationCallback != null && pendingLocationOrigin != null) {
                try {
                    pendingLocationCallback.invoke(pendingLocationOrigin, granted, true);
                } catch (Exception e) {
                    Log.e(TAG, "Location callback failed", e);
                }
            }
            pendingLocationCallback = null;
            pendingLocationOrigin = null;
            return;
        }

        if (requestCode == MEDIA_PERMISSION_CODE) {
            boolean granted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) { granted = false; break; }
            }
            if (pendingWebPermissionRequest != null) {
                PermissionRequest request = pendingWebPermissionRequest;
                String[] resources = pendingWebPermissionResources;
                pendingWebPermissionRequest = null;
                pendingWebPermissionResources = null;
                if (granted && resources != null && resources.length > 0) {
                    grantSpecificResources(request, Arrays.asList(resources));
                } else {
                    try { request.deny(); } catch (Exception ignored) {}
                }
            }
            return;
        }

        if (requestCode == DOWNLOAD_PERMISSION_CODE) {
            continuePendingDownloadAfterPermission(grantResults);
            return;
        }

        if (requestCode == NOTIFICATION_PERMISSION_CODE) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            Log.d(TAG, "Notifications permission: " + granted);
        }
    }

    public boolean handleActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != FILECHOOSER_RESULTCODE) return false;
        if (filePathCallback == null) return true;
        Uri[] results = null;
        try {
            if (resultCode == Activity.RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    results = new Uri[count];
                    for (int i = 0; i < count; i++) {
                        results[i] = data.getClipData().getItemAt(i).getUri();
                    }
                } else if (data.getData() != null) {
                    results = new Uri[]{data.getData()};
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "File chooser result failed", e);
        }
        filePathCallback.onReceiveValue(results);
        filePathCallback = null;
        return true;
    }

    private void cancelFileChooser() {
        if (filePathCallback != null) {
            try { filePathCallback.onReceiveValue(null); } catch (Exception ignored) {}
            filePathCallback = null;
        }
    }

    private void cancelPendingWebPermission() {
        if (pendingWebPermissionRequest != null) {
            try { pendingWebPermissionRequest.deny(); } catch (Exception ignored) {}
        }
        pendingWebPermissionRequest = null;
        pendingWebPermissionResources = null;
    }

    private void clearPendingWebPermission() {
        pendingWebPermissionRequest = null;
        pendingWebPermissionResources = null;
    }

    private void clearPendingDownload() {
        pendingDownloadUrl = null;
        pendingDownloadUserAgent = null;
        pendingDownloadContentDisposition = null;
        pendingDownloadMimeType = null;
    }

    private String formatFileSize(long bytes) {
        if (bytes <= 0) return null;
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb);
        double gb = mb / 1024.0;
        return String.format(Locale.US, "%.1f GB", gb);
    }

    public void destroy() {
        cancelFileChooser();
        if (pendingWebPermissionRequest != null) {
            try { pendingWebPermissionRequest.deny(); } catch (Exception ignored) {}
        }
        pendingWebPermissionRequest = null;
        pendingWebPermissionResources = null;
        pendingLocationCallback = null;
        pendingLocationOrigin = null;
        closePaymentPopup();
        clearPendingDownload();
    }
                            }
