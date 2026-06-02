package dev.anthropic.pidroid.llm.anthropic

import dev.anthropic.pidroid.core.message.ContentBlock
import dev.anthropic.pidroid.core.message.Message
import dev.anthropic.pidroid.core.model.StopReason
import dev.anthropic.pidroid.llm.AssistantMessageEvent
import dev.anthropic.pidroid.llm.LlmConfig
import dev.anthropic.pidroid.llm.LlmProvider
import dev.anthropic.pidroid.tools.ToolDefinition
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit

/**
 * Anthropic Claude API provider.
 *
 * Uses OkHttp's EventSource for SSE streaming. Maps Anthropic's event stream
 * to [AssistantMessageEvent]s via [AnthropicSseParser].
 *
 * ## Stream Contract
 * - Never throws from [stream] — all errors become [AssistantMessageEvent.Error]
 * - Cancelling the Flow collector closes the SSE connection
 * - One [Start], zero+ deltas, one terminal ([Done] or [Error])
 */
class AnthropicProvider(
    private val httpClient: OkHttpClient = defaultClient(),
) : LlmProvider {
    override val name: String = "anthropic"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    override fun stream(
        messages: List<Message>,
        tools: List<ToolDefinition>,
        config: LlmConfig,
    ): Flow<AssistantMessageEvent> = callbackFlow {
        val parser = AnthropicSseParser()
        val textAccumulator = StringBuilder()
        val thinkingAccumulator = StringBuilder()

        val requestBody = buildRequestBody(messages, tools, config)
        val baseUrl = config.baseUrl ?: DEFAULT_BASE_URL
        val request = Request.Builder()
            .url("$baseUrl/v1/messages")
            .header("x-api-key", config.apiKey)
            .header("anthropic-version", API_VERSION)
            .header("content-type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                val eventType = type ?: return
                val event = parser.parse(eventType, data) ?: return

                // Accumulate text/thinking for final message assembly
                when (event) {
                    is AssistantMessageEvent.TextDelta -> textAccumulator.append(event.text)
                    is AssistantMessageEvent.ThinkingDelta -> thinkingAccumulator.append(event.text)
                    is AssistantMessageEvent.Done -> {
                        // Add accumulated blocks before done
                        if (textAccumulator.isNotEmpty()) parser.addTextBlock(textAccumulator.toString())
                        if (thinkingAccumulator.isNotEmpty()) parser.addThinkingBlock(thinkingAccumulator.toString())
                    }
                    else -> {}
                }

                trySend(event)
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                val errorMsg = t?.message ?: response?.message ?: "Unknown error"
                trySend(
                    AssistantMessageEvent.Error(
                        partial = Message.Assistant(content = emptyList()),
                        error = errorMsg,
                    )
                )
                channel.close()
            }

            override fun onClosed(eventSource: EventSource) {
                channel.close()
            }
        }

        val eventSource = EventSources.createFactory(httpClient)
            .newEventSource(request, listener)

        awaitClose {
            eventSource.cancel()
        }
    }

    private fun buildRequestBody(
        messages: List<Message>,
        tools: List<ToolDefinition>,
        config: LlmConfig,
    ): String {
        val body = buildJsonObject {
            put("model", config.model)
            put("max_tokens", config.maxTokens)
            put("temperature", config.temperature)
            put("stream", true)

            // System prompt
            config.systemPrompt?.let { put("system", it) }

            // Messages
            put("messages", buildJsonArray {
                for (msg in messages) {
                    when (msg) {
                        is Message.User -> add(buildJsonObject {
                            put("role", "user")
                            put("content", serializeContentBlocks(msg.content))
                        })
                        is Message.Assistant -> add(buildJsonObject {
                            put("role", "assistant")
                            put("content", serializeContentBlocks(msg.content))
                        })
                        is Message.ToolResult -> add(buildJsonObject {
                            put("role", "user")
                            put("content", buildJsonArray {
                                add(buildJsonObject {
                                    put("type", "tool_result")
                                    put("tool_use_id", msg.toolCallId)
                                    put("content", msg.content)
                                    if (msg.isError) put("is_error", true)
                                })
                            })
                        })
                        is Message.System -> {} // handled separately
                    }
                }
            })

            // Tools
            if (tools.isNotEmpty()) {
                put("tools", buildJsonArray {
                    for (tool in tools) {
                        add(buildJsonObject {
                            put("name", tool.name)
                            put("description", tool.description)
                            put("input_schema", tool.inputSchema)
                        })
                    }
                })
            }
        }
        return json.encodeToString(JsonObject.serializer(), body)
    }

    private fun serializeContentBlocks(blocks: List<ContentBlock>): JsonArray {
        return buildJsonArray {
            for (block in blocks) {
                when (block) {
                    is ContentBlock.Text -> add(buildJsonObject {
                        put("type", "text")
                        put("text", block.text)
                    })
                    is ContentBlock.ToolCall -> add(buildJsonObject {
                        put("type", "tool_use")
                        put("id", block.id)
                        put("name", block.name)
                        put("input", block.arguments)
                    })
                    is ContentBlock.Thinking -> add(buildJsonObject {
                        put("type", "thinking")
                        put("thinking", block.text)
                    })
                }
            }
        }
    }

    companion object {
        private const val DEFAULT_BASE_URL = "https://api.anthropic.com"
        private const val API_VERSION = "2023-06-01"

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
