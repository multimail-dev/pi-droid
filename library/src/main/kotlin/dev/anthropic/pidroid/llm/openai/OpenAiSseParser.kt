package dev.anthropic.pidroid.llm.openai

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
 * Parses OpenAI's chat completion SSE stream into [AssistantMessageEvent]s.
 *
 * OpenAI uses a single event type with `data:` payload containing
 * `chat.completion.chunk` objects. The stream ends with `data: [DONE]`.
 */
internal class OpenAiSseParser {
    private val json = Json { ignoreUnknownKeys = true }
    private val textAccumulator = StringBuilder()
    private val toolCallArgs = mutableMapOf<Int, StringBuilder>()
    private val toolCallIds = mutableMapOf<Int, String>()
    private val toolCallNames = mutableMapOf<Int, String>()
    private var usage: TokenUsage? = null
    private var finishReason: StopReason? = null
    private var started = false

    /**
     * Parse a single SSE data line.
     *
     * @param data The raw data payload (after "data: " prefix, before [DONE])
     * @return Parsed event(s), or empty if this chunk doesn't produce user-visible events
     */
    fun parse(data: String): List<AssistantMessageEvent> {
        if (data.isBlank()) return emptyList()
        if (data == "[DONE]") return listOf(buildDoneEvent())

        return try {
            val chunk = json.parseToJsonElement(data).jsonObject
            parseChunk(chunk)
        } catch (e: Exception) {
            listOf(
                AssistantMessageEvent.Error(
                    partial = buildCurrentMessage(),
                    error = "Parse error: ${e.message}",
                )
            )
        }
    }

    private fun parseChunk(chunk: JsonObject): List<AssistantMessageEvent> {
        val events = mutableListOf<AssistantMessageEvent>()
        val choices = chunk["choices"]?.jsonArray ?: return events

        // Extract usage if present (final chunk)
        chunk["usage"]?.jsonObject?.let { usageObj ->
            val inputTokens = usageObj["prompt_tokens"]?.jsonPrimitive?.intOrNull ?: 0
            val outputTokens = usageObj["completion_tokens"]?.jsonPrimitive?.intOrNull ?: 0
            usage = TokenUsage(inputTokens, outputTokens)
        }

        if (choices.isEmpty()) return events
        val choice = choices[0].jsonObject
        val delta = choice["delta"]?.jsonObject ?: return events

        // Emit Start on first chunk
        if (!started) {
            started = true
            events.add(AssistantMessageEvent.Start(Message.Assistant(content = emptyList())))
        }

        // Check finish_reason
        val reason = choice["finish_reason"]?.jsonPrimitive?.contentOrNull
        if (reason != null) {
            finishReason = when (reason) {
                "stop" -> StopReason.STOP
                "length" -> StopReason.LENGTH
                "tool_calls" -> StopReason.TOOL_USE
                "content_filter" -> StopReason.ERROR
                else -> StopReason.STOP
            }
        }

        // Text content delta
        delta["content"]?.jsonPrimitive?.contentOrNull?.let { text ->
            if (text.isNotEmpty()) {
                textAccumulator.append(text)
                events.add(AssistantMessageEvent.TextDelta(text))
            }
        }

        // Tool calls delta
        delta["tool_calls"]?.jsonArray?.forEach { tcElement ->
            val tc = tcElement.jsonObject
            val index = tc["index"]?.jsonPrimitive?.int ?: return@forEach

            // First time seeing this index — record id and name
            tc["id"]?.jsonPrimitive?.contentOrNull?.let { id ->
                toolCallIds[index] = id
            }
            tc["function"]?.jsonObject?.let { fn ->
                fn["name"]?.jsonPrimitive?.contentOrNull?.let { name ->
                    toolCallNames[index] = name
                }
                fn["arguments"]?.jsonPrimitive?.contentOrNull?.let { args ->
                    toolCallArgs.getOrPut(index) { StringBuilder() }.append(args)
                    events.add(
                        AssistantMessageEvent.ToolCallDelta(
                            id = toolCallIds[index] ?: "",
                            name = if (toolCallNames[index] != null && !toolCallArgs.containsKey(index))
                                toolCallNames[index] else null,
                            argumentsDelta = args,
                        )
                    )
                }
            }
        }

        return events
    }

    private fun buildDoneEvent(): AssistantMessageEvent {
        val reason = finishReason ?: StopReason.STOP
        return AssistantMessageEvent.Done(
            message = buildCurrentMessage(reason),
            stopReason = reason,
            usage = usage,
        )
    }

    private fun buildCurrentMessage(reason: StopReason? = null): Message.Assistant {
        val blocks = mutableListOf<ContentBlock>()
        if (textAccumulator.isNotEmpty()) {
            blocks.add(ContentBlock.Text(textAccumulator.toString()))
        }
        for ((index, argsBuilder) in toolCallArgs) {
            val argsStr = argsBuilder.toString()
            val argsJson = try {
                json.parseToJsonElement(argsStr).jsonObject
            } catch (_: Exception) {
                JsonObject(emptyMap())
            }
            blocks.add(
                ContentBlock.ToolCall(
                    id = toolCallIds[index] ?: "",
                    name = toolCallNames[index] ?: "",
                    arguments = argsJson,
                )
            )
        }
        return Message.Assistant(content = blocks, stopReason = reason)
    }

    /** Reset parser state for reuse */
    fun reset() {
        textAccumulator.clear()
        toolCallArgs.clear()
        toolCallIds.clear()
        toolCallNames.clear()
        usage = null
        finishReason = null
        started = false
    }
}
