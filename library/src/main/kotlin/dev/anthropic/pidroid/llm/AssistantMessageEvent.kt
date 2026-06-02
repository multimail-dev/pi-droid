package dev.anthropic.pidroid.llm

import dev.anthropic.pidroid.core.message.Message
import dev.anthropic.pidroid.core.model.StopReason

/**
 * Events emitted during LLM response streaming.
 *
 * Maps to Pi's StreamFn contract: the stream NEVER throws. All failures
 * are encoded as [Error] events. Consumers process the sealed hierarchy
 * exhaustively.
 */
sealed class AssistantMessageEvent {
    /**
     * Streaming started — partial message with initial metadata.
     */
    data class Start(val partial: Message.Assistant) : AssistantMessageEvent()

    /**
     * Incremental text content delta.
     */
    data class TextDelta(val text: String) : AssistantMessageEvent()

    /**
     * Incremental thinking/chain-of-thought delta.
     */
    data class ThinkingDelta(val text: String) : AssistantMessageEvent()

    /**
     * Incremental tool call delta.
     *
     * @property id Tool call ID (set on first delta, may be null on subsequent)
     * @property name Tool name (set on first delta, may be null on subsequent)
     * @property argumentsDelta Partial JSON string to accumulate
     */
    data class ToolCallDelta(
        val id: String,
        val name: String?,
        val argumentsDelta: String,
    ) : AssistantMessageEvent()

    /**
     * Stream completed successfully.
     *
     * @property message The fully assembled assistant message
     * @property stopReason Why the model stopped
     * @property usage Token usage statistics (null if provider doesn't report)
     */
    data class Done(
        val message: Message.Assistant,
        val stopReason: StopReason,
        val usage: TokenUsage?,
    ) : AssistantMessageEvent()

    /**
     * Stream encountered an error.
     *
     * The partial message contains whatever was accumulated before the error.
     * The stream ends after this event.
     */
    data class Error(
        val partial: Message.Assistant,
        val error: String,
        val stopReason: StopReason = StopReason.ERROR,
    ) : AssistantMessageEvent()
}

/**
 * Token usage statistics from the provider.
 */
data class TokenUsage(
    val inputTokens: Int,
    val outputTokens: Int,
)
