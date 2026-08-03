package com.kashmirtravelify.crm;

import android.Manifest;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.provider.MediaStore;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.DownloadListener;
import android.webkit.GeolocationPermissions;
import android.webkit.MimeTypeMap;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.util.Base64;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;

public class MainActivity extends android.app.Activity {
    private static final String HOME_URL = "https://crm.kashmirtravelify.com/newcrm/";
    private static final int FILE_CHOOSER_REQUEST = 1001;

    private WebView webView;
    private ProgressBar progressBar;
    private View errorPanel;
    private View splashPanel;
    private ValueCallback<Uri[]> filePathCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        configureSystemBarsSafely();

        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        errorPanel = findViewById(R.id.errorPanel);
        splashPanel = findViewById(R.id.splashPanel);
        findViewById(R.id.retryButton).setOnClickListener(v -> {
            errorPanel.setVisibility(View.GONE);
            webView.setVisibility(View.VISIBLE);
            webView.reload();
        });

        configureWebView();
        clearWebCacheOncePerAppVersion();
        requestNotificationPermission();

        if (savedInstanceState == null) {
            webView.loadUrl(HOME_URL);
        } else {
            webView.restoreState(savedInstanceState);
        }
    }



    private void clearWebCacheOncePerAppVersion() {
        // The Flight/Hotel Live API mobile tap fix is delivered by the CRM website.
        // Clear only WebView cache once after an app update so old CSS/JavaScript
        // cannot keep the previous mobile popup behaviour. Login cookies remain intact.
        SharedPreferences preferences = getSharedPreferences("kashmir_travelify_app", MODE_PRIVATE);
        int lastCacheVersion = preferences.getInt("cache_version", -1);
        if (lastCacheVersion != BuildConfig.VERSION_CODE) {
            webView.clearCache(true);
            preferences.edit().putInt("cache_version", BuildConfig.VERSION_CODE).apply();
        }
    }

    private void configureSystemBarsSafely() {
        Window window = getWindow();
        int chromeColor = Color.rgb(7, 55, 99);

        window.setStatusBarColor(chromeColor);
        window.setNavigationBarColor(chromeColor);

        // Ask Android to keep app content inside the system bars. This avoids
        // status-bar overlap without relying on a custom inset listener.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(true);
            android.view.WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.setSystemBarsAppearance(
                        0,
                        android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                );
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.getDecorView().setSystemUiVisibility(0);
        }
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        // Respect the website's responsive viewport inside Android WebView.
        // With these disabled, WebView can use a desktop-like ~980px layout viewport.
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(false);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setTextZoom(100);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        // Keep the normal mobile Chrome identity. Some responsive sites treat the
        // WebView marker ("; wv") differently and return their desktop layout.
        String mobileUserAgent = settings.getUserAgentString()
                .replace("; wv", "")
                .replace("Version/4.0 ", "");
        if (!mobileUserAgent.contains(" Mobile")) {
            mobileUserAgent = mobileUserAgent.replace(" Safari/", " Mobile Safari/");
        }
        settings.setUserAgentString(mobileUserAgent);

        // Let WebView calculate the scale from width=device-width rather than
        // forcing a desktop-style 100% initial scale.
        webView.setInitialScale(0);
        webView.setVerticalScrollBarEnabled(true);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        // Itinerary PDFs are generated in the CRM as browser blob/data downloads.
        // Android DownloadManager cannot download blob: or data: URLs directly, so
        // expose a tiny bridge that saves the generated PDF into Downloads.
        webView.addJavascriptInterface(new DownloadBridge(), "KashmirDownload");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                errorPanel.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (splashPanel != null) splashPanel.setVisibility(View.GONE);
                // Prevent pinch/double-tap zoom without changing the CRM's responsive layout.
                view.evaluateJavascript(
                    "(function(){" +
                    "var m=document.querySelector('meta[name=viewport]');" +
                    "if(!m){m=document.createElement('meta');m.name='viewport';document.head.appendChild(m);}" +
                    "m.content='width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover';" +
                    "document.documentElement.style.webkitTextSizeAdjust='100%';" +
                    "document.body.style.webkitOverflowScrolling='touch';" +
                    "function fixBottomNav(){" +
                    "var maxH=0,best=null;document.querySelectorAll('body *').forEach(function(el){" +
                    "var s=getComputedStyle(el),r=el.getBoundingClientRect(),b=parseFloat(s.bottom||'999');" +
                    "if((s.position==='fixed'||s.position==='sticky')&&b<80&&r.width>innerWidth*.75&&r.height>45&&r.height<190){if(r.height>maxH){maxH=r.height;best=el;}}" +
                    "});if(best){best.style.setProperty('bottom','0px','important');best.style.setProperty('margin-bottom','0px','important');best.style.setProperty('z-index','2147483000','important');" +
                    "document.documentElement.style.setProperty('padding-bottom',maxH+'px','important');document.body.style.setProperty('padding-bottom',maxH+'px','important');}" +
                    "}" +
                    "fixBottomNav();setTimeout(fixBottomNav,500);setTimeout(fixBottomNav,1500);" +
                    "})();", null);

                installGeneratedDownloadInterceptor(view);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(request.getUrl().toString());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrl(url);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    webView.setVisibility(View.GONE);
                    if (splashPanel != null) splashPanel.setVisibility(View.GONE);
                errorPanel.setVisibility(View.VISIBLE);
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                progressBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), FILE_CHOOSER_REQUEST);
                } catch (ActivityNotFoundException e) {
                    filePathCallback = null;
                    Toast.makeText(MainActivity.this, "File picker available nahi hai.", Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> request.grant(request.getResources()));
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);

            // CRM itinerary PDF generation uses blob:/data: URLs. Those URLs only
            // exist inside this WebView and cannot be handed to DownloadManager.
            if (url != null && (url.startsWith("blob:") || url.startsWith("data:"))) {
                downloadGeneratedFile(url, fileName, mimeType);
                return;
            }

            try {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                if (mimeType != null && !mimeType.isEmpty()) request.setMimeType(mimeType);
                if (userAgent != null && !userAgent.isEmpty()) request.addRequestHeader("User-Agent", userAgent);

                String cookies = CookieManager.getInstance().getCookie(url);
                if (cookies != null && !cookies.isEmpty()) request.addRequestHeader("Cookie", cookies);

                String referer = webView.getUrl();
                if (referer != null && !referer.isEmpty()) request.addRequestHeader("Referer", referer);

                request.setTitle(fileName);
                request.setDescription("Kashmir Travelify CRM");
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

                DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                manager.enqueue(request);
                Toast.makeText(this, "Download started", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception ignored) {
                    Toast.makeText(this, "Download could not be started.", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void installGeneratedDownloadInterceptor(WebView view) {
        String script =
                "(function(){" +
                "if(window.__odysseyGeneratedDownloadFix)return;" +
                "window.__odysseyGeneratedDownloadFix=true;" +
                "function nameFor(a){return (a&&a.getAttribute&&a.getAttribute('download'))||('Kashmir-Travelify-Itinerary-'+Date.now()+'.pdf');}" +
                "async function save(url,name){try{" +
                "var response=await fetch(url);var blob=await response.blob();var reader=new FileReader();" +
                "reader.onloadend=function(){KashmirDownload.saveBase64File(reader.result,name,blob.type||'application/pdf');};" +
                "reader.onerror=function(){KashmirDownload.reportError('Unable to read generated file');};reader.readAsDataURL(blob);" +
                "}catch(e){KashmirDownload.reportError(String(e));}}" +
                "document.addEventListener('click',function(e){" +
                "var a=e.target&&e.target.closest?e.target.closest('a'):null;if(!a)return;var href=a.href||'';" +
                "if(href.indexOf('blob:')===0||href.indexOf('data:')===0){e.preventDefault();e.stopPropagation();save(href,nameFor(a));}" +
                "},true);" +
                "var originalClick=HTMLAnchorElement.prototype.click;HTMLAnchorElement.prototype.click=function(){" +
                "var href=this.href||'';if(href.indexOf('blob:')===0||href.indexOf('data:')===0){save(href,nameFor(this));return;}" +
                "return originalClick.apply(this,arguments);};" +
                "})();";
        view.evaluateJavascript(script, null);
    }

    private void downloadGeneratedFile(String url, String fileName, String mimeType) {
        String finalFileName = normalizeDownloadFileName(fileName, mimeType);
        String finalMimeType = (mimeType == null || mimeType.isEmpty()) ? "application/pdf" : mimeType;

        String script =
                "(async function(){try{" +
                "var response=await fetch(" + jsQuote(url) + ");" +
                "var blob=await response.blob();" +
                "var reader=new FileReader();" +
                "reader.onloadend=function(){KashmirDownload.saveBase64File(reader.result," +
                jsQuote(finalFileName) + ",blob.type||" + jsQuote(finalMimeType) + ");};" +
                "reader.onerror=function(){KashmirDownload.reportError('Unable to read generated PDF');};" +
                "reader.readAsDataURL(blob);" +
                "}catch(e){KashmirDownload.reportError(String(e));}})();";

        webView.evaluateJavascript(script, null);
    }

    private String normalizeDownloadFileName(String fileName, String mimeType) {
        String name = fileName == null ? "" : fileName.trim();
        if (name.isEmpty() || name.equalsIgnoreCase("download") || name.startsWith("blob")) {
            name = "Kashmir-Travelify-Itinerary-" + System.currentTimeMillis();
        }
        name = name.replaceAll("[\\/:*?\"<>|]", "_");

        String lower = name.toLowerCase();
        String type = mimeType == null ? "" : mimeType.toLowerCase();
        if (type.contains("pdf") && !lower.endsWith(".pdf")) name += ".pdf";
        return name;
    }

    private String jsQuote(String value) {
        if (value == null) return "null";
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("</", "<\\/") + "\"";
    }

    private class DownloadBridge {
        @JavascriptInterface
        public void saveBase64File(String dataUrl, String fileName, String mimeType) {
            try {
                if (dataUrl == null || dataUrl.isEmpty()) {
                    throw new IllegalArgumentException("Empty download data");
                }

                int commaIndex = dataUrl.indexOf(',');
                String payload = commaIndex >= 0 ? dataUrl.substring(commaIndex + 1) : dataUrl;
                byte[] bytes = Base64.decode(payload, Base64.DEFAULT);
                String safeName = normalizeDownloadFileName(fileName, mimeType);
                saveBytesToDownloads(bytes, safeName, mimeType);
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "PDF downloaded: " + safeName, Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "PDF download failed.", Toast.LENGTH_SHORT).show());
            }
        }

        @JavascriptInterface
        public void reportError(String message) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this,
                    "PDF download failed.", Toast.LENGTH_SHORT).show());
        }
    }

    private void saveBytesToDownloads(byte[] bytes, String fileName, String mimeType) throws Exception {
        String finalMimeType = (mimeType == null || mimeType.isEmpty()) ? "application/pdf" : mimeType;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, finalMimeType);
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);

            Uri item = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (item == null) throw new IllegalStateException("Could not create download file");

            try (OutputStream out = getContentResolver().openOutputStream(item)) {
                if (out == null) throw new IllegalStateException("Could not open download file");
                out.write(bytes);
                out.flush();
            } catch (Exception e) {
                getContentResolver().delete(item, null, null);
                throw e;
            }

            ContentValues done = new ContentValues();
            done.put(MediaStore.MediaColumns.IS_PENDING, 0);
            getContentResolver().update(item, done, null, null);
        } else {
            // Android 7-9 fallback: save inside the app's external Downloads area
            // without changing the app's permission model.
            File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (dir == null) throw new IllegalStateException("Downloads folder unavailable");
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Could not create Downloads folder");

            File output = uniqueFile(dir, fileName);
            try (FileOutputStream out = new FileOutputStream(output)) {
                out.write(bytes);
                out.flush();
            }
        }
    }

    private File uniqueFile(File dir, String fileName) {
        File candidate = new File(dir, fileName);
        if (!candidate.exists()) return candidate;

        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        String ext = dot > 0 ? fileName.substring(dot) : "";
        int index = 1;
        while (candidate.exists()) {
            candidate = new File(dir, base + " (" + index + ")" + ext);
            index++;
        }
        return candidate;
    }

    private boolean handleUrl(String url) {
        if (url == null) return false;
        if (url.startsWith("https://") || url.startsWith("http://")) return false;
        if (url.startsWith("intent://")) {
            try {
                Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                startActivity(intent);
            } catch (URISyntaxException | ActivityNotFoundException ignored) { }
            return true;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException ignored) { }
        return true;
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 2001);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST && filePathCallback != null) {
            Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            filePathCallback.onReceiveValue(result);
            filePathCallback = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }
}
