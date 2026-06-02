package dev.anthropic.pidroid.tools

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Grouping category for tool organization and UI display.
 */
@Serializable
enum class ToolCategory {
    @SerialName("communication")
    COMMUNICATION,

    @SerialName("calendar")
    CALENDAR,

    @SerialName("contacts")
    CONTACTS,

    @SerialName("device")
    DEVICE,

    @SerialName("memory")
    MEMORY,

    @SerialName("navigation")
    NAVIGATION,

    @SerialName("scheduling")
    SCHEDULING,
}
