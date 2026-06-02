package dev.anthropic.pidroid.core.message

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * A content block within a message.
 *
 * Maps to Pi's content block model. Messages contain one or more content blocks,
 * each representing a different modality (text, tool call, thinking).
 */
@Serializable
sealed class ContentBlock {
    /**
     * Plain text content.
     */
    @Serializable
    @SerialName("text")
    data class Text(val text: String) : ContentBlock()

    /**
     * A tool call request from the assistant.
     *
     * @property id Unique identifier for this tool call (used to match results)
     * @property name Tool name from the registry
     * @property arguments Tool input as a JSON object
     */
    @Serializable
    @SerialName("tool_call")
    data class ToolCall(
        val id: String,
        val name: String,
        val arguments: JsonObject,
    ) : ContentBlock()

    /**
     * Extended thinking content (chain-of-thought visible to user).
     */
    @Serializable
    @SerialName("thinking")
    data class Thinking(val text: String) : ContentBlock()
}
