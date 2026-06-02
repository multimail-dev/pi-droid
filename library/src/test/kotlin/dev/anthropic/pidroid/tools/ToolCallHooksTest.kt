package dev.anthropic.pidroid.tools

import dev.anthropic.pidroid.capabilities.CapabilityGrant
import dev.anthropic.pidroid.core.message.ContentBlock
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ToolCallHooksTest {
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
        hooks: ToolCallHooks = ToolCallHooks(),
    ): ToolExecutor {
        gate = ConfirmationGate()
        return ToolExecutor(registry, gate, handlers, hooks)
    }

    private fun fakeHandler(content: String = "ok") = object : ToolHandler {
        override suspend fun execute(
            toolCallId: String,
            arguments: JsonObject,
            context: ToolExecutionContext,
        ) = ToolResult(toolCallId = toolCallId, content = content)
    }

    private fun toolCall(id: String = "tc_1", name: String = "read_notifications") =
        ContentBlock.ToolCall(id, name, JsonObject(emptyMap()))

    @Test
    fun `beforeToolCall Proceed allows tool execution`() = runTest {
        activateRegistry()
        val hooks = ToolCallHooks(
            beforeToolCall = { ToolCallDecision.Proceed },
        )
        val executor = createExecutor(mapOf("read_notifications" to fakeHandler("success")), hooks)

        val results = executor.dispatch(listOf(toolCall()))

        assertEquals(1, results.size)
        assertEquals("success", results[0].content)
        assertFalse(results[0].isError)
    }

    @Test
    fun `beforeToolCall Block prevents tool execution`() = runTest {
        activateRegistry()
        var handlerCalled = false
        val handler = object : ToolHandler {
            override suspend fun execute(
                toolCallId: String,
                arguments: JsonObject,
                context: ToolExecutionContext,
            ): ToolResult {
                handlerCalled = true
                return ToolResult(toolCallId, "should not reach here")
            }
        }

        val hooks = ToolCallHooks(
            beforeToolCall = { ToolCallDecision.Block("rate limited") },
        )
        val executor = createExecutor(mapOf("read_notifications" to handler), hooks)

        val results = executor.dispatch(listOf(toolCall()))

        assertEquals(1, results.size)
        assertTrue(results[0].isError)
        assertEquals("rate limited", results[0].content)
        assertFalse(handlerCalled)
    }

    @Test
    fun `afterToolCall returns override with modified content`() = runTest {
        activateRegistry()
        val hooks = ToolCallHooks(
            afterToolCall = { ctx ->
                ToolResultOverride(content = "redacted: ${ctx.result.content}")
            },
        )
        val executor = createExecutor(mapOf("read_notifications" to fakeHandler("sensitive data")), hooks)

        val results = executor.dispatch(listOf(toolCall()))

        assertEquals(1, results.size)
        assertEquals("redacted: sensitive data", results[0].content)
        assertFalse(results[0].isError)
    }

    @Test
    fun `afterToolCall returns override with modified isError`() = runTest {
        activateRegistry()
        val hooks = ToolCallHooks(
            afterToolCall = { ToolResultOverride(isError = true) },
        )
        val executor = createExecutor(mapOf("read_notifications" to fakeHandler("output")), hooks)

        val results = executor.dispatch(listOf(toolCall()))

        assertEquals(1, results.size)
        assertEquals("output", results[0].content)
        assertTrue(results[0].isError)
    }

    @Test
    fun `afterToolCall returns null keeps result unchanged`() = runTest {
        activateRegistry()
        val hooks = ToolCallHooks(
            afterToolCall = { null },
        )
        val executor = createExecutor(mapOf("read_notifications" to fakeHandler("original")), hooks)

        val results = executor.dispatch(listOf(toolCall()))

        assertEquals(1, results.size)
        assertEquals("original", results[0].content)
        assertFalse(results[0].isError)
    }

    @Test
    fun `both hooks null preserves existing behavior`() = runTest {
        activateRegistry()
        val hooks = ToolCallHooks(beforeToolCall = null, afterToolCall = null)
        val executor = createExecutor(mapOf("read_notifications" to fakeHandler("normal")), hooks)

        val results = executor.dispatch(listOf(toolCall()))

        assertEquals(1, results.size)
        assertEquals("normal", results[0].content)
        assertFalse(results[0].isError)
    }

    @Test
    fun `default ToolCallHooks (no hooks) preserves existing behavior`() = runTest {
        activateRegistry()
        val executor = createExecutor(mapOf("read_notifications" to fakeHandler("normal")))

        val results = executor.dispatch(listOf(toolCall()))

        assertEquals(1, results.size)
        assertEquals("normal", results[0].content)
        assertFalse(results[0].isError)
    }

    @Test
    fun `beforeToolCall throws exception treated as Block`() = runTest {
        activateRegistry()
        val hooks = ToolCallHooks(
            beforeToolCall = { throw RuntimeException("hook crashed") },
        )
        val executor = createExecutor(mapOf("read_notifications" to fakeHandler()), hooks)

        val results = executor.dispatch(listOf(toolCall()))

        assertEquals(1, results.size)
        assertTrue(results[0].isError)
        assertTrue(results[0].content.contains("beforeToolCall hook failed"))
        assertTrue(results[0].content.contains("hook crashed"))
    }

    @Test
    fun `afterToolCall throws exception keeps original result`() = runTest {
        activateRegistry()
        val hooks = ToolCallHooks(
            afterToolCall = { throw RuntimeException("post-hook crashed") },
        )
        val executor = createExecutor(mapOf("read_notifications" to fakeHandler("preserved")), hooks)

        val results = executor.dispatch(listOf(toolCall()))

        assertEquals(1, results.size)
        assertEquals("preserved", results[0].content)
        assertFalse(results[0].isError)
    }

    @Test
    fun `beforeToolCall receives correct context`() = runTest {
        activateRegistry()
        var receivedContext: BeforeToolCallContext? = null
        val hooks = ToolCallHooks(
            beforeToolCall = { ctx ->
                receivedContext = ctx
                ToolCallDecision.Proceed
            },
        )
        val executor = createExecutor(mapOf("read_notifications" to fakeHandler()), hooks)

        executor.dispatch(listOf(toolCall("my_id", "read_notifications")))

        val ctx = receivedContext!!
        assertEquals("my_id", ctx.toolCall.id)
        assertEquals("read_notifications", ctx.toolCall.name)
        assertEquals("read_notifications", ctx.toolDef.name)
        assertTrue(ctx.messages.isEmpty())
    }

    @Test
    fun `afterToolCall receives correct context`() = runTest {
        activateRegistry()
        var receivedContext: AfterToolCallContext? = null
        val hooks = ToolCallHooks(
            afterToolCall = { ctx ->
                receivedContext = ctx
                null
            },
        )
        val executor = createExecutor(mapOf("read_notifications" to fakeHandler("result_data")), hooks)

        executor.dispatch(listOf(toolCall("my_id", "read_notifications")))

        val ctx = receivedContext!!
        assertEquals("my_id", ctx.toolCall.id)
        assertEquals("read_notifications", ctx.toolCall.name)
        assertEquals("read_notifications", ctx.toolDef.name)
        assertEquals("result_data", ctx.result.content)
        assertFalse(ctx.isError)
    }
}
