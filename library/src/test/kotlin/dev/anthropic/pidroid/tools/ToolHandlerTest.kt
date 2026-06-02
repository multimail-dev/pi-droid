package dev.anthropic.pidroid.tools

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Compile-check tests for ToolHandler interface.
 * Verifies that the interface can be implemented as a test fake.
 */
class ToolHandlerTest {

    /** Fake implementation that returns a canned result */
    private class FakeToolHandler(
        private val resultContent: String = "OK",
        private val isError: Boolean = false,
    ) : ToolHandler {
        var lastToolCallId: String? = null
        var lastArguments: JsonObject? = null

        override suspend fun execute(
            toolCallId: String,
            arguments: JsonObject,
            context: ToolExecutionContext,
        ): ToolResult {
            lastToolCallId = toolCallId
            lastArguments = arguments
            return ToolResult(
                toolCallId = toolCallId,
                content = resultContent,
                isError = isError,
            )
        }
    }

    /** Fake context for testing */
    private class FakeToolExecutionContext : ToolExecutionContext {
        val progressMessages = mutableListOf<String>()
        override var isCancelled: Boolean = false

        override suspend fun reportProgress(message: String) {
            progressMessages.add(message)
        }
    }

    @Test
    fun `ToolHandler interface can be implemented as fake`() = runTest {
        val handler: ToolHandler = FakeToolHandler("Success")
        val context = FakeToolExecutionContext()
        val args = buildJsonObject { put("query", "test") }

        val result = handler.execute("tc_001", args, context)
        assertEquals("tc_001", result.toolCallId)
        assertEquals("Success", result.content)
        assertFalse(result.isError)
    }

    @Test
    fun `ToolExecutionContext tracks progress`() = runTest {
        val context = FakeToolExecutionContext()
        context.reportProgress("Step 1")
        context.reportProgress("Step 2")
        assertEquals(listOf("Step 1", "Step 2"), context.progressMessages)
    }
}
