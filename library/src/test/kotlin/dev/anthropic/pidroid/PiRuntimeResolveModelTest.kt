package dev.anthropic.pidroid

import dev.anthropic.pidroid.llm.registry.ApiType
import dev.anthropic.pidroid.llm.registry.ModelRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PiRuntimeResolveModelTest {

    @Before
    fun setup() {
        PiRuntime.resetForTesting()
        ModelRegistry.init(RuntimeEnvironment.getApplication())
    }

    @After
    fun teardown() {
        PiRuntime.resetForTesting()
    }

    @Test
    fun `resolveModelInfo returns ModelInfo for known provider and model`() {
        val config = LlmProviderConfig(
            provider = "anthropic",
            modelId = "claude-sonnet-4-20250514",
            apiKey = "test-key",
        )
        val modelInfo = PiRuntime.resolveModelInfo(config)
        assertNotNull(modelInfo)
        assertEquals("claude-sonnet-4-20250514", modelInfo.id)
        assertEquals(ApiType.ANTHROPIC_MESSAGES, modelInfo.api)
        assertEquals("anthropic", modelInfo.provider)
    }

    @Test
    fun `resolveModelInfo falls back to openai-completions when unknown model has baseUrl`() {
        val config = LlmProviderConfig(
            provider = "custom-llm",
            modelId = "my-custom-model",
            apiKey = "test-key",
            baseUrl = "https://my-custom-llm.example.com/v1",
        )
        val modelInfo = PiRuntime.resolveModelInfo(config)
        assertEquals("my-custom-model", modelInfo.id)
        assertEquals(ApiType.OPENAI_COMPLETIONS, modelInfo.api)
        assertEquals("custom-llm", modelInfo.provider)
        assertEquals("https://my-custom-llm.example.com/v1", modelInfo.baseUrl)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `resolveModelInfo throws when unknown model has no baseUrl`() {
        val config = LlmProviderConfig(
            provider = "unknown-provider",
            modelId = "nonexistent-model",
            apiKey = "test-key",
        )
        PiRuntime.resolveModelInfo(config)
    }

    @Test
    fun `resolveModelInfo error message includes provider and model`() {
        val config = LlmProviderConfig(
            provider = "unknown-provider",
            modelId = "nonexistent-model",
            apiKey = "test-key",
        )
        try {
            PiRuntime.resolveModelInfo(config)
        } catch (e: IllegalArgumentException) {
            assert(e.message!!.contains("nonexistent-model"))
            assert(e.message!!.contains("unknown-provider"))
            assert(e.message!!.contains("baseUrl"))
        }
    }

    @Test
    fun `resolveModelInfo works for openai provider`() {
        val config = LlmProviderConfig(
            provider = "openai",
            modelId = "gpt-4o",
            apiKey = "test-key",
        )
        val modelInfo = PiRuntime.resolveModelInfo(config)
        assertEquals("gpt-4o", modelInfo.id)
        assertEquals("openai", modelInfo.provider)
    }

    @Test
    fun `resolveModelInfo works for google provider`() {
        val config = LlmProviderConfig(
            provider = "google",
            modelId = "gemini-2.0-flash",
            apiKey = "test-key",
        )
        val modelInfo = PiRuntime.resolveModelInfo(config)
        assertEquals("gemini-2.0-flash", modelInfo.id)
        assertEquals("google", modelInfo.provider)
    }
}
