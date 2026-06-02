package dev.anthropic.pidroid.llm.openai

import dev.anthropic.pidroid.core.message.ContentBlock
import dev.anthropic.pidroid.core.message.Message
import dev.anthropic.pidroid.llm.AssistantMessageEvent
import dev.anthropic.pidroid.llm.LlmConfig
import dev.anthropic.pidroid.llm.LlmProvider
import dev.anthropic.pidroid.tools.ToolDefinition
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
 * OpenAI Responses API provider.
 *
 * Supports OpenAI's newer Responses API (POST /v1/responses), which is distinct
 * from the legacy Chat Completions API. Used by newer OpenAI models, GitHub Copilot,
 * and Azure OpenAI.
 *
 * Key differences from Chat Completions:
 * - Request uses `input` not `messages`
 * - System prompt sent as `developer` role
 * - Tool calls use `function_call` items with `call_id` (not `tool_calls` with `id`)
 * - SSE uses named event types (like Anthropic), not unnamed `data:` lines
 *
 * Uses OkHttp's EventSource for SSE streaming. Maps Responses API events
 * to [AssistantMessageEvent]s via [ResponsesSseParser].
 *
 * ## Stream Contract
 * - Never throws from [stream] — all errors become [AssistantMessageEvent.Error]
 * - Cancelling the Flow collector closes the SSE connection
 * - One [Start], zero+ deltas, one terminal ([Done] or [Error])
 */
class OpenAiResponsesProvider(
    private val httpClient: OkHttpClient = defaultClient(),
) : LlmProvider {
    override val name: String = "openai-responses"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    override fun stream(
        messages: List<Message>,
        tools: List<ToolDefinition>,
        config: LlmConfig,
    ): Flow<AssistantMessageEvent> = callbackFlow {
        val parser = ResponsesSseParser()

        val requestBody = buildRequestBody(messages, tools, config)
        val baseUrl = config.baseUrl ?: DEFAULT_BASE_URL
        val isAzure = baseUrl.contains("azure")

        val requestBuilder = Request.Builder()
            .url("$baseUrl/v1/responses")
            .header("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))

        // Azure uses api-key header; standard OpenAI uses Authorization: Bearer
        if (isAzure) {
            requestBuilder.header("api-key", config.apiKey)
        } else {
            requestBuilder.header("Authorization", "Bearer ${config.apiKey}")
        }

        val request = requestBuilder.build()

        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                val eventType = type ?: return
                val event = parser.parse(eventType, data) ?: return

                trySend(event)

                // Close channel on terminal events
                if (event is AssistantMessageEvent.Done || event is AssistantMessageEvent.Error) {
                    channel.close()
                }
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

    internal fun buildRequestBody(
        messages: List<Message>,
        tools: List<ToolDefinition>,
        config: LlmConfig,
    ): String {
        val body = buildJsonObject {
            put("model", config.model)
            put("stream", true)

            // Input messages — Responses API uses "input" not "messages"
            put("input", buildJsonArray {
                // System prompt as developer role (first input message)
                config.systemPrompt?.let { sys ->
                    add(buildJsonObject {
                        put("role", "developer")
                        put("content", buildJsonArray {
                            add(buildJsonObject {
                                put("type", "input_text")
                                put("text", sys)
                            })
                        })
                    })
                }

                for (msg in messages) {
                    when (msg) {
                        is Message.User -> add(buildJsonObject {
                            put("role", "user")
                            put("content", buildJsonArray {
                                add(buildJsonObject {
                                    put("type", "input_text")
                                    put("text", msg.content.filterIsInstance<ContentBlock.Text>()
                                        .joinToString("") { it.text })
                                })
                            })
                        })
                        is Message.Assistant -> {
                            // Assistant text → output message item
                            val text = msg.content.filterIsInstance<ContentBlock.Text>()
                                .joinToString("") { it.text }
                            if (text.isNotEmpty()) {
                                add(buildJsonObject {
                                    put("type", "message")
                                    put("role", "assistant")
                                    put("content", buildJsonArray {
                                        add(buildJsonObject {
                                            put("type", "output_text")
                                            put("text", text)
                                        })
                                    })
                                    put("status", "completed")
                                })
                            }
                            // Assistant tool calls → function_call items
                            for (tc in msg.content.filterIsInstance<ContentBlock.ToolCall>()) {
                                add(buildJsonObject {
                                    put("type", "function_call")
                                    put("call_id", tc.id)
                                    put("name", tc.name)
                                    put("arguments", json.encodeToString(
                                        JsonObject.serializer(), tc.arguments))
                                })
                            }
                        }
                        is Message.ToolResult -> add(buildJsonObject {
                            put("type", "function_call_output")
                            put("call_id", msg.toolCallId)
                            put("output", msg.content)
                        })
                        is Message.System -> {} // handled above as developer role
                    }
                }
            })

            // Tools — Responses API uses flat function definitions with strict flag
            if (tools.isNotEmpty()) {
                put("tools", buildJsonArray {
                    for (tool in tools) {
                        add(buildJsonObject {
                            put("type", "function")
                            put("name", tool.name)
                            put("description", tool.description)
                            put("parameters", tool.inputSchema)
                            put("strict", true)
                        })
                    }
                })
            }
        }
        return json.encodeToString(JsonObject.serializer(), body)
    }

    companion object {
        private const val DEFAULT_BASE_URL = "https://api.openai.com"

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
