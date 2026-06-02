package dev.anthropic.pidroid.tools

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Result of executing a tool.
 *
 * Produced by [ToolHandler] implementations and converted to [Message.ToolResult]
 * for inclusion in the conversation.
 *
 * @property toolCallId The ID from the original tool call (links result to request)
 * @property content Human-readable result content
 * @property isError Whether this result represents a failure
 * @property metadata Optional structured data for audit/debugging (not sent to LLM)
 */
@Serializable
data class ToolResult(
    @SerialName("tool_call_id")
    val toolCallId: String,
    val content: String,
    @SerialName("is_error")
    val isError: Boolean = false,
    val metadata: JsonObject? = null,
)
