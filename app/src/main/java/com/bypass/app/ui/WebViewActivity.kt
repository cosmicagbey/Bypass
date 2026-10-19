package com.bypass.app.ui

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.webkit.*
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.bypass.app.R

/**
 * A bottom-sheet style Activity hosting a mini WebView.
 * It loads the downloader URL and, once the page is ready, injects
 * the clipboard URL into the first visible text input on the page.
 * It also hooks into Android's DownloadManager to seamlessly handle video downloads.
 */
class WebViewActivity : ComponentActivity() {

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_PLATFORM_NAME = "extra_platform_name"
        const val EXTRA_CLIPBOARD_URL = "extra_clipboard_url"
    }

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webview)

        val url = intent.getStringExtra(EXTRA_URL) ?: run { finish(); return }
        val platformName = intent.getStringExtra(EXTRA_PLATFORM_NAME) ?: "Downloader"
        val clipboardUrl = intent.getStringExtra(EXTRA_CLIPBOARD_URL) ?: ""

        // Dim background tap dismisses bottom sheet
        findViewById<View>(R.id.dim_background).setOnClickListener { finish() }

        // Toolbar
        findViewById<TextView>(R.id.tv_platform_title).text = platformName
        findViewById<ImageButton>(R.id.btn_close).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btn_refresh).setOnClickListener { webView.reload() }
        findViewById<ImageButton>(R.id.btn_open_browser).setOnClickListener {
            val targetUrl = webView.url ?: url
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
            userAgentString = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }

        // Download handling via Android DownloadManager
        webView.setDownloadListener { downloadUrl, userAgent, contentDisposition, mimeType, _ ->
            try {
                val fileName = URLUtil.guessFileName(downloadUrl, contentDisposition, mimeType)
                val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
                    setMimeType(mimeType)
                    addRequestHeader("User-Agent", userAgent)
                    val cookies = CookieManager.getInstance().getCookie(downloadUrl)
                    if (cookies != null) {
                        addRequestHeader("Cookie", cookies)
                    }
                    setDescription("Downloading via Bypass...")
                    setTitle(fileName)
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                }
                val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                downloadManager.enqueue(request)
                Toast.makeText(this, "Downloading: $fileName", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                // If download manager fails, open in system browser as fallback
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)))
                } catch (ex: Exception) {
                    Toast.makeText(this, "Download error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, pageUrl: String) {
                super.onPageFinished(view, pageUrl)
                if (clipboardUrl.isNotBlank()) {
                    injectUrl(clipboardUrl)
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme?.lowercase() ?: ""
                return if (scheme == "http" || scheme == "https") {
                    false
                } else {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, request.url))
                    } catch (_: Exception) {}
                    true
                }
            }
        }

        webView.loadUrl(url)
    }

    /**
     * Injects [videoUrl] into the first text/url input on the page via JavaScript.
     * Triggers 'input' and 'change' events so modern web frameworks detect the value.
     */
    private fun injectUrl(videoUrl: String) {
        val escaped = videoUrl
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "")
            .replace("\r", "")
        val js = """
            (function() {
                var inputs = document.querySelectorAll('input[type="text"], input[type="url"], input:not([type])');
                for (var i = 0; i < inputs.length; i++) {
                    var el = inputs[i];
                    if (el.offsetParent !== null) { // visible input
                        var nativeInputValueSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
                        if (nativeInputValueSetter) {
                            nativeInputValueSetter.call(el, '$escaped');
                        } else {
                            el.value = '$escaped';
                        }
                        el.dispatchEvent(new Event('input', { bubbles: true }));
                        el.dispatchEvent(new Event('change', { bubbles: true }));
                        break;
                    }
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

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
