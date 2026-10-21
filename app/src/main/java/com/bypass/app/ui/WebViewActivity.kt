package com.bypass.app.ui

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.*
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.bypass.app.R

/**
 * A bottom-sheet style Activity hosting a mini WebView.
 *
 * Key features:
 * 1. Automatically enters clipboard URL into the downloader input.
 * 2. Injects a capture-phase JavaScript hook that intercepts download button clicks,
 *    killing aggressive ad popups/popunders before they can fire.
 * 3. Bridges directly to native Android DownloadManager via JavascriptInterface.
 * 4. Filters out third-party ad redirects (like Google ads or shady ad networks).
 * 5. Closes automatically once download is confirmed, returning the user to their app.
 * 6. Background DownloadReceiver notifies the user when the download finishes.
 */
class WebViewActivity : ComponentActivity() {

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_PLATFORM_NAME = "extra_platform_name"
        const val EXTRA_CLIPBOARD_URL = "extra_clipboard_url"
    }

    private lateinit var webView: WebView
    private var baseUrl: String = ""
    private var isDownloadEnqueued: Boolean = false

    /**
     * JS interface exposed to the WebView page as `window.AndroidDownloader`.
     */
    inner class DownloaderJsInterface {
        @JavascriptInterface
        fun onDownloadTriggered(downloadUrl: String?, title: String?) {
            if (downloadUrl.isNullOrBlank()) return
            runOnUiThread {
                enqueueDownload(
                    downloadUrl = downloadUrl,
                    userAgent = webView.settings.userAgentString,
                    contentDisposition = "",
                    mimeType = "video/mp4",
                    suggestedTitle = title
                )
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webview)

        baseUrl = intent.getStringExtra(EXTRA_URL) ?: run { finish(); return }
        val platformName = intent.getStringExtra(EXTRA_PLATFORM_NAME) ?: "Downloader"

        val clipboardUrl = intent.getStringExtra(EXTRA_CLIPBOARD_URL)?.takeIf { it.isNotBlank() }
            ?: readClipboardUrl()

        // Dim background tap dismisses bottom sheet
        findViewById<View>(R.id.dim_background).setOnClickListener { finish() }

        // Toolbar controls
        findViewById<TextView>(R.id.tv_platform_title).text = platformName
        findViewById<ImageButton>(R.id.btn_close).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btn_refresh).setOnClickListener { webView.reload() }
        findViewById<ImageButton>(R.id.btn_open_browser).setOnClickListener {
            val targetUrl = webView.url ?: baseUrl
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)))
            } catch (e: Exception) {
                Toast.makeText(this, "Could not open browser", Toast.LENGTH_SHORT).show()
            }
        }

        // Setup WebView
        webView = findViewById(R.id.web_view)
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = false

            // BLOCK POPUPS: do not allow new popup windows or automatic popups.
            // All interactions stay contained within our WebView!
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)

            userAgentString = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }

        // Bridge JavaScript directly to native Android
        webView.addJavascriptInterface(DownloaderJsInterface(), "AndroidDownloader")

        // Download handling when WebView signals a downloadable file
        webView.setDownloadListener { downloadUrl, userAgent, contentDisposition, mimeType, _ ->
            enqueueDownload(downloadUrl, userAgent, contentDisposition, mimeType, null)
        }

        webView.webChromeClient = object : WebChromeClient() {
            // Keep everything in a single view — refuse popup windows
            override fun onCreateWindow(
                view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?
            ): Boolean = false
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, pageUrl: String) {
                super.onPageFinished(view, pageUrl)

                // Inject clipboard URL
                if (clipboardUrl.isNotBlank()) {
                    injectUrl(clipboardUrl)
                }

                // Inject ad blocker & download button interceptor
                injectAdBlockAndDownloadHook()
            }

            override fun shouldOverrideUrlLoading(
                view: WebView, request: WebResourceRequest
            ): Boolean {
                val targetUrl = request.url.toString()
                val scheme = request.url.scheme?.lowercase() ?: ""

                // 1. If it's a media/download URL, intercept and start download!
                if (isLikelyDownloadUrl(targetUrl)) {
                    enqueueDownload(targetUrl, webView.settings.userAgentString, "", "video/mp4", null)
                    return true
                }

                // 2. Block known ads & trackers
                if (isAdUrl(targetUrl)) {
                    return true
                }

                // 3. Block deep links (market://, intent://, etc.) from opening external app stores
                if (scheme != "http" && scheme != "https") {
                    return true
                }

                // 4. Allow legitimate navigation on the downloader website
                if (isAllowedSite(targetUrl, baseUrl)) {
                    return false
                }

                // 5. Block external third-party redirects (Google ads, ad networks)
                // This keeps the user on the download page and stops the "Google site" popup!
                return true
            }
        }

        webView.loadUrl(baseUrl)
    }

    // -----------------------------------------------------------------------
    // Native Download Enqueue
    // -----------------------------------------------------------------------

    fun enqueueDownload(
        downloadUrl: String,
        userAgent: String,
        contentDisposition: String,
        mimeType: String,
        suggestedTitle: String?
    ) {
        if (isDownloadEnqueued) return
        isDownloadEnqueued = true

        try {
            var fileName = suggestedTitle?.trim() ?: ""
            if (fileName.isBlank() || fileName.equals("Video", ignoreCase = true)) {
                fileName = URLUtil.guessFileName(downloadUrl, contentDisposition, mimeType)
            }
            // Clean illegal file characters
            fileName = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
            if (!fileName.endsWith(".mp4", ignoreCase = true) && !fileName.contains(".")) {
                fileName += ".mp4"
            }

            // Save to Movies/Bypass so Gallery apps immediately index into the "Bypass" video album
            val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            val bypassDir = java.io.File(moviesDir, "Bypass")
            try {
                if (!bypassDir.exists()) {
                    bypassDir.mkdirs()
                }
            } catch (_: Exception) {}

            val destinationSubPath = if (bypassDir.exists() || bypassDir.isDirectory) {
                "Bypass/$fileName"
            } else {
                fileName
            }

            val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
                setMimeType(if (mimeType.isNotBlank()) mimeType else "video/mp4")
                if (userAgent.isNotBlank()) {
                    addRequestHeader("User-Agent", userAgent)
                }
                val cookies = CookieManager.getInstance().getCookie(downloadUrl)
                if (cookies != null) {
                    addRequestHeader("Cookie", cookies)
                }
                addRequestHeader("Referer", baseUrl)
                setDescription("Downloading via Bypass...")
                setTitle(fileName)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                @Suppress("DEPRECATION")
                allowScanningByMediaScanner()
                setDestinationInExternalPublicDir(Environment.DIRECTORY_MOVIES, destinationSubPath)
            }

            val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)

            Toast.makeText(this, "⬇️ Download started: $fileName", Toast.LENGTH_SHORT).show()

            // Close the mini-browser so user can immediately continue what they were doing!
            Handler(Looper.getMainLooper()).postDelayed({
                finish()
            }, 800)
        } catch (e: Exception) {
            isDownloadEnqueued = false
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)))
            } catch (ex: Exception) {
                Toast.makeText(this, "Download error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // -----------------------------------------------------------------------
    // Domain & URL Filters
    // -----------------------------------------------------------------------

    private fun isAllowedSite(url: String, currentBaseUrl: String): Boolean {
        val baseHost = try { Uri.parse(currentBaseUrl).host?.lowercase()?.removePrefix("www.") ?: "" } catch (_: Exception) { "" }
        val targetHost = try { Uri.parse(url).host?.lowercase()?.removePrefix("www.") ?: "" } catch (_: Exception) { "" }
        if (targetHost.isEmpty() || baseHost.isEmpty()) return false

        // Match base domain or known subdomains/workers
        return targetHost.endsWith(baseHost) ||
               baseHost.endsWith(targetHost) ||
               targetHost.contains("savefrom") ||
               targetHost.contains("ssstwitter")
    }

    private fun isLikelyDownloadUrl(url: String): Boolean {
        val lower = url.lowercase()
        if (lower.startsWith("data:") || lower.startsWith("javascript:")) return false

        val downloadExtensions = listOf(
            ".mp4", ".mp3", ".webm", ".mkv", ".avi", ".mov",
            ".m4a", ".m4v", ".3gp", ".flv", ".wmv", ".aac", ".ts"
        )
        val uri = try { Uri.parse(lower) } catch (_: Exception) { null }
        val path = uri?.path ?: ""
        if (downloadExtensions.any { path.endsWith(it) || lower.contains(it) }) return true

        if (lower.contains("mime=video") || lower.contains("mime=audio")) return true
        if (lower.contains("videoplayback") || lower.contains("googlevideo.com")) return true
        if (lower.contains("savefrom.net/redirect")) return true
        if (lower.contains("dl=1") || lower.contains("download=1") || lower.contains("download=true")) return true
        if (lower.contains("fbcdn.net") && (lower.contains("video") || lower.contains(".mp4") || lower.contains("bytestart"))) return true
        if (lower.contains("tiktokcdn.com") || lower.contains("cdninstagram.com")) return true
        if (lower.contains("video.twimg.com")) return true

        return false
    }

    private fun isAdUrl(url: String): Boolean {
        val adDomains = listOf(
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "google-analytics.com", "facebook.com/tr", "adnxs.com",
            "adsrvr.org", "adform.net", "taboola.com", "outbrain.com",
            "popads.net", "popcash.net", "propellerads.com",
            "trafficjunky.com", "exoclick.com", "juicyads.com",
            "clickadu.com", "hilltopads.net", "adsterra.com",
            "pushground.com", "richpush.co", "megapush.com",
            "ad.plus", "bidvertiser.com", "adkeeper.co", "mgid.com"
        )
        val lower = url.lowercase()
        return adDomains.any { lower.contains(it) }
    }

    // -----------------------------------------------------------------------
    // JavaScript Hooks & Ad Blocker
    // -----------------------------------------------------------------------

    /**
     * Injects client-side scripts that:
     * 1. Neutralize `window.open` so popup ads cannot launch.
     * 2. Intercept download button clicks during the CAPTURE phase,
     *    cancelling ad scripts from firing and sending the video link straight
     *    to Android's DownloadManager!
     */
    private fun injectAdBlockAndDownloadHook() {
        val hookJs = """
            (function() {
                if (window.__bypass_hooked) return;
                window.__bypass_hooked = true;

                // 1. Block popup ads
                window.open = function(url) {
                    if (url && (url.indexOf('.mp4') !== -1 || url.indexOf('videoplayback') !== -1 || url.indexOf('savefrom.net/redirect') !== -1)) {
                        if (window.AndroidDownloader) {
                            window.AndroidDownloader.onDownloadTriggered(url, '');
                        }
                    }
                    return null;
                };

                // 2. Capture clicks on download links/buttons BEFORE site ad listeners run
                document.addEventListener('click', function(e) {
                    var el = e.target;
                    while (el && el.tagName !== 'A' && el !== document.body) {
                        el = el.parentElement;
                    }
                    if (el && el.tagName === 'A') {
                        var href = el.getAttribute('href') || el.href || '';
                        var hasDownload = el.hasAttribute('download');
                        var className = el.className || '';
                        var isDownloadBtn = hasDownload ||
                            className.indexOf('download-icon') !== -1 ||
                            className.indexOf('link-download') !== -1 ||
                            className.indexOf('def-btn-box') !== -1 ||
                            className.indexOf('sf-dump') !== -1 ||
                            href.indexOf('.mp4') !== -1 ||
                            href.indexOf('videoplayback') !== -1 ||
                            href.indexOf('savefrom.net/redirect') !== -1 ||
                            href.indexOf('dl=1') !== -1 ||
                            href.indexOf('download=1') !== -1;

                        if (isDownloadBtn && href && href.indexOf('javascript:') !== 0 && href.indexOf('#') !== 0) {
                            // Stop ad popups from intercepting this click!
                            e.preventDefault();
                            e.stopPropagation();
                            e.stopImmediatePropagation();

                            // Scrape video title if present
                            var titleEl = document.querySelector('.title') ||
                                          document.querySelector('.video-title') ||
                                          document.querySelector('.row-title') ||
                                          document.querySelector('.info-box .title') ||
                                          document.querySelector('meta[property="og:title"]');
                            var title = titleEl ? (titleEl.innerText || titleEl.getAttribute('content') || '') : '';
                            var downloadAttr = el.getAttribute('download') || '';

                            if (window.AndroidDownloader) {
                                window.AndroidDownloader.onDownloadTriggered(href, downloadAttr || title);
                            }
                            return false;
                        }
                    }
                }, true); // true = capture phase!
            })();
        """.trimIndent()
        webView.evaluateJavascript(hookJs, null)
    }

    private fun injectUrl(videoUrl: String) {
        val escaped = videoUrl
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "")
            .replace("\r", "")
        val js = """
            (function() {
                var inputs = document.querySelectorAll('input[type="text"], input[type="url"], input[type="search"], input:not([type])');
                for (var i = 0; i < inputs.length; i++) {
                    var el = inputs[i];
                    if (el.offsetParent !== null) {
                        var nativeInputValueSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
                        if (nativeInputValueSetter) {
                            nativeInputValueSetter.call(el, '$escaped');
                        } else {
                            el.value = '$escaped';
                        }
                        el.dispatchEvent(new Event('input', { bubbles: true }));
                        el.dispatchEvent(new Event('change', { bubbles: true }));
                        el.dispatchEvent(new Event('keyup', { bubbles: true }));

                        // If there is an adjacent submit button, tap it
                        var submitBtn = document.querySelector('#sf_submit') ||
                                        document.querySelector('button[type="submit"]') ||
                                        document.querySelector('.btn-download');
                        if (submitBtn) {
                            submitBtn.click();
                        }
                        break;
                    }
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    // -----------------------------------------------------------------------
    // Clipboard helper
    // -----------------------------------------------------------------------

    private fun readClipboardUrl(): String {
        return try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = clipboard.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val text = clipData.getItemAt(0)?.text?.toString() ?: ""
                val regex = Regex("https?://[^\\s\"'<>]+")
                regex.find(text)?.value ?: ""
            } else ""
        } catch (_: Exception) { "" }
    }

    // -----------------------------------------------------------------------
    // Back navigation
    // -----------------------------------------------------------------------

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (this::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
}
