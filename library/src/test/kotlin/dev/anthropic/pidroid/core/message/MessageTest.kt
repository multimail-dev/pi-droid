package dev.anthropic.pidroid.core.message

import dev.anthropic.pidroid.core.model.StopReason
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // --- Round-trip serialization tests ---

    @Test
    fun `User message round-trips through serialization`() {
        val original = Message.User(
            content = listOf(ContentBlock.Text("Hello, agent"))
        )
        val encoded = json.encodeToString<Message>(original)
        val decoded = json.decodeFromString<Message>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `User message convenience constructor creates single text block`() {
        val msg = Message.User("Hello")
        assertEquals(1, msg.content.size)
        assertEquals("Hello", (msg.content[0] as ContentBlock.Text).text)
    }

    @Test
    fun `Assistant message round-trips with stop reason`() {
        val original = Message.Assistant(
            content = listOf(ContentBlock.Text("Here's my response")),
            stopReason = StopReason.STOP,
        )
        val encoded = json.encodeToString<Message>(original)
        val decoded = json.decodeFromString<Message>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `Assistant message round-trips with null stop reason`() {
        val original = Message.Assistant(
            content = listOf(ContentBlock.Text("Partial")),
            stopReason = null,
        )
        val encoded = json.encodeToString<Message>(original)
        val decoded = json.decodeFromString<Message>(encoded)
        assertEquals(original, decoded)
        assertNull((decoded as Message.Assistant).stopReason)
    }

    @Test
    fun `ToolResult message round-trips`() {
        val original = Message.ToolResult(
            toolCallId = "call_123",
            content = """{"result": "success"}""",
            isError = false,
        )
        val encoded = json.encodeToString<Message>(original)
        val decoded = json.decodeFromString<Message>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `ToolResult with isError true round-trips`() {
        val original = Message.ToolResult(
            toolCallId = "call_456",
            content = "Permission denied",
            isError = true,
        )
        val encoded = json.encodeToString<Message>(original)
        val decoded = json.decodeFromString<Message>(encoded)
        assertTrue((decoded as Message.ToolResult).isError)
    }

    @Test
    fun `System message round-trips`() {
        val original = Message.System(content = "You are a helpful assistant")
        val encoded = json.encodeToString<Message>(original)
        val decoded = json.decodeFromString<Message>(encoded)
        assertEquals(original, decoded)
    }

    // --- ContentBlock tests ---

    @Test
    fun `ToolCall preserves JsonObject arguments`() {
        val args = buildJsonObject {
            put("query", "meeting tomorrow")
            put("limit", 5)
        }
        val block = ContentBlock.ToolCall(
            id = "tc_001",
            name = "search_calendar",
            arguments = args,
        )
        val encoded = json.encodeToString<ContentBlock>(block)
        val decoded = json.decodeFromString<ContentBlock>(encoded) as ContentBlock.ToolCall
        assertEquals(args, decoded.arguments)
        assertEquals("meeting tomorrow", (decoded.arguments["query"] as JsonPrimitive).content)
    }

    @Test
    fun `Thinking content block round-trips`() {
        val original = ContentBlock.Thinking("Let me think about this...")
        val encoded = json.encodeToString<ContentBlock>(original)
        val decoded = json.decodeFromString<ContentBlock>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `Empty content blocks serialize correctly`() {
        val msg = Message.User(content = emptyList())
        val encoded = json.encodeToString<Message>(msg)
        val decoded = json.decodeFromString<Message>(encoded) as Message.User
        assertTrue(decoded.content.isEmpty())
    }

    // --- StopReason tests ---

    @Test
    fun `StopReason covers all expected values`() {
        val expected = setOf("stop", "length", "tool_use", "error", "aborted")
        val actual = StopReason.entries.map { json.encodeToString(it).trim('"') }.toSet()
        assertEquals(expected, actual)
    }

    // --- Role tests ---

    @Test
    fun `Message roles are correct`() {
        assertEquals(Role.USER, Message.User("hi").role)
        assertEquals(Role.ASSISTANT, Message.Assistant(emptyList()).role)
        assertEquals(Role.TOOL, Message.ToolResult("id", "content").role)
    }

    // --- Assistant convenience accessors ---

    @Test
    fun `Assistant toolCalls extracts tool call blocks`() {
        val msg = Message.Assistant(
            content = listOf(
                ContentBlock.Text("I'll search for that"),
                ContentBlock.ToolCall("tc_1", "search", JsonObject(emptyMap())),
                ContentBlock.Text("and also check"),
                ContentBlock.ToolCall("tc_2", "lookup", JsonObject(emptyMap())),
            )
        )
        assertEquals(2, msg.toolCalls.size)
        assertEquals("tc_1", msg.toolCalls[0].id)
        assertEquals("tc_2", msg.toolCalls[1].id)
    }

    @Test
    fun `Assistant text concatenates all text blocks`() {
        val msg = Message.Assistant(
            content = listOf(
                ContentBlock.Text("Hello "),
                ContentBlock.ToolCall("tc_1", "search", JsonObject(emptyMap())),
                ContentBlock.Text("world"),
            )
        )
        assertEquals("Hello world", msg.text)
    }

    // --- Forward compatibility ---

    @Test
    fun `Unknown fields in JSON are ignored`() {
        val jsonStr = """{"type":"user","content":[{"type":"text","text":"hi"}],"unknown_field":"value"}"""
        val decoded = json.decodeFromString<Message>(jsonStr)
        assertTrue(decoded is Message.User)
    }
}
