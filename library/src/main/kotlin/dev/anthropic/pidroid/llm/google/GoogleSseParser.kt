package dev.anthropic.pidroid.llm.google

import dev.anthropic.pidroid.core.message.ContentBlock
import dev.anthropic.pidroid.core.message.Message
import dev.anthropic.pidroid.core.model.StopReason
import dev.anthropic.pidroid.llm.AssistantMessageEvent
import dev.anthropic.pidroid.llm.TokenUsage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parses Google Generative AI SSE stream into [AssistantMessageEvent]s.
 *
 * Google's SSE format (with `?alt=sse`):
 * - Each `data:` line contains a JSON object with `candidates[]` and `usageMetadata`
 * - No named event types (unlike Anthropic) — all data lines are the same format
 * - Parts: `{ text: "..." }`, `{ functionCall: { name, args } }`, `{ thought: true, text: "..." }`
 * - `finishReason` on `candidates[0]`: STOP, MAX_TOKENS, etc.
 * - Tool calls arrive as complete objects per part (not incremental like OpenAI)
 */
internal class GoogleSseParser {
    private val json = Json { ignoreUnknownKeys = true }
    private val textAccumulator = StringBuilder()
    private val thinkingAccumulator = StringBuilder()
    private val toolCalls = mutableListOf<ContentBlock.ToolCall>()
    private var toolCallCounter = 0
    private var usage: TokenUsage? = null
    private var finishReason: StopReason? = null
    private var started = false

    /**
     * Parse a single SSE data line.
     *
     * @param data The raw data payload (after "data: " prefix)
     * @return Parsed event(s), or empty if this chunk doesn't produce user-visible events
     */
    fun parse(data: String): List<AssistantMessageEvent> {
        if (data.isBlank()) return emptyList()

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

        // Emit Start on first chunk
        if (!started) {
            started = true
            events.add(AssistantMessageEvent.Start(Message.Assistant(content = emptyList())))
        }

        // Extract usage metadata
        chunk["usageMetadata"]?.jsonObject?.let { usageObj ->
            val inputTokens = usageObj["promptTokenCount"]?.jsonPrimitive?.intOrNull ?: 0
            val outputTokens = usageObj["candidatesTokenCount"]?.jsonPrimitive?.intOrNull ?: 0
            usage = TokenUsage(inputTokens, outputTokens)
        }

        // Process candidates
        val candidates = chunk["candidates"]?.jsonArray ?: return events
        if (candidates.isEmpty()) return events
        val candidate = candidates[0].jsonObject

        // Check finish reason
        candidate["finishReason"]?.jsonPrimitive?.contentOrNull?.let { reason ->
            finishReason = when (reason) {
                "STOP" -> StopReason.STOP
                "MAX_TOKENS" -> StopReason.LENGTH
                else -> StopReason.ERROR
            }
        }

        // Process content parts
        val content = candidate["content"]?.jsonObject
        val parts = content?.get("parts")?.jsonArray ?: return events

        for (partElement in parts) {
            val part = partElement.jsonObject

            // Check for function call
            val functionCall = part["functionCall"]?.jsonObject
            if (functionCall != null) {
                val name = functionCall["name"]?.jsonPrimitive?.contentOrNull ?: ""
                val args = functionCall["args"]?.jsonObject ?: JsonObject(emptyMap())
                val id = "google-tc-${toolCallCounter++}"

                toolCalls.add(ContentBlock.ToolCall(id = id, name = name, arguments = args))
                // Override finish reason when tool calls are present
                finishReason = StopReason.TOOL_USE

                events.add(
                    AssistantMessageEvent.ToolCallDelta(
                        id = id,
                        name = name,
                        argumentsDelta = json.encodeToString(JsonObject.serializer(), args),
                    )
                )
                continue
            }

            // Check for text (may be thinking or regular text)
            val text = part["text"]?.jsonPrimitive?.contentOrNull
            if (text != null) {
                val isThinking = part["thought"]?.jsonPrimitive?.booleanOrNull == true
                if (isThinking) {
                    thinkingAccumulator.append(text)
                    events.add(AssistantMessageEvent.ThinkingDelta(text))
                } else {
                    textAccumulator.append(text)
                    events.add(AssistantMessageEvent.TextDelta(text))
                }
            }
        }

        // If finishReason is set in this chunk, emit Done
        if (finishReason != null) {
            events.add(buildDoneEvent())
        }

        return events
    }

    /**
     * Build the Done event when the stream ends without a finishReason
     * (e.g., connection closed). Only call if no Done has been emitted yet.
     */
    fun buildStreamEndEvent(): AssistantMessageEvent {
        return buildDoneEvent()
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
        if (thinkingAccumulator.isNotEmpty()) {
            blocks.add(ContentBlock.Thinking(thinkingAccumulator.toString()))
        }
        if (textAccumulator.isNotEmpty()) {
            blocks.add(ContentBlock.Text(textAccumulator.toString()))
        }
        blocks.addAll(toolCalls)
        return Message.Assistant(content = blocks, stopReason = reason)
    }

    /** Reset parser state for reuse */
    fun reset() {
        textAccumulator.clear()
        thinkingAccumulator.clear()
        toolCalls.clear()
        toolCallCounter = 0
        usage = null
        finishReason = null
        started = false
    }
}
