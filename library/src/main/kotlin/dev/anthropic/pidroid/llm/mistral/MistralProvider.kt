package dev.anthropic.pidroid.llm.mistral

import dev.anthropic.pidroid.core.message.ContentBlock
import dev.anthropic.pidroid.core.message.Message
import dev.anthropic.pidroid.llm.AssistantMessageEvent
import dev.anthropic.pidroid.llm.LlmConfig
import dev.anthropic.pidroid.llm.LlmProvider
import dev.anthropic.pidroid.llm.openai.OpenAiCompletionsProvider
import dev.anthropic.pidroid.llm.registry.OpenAiCompat
import dev.anthropic.pidroid.tools.ToolDefinition
import kotlinx.coroutines.flow.Flow
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Mistral Conversations API provider.
 *
 * Thin wrapper around [OpenAiCompletionsProvider] that applies Mistral-specific
 * compat settings and normalizes tool call IDs to meet Mistral's 9-character limit.
 *
 * ## Mistral compat differences from OpenAI
 * - `max_tokens` field name (not `max_completion_tokens`)
 * - Tool result messages must include `name` field (`requiresToolResultName`)
 * - No `stream_options` support (`supportsUsageInStreaming = false`)
 * - Uses `system` role, not `developer` (`supportsDeveloperRole = false`)
 * - No `strict` mode in tool definitions (`supportsStrictMode = false`)
 */
class MistralProvider(
    httpClient: OkHttpClient = defaultClient(),
) : LlmProvider {
    override val name: String = "mistral"

    private val delegate = OpenAiCompletionsProvider(httpClient)

    override fun stream(
        messages: List<Message>,
        tools: List<ToolDefinition>,
        config: LlmConfig,
    ): Flow<AssistantMessageEvent> {
        val normalizedMessages = normalizeToolCallIds(messages)
        val mistralConfig = config.copy(compat = MISTRAL_COMPAT)
        return delegate.stream(normalizedMessages, tools, mistralConfig)
    }

    companion object {
        /** Maximum length for Mistral tool call IDs. */
        const val MISTRAL_TOOL_CALL_ID_LENGTH = 9

        /**
         * Mistral-specific compat settings.
         * Values mirror the reference implementation in pi-ai's `mistral.ts`.
         */
        val MISTRAL_COMPAT = OpenAiCompat(
            maxTokensField = "max_tokens",
            requiresToolResultName = true,
            supportsUsageInStreaming = false,
            supportsDeveloperRole = false,
            supportsStrictMode = false,
        )

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

/**
 * Normalize tool call IDs in a message list for Mistral's 9-character limit.
 *
 * Mistral requires tool call IDs to be at most [MistralProvider.MISTRAL_TOOL_CALL_ID_LENGTH]
 * characters. This function builds a consistent mapping so that:
 * - Tool call IDs in [ContentBlock.ToolCall] are truncated
 * - Matching tool result IDs in [Message.ToolResult] are truncated identically
 *
 * IDs that are already within the limit are left unchanged.
 */
internal fun normalizeToolCallIds(messages: List<Message>): List<Message> {
    val idMap = mutableMapOf<String, String>()

    for (msg in messages) {
        when (msg) {
            is Message.Assistant -> {
                for (block in msg.content) {
                    if (block is ContentBlock.ToolCall) {
                        idMap.getOrPut(block.id) { truncateToolCallId(block.id) }
                    }
                }
            }
            is Message.ToolResult -> {
                idMap.getOrPut(msg.toolCallId) { truncateToolCallId(msg.toolCallId) }
            }
            else -> {}
        }
    }

    if (idMap.all { (k, v) -> k == v }) return messages

    return messages.map { msg ->
        when (msg) {
            is Message.Assistant -> {
                val newContent = msg.content.map { block ->
                    if (block is ContentBlock.ToolCall) {
                        val newId = idMap[block.id] ?: block.id
                        if (newId != block.id) block.copy(id = newId) else block
                    } else {
                        block
                    }
                }
                if (newContent !== msg.content) msg.copy(content = newContent) else msg
            }
            is Message.ToolResult -> {
                val newId = idMap[msg.toolCallId] ?: msg.toolCallId
                if (newId != msg.toolCallId) msg.copy(toolCallId = newId) else msg
            }
            else -> msg
        }
    }
}

/**
 * Truncate a tool call ID to [MistralProvider.MISTRAL_TOOL_CALL_ID_LENGTH] characters.
 */
internal fun truncateToolCallId(id: String): String {
    return if (id.length <= MistralProvider.MISTRAL_TOOL_CALL_ID_LENGTH) {
        id
    } else {
        id.take(MistralProvider.MISTRAL_TOOL_CALL_ID_LENGTH)
    }
}
