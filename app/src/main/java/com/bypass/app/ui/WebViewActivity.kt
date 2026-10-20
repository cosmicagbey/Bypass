package com.bypass.app.ui

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import android.webkit.*
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.NotificationCompat
import com.bypass.app.R

/**
 * A bottom-sheet style Activity hosting a mini WebView.
 * It loads the downloader URL and, once the page is ready, injects
 * the clipboard URL into the first visible text input on the page.
 *
 * Key behaviors:
 * - Handles window.open() popups from download sites (loads them in same WebView)
 * - Blocks common ad/tracker redirects
 * - Downloads via Android DownloadManager
 * - Auto-closes after download starts
 * - Sends a notification when download completes
 *
 * Runs in its own task (taskAffinity) so dismissing it never brings
 * the main Bypass app to the foreground.
 */
class WebViewActivity : ComponentActivity() {

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_PLATFORM_NAME = "extra_platform_name"
        const val EXTRA_CLIPBOARD_URL = "extra_clipboard_url"
        private const val DOWNLOAD_CHANNEL_ID = "bypass_download_channel"
        private const val DOWNLOAD_NOTIF_ID = 500
    }

    private lateinit var webView: WebView
    private var baseUrl: String = ""
    private var pendingDownloadId: Long = -1L

    // Broadcast receiver that fires when DownloadManager finishes a download
    private val downloadCompleteReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (id == pendingDownloadId && id != -1L) {
                val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val query = DownloadManager.Query().setFilterById(id)
                val cursor = dm.query(query)
                if (cursor != null && cursor.moveToFirst()) {
                    val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val titleIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)
                    val status = if (statusIdx >= 0) cursor.getInt(statusIdx) else -1
                    val title = if (titleIdx >= 0) cursor.getString(titleIdx) else "Video"
                    cursor.close()

                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        showDownloadCompleteNotification(title)
                    }
                }
                pendingDownloadId = -1L
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webview)

        baseUrl = intent.getStringExtra(EXTRA_URL) ?: run { finish(); return }
        val platformName = intent.getStringExtra(EXTRA_PLATFORM_NAME) ?: "Downloader"

        // Read clipboard URL: prefer the intent extra (if provided), otherwise read clipboard
        // directly. Since this is a focused Activity, clipboard access works on Android 10+.
        val clipboardUrl = intent.getStringExtra(EXTRA_CLIPBOARD_URL)?.takeIf { it.isNotBlank() }
            ?: readClipboardUrl()

        // Create notification channel for download-complete alerts
        createDownloadNotificationChannel()

        // Register for download completion broadcasts
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadCompleteReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(downloadCompleteReceiver, filter)
        }

        // Dim background tap dismisses bottom sheet
        findViewById<View>(R.id.dim_background).setOnClickListener { finish() }

        // Toolbar
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

        // WebView setup
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
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            userAgentString = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }

        // Download handling via Android DownloadManager
        webView.setDownloadListener { downloadUrl, userAgent, contentDisposition, mimeType, _ ->
            enqueueDownload(downloadUrl, userAgent, contentDisposition, mimeType)
        }

        // Handle window.open() popups — savefrom.net download buttons use these.
        // We load the popup URL in the same WebView instead of opening a new browser.
        webView.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(
                view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message
            ): Boolean {
                // Extract the URL the site is trying to open in a new window
                val transport = resultMsg.obj as? WebView.WebViewTransport

                // Create a temporary invisible WebView to capture the target URL
                val tempWebView = WebView(this@WebViewActivity)
                tempWebView.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        v: WebView, request: WebResourceRequest
                    ): Boolean {
                        val targetUrl = request.url.toString()

                        // Block known ad/tracker domains — don't navigate there
                        if (isAdUrl(targetUrl)) {
                            return true
                        }

                        // If it looks like a downloadable file link, trigger download directly
                        if (isLikelyDownloadUrl(targetUrl)) {
                            enqueueDownload(
                                targetUrl,
                                webView.settings.userAgentString,
                                "",
                                "video/mp4"
                            )
                            return true
                        }

                        // Otherwise load it in our main WebView (e.g. savefrom download page)
                        webView.loadUrl(targetUrl)
                        return true
                    }
                }

                transport?.webView = tempWebView
                resultMsg.sendToTarget()
                return true
            }

            override fun onCloseWindow(window: WebView?) {
                // Popup closed itself; ignore
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, pageUrl: String) {
                super.onPageFinished(view, pageUrl)
                if (clipboardUrl.isNotBlank()) {
                    injectUrl(clipboardUrl)
                }
            }

            override fun shouldOverrideUrlLoading(
                view: WebView, request: WebResourceRequest
            ): Boolean {
                val targetUrl = request.url.toString()
                val scheme = request.url.scheme?.lowercase() ?: ""

                // Block ad/tracker redirects
                if (isAdUrl(targetUrl)) {
                    return true
                }

                // Direct download links — intercept and download
                if (isLikelyDownloadUrl(targetUrl)) {
                    enqueueDownload(
                        targetUrl,
                        webView.settings.userAgentString,
                        "",
                        "video/mp4"
                    )
                    return true
                }

                return if (scheme == "http" || scheme == "https") {
                    false // let WebView handle normal navigation
                } else {
                    // intent://, market://, etc — open externally
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, request.url))
                    } catch (_: Exception) {}
                    true
                }
            }
        }

        webView.loadUrl(baseUrl)
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(downloadCompleteReceiver)
        } catch (_: Exception) {}
        super.onDestroy()
    }

    // -----------------------------------------------------------------------
    // Download helpers
    // -----------------------------------------------------------------------

    private fun enqueueDownload(
        downloadUrl: String,
        userAgent: String,
        contentDisposition: String,
        mimeType: String
    ) {
        try {
            val fileName = URLUtil.guessFileName(downloadUrl, contentDisposition, mimeType)
            val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
                setMimeType(mimeType)
                addRequestHeader("User-Agent", userAgent)
                val cookies = CookieManager.getInstance().getCookie(downloadUrl)
                if (cookies != null) {
                    addRequestHeader("Cookie", cookies)
                }
                addRequestHeader("Referer", baseUrl)
                setDescription("Downloading via Bypass...")
                setTitle(fileName)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            }
            val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            pendingDownloadId = downloadManager.enqueue(request)
            Toast.makeText(this, "⬇️ Download started: $fileName", Toast.LENGTH_SHORT).show()

            // Auto-close the mini browser so user returns to their previous app
            Handler(Looper.getMainLooper()).postDelayed({ finish() }, 1500)
        } catch (e: Exception) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)))
            } catch (ex: Exception) {
                Toast.makeText(this, "Download error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Returns true for common ad/tracker domains that should be blocked.
     */
    private fun isAdUrl(url: String): Boolean {
        val adDomains = listOf(
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "google-analytics.com", "facebook.com/tr", "adnxs.com",
            "adsrvr.org", "adform.net", "taboola.com", "outbrain.com",
            "popads.net", "popcash.net", "propellerads.com",
            "trafficjunky.com", "exoclick.com", "juicyads.com",
            "clickadu.com", "hilltopads.net", "adsterra.com",
            "pushground.com", "richpush.co", "megapush.com",
            "ad.plus", "bidvertiser.com"
        )
        val lower = url.lowercase()
        return adDomains.any { lower.contains(it) }
    }

    /**
     * Returns true if the URL looks like a direct media/file download.
     */
    private fun isLikelyDownloadUrl(url: String): Boolean {
        val lower = url.lowercase()
        val downloadExtensions = listOf(
            ".mp4", ".mp3", ".webm", ".mkv", ".avi", ".mov",
            ".m4a", ".m4v", ".3gp", ".flv", ".wmv", ".aac"
        )
        // Check file extension (strip query params)
        val path = Uri.parse(lower).path ?: ""
        if (downloadExtensions.any { path.endsWith(it) }) return true
        // Check query params for common download indicators
        if (lower.contains("mime=video") || lower.contains("mime=audio")) return true
        if (lower.contains("dl=1") || lower.contains("download=1")) return true
        return false
    }

    // -----------------------------------------------------------------------
    // Clipboard helpers
    // -----------------------------------------------------------------------

    private fun readClipboardUrl(): String {
        return try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = clipboard.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val text = clipData.getItemAt(0)?.text?.toString() ?: ""
                extractUrl(text)
            } else ""
        } catch (_: Exception) { "" }
    }

    private fun extractUrl(text: String): String {
        if (text.isBlank()) return ""
        val regex = Regex("https?://[^\\s\"'<>]+")
        return regex.find(text)?.value ?: ""
    }

    // -----------------------------------------------------------------------
    // URL injection
    // -----------------------------------------------------------------------

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
                        break;
                    }
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    // -----------------------------------------------------------------------
    // Download-complete notification
    // -----------------------------------------------------------------------

    private fun createDownloadNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                DOWNLOAD_CHANNEL_ID,
                "Download Complete",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies when a video download finishes"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun showDownloadCompleteNotification(fileName: String) {
        // Open Downloads folder when notification is tapped
        val openIntent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, openIntent,
            android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bubble_notification)
            .setContentTitle("✅ Download Complete")
            .setContentText(fileName)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        getSystemService(NotificationManager::class.java)
            .notify(DOWNLOAD_NOTIF_ID, notification)
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
