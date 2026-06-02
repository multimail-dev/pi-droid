package dev.anthropic.pidroid.llm

import dev.anthropic.pidroid.core.message.Message
import dev.anthropic.pidroid.core.model.StopReason
import dev.anthropic.pidroid.llm.anthropic.AnthropicProvider
import dev.anthropic.pidroid.llm.google.GoogleProvider
import dev.anthropic.pidroid.llm.mistral.MistralProvider
import dev.anthropic.pidroid.llm.openai.OpenAiCompletionsProvider
import dev.anthropic.pidroid.llm.openai.OpenAiResponsesProvider
import dev.anthropic.pidroid.llm.registry.ApiType
import dev.anthropic.pidroid.llm.registry.ModelInfo
import dev.anthropic.pidroid.tools.ToolDefinition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Factory that dispatches [ModelInfo.api] strings to the correct [LlmProvider].
 *
 * Replaces the hardcoded `when (config.provider)` switch in PiRuntime with a
 * registry-driven approach. Each API type string maps to the provider class
 * that speaks that protocol.
 *
 * Usage:
 * ```kotlin
 * val provider = ProviderFactory.create(modelInfo)
 * val events = provider.stream(messages, tools, config)
 * ```
 */
object ProviderFactory {

    /**
     * Create an [LlmProvider] for the given [modelInfo].
     *
     * @param modelInfo Model metadata from the registry — [ModelInfo.api] drives dispatch
     * @param httpClient OkHttpClient to use for requests (defaults to [defaultClient])
     * @return An LlmProvider that speaks the protocol identified by [ModelInfo.api],
     *         or an error-emitting provider for unsupported API types
     */
    fun create(modelInfo: ModelInfo, httpClient: OkHttpClient = defaultClient()): LlmProvider {
        return when (modelInfo.api) {
            ApiType.ANTHROPIC_MESSAGES ->
                AnthropicProvider(httpClient)

            ApiType.OPENAI_COMPLETIONS ->
                OpenAiCompletionsProvider(httpClient)

            ApiType.MISTRAL_CONVERSATIONS ->
                MistralProvider(httpClient)

            ApiType.OPENAI_RESPONSES,
            ApiType.AZURE_OPENAI_RESPONSES ->
                OpenAiResponsesProvider(httpClient)

            ApiType.GOOGLE_GENERATIVE_AI,
            ApiType.GOOGLE_VERTEX ->
                GoogleProvider(httpClient)

            else ->
                UnsupportedProvider(modelInfo.api)
        }
    }

    /**
     * Default OkHttpClient with standard timeouts matching existing providers.
     *
     * - 30s connect timeout
     * - 120s read timeout (SSE streams need long reads)
     * - 30s write timeout
     */
    fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
}

/**
 * Error-emitting provider for unsupported API types.
 *
 * Emits a single [AssistantMessageEvent.Error] with a descriptive message
 * indicating which API type is not supported, then closes the flow.
 */
private class UnsupportedProvider(private val apiType: String) : LlmProvider {
    override val name: String = "unsupported"

    override fun stream(
        messages: List<Message>,
        tools: List<ToolDefinition>,
        config: LlmConfig,
    ): Flow<AssistantMessageEvent> = flow {
        emit(
            AssistantMessageEvent.Error(
                partial = Message.Assistant(content = emptyList()),
                error = "Unsupported API type: $apiType",
            )
        )
    }
}
