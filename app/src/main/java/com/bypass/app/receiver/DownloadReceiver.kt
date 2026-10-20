package com.bypass.app.receiver

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.bypass.app.R

/**
 * Manifest-registered broadcast receiver that listens for DownloadManager completions.
 * Survives even after WebViewActivity or FloatingBubbleService are closed or dismissed.
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

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = dm.query(query) ?: return

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
                if (desc != "Downloading via Bypass...") return

                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    showCompletionNotification(context, title, localUriStr)
                }
            }
        } catch (_: Exception) {
        } finally {
            cursor.close()
        }
    }

    private fun showCompletionNotification(context: Context, title: String, localUriStr: String?) {
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

        // Tapping notification opens the downloaded video file or Downloads folder
        val openIntent = if (!localUriStr.isNullOrBlank()) {
            val fileUri = Uri.parse(localUriStr)
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(fileUri, "video/*")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
