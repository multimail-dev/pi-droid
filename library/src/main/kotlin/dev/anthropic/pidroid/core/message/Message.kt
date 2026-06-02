package dev.anthropic.pidroid.core.message

import dev.anthropic.pidroid.core.model.StopReason
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A message in the agent conversation.
 *
 * Sealed hierarchy maps to Pi's message model:
 * - User: prompt from the user
 * - Assistant: response from the LLM (may contain text, tool calls, thinking)
 * - ToolResult: result of executing a tool call
 * - System: system prompt (injected, not user-visible)
 *
 * Implements [AgentMessage] so that core message types participate in the
 * extensible message system. Custom (non-LLM) messages implement [AgentMessage]
 * directly without extending this sealed class.
 */
@Serializable
sealed class Message : AgentMessage {
    abstract override val role: Role

    @Serializable
    @SerialName("user")
    data class User(
        val content: List<ContentBlock>,
    ) : Message() {
        override val role: Role get() = Role.USER

        constructor(text: String) : this(listOf(ContentBlock.Text(text)))
    }

    @Serializable
    @SerialName("assistant")
    data class Assistant(
        val content: List<ContentBlock>,
        @SerialName("stop_reason")
        val stopReason: StopReason? = null,
    ) : Message() {
        override val role: Role get() = Role.ASSISTANT

        /** Extract all tool calls from this assistant message */
        val toolCalls: List<ContentBlock.ToolCall>
            get() = content.filterIsInstance<ContentBlock.ToolCall>()

        /** Extract concatenated text from all text blocks */
        val text: String
            get() = content.filterIsInstance<ContentBlock.Text>()
                .joinToString("") { it.text }
    }

    @Serializable
    @SerialName("tool_result")
    data class ToolResult(
        @SerialName("tool_call_id")
        val toolCallId: String,
        val content: String,
        @SerialName("is_error")
        val isError: Boolean = false,
        @SerialName("tool_name")
        val toolName: String? = null,
    ) : Message() {
        override val role: Role get() = Role.TOOL
    }

    @Serializable
    @SerialName("system")
    data class System(
        val content: String,
    ) : Message() {
        override val role: Role get() = Role.USER // system prompts sent as user role
    }
}
