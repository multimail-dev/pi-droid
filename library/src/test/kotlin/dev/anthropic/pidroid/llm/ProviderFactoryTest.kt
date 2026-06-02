package dev.anthropic.pidroid.llm

import dev.anthropic.pidroid.core.model.StopReason
import dev.anthropic.pidroid.llm.anthropic.AnthropicProvider
import dev.anthropic.pidroid.llm.google.GoogleProvider
import dev.anthropic.pidroid.llm.mistral.MistralProvider
import dev.anthropic.pidroid.llm.openai.OpenAiCompletionsProvider
import dev.anthropic.pidroid.llm.openai.OpenAiResponsesProvider
import dev.anthropic.pidroid.llm.registry.ApiType
import dev.anthropic.pidroid.llm.registry.ModelInfo
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderFactoryTest {

    private fun modelInfo(api: String, id: String = "test-model"): ModelInfo = ModelInfo(
        id = id,
        name = "Test Model",
        api = api,
        provider = "test",
        baseUrl = "https://test.example.com",
    )

    // -- Happy-path dispatch tests --

    @Test
    fun `anthropic-messages returns AnthropicProvider`() {
        val provider = ProviderFactory.create(modelInfo(ApiType.ANTHROPIC_MESSAGES))
        assertTrue(
            "Expected AnthropicProvider, got ${provider::class.simpleName}",
            provider is AnthropicProvider,
        )
    }

    @Test
    fun `openai-completions returns OpenAiCompletionsProvider`() {
        val provider = ProviderFactory.create(modelInfo(ApiType.OPENAI_COMPLETIONS))
        assertTrue(
            "Expected OpenAiCompletionsProvider, got ${provider::class.simpleName}",
            provider is OpenAiCompletionsProvider,
        )
    }

    @Test
    fun `mistral-conversations returns MistralProvider`() {
        val provider = ProviderFactory.create(modelInfo(ApiType.MISTRAL_CONVERSATIONS))
        assertTrue(
            "Expected MistralProvider for Mistral, got ${provider::class.simpleName}",
            provider is MistralProvider,
        )
    }

    @Test
    fun `openai-responses returns OpenAiResponsesProvider`() {
        val provider = ProviderFactory.create(modelInfo(ApiType.OPENAI_RESPONSES))
        assertTrue(
            "Expected OpenAiResponsesProvider, got ${provider::class.simpleName}",
            provider is OpenAiResponsesProvider,
        )
    }

    @Test
    fun `azure-openai-responses returns OpenAiResponsesProvider`() {
        val provider = ProviderFactory.create(modelInfo(ApiType.AZURE_OPENAI_RESPONSES))
        assertTrue(
            "Expected OpenAiResponsesProvider for Azure, got ${provider::class.simpleName}",
            provider is OpenAiResponsesProvider,
        )
    }

    @Test
    fun `google-generative-ai returns GoogleProvider`() {
        val provider = ProviderFactory.create(modelInfo(ApiType.GOOGLE_GENERATIVE_AI))
        assertTrue(
            "Expected GoogleProvider, got ${provider::class.simpleName}",
            provider is GoogleProvider,
        )
    }

    @Test
    fun `google-vertex returns GoogleProvider`() {
        val provider = ProviderFactory.create(modelInfo(ApiType.GOOGLE_VERTEX))
        assertTrue(
            "Expected GoogleProvider for Vertex, got ${provider::class.simpleName}",
            provider is GoogleProvider,
        )
    }

    // -- Error / unsupported path tests --

    @Test
    fun `unknown API type returns error provider`() {
        val provider = ProviderFactory.create(modelInfo("totally-unknown-api"))
        assertEquals("unsupported", provider.name)
    }

    @Test
    fun `error provider emits Error event with descriptive message`() = runBlocking {
        val provider = ProviderFactory.create(modelInfo("totally-unknown-api"))
        val config = LlmConfig(apiKey = "test", model = "test")
        val events = provider.stream(emptyList(), emptyList(), config).toList()

        assertEquals(1, events.size)
        val error = events[0]
        assertTrue("Expected Error event, got ${error::class.simpleName}", error is AssistantMessageEvent.Error)
        error as AssistantMessageEvent.Error
        assertEquals("Unsupported API type: totally-unknown-api", error.error)
        assertEquals(StopReason.ERROR, error.stopReason)
        assertTrue("Partial message should have empty content", error.partial.content.isEmpty())
    }

    @Test
    fun `bedrock-converse-stream returns error provider`() {
        val provider = ProviderFactory.create(modelInfo(ApiType.BEDROCK_CONVERSE_STREAM))
        assertEquals("unsupported", provider.name)
    }

    @Test
    fun `bedrock error provider emits descriptive error`() = runBlocking {
        val provider = ProviderFactory.create(modelInfo(ApiType.BEDROCK_CONVERSE_STREAM))
        val config = LlmConfig(apiKey = "test", model = "test")
        val events = provider.stream(emptyList(), emptyList(), config).toList()

        assertEquals(1, events.size)
        val error = events[0] as AssistantMessageEvent.Error
        assertEquals("Unsupported API type: bedrock-converse-stream", error.error)
    }

    // -- Custom httpClient test --

    @Test
    fun `create accepts custom OkHttpClient`() {
        val customClient = ProviderFactory.defaultClient()
        val provider = ProviderFactory.create(modelInfo(ApiType.ANTHROPIC_MESSAGES), customClient)
        assertTrue(provider is AnthropicProvider)
    }
}
