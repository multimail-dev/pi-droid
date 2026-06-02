package dev.anthropic.pidroid

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PiRuntimeSystemPromptTest {

    @Before
    fun setup() {
        PiRuntime.resetForTesting()
    }

    @After
    fun teardown() {
        PiRuntime.resetForTesting()
    }

    @Test
    fun `systemPrompt default is null`() {
        val config = PiRuntimeConfig(
            llmProvider = LlmProviderConfig(
                provider = "anthropic",
                modelId = "claude-sonnet-4-20250514",
                apiKey = "test-key",
            ),
        )
        assertNull(config.systemPrompt)
    }

    @Test
    fun `systemPrompt set on config is preserved`() {
        val config = PiRuntimeConfig(
            llmProvider = LlmProviderConfig(
                provider = "anthropic",
                modelId = "claude-sonnet-4-20250514",
                apiKey = "test-key",
            ),
            systemPrompt = "You are a helpful assistant on an Android phone.",
        )
        assertEquals("You are a helpful assistant on an Android phone.", config.systemPrompt)
    }

    @Test
    fun `initialize with systemPrompt creates runtime successfully`() = runTest {
        val config = PiRuntimeConfig(
            llmProvider = LlmProviderConfig(
                provider = "anthropic",
                modelId = "claude-sonnet-4-20250514",
                apiKey = "test-key",
            ),
            systemPrompt = "You are a helpful assistant.",
        )
        val runtime = PiRuntime.initialize(RuntimeEnvironment.getApplication(), config)
        assertNotNull(runtime)
        assertEquals(RuntimeStatus.IDLE, runtime.state.value.status)
    }

    @Test
    fun `initialize without systemPrompt preserves backward compatibility`() = runTest {
        val config = PiRuntimeConfig(
            llmProvider = LlmProviderConfig(
                provider = "anthropic",
                modelId = "claude-sonnet-4-20250514",
                apiKey = "test-key",
            ),
        )
        val runtime = PiRuntime.initialize(RuntimeEnvironment.getApplication(), config)
        assertNotNull(runtime)
        assertEquals(RuntimeStatus.IDLE, runtime.state.value.status)
    }
}
