package dev.anthropic.pidroid.llm.openai

import dev.anthropic.pidroid.core.message.ContentBlock
import dev.anthropic.pidroid.core.message.Message
import dev.anthropic.pidroid.core.model.StopReason
import dev.anthropic.pidroid.llm.AssistantMessageEvent
import dev.anthropic.pidroid.llm.TokenUsage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parses OpenAI Responses API SSE stream into [AssistantMessageEvent]s.
 *
 * Unlike the Chat Completions API which uses unnamed `data:` lines,
 * the Responses API uses named event types delivered via the SSE `event:` field.
 * OkHttp's EventSourceListener receives the event type in the `type` parameter
 * of `onEvent`.
 *
 * Key event types:
 * - `response.created` — stream started
 * - `response.output_text.delta` — text delta
 * - `response.function_call_arguments.delta` — tool call arguments delta
 * - `response.reasoning_summary_text.delta` — thinking/reasoning delta
 * - `response.output_item.done` — output item finalized (text or function_call)
 * - `response.completed` — response complete with usage
 * - `response.failed` — response failed
 */
internal class ResponsesSseParser {
    private val json = Json { ignoreUnknownKeys = true }
    private val textAccumulator = StringBuilder()
    private val thinkingAccumulator = StringBuilder()
    private val toolCallArgs = mutableMapOf<String, StringBuilder>()
    private val toolCallIds = mutableMapOf<String, String>()
    private val toolCallNames = mutableMapOf<String, String>()
    private var usage: TokenUsage? = null
    private var stopReason: StopReason? = null

    /**
     * Parse a single SSE event (named event type + data payload).
     *
     * @param eventType The SSE event type (e.g., "response.created")
     * @param data The JSON data payload
     * @return The parsed event, or null if this SSE event doesn't produce a user-visible event
     */
    fun parse(eventType: String, data: String): AssistantMessageEvent? {
        if (data.isBlank()) return null

        return try {
            val jsonData = json.parseToJsonElement(data).jsonObject
            when (eventType) {
                "response.created" -> parseResponseCreated()
                "response.output_item.added" -> parseOutputItemAdded(jsonData)
                "response.content_part.added" -> null // wait for deltas
                "response.output_text.delta" -> parseTextDelta(jsonData)
                "response.function_call_arguments.delta" -> parseFunctionCallArgsDelta(jsonData)
                "response.reasoning_summary_part.added" -> null // wait for deltas
                "response.reasoning_summary_text.delta" -> parseReasoningSummaryDelta(jsonData)
                "response.reasoning_summary_part.done" -> parseReasoningSummaryPartDone()
                "response.output_item.done" -> parseOutputItemDone(jsonData)
                "response.completed" -> parseResponseCompleted(jsonData)
                "response.failed" -> parseResponseFailed(jsonData)
                "error" -> parseError(jsonData)
                else -> null
            }
        } catch (e: Exception) {
            AssistantMessageEvent.Error(
                partial = buildCurrentMessage(),
                error = "Parse error: ${e.message}",
            )
        }
    }

    private fun parseResponseCreated(): AssistantMessageEvent {
        return AssistantMessageEvent.Start(
            partial = Message.Assistant(content = emptyList())
        )
    }

    private fun parseOutputItemAdded(data: JsonObject): AssistantMessageEvent? {
        val item = data["item"]?.jsonObject ?: return null
        val type = item["type"]?.jsonPrimitive?.contentOrNull

        return when (type) {
            "function_call" -> {
                val callId = item["call_id"]?.jsonPrimitive?.contentOrNull ?: ""
                val name = item["name"]?.jsonPrimitive?.contentOrNull ?: ""
                val itemId = item["id"]?.jsonPrimitive?.contentOrNull ?: callId
                toolCallIds[itemId] = callId
                toolCallNames[itemId] = name
                toolCallArgs[itemId] = StringBuilder()
                AssistantMessageEvent.ToolCallDelta(id = callId, name = name, argumentsDelta = "")
            }
            // "message" and "reasoning" items don't produce immediate user-visible events
            else -> null
        }
    }

    private fun parseTextDelta(data: JsonObject): AssistantMessageEvent? {
        val delta = data["delta"]?.jsonPrimitive?.contentOrNull ?: return null
        if (delta.isEmpty()) return null
        textAccumulator.append(delta)
        return AssistantMessageEvent.TextDelta(delta)
    }

    private fun parseFunctionCallArgsDelta(data: JsonObject): AssistantMessageEvent? {
        val delta = data["delta"]?.jsonPrimitive?.contentOrNull ?: return null
        if (delta.isEmpty()) return null
        val itemId = data["item_id"]?.jsonPrimitive?.contentOrNull

        // Append to the matching tool call's argument builder
        val targetId = itemId ?: toolCallArgs.keys.lastOrNull() ?: return null
        toolCallArgs[targetId]?.append(delta)

        val callId = toolCallIds[targetId] ?: targetId
        return AssistantMessageEvent.ToolCallDelta(id = callId, name = null, argumentsDelta = delta)
    }

    private fun parseReasoningSummaryDelta(data: JsonObject): AssistantMessageEvent? {
        val delta = data["delta"]?.jsonPrimitive?.contentOrNull ?: return null
        if (delta.isEmpty()) return null
        thinkingAccumulator.append(delta)
        return AssistantMessageEvent.ThinkingDelta(delta)
    }

    private fun parseReasoningSummaryPartDone(): AssistantMessageEvent? {
        // Emit a newline separator between reasoning summary parts, matching pi-ai behavior
        thinkingAccumulator.append("\n\n")
        return AssistantMessageEvent.ThinkingDelta("\n\n")
    }

    private fun parseOutputItemDone(data: JsonObject): AssistantMessageEvent? {
        val item = data["item"]?.jsonObject ?: return null
        val type = item["type"]?.jsonPrimitive?.contentOrNull

        when (type) {
            "function_call" -> {
                val itemId = item["id"]?.jsonPrimitive?.contentOrNull ?: return null
                val callId = item["call_id"]?.jsonPrimitive?.contentOrNull
                    ?: toolCallIds[itemId] ?: ""
                val name = item["name"]?.jsonPrimitive?.contentOrNull
                    ?: toolCallNames[itemId] ?: ""

                // Use the final arguments from the done event if available,
                // otherwise use what we accumulated from deltas
                val argsStr = item["arguments"]?.jsonPrimitive?.contentOrNull
                    ?: toolCallArgs[itemId]?.toString()
                    ?: "{}"

                // Update the accumulated args with the final value
                toolCallIds[itemId] = callId
                toolCallNames[itemId] = name
                toolCallArgs[itemId] = StringBuilder(argsStr)
            }
            // "message" and "reasoning" done events — no additional user-visible event needed
        }
        return null
    }

    private fun parseResponseCompleted(data: JsonObject): AssistantMessageEvent {
        val response = data["response"]?.jsonObject

        // Extract usage
        val usageObj = response?.get("usage")?.jsonObject
        if (usageObj != null) {
            val inputTokens = usageObj["input_tokens"]?.jsonPrimitive?.intOrNull ?: 0
            val outputTokens = usageObj["output_tokens"]?.jsonPrimitive?.intOrNull ?: 0
            usage = TokenUsage(inputTokens = inputTokens, outputTokens = outputTokens)
        }

        // Map response status to stop reason
        val status = response?.get("status")?.jsonPrimitive?.contentOrNull
        stopReason = when (status) {
            "completed" -> StopReason.STOP
            "incomplete" -> StopReason.LENGTH
            "failed", "cancelled" -> StopReason.ERROR
            else -> StopReason.STOP
        }

        // If there are tool calls, override to TOOL_USE (matching pi-ai behavior)
        if (toolCallArgs.isNotEmpty()) {
            stopReason = StopReason.TOOL_USE
        }

        return AssistantMessageEvent.Done(
            message = buildCurrentMessage(stopReason),
            stopReason = stopReason ?: StopReason.STOP,
            usage = usage,
        )
    }

    private fun parseResponseFailed(data: JsonObject): AssistantMessageEvent {
        val response = data["response"]?.jsonObject
        val error = response?.get("error")?.jsonObject
        val details = response?.get("incomplete_details")?.jsonObject
        val message = if (error != null) {
            val code = error["code"]?.jsonPrimitive?.contentOrNull ?: "unknown"
            val msg = error["message"]?.jsonPrimitive?.contentOrNull ?: "no message"
            "$code: $msg"
        } else if (details != null) {
            val reason = details["reason"]?.jsonPrimitive?.contentOrNull ?: "unknown"
            "incomplete: $reason"
        } else {
            "Unknown error (no error details in response)"
        }
        return AssistantMessageEvent.Error(
            partial = buildCurrentMessage(),
            error = message,
        )
    }

    private fun parseError(data: JsonObject): AssistantMessageEvent {
        val code = data["code"]?.jsonPrimitive?.contentOrNull ?: "unknown"
        val message = data["message"]?.jsonPrimitive?.contentOrNull ?: "Unknown error"
        return AssistantMessageEvent.Error(
            partial = buildCurrentMessage(),
            error = "Error Code $code: $message",
        )
    }

    private fun buildCurrentMessage(reason: StopReason? = null): Message.Assistant {
        val blocks = mutableListOf<ContentBlock>()

        // Add thinking block if accumulated
        if (thinkingAccumulator.isNotEmpty()) {
            blocks.add(ContentBlock.Thinking(thinkingAccumulator.toString()))
        }

        // Add text block if accumulated
        if (textAccumulator.isNotEmpty()) {
            blocks.add(ContentBlock.Text(textAccumulator.toString()))
        }

        // Add tool call blocks
        for ((itemId, argsBuilder) in toolCallArgs) {
            val argsStr = argsBuilder.toString()
            val argsJson = try {
                json.parseToJsonElement(argsStr).jsonObject
            } catch (_: Exception) {
                JsonObject(emptyMap())
            }
            blocks.add(
                ContentBlock.ToolCall(
                    id = toolCallIds[itemId] ?: "",
                    name = toolCallNames[itemId] ?: "",
                    arguments = argsJson,
                )
            )
        }
        return Message.Assistant(content = blocks, stopReason = reason)
    }

    /** Reset parser state for reuse */
    fun reset() {
        textAccumulator.clear()
        thinkingAccumulator.clear()
        toolCallArgs.clear()
        toolCallIds.clear()
        toolCallNames.clear()
        usage = null
        stopReason = null
    }
}
