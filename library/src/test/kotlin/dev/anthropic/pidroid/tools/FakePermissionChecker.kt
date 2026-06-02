package dev.anthropic.pidroid.tools

/**
 * Fake permission checker for testing.
 * All permissions are configurable via the granted sets.
 */
class FakePermissionChecker : PermissionChecker {
    val grantedPermissions = mutableSetOf<String>()
    var notificationListenerEnabled = false
    var usageStatsGranted = false

    override fun isPermissionGranted(permission: String): Boolean =
        permission in grantedPermissions

    override fun isNotificationListenerEnabled(): Boolean = notificationListenerEnabled

    override fun isUsageStatsAccessGranted(): Boolean = usageStatsGranted
}
