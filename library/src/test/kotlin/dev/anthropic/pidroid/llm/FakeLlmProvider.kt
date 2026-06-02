package dev.anthropic.pidroid.llm

import dev.anthropic.pidroid.core.message.Message
import dev.anthropic.pidroid.tools.ToolDefinition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Fake LLM provider for testing.
 *
 * Emits canned event sequences without making any network calls.
 * Test code sets [responses] to control what each call to [stream] returns.
 */
class FakeLlmProvider(
    override val name: String = "fake",
) : LlmProvider {
    /** Queue of response sequences. Each call to stream() consumes the next entry. */
    val responses = mutableListOf<List<AssistantMessageEvent>>()

    /** Record of calls made to stream() */
    val calls = mutableListOf<StreamCall>()

    override fun stream(
        messages: List<Message>,
        tools: List<ToolDefinition>,
        config: LlmConfig,
    ): Flow<AssistantMessageEvent> = flow {
        calls.add(StreamCall(messages, tools, config))
        val events = if (responses.isNotEmpty()) {
            responses.removeFirst()
        } else {
            error("FakeLlmProvider: no response queued")
        }
        for (event in events) {
            emit(event)
        }
    }

    data class StreamCall(
        val messages: List<Message>,
        val tools: List<ToolDefinition>,
        val config: LlmConfig,
    )
}
