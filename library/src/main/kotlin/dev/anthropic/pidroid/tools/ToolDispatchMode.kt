package dev.anthropic.pidroid.tools

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Controls how a tool participates in batch dispatch.
 *
 * When a batch of tool calls contains ANY tool with [SEQUENTIAL],
 * the entire batch executes serially. Otherwise, all tools run concurrently.
 *
 * This is distinct from [ToolExecutionMode] which classifies native/android_service/activity_result.
 */
@Serializable
enum class ToolDispatchMode {
    /** Tool can execute concurrently with other tools in a batch */
    @SerialName("parallel")
    PARALLEL,

    /** Tool requires serial execution — forces entire batch to sequential */
    @SerialName("sequential")
    SEQUENTIAL,
}
