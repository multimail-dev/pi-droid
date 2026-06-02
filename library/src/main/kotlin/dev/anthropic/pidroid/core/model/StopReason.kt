package dev.anthropic.pidroid.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Reason the assistant stopped generating.
 *
 * Maps to Pi's stop reasons and covers both Anthropic and OpenAI stop signals.
 */
@Serializable
enum class StopReason {
    /** Normal completion — model finished its response */
    @SerialName("stop")
    STOP,

    /** Hit max_tokens limit */
    @SerialName("length")
    LENGTH,

    /** Model wants to use a tool */
    @SerialName("tool_use")
    TOOL_USE,

    /** Provider returned an error */
    @SerialName("error")
    ERROR,

    /** Cancelled by user or system (Job.cancel()) */
    @SerialName("aborted")
    ABORTED,
}
