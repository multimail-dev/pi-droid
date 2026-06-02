package dev.anthropic.pidroid

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import dev.anthropic.pidroid.tools.PermissionChecker

/**
 * Real Android implementation of [PermissionChecker].
 *
 * Queries the actual Android permission state and special access grants.
 */
class AndroidPermissionChecker(private val context: Context) : PermissionChecker {

    override fun isPermissionGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    override fun isNotificationListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ) ?: return false

        val componentName = ComponentName(context, "dev.anthropic.pidroid.android.PiNotificationListenerService")
        return flat.contains(componentName.flattenToString())
    }

    override fun isUsageStatsAccessGranted(): Boolean {
        // UsageStatsManager access check — not in MVP scope
        return false
    }
}
