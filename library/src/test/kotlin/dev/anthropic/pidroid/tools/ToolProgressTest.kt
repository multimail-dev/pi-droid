package dev.anthropic.pidroid.tools

import dev.anthropic.pidroid.capabilities.CapabilityGrant
import dev.anthropic.pidroid.core.event.AgentEvent
import dev.anthropic.pidroid.core.message.ContentBlock
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ToolProgressTest {
    private lateinit var permissionChecker: FakePermissionChecker
    private lateinit var registry: ToolRegistry
    private lateinit var gate: ConfirmationGate

    @Before
    fun setup() {
        permissionChecker = FakePermissionChecker()
        permissionChecker.notificationListenerEnabled = true
        registry = ToolRegistry(permissionChecker)
    }

    private suspend fun activateRegistry() {
        registry.declareCapability(CapabilityGrant(CapabilityGrant.CAPABILITY_NOTIFICATION_LISTENER))
    }

    private fun createExecutor(
        handlers: Map<String, ToolHandler>,
        emitEvent: (suspend (AgentEvent) -> Unit)? = null,
    ): ToolExecutor {
        gate = ConfirmationGate()
        val executor = ToolExecutor(registry, gate, handlers)
        executor.emitEvent = emitEvent
        return executor
    }

    private fun toolCall(id: String = "tc_1", name: String = "read_notifications") =
        ContentBlock.ToolCall(id, name, JsonObject(emptyMap()))

    @Test
    fun `reportProgress emits ToolExecutionUpdate event`() = runTest {
        activateRegistry()
        val emittedEvents = mutableListOf<AgentEvent>()
        val handler = object : ToolHandler {
            override suspend fun execute(
                toolCallId: String,
                arguments: JsonObject,
                context: ToolExecutionContext,
            ): ToolResult {
                context.reportProgress("step 1 of 3")
                return ToolResult(toolCallId, "done")
            }
        }

        val executor = createExecutor(
            mapOf("read_notifications" to handler),
            emitEvent = { emittedEvents.add(it) },
        )

        executor.dispatch(listOf(toolCall("tc_progress")))

        assertEquals(1, emittedEvents.size)
        val event = emittedEvents[0] as AgentEvent.ToolExecutionUpdate
        assertEquals("tc_progress", event.toolCallId)
        assertEquals("step 1 of 3", event.progress)
    }

    @Test
    fun `multiple reportProgress calls emit multiple events`() = runTest {
        activateRegistry()
        val emittedEvents = mutableListOf<AgentEvent>()
        val handler = object : ToolHandler {
            override suspend fun execute(
                toolCallId: String,
                arguments: JsonObject,
                context: ToolExecutionContext,
            ): ToolResult {
                context.reportProgress("starting")
                context.reportProgress("processing")
                context.reportProgress("finishing")
                return ToolResult(toolCallId, "done")
            }
        }

        val executor = createExecutor(
            mapOf("read_notifications" to handler),
            emitEvent = { emittedEvents.add(it) },
        )

        executor.dispatch(listOf(toolCall()))

        assertEquals(3, emittedEvents.size)
        assertEquals("starting", (emittedEvents[0] as AgentEvent.ToolExecutionUpdate).progress)
        assertEquals("processing", (emittedEvents[1] as AgentEvent.ToolExecutionUpdate).progress)
        assertEquals("finishing", (emittedEvents[2] as AgentEvent.ToolExecutionUpdate).progress)
    }

    @Test
    fun `no reportProgress calls emit no events`() = runTest {
        activateRegistry()
        val emittedEvents = mutableListOf<AgentEvent>()
        val handler = object : ToolHandler {
            override suspend fun execute(
                toolCallId: String,
                arguments: JsonObject,
                context: ToolExecutionContext,
            ): ToolResult {
                return ToolResult(toolCallId, "done")
            }
        }

        val executor = createExecutor(
            mapOf("read_notifications" to handler),
            emitEvent = { emittedEvents.add(it) },
        )

        executor.dispatch(listOf(toolCall()))

        assertTrue(emittedEvents.isEmpty())
    }

    @Test
    fun `reportProgress with null emitEvent is a no-op`() = runTest {
        activateRegistry()
        val handler = object : ToolHandler {
            override suspend fun execute(
                toolCallId: String,
                arguments: JsonObject,
                context: ToolExecutionContext,
            ): ToolResult {
                context.reportProgress("should not crash")
                return ToolResult(toolCallId, "done")
            }
        }

        val executor = createExecutor(
            mapOf("read_notifications" to handler),
            emitEvent = null,
        )

        val results = executor.dispatch(listOf(toolCall()))

        assertEquals("done", results[0].content)
        assertFalse(results[0].isError)
    }

    @Test
    fun `isCancelled reflects active job state`() = runTest {
        activateRegistry()
        val handler = object : ToolHandler {
            override suspend fun execute(
                toolCallId: String,
                arguments: JsonObject,
                context: ToolExecutionContext,
            ): ToolResult {
                // Inside an active coroutine, should not be cancelled
                assertFalse(context.isCancelled)
                return ToolResult(toolCallId, "done")
            }
        }

        val executor = createExecutor(mapOf("read_notifications" to handler))

        val results = executor.dispatch(listOf(toolCall()))
        assertEquals("done", results[0].content)
    }
}
