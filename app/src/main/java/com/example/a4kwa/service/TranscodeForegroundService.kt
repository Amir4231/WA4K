package com.example.a4kwa.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.a4kwa.MainActivity
import com.example.a4kwa.R

class TranscodeForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "transcode_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_CANCEL = "com.example.a4kwa.action.CANCEL_TRANSCODE"
        private const val TAG = "TranscodeService"
        const val EXTRA_IS_FOREGROUND = "is_foreground"

        fun createNotification(context: Context, progress: Int, max: Int, statusText: String): Notification {
            createChannel(context)
            val cancelIntent = PendingIntent.getBroadcast(
                context, 0, Intent(ACTION_CANCEL), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val contentIntent = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("Rendering Status")
                .setContentText(statusText)
                .setSmallIcon(android.R.drawable.ic_menu_share)
                .setOngoing(true)
                .setProgress(max, progress, max == 0)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelIntent)
                .setContentIntent(contentIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        }

        private fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID, "Transcoding", NotificationManager.IMPORTANCE_LOW
                ).apply { description = "Video processing progress" }
                context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra(EXTRA_IS_FOREGROUND, false) == true) {
            val notification = createNotification(this, 0, 0, "Starting...")
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_NOT_STICKY
    }

    fun updateProgress(progress: Int, max: Int, statusText: String) {
        val notification = createNotification(this, progress, max, statusText)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
