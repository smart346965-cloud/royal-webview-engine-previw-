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
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RoyalCapabilitiesEngine {

    private static final String TAG = "RoyalCapabilities";

    // =========================================================
    // RESULT CODES
    // =========================================================

    public static final int FILECHOOSER_RESULTCODE = 101;
    private static final int LOCATION_PERMISSION_CODE = 102;
    private static final int MEDIA_PERMISSION_CODE = 103;
    private static final int NOTIFICATION_PERMISSION_CODE = 105;
    private static final int DOWNLOAD_PERMISSION_CODE = 106;

    // =========================================================
    // ACTIVITY
    // =========================================================

    private final Activity activity;

    // =========================================================
    // FILE CHOOSER
    // مهم: لم يعد static
    // =========================================================

    private ValueCallback<Uri[]> filePathCallback;

    // =========================================================
    // WEB PERMISSION REQUEST
    // =========================================================

    private PermissionRequest pendingWebPermissionRequest;

    private String[] pendingWebPermissionResources;

    // =========================================================
    // GEOLOCATION
    // =========================================================

    private GeolocationPermissions.Callback pendingLocationCallback;
    private String pendingLocationOrigin;

    // =========================================================
    // DOWNLOAD STATE
    // =========================================================

    private String pendingDownloadUrl;
    private String pendingDownloadUserAgent;
    private String pendingDownloadContentDisposition;
    private String pendingDownloadMimeType;

    // =========================================================
    // CONSTRUCTOR
    // =========================================================

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

                if (url == null || url.trim().isEmpty()) {
                    return;
                }

                pendingDownloadUrl = url;
                pendingDownloadUserAgent = userAgent;
                pendingDownloadContentDisposition = contentDisposition;
                pendingDownloadMimeType = mimetype;

                String fileName = URLUtil.guessFileName(
                        url,
                        contentDisposition,
                        mimetype
                );

                showDownloadConfirmation(
                        fileName,
                        mimetype,
                        contentLength
                );
            }
        });
    }

    // =========================================================
    // DOWNLOAD CONFIRMATION
    // =========================================================

    private void showDownloadConfirmation(
            String fileName,
            String mimeType,
            long contentLength
    ) {

        if (activity.isFinishing() || activity.isDestroyed()) {
            clearPendingDownload();
            return;
        }

        String sizeText = formatFileSize(contentLength);

        String message;

        if (sizeText != null) {
            message =
                    "هل تريد تنزيل هذا الملف؟\n\n" +
                    "الملف: " + fileName + "\n" +
                    "الحجم: " + sizeText;
        } else {
            message =
                    "هل تريد تنزيل هذا الملف؟\n\n" +
                    "الملف: " + fileName;
        }

        new AlertDialog.Builder(activity)
                .setTitle("تنزيل ملف")
                .setMessage(message)
                .setNegativeButton("إلغاء", (dialog, which) -> {
                    clearPendingDownload();
                })
                .setPositiveButton("تنزيل", (dialog, which) -> {
                    startPendingDownload(fileName, mimeType);
                })
                .setOnCancelListener(dialog -> {
                    clearPendingDownload();
                })
                .show();
    }

    // =========================================================
    // START DOWNLOAD
    // =========================================================

    private void startPendingDownload(
            String fileName,
            String mimeType
    ) {

        if (pendingDownloadUrl == null) {
            return;
        }

        /*
         * Android Q / API 29 وما بعده:
         * لا نطلب WRITE_EXTERNAL_STORAGE من أجل
         * DownloadManager عند الحفظ في Downloads.
         */

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {

            if (ContextCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(
                        activity,
                        new String[]{
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                        },
                        DOWNLOAD_PERMISSION_CODE
                );

                return;
            }
        }

        enqueueDownload(fileName, mimeType);
    }

    // =========================================================
    // ENQUEUE DOWNLOAD
    // =========================================================

    private void enqueueDownload(
            String fileName,
            String mimeType
    ) {

        try {

            Uri uri = Uri.parse(pendingDownloadUrl);

            DownloadManager.Request request =
                    new DownloadManager.Request(uri);

            if (mimeType != null && !mimeType.isEmpty()) {
                request.setMimeType(mimeType);
            }

            // نقل جلسة الموقع
            String cookies =
                    CookieManager
                            .getInstance()
                            .getCookie(pendingDownloadUrl);

            if (cookies != null && !cookies.isEmpty()) {
                request.addRequestHeader("Cookie", cookies);
            }

            if (pendingDownloadUserAgent != null) {
                request.addRequestHeader(
                        "User-Agent",
                        pendingDownloadUserAgent
                );
            }

            request.setTitle(fileName);
            request.setDescription("جاري تنزيل الملف");

            request.allowScanningByMediaScanner();

            request.setNotificationVisibility(
                    DownloadManager.Request
                            .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            );

            request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    fileName
            );

            DownloadManager manager =
                    (DownloadManager) activity.getSystemService(
                            Context.DOWNLOAD_SERVICE
                    );

            if (manager == null) {
                Toast.makeText(
                        activity,
                        "تعذر تشغيل مدير التنزيلات",
                        Toast.LENGTH_LONG
                ).show();

                clearPendingDownload();
                return;
            }

            manager.enqueue(request);

            Toast.makeText(
                    activity,
                    "بدأ تنزيل الملف",
                    Toast.LENGTH_SHORT
            ).show();

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Download failed",
                    e
            );

            Toast.makeText(
                    activity,
                    "تعذر بدء تنزيل الملف",
                    Toast.LENGTH_LONG
            ).show();

        } finally {
            clearPendingDownload();
        }
    }

    // =========================================================
    // DOWNLOAD PERMISSION RESULT
    // =========================================================

    private void continuePendingDownloadAfterPermission(
            int[] grantResults
    ) {

        if (grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {

            String fileName = URLUtil.guessFileName(
                    pendingDownloadUrl,
                    pendingDownloadContentDisposition,
                    pendingDownloadMimeType
            );

            enqueueDownload(
                    fileName,
                    pendingDownloadMimeType
            );

        } else {

            Toast.makeText(
                    activity,
                    "لم يتم السماح بحفظ الملف",
                    Toast.LENGTH_SHORT
            ).show();

            clearPendingDownload();
        }
    }

    // =========================================================
    // 2. WEB CHROME CLIENT
    // =========================================================

    public WebChromeClient buildChromeClient(
            ProgressBar progressBar
    ) {

        return new WebChromeClient() {

            // =====================================================
            // PROGRESS
            // =====================================================

            @Override
            public void onProgressChanged(
                    WebView view,
                    int newProgress
            ) {

                if (progressBar == null) {
                    return;
                }

                progressBar.setProgress(newProgress);

                if (newProgress >= 100) {

                    progressBar.animate()
                            .alpha(0f)
                            .setDuration(150)
                            .withEndAction(() -> {

                                progressBar.setVisibility(
                                        View.GONE
                                );

                            })
                            .start();

                } else {

                    progressBar.setVisibility(
                            View.VISIBLE
                    );

                    progressBar.setAlpha(1f);
                }
            }

            // =====================================================
            // FILE UPLOAD
            // =====================================================

            @Override
            public boolean onShowFileChooser(
                    WebView webView,
                    ValueCallback<Uri[]> callback,
                    FileChooserParams fileChooserParams
            ) {

                cancelFileChooser();

                filePathCallback = callback;

                Intent contentSelectionIntent =
                        new Intent(Intent.ACTION_GET_CONTENT);

                contentSelectionIntent.addCategory(
                        Intent.CATEGORY_OPENABLE
                );

                String acceptType = "*/*";

                if (fileChooserParams != null) {

                    String[] types =
                            fileChooserParams.getAcceptTypes();

                    if (types != null && types.length > 0) {

                        StringBuilder builder =
                                new StringBuilder();

                        for (String type : types) {

                            if (type == null ||
                                    type.trim().isEmpty()) {
                                continue;
                            }

                            if (builder.length() > 0) {
                                builder.append(",");
                            }

                            builder.append(type);
                        }

                        if (builder.length() > 0) {
                            acceptType = builder.toString();
                        }
                    }
                }

                contentSelectionIntent.setType(
                        acceptType
                );

                if (Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.LOLLIPOP) {

                    boolean multiple =
                            fileChooserParams != null &&
                            fileChooserParams.getMode() ==
                                    FileChooserParams
                                            .MODE_OPEN_MULTIPLE;

                    contentSelectionIntent.putExtra(
                            Intent.EXTRA_ALLOW_MULTIPLE,
                            multiple
                    );
                }

                Intent chooser =
                        new Intent(Intent.ACTION_CHOOSER);

                chooser.putExtra(
                        Intent.EXTRA_INTENT,
                        contentSelectionIntent
                );

                chooser.putExtra(
                        Intent.EXTRA_TITLE,
                        "اختر ملفاً أو صورة"
                );

                try {

                    activity.startActivityForResult(
                            chooser,
                            FILECHOOSER_RESULTCODE
                    );

                    return true;

                } catch (Exception e) {

                    Log.e(
                            TAG,
                            "File chooser failed",
                            e
                    );

                    cancelFileChooser();
                    return false;
                }
            }

            // =====================================================
            // GEOLOCATION
            // =====================================================

            @Override
            public void onGeolocationPermissionsShowPrompt(
                    String origin,
                    GeolocationPermissions.Callback callback
            ) {

                if (origin == null || callback == null) {
                    return;
                }

                if (ContextCompat.checkSelfPermission(
                        activity,
                        Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED) {

                    callback.invoke(
                            origin,
                            true,
                            true
                    );

                    return;
                }

                pendingLocationOrigin = origin;
                pendingLocationCallback = callback;

                ActivityCompat.requestPermissions(
                        activity,
                        new String[]{
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                        },
                        LOCATION_PERMISSION_CODE
                );
            }

            // =====================================================
            // WEBRTC PERMISSIONS
            // =====================================================

            @Override
            public void onPermissionRequest(
                    final PermissionRequest request
            ) {

                if (Build.VERSION.SDK_INT <
                        Build.VERSION_CODES.LOLLIPOP) {

                    request.deny();
                    return;
                }

                activity.runOnUiThread(() -> {

                    if (request == null) {
                        return;
                    }

                    String[] requested =
                            request.getResources();

                    if (requested == null ||
                            requested.length == 0) {

                        request.deny();
                        return;
                    }

                    /*
                     * لا نقبل أي Resource مجهول.
                     * نسمح فقط بالكاميرا والميكروفون
                     * اللذين نديرهما صراحة.
                     */

                    List<String> allowedResources =
                            new ArrayList<>();

                    List<String> androidPermissions =
                            new ArrayList<>();

                    for (String resource : requested) {

                        if (PermissionRequest
                                .RESOURCE_VIDEO_CAPTURE
                                .equals(resource)) {

                            allowedResources.add(resource);

                            if (ContextCompat
                                    .checkSelfPermission(
                                            activity,
                                            Manifest.permission.CAMERA
                                    ) !=
                                    PackageManager.PERMISSION_GRANTED) {

                                androidPermissions.add(
                                        Manifest.permission.CAMERA
                                );
                            }

                        } else if (
                                PermissionRequest
                                        .RESOURCE_AUDIO_CAPTURE
                                        .equals(resource)
                        ) {

                            allowedResources.add(resource);

                            if (ContextCompat
                                    .checkSelfPermission(
                                            activity,
                                            Manifest.permission.RECORD_AUDIO
                                    ) !=
                                    PackageManager.PERMISSION_GRANTED) {

                                androidPermissions.add(
                                        Manifest.permission.RECORD_AUDIO
                                );
                            }

                        } else {

                            /*
                             * MIDI / Protected Media / أي مورد
                             * مستقبلي لا يتم منحه تلقائياً.
                             */

                            Log.w(
                                    TAG,
                                    "Blocked WebView resource: " +
                                            resource
                            );
                        }
                    }

                    if (allowedResources.isEmpty()) {
                        request.deny();
                        return;
                    }

                    if (androidPermissions.isEmpty()) {

                        grantSpecificResources(
                                request,
                                allowedResources
                        );

                        return;
                    }

                    /*
                     * نحفظ الطلب حتى تأتي نتيجة Android.
                     */

                    cancelPendingWebPermission();

                    pendingWebPermissionRequest = request;

                    pendingWebPermissionResources =
                            allowedResources.toArray(
                                    new String[0]
                            );

                    /*
                     * إزالة التكرار.
                     */

                    Set<String> uniquePermissions =
                            new HashSet<>(
                                    androidPermissions
                            );

                    ActivityCompat.requestPermissions(
                            activity,
                            uniquePermissions.toArray(
                                    new String[0]
                            ),
                            MEDIA_PERMISSION_CODE
                    );
                });
            }

            // =====================================================
            // WEB PERMISSION CANCELED
            // =====================================================

            @Override
            public void onPermissionRequestCanceled(
                    PermissionRequest request
            ) {

                if (request != null &&
                        request ==
                                pendingWebPermissionRequest) {

                    clearPendingWebPermission();
                }
            }
        };
    }

    // =========================================================
    // GRANT SPECIFIC RESOURCES ONLY
    // =========================================================

    private void grantSpecificResources(
            PermissionRequest request,
            List<String> resources
    ) {

        if (request == null || resources == null ||
                resources.isEmpty()) {
            return;
        }

        try {

            request.grant(
                    resources.toArray(
                            new String[0]
                    )
            );

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "Unable to grant WebView resources",
                    e
            );

            try {
                request.deny();
            } catch (Exception ignored) {
            }
        }
    }

    // =========================================================
    // NOTIFICATION PERMISSION
    // =========================================================

    public void checkNotificationPermission() {

        if (Build.VERSION.SDK_INT <
                Build.VERSION_CODES.TIRAMISU) {
            return;
        }

        if (ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED) {
            return;
        }

        ActivityCompat.requestPermissions(
                activity,
                new String[]{
                        Manifest.permission.POST_NOTIFICATIONS
                },
                NOTIFICATION_PERMISSION_CODE
        );
    }

    // =========================================================
    // PERMISSION RESULT ROUTER
    // =========================================================

    public void handlePermissionResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {

        // ---------------------------------------------------------
        // LOCATION
        // ---------------------------------------------------------

        if (requestCode ==
                LOCATION_PERMISSION_CODE) {

            boolean granted = false;

            for (int result : grantResults) {

                if (result ==
                        PackageManager.PERMISSION_GRANTED) {

                    granted = true;
                    break;
                }
            }

            if (pendingLocationCallback != null &&
                    pendingLocationOrigin != null) {

                try {

                    pendingLocationCallback.invoke(
                            pendingLocationOrigin,
                            granted,
                            true
                    );

                } catch (Exception e) {

                    Log.e(
                            TAG,
                            "Location callback failed",
                            e
                    );
                }
            }

            pendingLocationCallback = null;
            pendingLocationOrigin = null;

            return;
        }

        // ---------------------------------------------------------
        // CAMERA / MICROPHONE
        // ---------------------------------------------------------

        if (requestCode ==
                MEDIA_PERMISSION_CODE) {

            boolean granted = true;

            for (int result : grantResults) {

                if (result !=
                        PackageManager.PERMISSION_GRANTED) {

                    granted = false;
                    break;
                }
            }

            if (pendingWebPermissionRequest != null) {

                PermissionRequest request =
                        pendingWebPermissionRequest;

                String[] resources =
                        pendingWebPermissionResources;

                pendingWebPermissionRequest = null;
                pendingWebPermissionResources = null;

                if (granted &&
                        resources != null &&
                        resources.length > 0) {

                    grantSpecificResources(
                            request,
                            java.util.Arrays.asList(
                                    resources
                            )
                    );

                } else {

                    try {
                        request.deny();
                    } catch (Exception ignored) {
                    }
                }
            }

            return;
        }

        // ---------------------------------------------------------
        // DOWNLOAD STORAGE — ONLY LEGACY ANDROID
        // ---------------------------------------------------------

        if (requestCode ==
                DOWNLOAD_PERMISSION_CODE) {

            continuePendingDownloadAfterPermission(
                    grantResults
            );

            return;
        }

        // ---------------------------------------------------------
        // NOTIFICATIONS
        // ---------------------------------------------------------

        if (requestCode ==
                NOTIFICATION_PERMISSION_CODE) {

            boolean granted =
                    grantResults.length > 0 &&
                    grantResults[0] ==
                            PackageManager.PERMISSION_GRANTED;

            Log.d(
                    TAG,
                    "Notifications permission: " +
                            granted
            );
        }
    }

    // =========================================================
    // FILE CHOOSER RESULT
    // =========================================================

    public boolean handleActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {

        if (requestCode != FILECHOOSER_RESULTCODE) {
            return false;
        }

        if (filePathCallback == null) {
            return true;
        }

        Uri[] results = null;

        try {

            if (resultCode ==
                    Activity.RESULT_OK &&
                    data != null) {

                if (data.getClipData() != null) {

                    int count =
                            data.getClipData().getItemCount();

                    results = new Uri[count];

                    for (int i = 0; i < count; i++) {

                        results[i] =
                                data.getClipData()
                                        .getItemAt(i)
                                        .getUri();
                    }

                } else if (data.getData() != null) {

                    results = new Uri[]{
                            data.getData()
                    };
                }
            }

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "File chooser result failed",
                    e
            );
        }

        filePathCallback.onReceiveValue(results);
        filePathCallback = null;

        return true;
    }

    // =========================================================
    // CANCEL FILE CHOOSER
    // =========================================================

    private void cancelFileChooser() {

        if (filePathCallback != null) {

            try {
                filePathCallback.onReceiveValue(null);
            } catch (Exception ignored) {
            }

            filePathCallback = null;
        }
    }

    // =========================================================
    // CANCEL WEB PERMISSION
    // =========================================================

    private void cancelPendingWebPermission() {

        if (pendingWebPermissionRequest != null) {

            try {
                pendingWebPermissionRequest.deny();
            } catch (Exception ignored) {
            }
        }

        pendingWebPermissionRequest = null;
        pendingWebPermissionResources = null;
    }

    private void clearPendingWebPermission() {

        pendingWebPermissionRequest = null;
        pendingWebPermissionResources = null;
    }

    // =========================================================
    // CLEAR DOWNLOAD
    // =========================================================

    private void clearPendingDownload() {

        pendingDownloadUrl = null;
        pendingDownloadUserAgent = null;
        pendingDownloadContentDisposition = null;
        pendingDownloadMimeType = null;
    }

    // =========================================================
    // FILE SIZE
    // =========================================================

    private String formatFileSize(long bytes) {

        if (bytes <= 0) {
            return null;
        }

        if (bytes < 1024) {
            return bytes + " B";
        }

        double kb = bytes / 1024.0;

        if (kb < 1024) {
            return String.format(
                    java.util.Locale.US,
                    "%.1f KB",
                    kb
            );
        }

        double mb = kb / 1024.0;

        if (mb < 1024) {
            return String.format(
                    java.util.Locale.US,
                    "%.1f MB",
                    mb
            );
        }

        double gb = mb / 1024.0;

        return String.format(
                java.util.Locale.US,
                "%.1f GB",
                gb
        );
    }

    // =========================================================
    // LIFECYCLE CLEANUP
    // =========================================================

    public void destroy() {

        cancelFileChooser();

        if (pendingWebPermissionRequest != null) {

            try {
                pendingWebPermissionRequest.deny();
            } catch (Exception ignored) {
            }
        }

        pendingWebPermissionRequest = null;
        pendingWebPermissionResources = null;

        pendingLocationCallback = null;
        pendingLocationOrigin = null;

        clearPendingDownload();
    }
    }
