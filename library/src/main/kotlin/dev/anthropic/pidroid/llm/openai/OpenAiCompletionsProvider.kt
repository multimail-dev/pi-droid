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
 * OpenAI Chat Completions API provider.
 *
 * Supports OpenAI and OpenAI-compatible providers (xAI, Groq, Cerebras,
 * OpenRouter, Ollama) via [LlmConfig.compat] settings that adjust the
 * request body for provider-specific quirks.
 *
 * Uses OkHttp's EventSource for SSE streaming. Maps OpenAI's event stream
 * to [AssistantMessageEvent]s via [OpenAiSseParser].
 */
class OpenAiCompletionsProvider(
    private val httpClient: OkHttpClient = defaultClient(),
) : LlmProvider {
    override val name: String = "openai"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    override fun stream(
        messages: List<Message>,
        tools: List<ToolDefinition>,
        config: LlmConfig,
    ): Flow<AssistantMessageEvent> = callbackFlow {
        val parser = OpenAiSseParser()

        val requestBody = buildRequestBody(messages, tools, config)
        val baseUrl = config.baseUrl ?: DEFAULT_BASE_URL
        val requestBuilder = Request.Builder()
            .url("$baseUrl/v1/chat/completions")
            .header("Authorization", "Bearer ${config.apiKey}")
            .header("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))

        // Apply custom headers from config
        config.headers?.forEach { (k, v) -> requestBuilder.header(k, v) }

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

    /**
     * Build the JSON request body, applying compat settings where appropriate.
     *
     * Visible for testing — call directly to verify request body construction
     * without needing a live HTTP connection.
     */
    internal fun buildRequestBody(
        messages: List<Message>,
        tools: List<ToolDefinition>,
        config: LlmConfig,
    ): String {
        val compat = config.compat
        val body = buildJsonObject {
            put("model", config.model)

            // Use compat maxTokensField if specified, default to "max_tokens"
            val maxTokensKey = compat?.maxTokensField ?: "max_tokens"
            put(maxTokensKey, config.maxTokens)

            put("temperature", config.temperature)
            put("stream", true)

            // Omit stream_options if provider doesn't support usage in streaming
            if (compat?.supportsUsageInStreaming != false) {
                put("stream_options", buildJsonObject { put("include_usage", true) })
            }

            // Messages
            put("messages", buildJsonArray {
                // System prompt as first message
                config.systemPrompt?.let { sys ->
                    val role = if (compat?.supportsDeveloperRole == true) "developer" else "system"
                    add(buildJsonObject {
                        put("role", role)
                        put("content", sys)
                    })
                }

                for (msg in messages) {
                    when (msg) {
                        is Message.User -> add(buildJsonObject {
                            put("role", "user")
                            put("content", msg.content.filterIsInstance<ContentBlock.Text>()
                                .joinToString("") { it.text })
                        })
                        is Message.Assistant -> {
                            val obj = buildJsonObject {
                                put("role", "assistant")
                                val text = msg.content.filterIsInstance<ContentBlock.Text>()
                                    .joinToString("") { it.text }
                                if (text.isNotEmpty()) put("content", text)
                                val toolCalls = msg.content.filterIsInstance<ContentBlock.ToolCall>()
                                if (toolCalls.isNotEmpty()) {
                                    put("tool_calls", buildJsonArray {
                                        for (tc in toolCalls) {
                                            add(buildJsonObject {
                                                put("id", tc.id)
                                                put("type", "function")
                                                put("function", buildJsonObject {
                                                    put("name", tc.name)
                                                    put("arguments", json.encodeToString(
                                                        JsonObject.serializer(), tc.arguments))
                                                })
                                            })
                                        }
                                    })
                                }
                            }
                            add(obj)
                        }
                        is Message.ToolResult -> add(buildJsonObject {
                            put("role", "tool")
                            put("tool_call_id", msg.toolCallId)
                            put("content", msg.content)
                            // Some providers require name in tool result messages
                            if (compat?.requiresToolResultName == true) {
                                msg.toolName?.let { put("name", it) }
                            }
                        })
                        is Message.System -> {} // handled above
                    }
                }
            })

            // Tools
            if (tools.isNotEmpty()) {
                put("tools", buildJsonArray {
                    for (tool in tools) {
                        add(buildJsonObject {
                            put("type", "function")
                            put("function", buildJsonObject {
                                put("name", tool.name)
                                put("description", tool.description)
                                put("parameters", tool.inputSchema)
                            })
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
