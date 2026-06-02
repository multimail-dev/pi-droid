package dev.anthropic.pidroid.llm.anthropic

import dev.anthropic.pidroid.core.message.ContentBlock
import dev.anthropic.pidroid.core.message.Message
import dev.anthropic.pidroid.core.model.StopReason
import dev.anthropic.pidroid.llm.AssistantMessageEvent
import dev.anthropic.pidroid.llm.TokenUsage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parses Anthropic's SSE stream into [AssistantMessageEvent]s.
 *
 * Anthropic's SSE event types:
 * - message_start: initial message metadata
 * - content_block_start: new content block (text/tool_use/thinking)
 * - content_block_delta: incremental content
 * - content_block_stop: content block complete
 * - message_delta: stop_reason + usage
 * - message_stop: stream complete
 * - error: API error
 */
internal class AnthropicSseParser {
    private val json = Json { ignoreUnknownKeys = true }
    private val contentBlocks = mutableListOf<ContentBlock>()
    private val toolCallArgs = mutableMapOf<Int, StringBuilder>()
    private val toolCallIds = mutableMapOf<Int, String>()
    private val toolCallNames = mutableMapOf<Int, String>()
    private var currentBlockIndex = 0
    private var usage: TokenUsage? = null
    private var stopReason: StopReason? = null

    /**
     * Parse a single SSE event (event type + data payload).
     *
     * @param eventType The SSE event type (e.g., "message_start")
     * @param data The JSON data payload
     * @return The parsed event, or null if this SSE event doesn't produce a user-visible event
     */
    fun parse(eventType: String, data: String): AssistantMessageEvent? {
        if (data.isBlank()) return null

        return try {
            val jsonData = json.parseToJsonElement(data).jsonObject
            when (eventType) {
                "message_start" -> parseMessageStart(jsonData)
                "content_block_start" -> parseContentBlockStart(jsonData)
                "content_block_delta" -> parseContentBlockDelta(jsonData)
                "content_block_stop" -> null // no user-visible event
                "message_delta" -> parseMessageDelta(jsonData)
                "message_stop" -> parseMessageStop()
                "error" -> parseError(jsonData)
                "ping" -> null
                else -> null
            }
        } catch (e: Exception) {
            AssistantMessageEvent.Error(
                partial = buildCurrentMessage(),
                error = "Parse error: ${e.message}",
            )
        }
    }

    private fun parseMessageStart(data: JsonObject): AssistantMessageEvent {
        val message = data["message"]?.jsonObject
        val usageObj = message?.get("usage")?.jsonObject
        if (usageObj != null) {
            val inputTokens = usageObj["input_tokens"]?.jsonPrimitive?.intOrNull ?: 0
            usage = TokenUsage(inputTokens = inputTokens, outputTokens = 0)
        }
        return AssistantMessageEvent.Start(
            partial = Message.Assistant(content = emptyList())
        )
    }

    private fun parseContentBlockStart(data: JsonObject): AssistantMessageEvent? {
        val index = data["index"]?.jsonPrimitive?.int ?: return null
        currentBlockIndex = index
        val block = data["content_block"]?.jsonObject ?: return null
        val type = block["type"]?.jsonPrimitive?.contentOrNull

        return when (type) {
            "text" -> null // wait for deltas
            "thinking" -> null // wait for deltas
            "tool_use" -> {
                val id = block["id"]?.jsonPrimitive?.contentOrNull ?: ""
                val name = block["name"]?.jsonPrimitive?.contentOrNull ?: ""
                toolCallIds[index] = id
                toolCallNames[index] = name
                toolCallArgs[index] = StringBuilder()
                AssistantMessageEvent.ToolCallDelta(id = id, name = name, argumentsDelta = "")
            }
            else -> null
        }
    }

    private fun parseContentBlockDelta(data: JsonObject): AssistantMessageEvent? {
        val index = data["index"]?.jsonPrimitive?.int ?: return null
        val delta = data["delta"]?.jsonObject ?: return null
        val type = delta["type"]?.jsonPrimitive?.contentOrNull

        return when (type) {
            "text_delta" -> {
                val text = delta["text"]?.jsonPrimitive?.contentOrNull ?: ""
                if (text.isNotEmpty()) {
                    AssistantMessageEvent.TextDelta(text)
                } else null
            }
            "thinking_delta" -> {
                val thinking = delta["thinking"]?.jsonPrimitive?.contentOrNull ?: ""
                if (thinking.isNotEmpty()) {
                    AssistantMessageEvent.ThinkingDelta(thinking)
                } else null
            }
            "input_json_delta" -> {
                val partial = delta["partial_json"]?.jsonPrimitive?.contentOrNull ?: ""
                toolCallArgs[index]?.append(partial)
                if (partial.isNotEmpty()) {
                    val id = toolCallIds[index] ?: ""
                    AssistantMessageEvent.ToolCallDelta(id = id, name = null, argumentsDelta = partial)
                } else null
            }
            else -> null
        }
    }

    private fun parseMessageDelta(data: JsonObject): AssistantMessageEvent? {
        val delta = data["delta"]?.jsonObject
        val stopReasonStr = delta?.get("stop_reason")?.jsonPrimitive?.contentOrNull
        stopReason = when (stopReasonStr) {
            "end_turn" -> StopReason.STOP
            "max_tokens" -> StopReason.LENGTH
            "tool_use" -> StopReason.TOOL_USE
            "stop_sequence" -> StopReason.STOP
            else -> null
        }

        val usageObj = data["usage"]?.jsonObject
        if (usageObj != null) {
            val outputTokens = usageObj["output_tokens"]?.jsonPrimitive?.intOrNull ?: 0
            usage = usage?.copy(outputTokens = outputTokens)
                ?: TokenUsage(inputTokens = 0, outputTokens = outputTokens)
        }
        return null
    }

    private fun parseMessageStop(): AssistantMessageEvent {
        return AssistantMessageEvent.Done(
            message = buildCurrentMessage(stopReason),
            stopReason = stopReason ?: StopReason.STOP,
            usage = usage,
        )
    }

    private fun parseError(data: JsonObject): AssistantMessageEvent {
        val error = data["error"]?.jsonObject
        val message = error?.get("message")?.jsonPrimitive?.contentOrNull
            ?: "Unknown Anthropic API error"
        return AssistantMessageEvent.Error(
            partial = buildCurrentMessage(),
            error = message,
        )
    }

    private fun buildCurrentMessage(reason: StopReason? = null): Message.Assistant {
        // Finalize any pending tool call blocks
        val finalBlocks = mutableListOf<ContentBlock>()
        finalBlocks.addAll(contentBlocks)
        for ((index, argsBuilder) in toolCallArgs) {
            val argsStr = argsBuilder.toString()
            val argsJson = try {
                json.parseToJsonElement(argsStr).jsonObject
            } catch (_: Exception) {
                JsonObject(emptyMap())
            }
            finalBlocks.add(
                ContentBlock.ToolCall(
                    id = toolCallIds[index] ?: "",
                    name = toolCallNames[index] ?: "",
                    arguments = argsJson,
                )
            )
        }
        return Message.Assistant(content = finalBlocks, stopReason = reason)
    }

    /**
     * Process accumulated text deltas into content blocks.
     * Called by the provider as text/thinking accumulates.
     */
    fun addTextBlock(text: String) {
        contentBlocks.add(ContentBlock.Text(text))
    }

    fun addThinkingBlock(text: String) {
        contentBlocks.add(ContentBlock.Thinking(text))
    }

    /** Reset parser state for reuse */
    fun reset() {
        contentBlocks.clear()
        toolCallArgs.clear()
        toolCallIds.clear()
        toolCallNames.clear()
        currentBlockIndex = 0
        usage = null
        stopReason = null
    }
}
