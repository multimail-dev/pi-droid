package dev.anthropic.pidroid.core.event

import dev.anthropic.pidroid.core.message.ContentBlock
import dev.anthropic.pidroid.core.message.Message
import dev.anthropic.pidroid.core.model.AgentConfig
import dev.anthropic.pidroid.core.model.StopReason
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class AgentEventTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `AgentStart round-trips serialization`() {
        val config = AgentConfig(
            provider = "anthropic",
            model = "claude-sonnet-4-20250514",
            maxTurns = 25,
            toolCount = 18,
            memoryEnabled = true,
        )
        val event = AgentEvent.AgentStart(config)
        val encoded = json.encodeToString<AgentEvent>(event)
        val decoded = json.decodeFromString<AgentEvent>(encoded)
        assertEquals(event, decoded)
    }

    @Test
    fun `AgentEnd round-trips serialization`() {
        val event = AgentEvent.AgentEnd(reason = StopReason.STOP)
        val encoded = json.encodeToString<AgentEvent>(event)
        val decoded = json.decodeFromString<AgentEvent>(encoded)
        assertEquals(event, decoded)
    }

    @Test
    fun `TurnStart round-trips serialization`() {
        val event = AgentEvent.TurnStart(turnIndex = 0)
        val encoded = json.encodeToString<AgentEvent>(event)
        val decoded = json.decodeFromString<AgentEvent>(encoded)
        assertEquals(event, decoded)
    }

    @Test
    fun `TurnEnd round-trips serialization`() {
        val event = AgentEvent.TurnEnd(turnIndex = 2, stopReason = StopReason.TOOL_USE)
        val encoded = json.encodeToString<AgentEvent>(event)
        val decoded = json.decodeFromString<AgentEvent>(encoded)
        assertEquals(event, decoded)
    }

    @Test
    fun `MessageStart round-trips serialization`() {
        val msg = Message.Assistant(
            content = listOf(ContentBlock.Text("Hello")),
            stopReason = null,
        )
        val event = AgentEvent.MessageStart(message = msg)
        val encoded = json.encodeToString<AgentEvent>(event)
        val decoded = json.decodeFromString<AgentEvent>(encoded)
        assertEquals(event, decoded)
    }

    @Test
    fun `MessageUpdate with text delta round-trips`() {
        val event = AgentEvent.MessageUpdate(delta = ContentBlock.Text("chunk"))
        val encoded = json.encodeToString<AgentEvent>(event)
        val decoded = json.decodeFromString<AgentEvent>(encoded)
        assertEquals(event, decoded)
    }

    @Test
    fun `MessageEnd round-trips serialization`() {
        val msg = Message.Assistant(
            content = listOf(ContentBlock.Text("Complete response")),
            stopReason = StopReason.STOP,
        )
        val event = AgentEvent.MessageEnd(message = msg)
        val encoded = json.encodeToString<AgentEvent>(event)
        val decoded = json.decodeFromString<AgentEvent>(encoded)
        assertEquals(event, decoded)
    }

    @Test
    fun `ToolExecutionStart round-trips serialization`() {
        val event = AgentEvent.ToolExecutionStart(
            toolCallId = "tc_001",
            toolName = "read_notifications",
        )
        val encoded = json.encodeToString<AgentEvent>(event)
        val decoded = json.decodeFromString<AgentEvent>(encoded)
        assertEquals(event, decoded)
    }

    @Test
    fun `ToolExecutionUpdate round-trips serialization`() {
        val event = AgentEvent.ToolExecutionUpdate(
            toolCallId = "tc_001",
            progress = "Fetching 12 notifications...",
        )
        val encoded = json.encodeToString<AgentEvent>(event)
        val decoded = json.decodeFromString<AgentEvent>(encoded)
        assertEquals(event, decoded)
    }

    @Test
    fun `ToolExecutionEnd round-trips serialization`() {
        val result = Message.ToolResult(
            toolCallId = "tc_001",
            content = """[{"title":"Meeting at 3pm"}]""",
            isError = false,
        )
        val event = AgentEvent.ToolExecutionEnd(
            toolCallId = "tc_001",
            result = result,
        )
        val encoded = json.encodeToString<AgentEvent>(event)
        val decoded = json.decodeFromString<AgentEvent>(encoded)
        assertEquals(event, decoded)
    }

    // --- Event type discriminators ---

    @Test
    fun `All event types have correct type discriminator`() {
        val config = AgentConfig("test", "model", 25, 0)
        val msg = Message.Assistant(emptyList())
        val result = Message.ToolResult("id", "content")

        val events = listOf(
            AgentEvent.AgentStart(config) to AgentEventType.AGENT_START,
            AgentEvent.AgentEnd(StopReason.STOP) to AgentEventType.AGENT_END,
            AgentEvent.TurnStart(0) to AgentEventType.TURN_START,
            AgentEvent.TurnEnd(0, StopReason.STOP) to AgentEventType.TURN_END,
            AgentEvent.MessageStart(msg) to AgentEventType.MESSAGE_START,
            AgentEvent.MessageUpdate(ContentBlock.Text("x")) to AgentEventType.MESSAGE_UPDATE,
            AgentEvent.MessageEnd(msg) to AgentEventType.MESSAGE_END,
            AgentEvent.ToolExecutionStart("id", "name") to AgentEventType.TOOL_EXECUTION_START,
            AgentEvent.ToolExecutionUpdate("id", "prog") to AgentEventType.TOOL_EXECUTION_UPDATE,
            AgentEvent.ToolExecutionEnd("id", result) to AgentEventType.TOOL_EXECUTION_END,
        )

        for ((event, expectedType) in events) {
            assertEquals(
                "Event ${event::class.simpleName} should have type $expectedType",
                expectedType,
                event.type,
            )
        }
    }

    @Test
    fun `AgentEventType enum has exactly 10 values`() {
        assertEquals(10, AgentEventType.entries.size)
    }
}
