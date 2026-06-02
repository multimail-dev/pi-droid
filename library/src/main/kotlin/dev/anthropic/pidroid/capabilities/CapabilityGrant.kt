package dev.anthropic.pidroid.capabilities

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A capability grant declaration from the host app.
 *
 * Capabilities are the opt-in mechanism: the host declares what Android
 * capabilities it has and is willing to expose to the agent. The tool
 * registry intersects declared capabilities with actual Android permission
 * state to determine the active tool set.
 *
 * @property capabilityId Unique identifier (Android permission string or pidroid:// URI)
 * @property granted Whether the host asserts this capability is currently active
 *   (for non-standard capabilities that can't be checked via ContextCompat)
 */
@Serializable
data class CapabilityGrant(
    @SerialName("capability_id")
    val capabilityId: String,
    val granted: Boolean = true,
) {
    companion object {
        /** NotificationListenerService enabled */
        const val CAPABILITY_NOTIFICATION_LISTENER = "pidroid://notification_listener"
        /** UsageStatsManager access */
        const val CAPABILITY_USAGE_STATS = "pidroid://usage_stats"
        /** Device state (battery, connectivity) */
        const val CAPABILITY_DEVICE_STATE = "pidroid://device_state"
        /** AlarmManager access */
        const val CAPABILITY_ALARM = "pidroid://alarm"
    }
}
