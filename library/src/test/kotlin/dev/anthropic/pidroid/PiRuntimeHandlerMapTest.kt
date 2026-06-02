package dev.anthropic.pidroid

import dev.anthropic.pidroid.tools.ToolExecutionContext
import dev.anthropic.pidroid.tools.ToolHandler
import dev.anthropic.pidroid.tools.ToolResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
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
class PiRuntimeHandlerMapTest {

    private val testConfig = PiRuntimeConfig(
        llmProvider = LlmProviderConfig(
            provider = "anthropic",
            modelId = "claude-sonnet-4-20250514",
            apiKey = "test-key",
        ),
    )

    @Before
    fun setup() {
        PiRuntime.resetForTesting()
    }

    @After
    fun teardown() {
        PiRuntime.resetForTesting()
    }

    @Test
    fun `initialize with handlers creates runtime`() = runTest {
        val handler = object : ToolHandler {
            override suspend fun execute(
                toolCallId: String,
                arguments: JsonObject,
                context: ToolExecutionContext,
            ): ToolResult = ToolResult(toolCallId, "ok")
        }

        val runtime = PiRuntime.initialize(
            RuntimeEnvironment.getApplication(),
            testConfig,
            handlers = mapOf("test_tool" to handler),
        )
        assertNotNull(runtime)
        assertEquals(RuntimeStatus.IDLE, runtime.state.value.status)
    }

    @Test
    fun `initialize without handlers defaults to empty map`() = runTest {
        // Backward compatibility: initialize() with no handlers param works
        val runtime = PiRuntime.initialize(RuntimeEnvironment.getApplication(), testConfig)
        assertNotNull(runtime)
        assertEquals(RuntimeStatus.IDLE, runtime.state.value.status)
    }

    @Test
    fun `initialize with multiple handlers creates runtime`() = runTest {
        val handler1 = object : ToolHandler {
            override suspend fun execute(
                toolCallId: String,
                arguments: JsonObject,
                context: ToolExecutionContext,
            ): ToolResult = ToolResult(toolCallId, "calendar result")
        }
        val handler2 = object : ToolHandler {
            override suspend fun execute(
                toolCallId: String,
                arguments: JsonObject,
                context: ToolExecutionContext,
            ): ToolResult = ToolResult(toolCallId, "contact result")
        }

        val runtime = PiRuntime.initialize(
            RuntimeEnvironment.getApplication(),
            testConfig,
            handlers = mapOf(
                "read_calendar_events" to handler1,
                "search_contacts" to handler2,
            ),
        )
        assertNotNull(runtime)
    }
}
