package dev.anthropic.pidroid.llm

import dev.anthropic.pidroid.core.message.Message
import dev.anthropic.pidroid.tools.ToolDefinition
import kotlinx.coroutines.flow.Flow

/**
 * Interface for LLM providers.
 *
 * Implementations handle the specifics of each provider's API (request format,
 * SSE parsing, auth). The [stream] method returns a Flow that NEVER throws —
 * all errors are encoded as [AssistantMessageEvent.Error] events.
 *
 * ## Contract (from Pi's StreamFn)
 * - The returned Flow emits exactly one [AssistantMessageEvent.Start]
 * - Followed by zero or more delta events ([TextDelta], [ThinkingDelta], [ToolCallDelta])
 * - Terminated by exactly one [AssistantMessageEvent.Done] or [AssistantMessageEvent.Error]
 * - The Flow completes (not infinite) after the terminal event
 * - Cancellation of the Flow collector cancels the underlying HTTP connection
 */
interface LlmProvider {
    /** Provider name (e.g., "anthropic", "openai") */
    val name: String

    /**
     * Stream a response from the LLM.
     *
     * @param messages Conversation history to send
     * @param tools Available tools (sent as tool definitions in the request)
     * @param config Provider-specific configuration (model, temperature, etc.)
     * @return Flow of streaming events — never throws
     */
    fun stream(
        messages: List<Message>,
        tools: List<ToolDefinition>,
        config: LlmConfig,
    ): Flow<AssistantMessageEvent>
}
