package dev.anthropic.pidroid.tools

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * How a tool is executed by the runtime.
 *
 * Replaces the old `requiresBridge` boolean from the sidecar architecture.
 */
@Serializable
enum class ToolExecutionMode {
    /** Executed natively in-process via a ToolHandler */
    @SerialName("native")
    NATIVE,

    /** Requires Android system service interaction (ContentProvider, etc.) */
    @SerialName("android_service")
    ANDROID_SERVICE,

    /** Requires launching an Activity and awaiting result */
    @SerialName("activity_result")
    ACTIVITY_RESULT,
}
