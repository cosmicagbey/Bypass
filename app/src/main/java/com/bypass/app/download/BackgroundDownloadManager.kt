package com.bypass.app.download

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.webkit.*
import android.widget.Toast
import com.bypass.app.data.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Handles completely silent, background video downloading.
 *
 * 1. Tries high-speed direct stream extractors (TikWM, Cobalt) to bypass website ads entirely.
 * 2. Falls back to a headless (invisible) WebView automation with strict ad-blocking,
 *    DOM injection, and download link interception.
 * 3. Gracefully falls back to the mini browser if human verification (Captcha) is required.
 */
object BackgroundDownloadManager {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeHeadlessWebView: WebView? = null

    // Known ad networks to drop silently in headless mode
    private val AD_DOMAINS = listOf(
        "googleads", "doubleclick", "googlesyndication", "adservice",
        "adsterra", "popads", "monetag", "clickadu", "propellerads",
        "yandex", "adnxs", "exoclick", "trafficjunky", "ad-delivery"
    )

    /**
     * Main entry point to download a video silently in the background.
     */
    suspend fun startDownload(
        context: Context,
        platform: Platform,
        clipboardUrl: String,
        onFallbackToBrowser: () -> Unit
    ) {
        if (clipboardUrl.isBlank() || !clipboardUrl.startsWith("http")) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "No valid link found in clipboard! Copy a link first.", Toast.LENGTH_LONG).show()
            }
            return
        }

        withContext(Dispatchers.Main) {
            Toast.makeText(context, "⚡ Fetching video in background...", Toast.LENGTH_SHORT).show()
        }

        // --- Step 1: Try direct high-speed stream extractors (Instant & 100% ad-free) ---
        val directDownloadUrl = withContext(Dispatchers.IO) {
            resolveDirectStreamUrl(clipboardUrl, platform.name)
        }

        if (!directDownloadUrl.isNullOrBlank()) {
            withContext(Dispatchers.Main) {
                enqueueDownload(context, directDownloadUrl, platform.name)
            }
            return
        }

        // --- Step 2: Headless background WebView automation (Ad-blocked) ---
        withContext(Dispatchers.Main) {
            startHeadlessAutomation(context, platform, clipboardUrl, onFallbackToBrowser)
        }
    }

    // -----------------------------------------------------------------------
    // Direct Stream APIs (Zero Web Ads, ~300ms response)
    // -----------------------------------------------------------------------

    private fun resolveDirectStreamUrl(videoUrl: String, platformName: String): String? {
        val lower = platformName.lowercase()
        return try {
            if (lower.contains("tiktok")) {
                resolveTikTokStream(videoUrl) ?: resolveCobaltStream(videoUrl)
            } else {
                resolveCobaltStream(videoUrl)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Resolves TikTok video via TikWM API (Fast, ad-free, no watermark).
     */
    private fun resolveTikTokStream(videoUrl: String): String? {
        return try {
            val endpoint = "https://www.tikwm.com/api/?url=${URLEncoder.encode(videoUrl, "UTF-8")}"
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 7000
                readTimeout = 7000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)")
            }
            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use(BufferedReader::readText)
                val json = JSONObject(response)
                if (json.optInt("code", -1) == 0) {
                    val data = json.optJSONObject("data")
                    return data?.optString("play")?.takeIf { it.isNotBlank() }
                        ?: data?.optString("wmplay")?.takeIf { it.isNotBlank() }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Resolves X (Twitter), Facebook, Instagram, YouTube streams via Cobalt API instances.
     */
    private fun resolveCobaltStream(videoUrl: String): String? {
        val endpoints = listOf(
            "https://api.cobalt.tools/api/json",
            "https://cobalt-api.kwiatekm.tokyo/api/json"
        )
        for (endpoint in endpoints) {
            try {
                val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 7000
                    readTimeout = 7000
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                    doOutput = true
                }
                val payload = JSONObject().apply {
                    put("url", videoUrl)
                    put("videoQuality", "720")
                }
                OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }

                if (conn.responseCode == 200) {
                    val response = conn.inputStream.bufferedReader().use(BufferedReader::readText)
                    val json = JSONObject(response)
                    val url = json.optString("url")
                    if (url.isNotBlank()) return url
                }
            } catch (_: Exception) {}
        }
        return null
    }

    // -----------------------------------------------------------------------
    // Headless WebView Automation (Invisible & Ad-Blocked)
    // -----------------------------------------------------------------------

    @SuppressLint("SetJavaScriptEnabled")
    private fun startHeadlessAutomation(
        context: Context,
        platform: Platform,
        clipboardUrl: String,
        onFallbackToBrowser: () -> Unit
    ) {
        destroyActiveHeadless()

        val webView = WebView(context.applicationContext)
        activeHeadlessWebView = webView

        var hasFoundDownload = false

        // Timeout handler (14 seconds max before offering fallback to browser)
        val timeoutRunnable = Runnable {
            if (!hasFoundDownload) {
                destroyActiveHeadless()
                Toast.makeText(context, "Verification or extra step needed. Opening downloader...", Toast.LENGTH_SHORT).show()
                onFallbackToBrowser()
            }
        }
        mainHandler.postDelayed(timeoutRunnable, 14000)

        // JavaScript interface to receive download link from DOM mutation observer
        class HeadlessBridge {
            @JavascriptInterface
            fun onVideoFound(downloadUrl: String) {
                if (hasFoundDownload || downloadUrl.isBlank()) return
                hasFoundDownload = true
                mainHandler.removeCallbacks(timeoutRunnable)
                mainHandler.post {
                    enqueueDownload(context, downloadUrl, platform.name)
                    destroyActiveHeadless()
                }
            }
        }

        webView.addJavascriptInterface(HeadlessBridge(), "BypassBridge")

        // Cookie management
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)

        // Strict security & ad suppression
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            // Block popup windows entirely (kills pop-unders / ad tabs)
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mediaPlaybackRequiresUserGesture = true
            userAgentString = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }

        // Intercept native download trigger
        webView.setDownloadListener { downloadUrl, _, _, _, _ ->
            if (!hasFoundDownload) {
                hasFoundDownload = true
                mainHandler.removeCallbacks(timeoutRunnable)
                mainHandler.post {
                    enqueueDownload(context, downloadUrl, platform.name)
                    destroyActiveHeadless()
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val url = request?.url?.toString()?.lowercase() ?: ""
                // Drop known ad network scripts before they load
                for (ad in AD_DOMAINS) {
                    if (url.contains(ad)) {
                        return WebResourceResponse("text/plain", "UTF-8", null)
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val targetUrl = request?.url?.toString() ?: ""
                val lower = targetUrl.lowercase()

                // If navigation is directly targeting a video file, intercept it!
                if (lower.contains(".mp4") || lower.contains(".mov") || lower.contains(".webm") || lower.contains("download=true")) {
                    if (!hasFoundDownload) {
                        hasFoundDownload = true
                        mainHandler.removeCallbacks(timeoutRunnable)
                        enqueueDownload(context, targetUrl, platform.name)
                        destroyActiveHeadless()
                    }
                    return true
                }

                // Block external ad redirects
                val downloaderHost = Uri.parse(platform.downloaderUrl).host ?: ""
                val targetHost = request?.url?.host ?: ""
                if (targetHost.isNotEmpty() && !targetHost.contains(downloaderHost)) {
                    return true // Drop external ad redirects
                }

                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                injectAutoSubmitScript(view, clipboardUrl)
            }
        }

        webView.loadUrl(platform.downloaderUrl)
    }

    /**
     * Injects JavaScript into the downloader website to paste the URL, click download,
     * and observe DOM for the generated media link.
     */
    private fun injectAutoSubmitScript(view: WebView?, clipboardUrl: String) {
        val escapedUrl = clipboardUrl.replace("'", "\\'").replace("\"", "\\\"")
        val js = """
            (function() {
                try {
                    var input = document.querySelector('input[type="text"]') ||
                                document.querySelector('input[type="url"]') ||
                                document.querySelector('#url') ||
                                document.querySelector('input[name="url"]') ||
                                document.querySelector('input[placeholder*="link" i]') ||
                                document.querySelector('input[placeholder*="url" i]');
                    if (input && !input.dataset.bypassInjected) {
                        input.dataset.bypassInjected = "true";
                        input.value = '$escapedUrl';
                        input.dispatchEvent(new Event('input', { bubbles: true }));
                        input.dispatchEvent(new Event('change', { bubbles: true }));

                        setTimeout(function() {
                            var btn = document.querySelector('button[type="submit"]') ||
                                      document.querySelector('#send') ||
                                      document.querySelector('.btn-download') ||
                                      document.querySelector('button.submit') ||
                                      document.querySelector('input[type="submit"]');
                            if (btn) {
                                btn.click();
                            } else if (input.form) {
                                input.form.submit();
                            }
                        }, 400);
                    }

                    // Watch for download buttons or video links appearing in DOM
                    var observer = new MutationObserver(function() {
                        var links = document.querySelectorAll('a[href*=".mp4"], a.download-btn, a[download], a.btn-download, a[href*="token="]');
                        for (var i = 0; i < links.length; i++) {
                            var href = links[i].href;
                            if (href && href.startsWith('http') && !href.includes('google') && !href.includes('facebook.com/sharer')) {
                                window.BypassBridge.onVideoFound(href);
                                observer.disconnect();
                                break;
                            }
                        }
                    });
                    observer.observe(document.body, { childList: true, subtree: true });
                } catch(e) {}
            })();
        """.trimIndent()

        view?.evaluateJavascript(js, null)
    }

    private fun destroyActiveHeadless() {
        activeHeadlessWebView?.let {
            try {
                it.stopLoading()
                it.removeJavascriptInterface("BypassBridge")
                it.destroy()
            } catch (_: Exception) {}
        }
        activeHeadlessWebView = null
    }

    // -----------------------------------------------------------------------
    // Native Android DownloadManager Dispatch
    // -----------------------------------------------------------------------

    private fun enqueueDownload(context: Context, downloadUrl: String, platformName: String) {
        try {
            val fileName = "Bypass_${platformName}_${System.currentTimeMillis()}.mp4"
            val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
                setMimeType("video/mp4")
                addRequestHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13; Mobile)")
                val cookies = CookieManager.getInstance().getCookie(downloadUrl)
                if (!cookies.isNullOrBlank()) {
                    addRequestHeader("Cookie", cookies)
                }
                setTitle(fileName)
                setDescription("Downloading $platformName video via Bypass...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            }

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)

            Toast.makeText(context, "📥 Downloading $platformName video to Downloads!", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to start download: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}
