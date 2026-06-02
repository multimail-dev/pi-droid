package dev.anthropic.pidroid.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * Foreground service that keeps the agent loop alive during long-running tasks.
 *
 * Started when the agent begins a multi-turn conversation or background task.
 * Stopped when the agent goes idle. Uses `foregroundServiceType="dataSync"` which
 * is appropriate for ongoing API communication (LLM streaming).
 */
class PiAgentForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Pi Agent",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shown while the Pi agent is processing a task"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Pi Agent Active")
            .setContentText("Processing your request...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "pidroid_agent_foreground"
        private const val NOTIFICATION_ID = 9001
    }
}
