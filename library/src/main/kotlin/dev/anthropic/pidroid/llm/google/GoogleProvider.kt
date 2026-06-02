package dev.anthropic.pidroid.llm.google

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
 * Google Generative AI (Gemini) API provider.
 *
 * Uses OkHttp's EventSource for SSE streaming. Maps Google's event stream
 * to [AssistantMessageEvent]s via [GoogleSseParser].
 *
 * ## Auth
 * - Standard Google AI: API key as `?key={apiKey}` query parameter
 * - Vertex AI: If baseUrl contains "aiplatform.googleapis.com", uses
 *   `Authorization: Bearer` header instead of query param
 *
 * ## Stream Contract
 * - Never throws from [stream] — all errors become [AssistantMessageEvent.Error]
 * - Cancelling the Flow collector closes the SSE connection
 * - One [Start], zero+ deltas, one terminal ([Done] or [Error])
 */
class GoogleProvider(
    private val httpClient: OkHttpClient = defaultClient(),
) : LlmProvider {
    override val name: String = "google"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    override fun stream(
        messages: List<Message>,
        tools: List<ToolDefinition>,
        config: LlmConfig,
    ): Flow<AssistantMessageEvent> = callbackFlow {
        val parser = GoogleSseParser()

        val requestBody = buildRequestBody(messages, tools, config)
        val baseUrl = config.baseUrl ?: DEFAULT_BASE_URL
        val isVertexAi = baseUrl.contains("aiplatform.googleapis.com")

        val url = if (isVertexAi) {
            "$baseUrl/v1beta/publishers/google/models/${config.model}:streamGenerateContent"
        } else {
            "$baseUrl/v1beta/models/${config.model}:streamGenerateContent?alt=sse&key=${config.apiKey}"
        }

        val requestBuilder = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))

        if (isVertexAi) {
            requestBuilder.header("Authorization", "Bearer ${config.apiKey}")
        }

        val request = requestBuilder.build()

        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                val events = parser.parse(data)
                for (event in events) {
                    trySend(event)
                }
                // Close channel on terminal events
                events.lastOrNull()?.let { last ->
                    if (last is AssistantMessageEvent.Done || last is AssistantMessageEvent.Error) {
                        channel.close()
                    }
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
            // Contents (conversation messages)
            put("contents", buildJsonArray {
                for (msg in messages) {
                    when (msg) {
                        is Message.User -> add(buildJsonObject {
                            put("role", "user")
                            put("parts", buildJsonArray {
                                for (block in msg.content) {
                                    when (block) {
                                        is ContentBlock.Text -> add(buildJsonObject {
                                            put("text", block.text)
                                        })
                                        is ContentBlock.ToolCall -> {} // not in user messages
                                        is ContentBlock.Thinking -> {} // not in user messages
                                    }
                                }
                            })
                        })
                        is Message.Assistant -> add(buildJsonObject {
                            put("role", "model")
                            put("parts", buildJsonArray {
                                for (block in msg.content) {
                                    when (block) {
                                        is ContentBlock.Text -> add(buildJsonObject {
                                            put("text", block.text)
                                        })
                                        is ContentBlock.ToolCall -> add(buildJsonObject {
                                            put("functionCall", buildJsonObject {
                                                put("name", block.name)
                                                put("args", block.arguments)
                                            })
                                        })
                                        is ContentBlock.Thinking -> {} // not sent back to Google
                                    }
                                }
                            })
                        })
                        is Message.ToolResult -> {
                            // Tool results are user-role messages with functionResponse parts
                            add(buildJsonObject {
                                put("role", "user")
                                put("parts", buildJsonArray {
                                    add(buildJsonObject {
                                        put("functionResponse", buildJsonObject {
                                            put("name", msg.toolCallId) // use toolCallId as name placeholder
                                            put("response", buildJsonObject {
                                                put("output", msg.content)
                                            })
                                        })
                                    })
                                })
                            })
                        }
                        is Message.System -> {} // handled via systemInstruction
                    }
                }
            })

            // System prompt as systemInstruction
            config.systemPrompt?.let { sys ->
                put("systemInstruction", buildJsonObject {
                    put("parts", buildJsonArray {
                        add(buildJsonObject {
                            put("text", sys)
                        })
                    })
                })
            }

            // Generation config
            put("generationConfig", buildJsonObject {
                put("temperature", config.temperature)
                put("maxOutputTokens", config.maxTokens)
                put("responseMimeType", "text/plain")
            })

            // Tools (functionDeclarations)
            if (tools.isNotEmpty()) {
                put("tools", buildJsonArray {
                    add(buildJsonObject {
                        put("functionDeclarations", buildJsonArray {
                            for (tool in tools) {
                                add(buildJsonObject {
                                    put("name", tool.name)
                                    put("description", tool.description)
                                    put("parameters", tool.inputSchema)
                                })
                            }
                        })
                    })
                })
            }
        }
        return json.encodeToString(JsonObject.serializer(), body)
    }

    companion object {
        private const val DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com"

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
