package dev.anthropic.pidroid.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.service.notification.StatusBarNotification
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Provides read access to the device notification state.
 *
 * Delegates to [PiNotificationListenerService] for active notifications
 * and [NotificationManager] for channel queries.
 */
class NotificationAccessor(private val context: Context) {

    /**
     * Get notifications optionally filtered by package and/or recency.
     *
     * @param appFilter Only include notifications from these package names (null = all)
     * @param sinceMinutes Only include notifications from the last N minutes (null = all)
     * @return JSON array of notification objects
     */
    fun getNotifications(
        appFilter: List<String>? = null,
        sinceMinutes: Int? = null,
    ): JsonArray {
        val allNotifications = PiNotificationListenerService.getNotificationSnapshot()
        val now = System.currentTimeMillis()
        val cutoff = sinceMinutes?.let { now - (it * 60_000L) }

        val filtered = allNotifications.filter { sbn ->
            val passesPackageFilter = appFilter == null || sbn.packageName in appFilter
            val passesTimeFilter = cutoff == null || sbn.postTime >= cutoff
            passesPackageFilter && passesTimeFilter
        }

        return JsonArray(filtered.map { sbn -> sbnToJson(sbn) })
    }

    /**
     * Get notification channels for a specific package.
     */
    fun getNotificationChannels(packageName: String): JsonArray {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channels = manager.notificationChannels
        return JsonArray(channels.map { channel -> channelToJson(channel) })
    }

    private fun sbnToJson(sbn: StatusBarNotification): JsonObject {
        val extras = sbn.notification.extras
        return buildJsonObject {
            put("title", extras.getString("android.title") ?: "")
            put("text", extras.getString("android.text") ?: "")
            put("package", sbn.packageName)
            put("timestamp", sbn.postTime)
            put("category", sbn.notification.category ?: "")
            put("key", sbn.key)
        }
    }

    private fun channelToJson(channel: NotificationChannel): JsonObject {
        return buildJsonObject {
            put("id", channel.id)
            put("name", channel.name.toString())
            put("importance", channel.importance)
        }
    }
}
