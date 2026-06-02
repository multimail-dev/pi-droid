package dev.anthropic.pidroid.tools

import dev.anthropic.pidroid.capabilities.CapabilityGrant
import dev.anthropic.pidroid.core.message.ContentBlock
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Collections

class ToolExecutorSequentialTest {
    private lateinit var permissionChecker: FakePermissionChecker
    private lateinit var registry: ToolRegistry
    private lateinit var gate: ConfirmationGate

    @Before
    fun setup() {
        permissionChecker = FakePermissionChecker()
        registry = ToolRegistry(
            permissionChecker = permissionChecker,
            config = ToolRegistryConfig(
                generatedToolPolicy = GeneratedToolPolicy.AUTO_APPROVE_READ_ONLY,
            ),
        )
    }

    private fun createExecutor(handlers: Map<String, ToolHandler>): ToolExecutor {
        gate = ConfirmationGate()
        return ToolExecutor(registry, gate, handlers)
    }

    private fun makeExtensionTool(
        name: String,
        dispatchMode: ToolDispatchMode = ToolDispatchMode.PARALLEL,
    ) = ToolDefinition(
        name = name,
        description = "Test tool $name",
        inputSchema = JsonObject(emptyMap()),
        category = ToolCategory.DEVICE,
        riskLevel = RiskLevel.READ_ONLY,
        dispatchMode = dispatchMode,
        isExtension = true,
    )

    private suspend fun registerTools(vararg tools: ToolDefinition) {
        // Need at least one capability for tools to activate
        permissionChecker.notificationListenerEnabled = true
        registry.declareCapability(CapabilityGrant(CapabilityGrant.CAPABILITY_NOTIFICATION_LISTENER))
        for (tool in tools) {
            registry.registerExtensionTool(tool)
        }
    }

    @Test
    fun `all PARALLEL tools dispatch concurrently`() = runTest {
        val toolA = makeExtensionTool("tool_a", ToolDispatchMode.PARALLEL)
        val toolB = makeExtensionTool("tool_b", ToolDispatchMode.PARALLEL)
        registerTools(toolA, toolB)

        val executionOrder = Collections.synchronizedList(mutableListOf<String>())
        val handlers = mapOf<String, ToolHandler>(
            "tool_a" to object : ToolHandler {
                override suspend fun execute(
                    toolCallId: String,
                    arguments: JsonObject,
                    context: ToolExecutionContext,
                ): ToolResult {
                    executionOrder.add("a_start")
                    delay(100)
                    executionOrder.add("a_end")
                    return ToolResult(toolCallId, "result_a")
                }
            },
            "tool_b" to object : ToolHandler {
                override suspend fun execute(
                    toolCallId: String,
                    arguments: JsonObject,
                    context: ToolExecutionContext,
                ): ToolResult {
                    executionOrder.add("b_start")
                    delay(100)
                    executionOrder.add("b_end")
                    return ToolResult(toolCallId, "result_b")
                }
            },
        )

        val executor = createExecutor(handlers)
        val results = executor.dispatch(
            listOf(
                ContentBlock.ToolCall("id1", "tool_a", JsonObject(emptyMap())),
                ContentBlock.ToolCall("id2", "tool_b", JsonObject(emptyMap())),
            ),
        )

        assertEquals(2, results.size)
        assertEquals("result_a", results[0].content)
        assertEquals("result_b", results[1].content)
        // In parallel mode with virtual time, both start before either ends
        // (runTest advances virtual time for all coroutines simultaneously)
        assertTrue(
            "Both tools should start before either ends in parallel",
            executionOrder.indexOf("b_start") < executionOrder.indexOf("a_end"),
        )
    }

    @Test
    fun `SEQUENTIAL tool forces entire batch to serial execution`() = runTest {
        val seqTool = makeExtensionTool("tool_seq", ToolDispatchMode.SEQUENTIAL)
        val parTool = makeExtensionTool("tool_par", ToolDispatchMode.PARALLEL)
        registerTools(seqTool, parTool)

        val executionOrder = mutableListOf<String>()
        val handlers = mapOf<String, ToolHandler>(
            "tool_seq" to object : ToolHandler {
                override suspend fun execute(
                    toolCallId: String,
                    arguments: JsonObject,
                    context: ToolExecutionContext,
                ): ToolResult {
                    executionOrder.add("seq_start")
                    delay(50)
                    executionOrder.add("seq_end")
                    return ToolResult(toolCallId, "result_seq")
                }
            },
            "tool_par" to object : ToolHandler {
                override suspend fun execute(
                    toolCallId: String,
                    arguments: JsonObject,
                    context: ToolExecutionContext,
                ): ToolResult {
                    executionOrder.add("par_start")
                    delay(50)
                    executionOrder.add("par_end")
                    return ToolResult(toolCallId, "result_par")
                }
            },
        )

        val executor = createExecutor(handlers)
        val results = executor.dispatch(
            listOf(
                ContentBlock.ToolCall("id1", "tool_seq", JsonObject(emptyMap())),
                ContentBlock.ToolCall("id2", "tool_par", JsonObject(emptyMap())),
            ),
        )

        assertEquals(2, results.size)
        assertEquals("result_seq", results[0].content)
        assertEquals("result_par", results[1].content)
        // In sequential mode, first tool must complete before second starts
        assertEquals(listOf("seq_start", "seq_end", "par_start", "par_end"), executionOrder)
    }

    @Test
    fun `single tool in batch works regardless of dispatch mode`() = runTest {
        val seqTool = makeExtensionTool("tool_single", ToolDispatchMode.SEQUENTIAL)
        registerTools(seqTool)

        val handlers = mapOf<String, ToolHandler>(
            "tool_single" to object : ToolHandler {
                override suspend fun execute(
                    toolCallId: String,
                    arguments: JsonObject,
                    context: ToolExecutionContext,
                ): ToolResult {
                    return ToolResult(toolCallId, "single_result")
                }
            },
        )

        val executor = createExecutor(handlers)
        val results = executor.dispatch(
            listOf(ContentBlock.ToolCall("id1", "tool_single", JsonObject(emptyMap()))),
        )

        assertEquals(1, results.size)
        assertEquals("single_result", results[0].content)
        assertEquals(false, results[0].isError)
    }

    @Test
    fun `unknown tool returns error in sequential path`() = runTest {
        val seqTool = makeExtensionTool("tool_seq", ToolDispatchMode.SEQUENTIAL)
        registerTools(seqTool)

        val handlers = mapOf<String, ToolHandler>(
            "tool_seq" to object : ToolHandler {
                override suspend fun execute(
                    toolCallId: String,
                    arguments: JsonObject,
                    context: ToolExecutionContext,
                ): ToolResult {
                    return ToolResult(toolCallId, "ok")
                }
            },
        )

        val executor = createExecutor(handlers)
        // Mix known sequential tool with unknown tool.
        // Unknown tool triggers parallel path (getToolByName returns null, so dispatchMode
        // check is null != SEQUENTIAL → false), but the known seq tool makes batch sequential.
        val results = executor.dispatch(
            listOf(
                ContentBlock.ToolCall("id1", "tool_seq", JsonObject(emptyMap())),
                ContentBlock.ToolCall("id2", "nonexistent_tool", JsonObject(emptyMap())),
            ),
        )

        assertEquals(2, results.size)
        assertEquals("ok", results[0].content)
        assertEquals(false, results[0].isError)
        assertTrue(results[1].isError)
        assertTrue(results[1].content.contains("Unknown tool"))
    }

    @Test
    fun `unknown tool alone returns error via parallel path`() = runTest {
        permissionChecker.notificationListenerEnabled = true
        registry.declareCapability(CapabilityGrant(CapabilityGrant.CAPABILITY_NOTIFICATION_LISTENER))

        val executor = createExecutor(emptyMap())
        val results = executor.dispatch(
            listOf(ContentBlock.ToolCall("id1", "nonexistent_tool", JsonObject(emptyMap()))),
        )

        assertEquals(1, results.size)
        assertTrue(results[0].isError)
        assertTrue(results[0].content.contains("Unknown tool"))
    }
}
