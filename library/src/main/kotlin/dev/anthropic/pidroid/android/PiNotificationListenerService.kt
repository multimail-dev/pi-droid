package dev.anthropic.pidroid.android

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * NotificationListenerService that captures notifications for the agent.
 *
 * The user must grant access in Settings → Apps → Special App Access →
 * Notification Access for this service to receive callbacks.
 *
 * Active notifications are held in memory and accessed by [NotificationAccessor].
 */
class PiNotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        synchronized(lock) {
            notifications[sbn.key] = sbn
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        synchronized(lock) {
            notifications.remove(sbn.key)
        }
    }

    override fun onListenerConnected() {
        synchronized(lock) {
            notifications.clear()
            getActiveNotifications()?.forEach { sbn ->
                notifications[sbn.key] = sbn
            }
        }
    }

    companion object {
        private val lock = Any()
        internal val notifications: MutableMap<String, StatusBarNotification> = mutableMapOf()

        /** Exposed as a dedicated map for tests to inject fake notifications. */
        val activeNotifications: MutableMap<String, StatusBarNotification> get() = notifications

        /** Get a snapshot of current active notifications (thread-safe copy). */
        fun getNotificationSnapshot(): List<StatusBarNotification> {
            synchronized(lock) {
                return notifications.values.toList()
            }
        }
    }
}
