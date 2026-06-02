package dev.anthropic.pidroid.tools

import dev.anthropic.pidroid.capabilities.CapabilityGrant
import dev.anthropic.pidroid.core.message.ContentBlock
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ToolExecutorTest {
    private lateinit var permissionChecker: FakePermissionChecker
    private lateinit var registry: ToolRegistry
    private lateinit var gate: ConfirmationGate

    @Before
    fun setup() {
        permissionChecker = FakePermissionChecker()
        permissionChecker.notificationListenerEnabled = true
        registry = ToolRegistry(permissionChecker)
    }

    private fun createExecutor(handlers: Map<String, ToolHandler>): ToolExecutor {
        gate = ConfirmationGate()
        return ToolExecutor(registry, gate, handlers)
    }

    private fun fakeHandler(content: String = "ok") = object : ToolHandler {
        override suspend fun execute(
            toolCallId: String,
            arguments: JsonObject,
            context: ToolExecutionContext,
        ) = ToolResult(toolCallId = toolCallId, content = content)
    }

    @Test
    fun `AUTOMATIC policy executes immediately`() = runTest {
        registry.declareCapability(CapabilityGrant(CapabilityGrant.CAPABILITY_NOTIFICATION_LISTENER))
        val executor = createExecutor(mapOf("read_notifications" to fakeHandler("3 notifications")))

        val results = executor.dispatch(
            listOf(ContentBlock.ToolCall("tc_1", "read_notifications", JsonObject(emptyMap())))
        )

        assertEquals(1, results.size)
        assertEquals("3 notifications", results[0].content)
        assertEquals(false, results[0].isError)
    }

    @Test
    fun `USER_CONFIRM_MODAL suspends until approved`() = runTest {
        // create_calendar_event has USER_CONFIRM_MODAL
        permissionChecker.grantedPermissions.add("android.permission.WRITE_CALENDAR")
        registry.declareCapability(CapabilityGrant("android.permission.WRITE_CALENDAR"))
        val executor = createExecutor(mapOf("create_calendar_event" to fakeHandler("created")))

        val args = buildJsonObject {
            put("title", "Meeting")
            put("start_time", "2026-05-08T10:00:00Z")
            put("end_time", "2026-05-08T11:00:00Z")
        }

        // Start auto-approver BEFORE dispatching
        val approverJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            gate.requests.collect { req ->
                gate.respond(req.requestId, ConfirmationResult.APPROVED)
            }
        }

        val results = executor.dispatch(
            listOf(ContentBlock.ToolCall("tc_cal", "create_calendar_event", args))
        )

        approverJob.cancel()
        assertEquals("created", results[0].content)
    }

    @Test
    fun `USER_CONFIRM_MODAL denied returns error`() = runTest {
        permissionChecker.grantedPermissions.add("android.permission.WRITE_CALENDAR")
        registry.declareCapability(CapabilityGrant("android.permission.WRITE_CALENDAR"))
        val executor = createExecutor(mapOf("create_calendar_event" to fakeHandler()))

        val args = buildJsonObject {
            put("title", "X")
            put("start_time", "2026-05-08T10:00:00Z")
            put("end_time", "2026-05-08T11:00:00Z")
        }

        // Start denier BEFORE dispatching
        val denierJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            gate.requests.collect { req ->
                gate.respond(req.requestId, ConfirmationResult.DENIED)
            }
        }

        val results = executor.dispatch(
            listOf(ContentBlock.ToolCall("tc_deny", "create_calendar_event", args))
        )

        denierJob.cancel()
        assertTrue(results[0].isError)
        assertTrue(results[0].content.contains("denied"))
    }

    @Test
    fun `BLOCKED policy returns error immediately`() = runTest {
        permissionChecker.notificationListenerEnabled = true
        registry.declareCapability(CapabilityGrant(CapabilityGrant.CAPABILITY_NOTIFICATION_LISTENER))
        // Override read_notifications to BLOCKED
        registry.registerToolOverride("read_notifications", ToolOverride(
            confirmationPolicy = ConfirmationPolicy.BLOCKED,
        ))
        val executor = createExecutor(mapOf("read_notifications" to fakeHandler()))

        val results = executor.dispatch(
            listOf(ContentBlock.ToolCall("tc_blocked", "read_notifications", JsonObject(emptyMap())))
        )

        assertTrue(results[0].isError)
        assertTrue(results[0].content.contains("blocked"))
    }

    @Test
    fun `unknown tool returns error`() = runTest {
        registry.declareCapability(CapabilityGrant(CapabilityGrant.CAPABILITY_NOTIFICATION_LISTENER))
        val executor = createExecutor(emptyMap())

        val results = executor.dispatch(
            listOf(ContentBlock.ToolCall("tc_unknown", "nonexistent_tool", JsonObject(emptyMap())))
        )

        assertTrue(results[0].isError)
        assertTrue(results[0].content.contains("Unknown tool"))
    }

    @Test
    fun `tool handler exception caught and returned as error`() = runTest {
        registry.declareCapability(CapabilityGrant(CapabilityGrant.CAPABILITY_NOTIFICATION_LISTENER))
        val throwingHandler = object : ToolHandler {
            override suspend fun execute(
                toolCallId: String,
                arguments: JsonObject,
                context: ToolExecutionContext,
            ): ToolResult = throw RuntimeException("Handler crashed")
        }
        val executor = createExecutor(mapOf("read_notifications" to throwingHandler))

        val results = executor.dispatch(
            listOf(ContentBlock.ToolCall("tc_throw", "read_notifications", JsonObject(emptyMap())))
        )

        assertTrue(results[0].isError)
        assertTrue(results[0].content.contains("failed"))
    }

    @Test
    fun `tool execution timeout returns error`() = runTest {
        registry.declareCapability(CapabilityGrant(CapabilityGrant.CAPABILITY_NOTIFICATION_LISTENER))
        // Override timeout to 100ms (virtual time — delay advances past it)
        registry.registerToolOverride("read_notifications", ToolOverride(timeoutMs = 100))

        val slowHandler = object : ToolHandler {
            override suspend fun execute(
                toolCallId: String,
                arguments: JsonObject,
                context: ToolExecutionContext,
            ): ToolResult {
                delay(10_000) // virtual time: advances past the 100ms timeout
                return ToolResult(toolCallId, "done")
            }
        }
        val executor = createExecutor(mapOf("read_notifications" to slowHandler))

        val results = executor.dispatch(
            listOf(ContentBlock.ToolCall("tc_slow", "read_notifications", JsonObject(emptyMap())))
        )

        assertTrue(results[0].isError)
        assertTrue(results[0].content.contains("timed out"))
    }

    @Test
    fun `batch execution returns results in source order`() = runTest {
        registry.declareCapability(CapabilityGrant(CapabilityGrant.CAPABILITY_NOTIFICATION_LISTENER))
        registry.declareCapability(CapabilityGrant(CapabilityGrant.CAPABILITY_DEVICE_STATE))

        val handlers = mapOf(
            "read_notifications" to fakeHandler("notifs"),
            "get_battery_state" to fakeHandler("battery_ok"),
        )
        val executor = createExecutor(handlers)

        val results = executor.dispatch(
            listOf(
                ContentBlock.ToolCall("tc_1", "read_notifications", JsonObject(emptyMap())),
                ContentBlock.ToolCall("tc_2", "get_battery_state", JsonObject(emptyMap())),
            )
        )

        assertEquals(2, results.size)
        assertEquals("tc_1", results[0].toolCallId)
        assertEquals("notifs", results[0].content)
        assertEquals("tc_2", results[1].toolCallId)
        assertEquals("battery_ok", results[1].content)
    }
}
