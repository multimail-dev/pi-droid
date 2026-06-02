package dev.anthropic.pidroid.llm

import dev.anthropic.pidroid.core.message.ContentBlock
import dev.anthropic.pidroid.core.message.Message
import dev.anthropic.pidroid.core.model.StopReason

/**
 * Transforms conversation history for cross-provider replay.
 *
 * When switching LLM providers mid-conversation, messages from the old provider
 * may contain structures the new provider can't accept (opaque thinking signatures,
 * provider-specific tool call IDs, incomplete turns). This object rewrites the
 * history so any provider can consume it.
 *
 * Ported from pi-ai's `transform-messages.ts`.
 */
object MessageTransformer {

    private const val MISTRAL_MAX_ID_LENGTH = 9
    private const val ANTHROPIC_MAX_ID_LENGTH = 64
    private const val ANTHROPIC_ID_PREFIX = "toolu_"

    /**
     * Transform messages for replay on a (possibly different) provider.
     *
     * @param messages        Conversation history to transform
     * @param targetProvider  Provider name that will consume these messages (e.g., "anthropic", "openai")
     * @param targetApi       API variant of the target (e.g., "messages", "chat")
     * @param sourceProvider  Provider that produced the messages (null = same as target)
     * @param sourceApi       API variant of the source (null = same as target)
     * @return Transformed message list safe for the target provider
     */
    fun transform(
        messages: List<Message>,
        targetProvider: String,
        targetApi: String,
        sourceProvider: String? = null,
        sourceApi: String? = null,
    ): List<Message> {
        // Same-provider pass-through: nothing to transform
        val isCrossProvider = sourceProvider != null &&
            !sourceProvider.equals(targetProvider, ignoreCase = true)

        if (!isCrossProvider) return messages

        // Pass 1: process assistant messages — convert thinking, strip errored,
        //         normalize tool call IDs.  Build old->new ID mapping.
        val toolCallIdMap = mutableMapOf<String, String>()
        val pass1 = mutableListOf<Message>()

        for (msg in messages) {
            when (msg) {
                is Message.Assistant -> {
                    // Strip errored / aborted assistant messages entirely
                    if (msg.stopReason == StopReason.ERROR || msg.stopReason == StopReason.ABORTED) {
                        continue
                    }

                    val transformedContent = msg.content.mapNotNull { block ->
                        when (block) {
                            is ContentBlock.Thinking -> {
                                // Convert thinking to text with delimiters;
                                // drop empty thinking blocks
                                val text = block.text.trim()
                                if (text.isEmpty()) null
                                else ContentBlock.Text("<thinking>\n$text\n</thinking>")
                            }

                            is ContentBlock.ToolCall -> {
                                val newId = normalizeToolCallId(block.id, targetProvider)
                                if (newId != block.id) {
                                    toolCallIdMap[block.id] = newId
                                }
                                block.copy(id = newId)
                            }

                            is ContentBlock.Text -> block
                        }
                    }

                    // Keep the message only if it has content after transformation
                    if (transformedContent.isNotEmpty()) {
                        pass1.add(msg.copy(content = transformedContent))
                    }
                }

                else -> pass1.add(msg)
            }
        }

        // Pass 2: update tool result IDs, insert synthetic results for orphaned
        //         tool calls.
        val result = mutableListOf<Message>()
        var pendingToolCalls = mutableListOf<ContentBlock.ToolCall>()
        var existingToolResultIds = mutableSetOf<String>()

        fun flushOrphans() {
            for (tc in pendingToolCalls) {
                if (tc.id !in existingToolResultIds) {
                    result.add(
                        Message.ToolResult(
                            toolCallId = tc.id,
                            content = "Error: Tool execution was interrupted",
                            isError = true,
                        )
                    )
                }
            }
            pendingToolCalls = mutableListOf()
            existingToolResultIds = mutableSetOf()
        }

        for (msg in pass1) {
            when (msg) {
                is Message.Assistant -> {
                    // Flush any orphans from the previous assistant turn
                    flushOrphans()

                    // Track tool calls from this assistant message
                    val toolCalls = msg.content.filterIsInstance<ContentBlock.ToolCall>()
                    if (toolCalls.isNotEmpty()) {
                        pendingToolCalls = toolCalls.toMutableList()
                        existingToolResultIds = mutableSetOf()
                    }
                    result.add(msg)
                }

                is Message.ToolResult -> {
                    // Apply ID mapping if this tool result references a renamed ID
                    val mappedId = toolCallIdMap[msg.toolCallId] ?: msg.toolCallId
                    existingToolResultIds.add(mappedId)
                    if (mappedId != msg.toolCallId) {
                        result.add(msg.copy(toolCallId = mappedId))
                    } else {
                        result.add(msg)
                    }
                }

                is Message.User -> {
                    // User message breaks a tool-result sequence: flush orphans
                    flushOrphans()
                    result.add(msg)
                }

                is Message.System -> {
                    result.add(msg)
                }
            }
        }

        // Flush any trailing orphans
        flushOrphans()

        return result
    }

    /**
     * Normalize a tool call ID for the target provider.
     *
     * Different providers have different constraints:
     * - Mistral: max 9 characters
     * - Anthropic: alphanumeric + `_` + `-`, max 64 chars, starts with "toolu_"
     * - OpenAI / Google: generally permissive
     */
    internal fun normalizeToolCallId(id: String, targetProvider: String): String {
        val provider = targetProvider.lowercase()
        return when {
            provider == "mistral" -> {
                if (id.length <= MISTRAL_MAX_ID_LENGTH) id
                else id.take(MISTRAL_MAX_ID_LENGTH)
            }

            provider == "anthropic" -> {
                // Strip non-alphanumeric characters (keep _ and -)
                val cleaned = id.replace(Regex("[^a-zA-Z0-9_-]"), "")
                val truncated = if (cleaned.length > ANTHROPIC_MAX_ID_LENGTH) {
                    cleaned.take(ANTHROPIC_MAX_ID_LENGTH)
                } else {
                    cleaned
                }
                // Ensure it starts with "toolu_" if it doesn't already
                if (truncated.startsWith(ANTHROPIC_ID_PREFIX)) truncated
                else "${ANTHROPIC_ID_PREFIX}${truncated.take(ANTHROPIC_MAX_ID_LENGTH - ANTHROPIC_ID_PREFIX.length)}"
            }

            else -> id
        }
    }
}
