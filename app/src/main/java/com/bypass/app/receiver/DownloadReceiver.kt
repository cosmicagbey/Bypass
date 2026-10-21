package com.bypass.app.receiver

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import com.bypass.app.R
import com.bypass.app.ui.DownloadToastOverlay
import java.io.File

/**
 * Manifest-registered broadcast receiver that listens for DownloadManager completions.
 * Survives even after WebViewActivity or FloatingBubbleService are closed or dismissed.
 *
 * Automatically scans downloaded videos into Android's MediaStore so they
 * immediately appear in the user's Gallery (Google Photos, Samsung Gallery, etc.)
 * under the "Bypass" / "Movies" album and the main Videos timeline.
 *
 * Also triggers a non-intrusive 0.5 - 1.0 second on-screen notification at the
 * lower part of the screen indicating download is finished and where it was saved.
 */
class DownloadReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "bypass_download_complete_channel"
        const val NOTIF_ID = 202
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return

        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (downloadId == -1L) return

        val pendingResult = goAsync()
        var asyncHandled = false

        try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = dm.query(query)

            if (cursor == null) {
                pendingResult.finish()
                return
            }

            try {
                if (cursor.moveToFirst()) {
                    val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val titleIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)
                    val descIdx = cursor.getColumnIndex(DownloadManager.COLUMN_DESCRIPTION)
                    val uriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)

                    val status = if (statusIdx >= 0) cursor.getInt(statusIdx) else -1
                    val title = if (titleIdx >= 0) cursor.getString(titleIdx) else "Video"
                    val desc = if (descIdx >= 0) cursor.getString(descIdx) else ""
                    val localUriStr = if (uriIdx >= 0) cursor.getString(uriIdx) else null

                    // Only notify for downloads initiated by Bypass
                    if (desc != "Downloading via Bypass...") {
                        pendingResult.finish()
                        return
                    }

                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        // 1. Force index into Android MediaStore so it appears in the phone's Gallery
                        val savedLocation = indexIntoGallery(context, title, localUriStr)

                        // 2. Obtain a safe content URI for opening the video
                        val contentUri = try {
                            dm.getUriForDownloadedFile(downloadId)
                        } catch (_: Exception) {
                            null
                        }

                        // 3. Show high-priority completion notification in notification shade
                        showCompletionNotification(context, title, contentUri, localUriStr)

                        // 4. Show 0.5 - 1.0s on-screen notification at the lower part of the screen
                        asyncHandled = true
                        DownloadToastOverlay.show(
                            context = context,
                            title = "Download finished",
                            locationText = savedLocation,
                            durationMs = 950L,
                            onDismiss = {
                                try {
                                    pendingResult.finish()
                                } catch (_: Exception) {}
                            }
                        )
                    }
                }
            } finally {
                cursor.close()
            }
        } catch (_: Exception) {
        } finally {
            if (!asyncHandled) {
                try {
                    pendingResult.finish()
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Scans the downloaded video file into MediaStore so it immediately
     * shows up in the user's Gallery app under Movies/Bypass.
     * Returns a human-readable display of where the video was saved.
     */
    private fun indexIntoGallery(context: Context, title: String, localUriStr: String?): String {
        var detectedLocation: String? = null
        try {
            val candidatePaths = mutableListOf<String>()

            // Try local URI path if available
            if (!localUriStr.isNullOrBlank()) {
                val uri = Uri.parse(localUriStr)
                if (uri.scheme == "file") {
                    uri.path?.let { candidatePaths.add(it) }
                }
            }

            // Check primary Movies/Bypass directory
            val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            val bypassFile = File(moviesDir, "Bypass/$title")
            if (bypassFile.exists()) {
                candidatePaths.add(bypassFile.absolutePath)
                detectedLocation = "Movies / Bypass (Gallery)"
            }
            val directMovieFile = File(moviesDir, title)
            if (directMovieFile.exists()) {
                candidatePaths.add(directMovieFile.absolutePath)
                if (detectedLocation == null) detectedLocation = "Movies (Gallery)"
            }

            // Check Downloads directory
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val downloadFile = File(downloadsDir, title)
            if (downloadFile.exists()) {
                candidatePaths.add(downloadFile.absolutePath)
                if (detectedLocation == null) detectedLocation = "Downloads"
            }
            val downloadBypassFile = File(downloadsDir, "Bypass/$title")
            if (downloadBypassFile.exists()) {
                candidatePaths.add(downloadBypassFile.absolutePath)
                if (detectedLocation == null) detectedLocation = "Downloads / Bypass"
            }

            // Check DCIM directory
            val dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
            val dcimBypassFile = File(dcimDir, "Bypass/$title")
            if (dcimBypassFile.exists()) {
                candidatePaths.add(dcimBypassFile.absolutePath)
                if (detectedLocation == null) detectedLocation = "DCIM / Bypass (Gallery)"
            }
            val dcimFile = File(dcimDir, title)
            if (dcimFile.exists()) {
                candidatePaths.add(dcimFile.absolutePath)
                if (detectedLocation == null) detectedLocation = "DCIM (Gallery)"
            }

            val validFiles = candidatePaths.distinct().map { File(it) }.filter { it.exists() && it.length() > 0 }

            if (validFiles.isNotEmpty()) {
                val paths = validFiles.map { it.absolutePath }.toTypedArray()
                val mimeTypes = Array(paths.size) { "video/mp4" }

                // 1. MediaScannerConnection (canonical API across all Android versions)
                MediaScannerConnection.scanFile(
                    context.applicationContext,
                    paths,
                    mimeTypes
                ) { _, _ -> }

                // 2. Broadcast media scanner intent
                validFiles.forEach { file ->
                    try {
                        val scanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file))
                        context.sendBroadcast(scanIntent)
                    } catch (_: Exception) {}
                }

                // 3. MediaStore API insertion for Android 10+ (Q+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val target = validFiles.first()
                    try {
                        val resolver = context.contentResolver
                        val projection = arrayOf(MediaStore.Video.Media._ID)
                        val selection = "${MediaStore.Video.Media.DISPLAY_NAME} = ?"
                        val selectionArgs = arrayOf(target.name)
                        val exists = resolver.query(
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            projection, selection, selectionArgs, null
                        )?.use { it.count > 0 } ?: false

                        if (!exists) {
                            val values = ContentValues().apply {
                                put(MediaStore.Video.Media.DISPLAY_NAME, target.name)
                                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/Bypass")
                                put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                                put(MediaStore.Video.Media.DATE_MODIFIED, target.lastModified() / 1000)
                            }
                            resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                        }
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}

        if (!detectedLocation.isNullOrBlank()) {
            return detectedLocation
        }

        val prefs = context.getSharedPreferences("bypass_prefs", Context.MODE_PRIVATE)
        return when (prefs.getString("download_location", "movies")) {
            "downloads" -> "Downloads"
            "dcim" -> "DCIM / Bypass (Gallery)"
            else -> "Movies / Bypass (Gallery)"
        }
    }

    private fun showCompletionNotification(
        context: Context,
        title: String,
        contentUri: Uri?,
        localUriStr: String?
    ) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Bypass Downloads",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for completed video downloads"
                enableVibration(true)
            }
            nm.createNotificationChannel(channel)
        }

        // Tapping notification opens the downloaded video file or Gallery
        val openIntent = when {
            contentUri != null -> {
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(contentUri, "video/*")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            !localUriStr.isNullOrBlank() -> {
                val fileUri = Uri.parse(localUriStr)
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(fileUri, "video/*")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            else -> {
                Intent(Intent.ACTION_VIEW).apply {
                    type = "video/*"
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            title.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bubble_notification)
            .setContentTitle("✅ Download Complete")
            .setContentText(title)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        nm.notify(title.hashCode(), notification)
    }
}
