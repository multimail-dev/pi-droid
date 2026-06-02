package dev.anthropic.pidroid.extension

import dev.anthropic.pidroid.core.event.AgentEvent
import dev.anthropic.pidroid.core.event.AgentEventType
import dev.anthropic.pidroid.core.model.StopReason
import dev.anthropic.pidroid.tools.ConfirmationPolicy
import dev.anthropic.pidroid.tools.RiskLevel
import dev.anthropic.pidroid.tools.ToolCategory
import dev.anthropic.pidroid.tools.ToolDefinition
import dev.anthropic.pidroid.tools.ToolExecutionContext
import dev.anthropic.pidroid.tools.ToolHandler
import dev.anthropic.pidroid.tools.ToolResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ExtensionApiTest {

    private fun fakeToolHandler() = object : ToolHandler {
        override suspend fun execute(
            toolCallId: String,
            arguments: JsonObject,
            context: ToolExecutionContext,
        ): ToolResult = ToolResult(toolCallId, "ok")
    }

    private fun fakeTool(name: String) = ToolDefinition(
        name = name,
        description = "Test tool $name",
        inputSchema = buildJsonObject { put("type", "object") },
        category = ToolCategory.DEVICE,
        riskLevel = RiskLevel.READ_ONLY,
        isExtension = true,
    )

    // --- PiExtension compile check ---

    @Test
    fun `PiExtension can be implemented and onRegister called`() = runTest {
        val extension = object : PiExtension {
            override val name = "test-extension"
            var registered = false

            override suspend fun onRegister(api: ExtensionApi) {
                registered = true
                api.registerTool(fakeTool("ext_tool"), fakeToolHandler())
            }
        }

        val registry = ExtensionRegistry()
        extension.onRegister(registry)
        assertTrue(extension.registered)
        assertEquals(1, registry.tools.size)
    }

    // --- ExtensionApi.registerTool ---

    @Test
    fun `registerTool accepts ToolDefinition and ToolHandler pair`() {
        val registry = ExtensionRegistry()
        registry.registerTool(fakeTool("my_tool"), fakeToolHandler())
        assertTrue("my_tool" in registry.tools)
    }

    @Test
    fun `registerTool rejects duplicate tool names`() {
        val registry = ExtensionRegistry()
        registry.registerTool(fakeTool("dup_tool"), fakeToolHandler())
        try {
            registry.registerTool(fakeTool("dup_tool"), fakeToolHandler())
            fail("Should have thrown IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("dup_tool"))
        }
    }

    // --- ExtensionApi.on ---

    @Test
    fun `on accepts all AgentEventType enum values`() {
        val registry = ExtensionRegistry()
        for (eventType in AgentEventType.entries) {
            registry.on(eventType) { _ -> /* no-op */ }
        }
        assertEquals(AgentEventType.entries.size, registry.eventHandlers.size)
    }

    // --- Freeze behavior ---

    @Test
    fun `registerTool throws after freeze`() {
        val registry = ExtensionRegistry()
        registry.freeze()
        try {
            registry.registerTool(fakeTool("late_tool"), fakeToolHandler())
            fail("Should have thrown IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("frozen"))
        }
    }

    @Test
    fun `on throws after freeze`() {
        val registry = ExtensionRegistry()
        registry.freeze()
        try {
            registry.on(AgentEventType.AGENT_START) { _ -> }
            fail("Should have thrown IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("frozen"))
        }
    }

    // --- Event dispatch ---

    @Test
    fun `dispatchEvent calls handlers in registration order`() = runTest {
        val registry = ExtensionRegistry()
        val callOrder = mutableListOf<Int>()

        registry.on(AgentEventType.AGENT_END) { callOrder.add(1) }
        registry.on(AgentEventType.AGENT_END) { callOrder.add(2) }
        registry.on(AgentEventType.AGENT_END) { callOrder.add(3) }
        registry.freeze()

        registry.dispatchEvent(AgentEvent.AgentEnd(StopReason.STOP))
        assertEquals(listOf(1, 2, 3), callOrder)
    }

    @Test
    fun `dispatchEvent does not propagate handler exceptions`() = runTest {
        val registry = ExtensionRegistry()
        var secondCalled = false

        registry.on(AgentEventType.AGENT_END) { throw RuntimeException("boom") }
        registry.on(AgentEventType.AGENT_END) { secondCalled = true }
        registry.freeze()

        // Should not throw
        registry.dispatchEvent(AgentEvent.AgentEnd(StopReason.STOP))
        assertTrue("Second handler should still be called", secondCalled)
    }
}
