package dev.anthropic.pidroid.tools

/**
 * Interface for checking Android permission state.
 *
 * Abstracted to allow testing without Android context.
 * Real implementation uses ContextCompat.checkSelfPermission().
 */
interface PermissionChecker {
    /** Check if a standard Android permission is granted */
    fun isPermissionGranted(permission: String): Boolean

    /** Check if NotificationListenerService is enabled */
    fun isNotificationListenerEnabled(): Boolean

    /** Check if UsageStatsManager access is granted */
    fun isUsageStatsAccessGranted(): Boolean
}
