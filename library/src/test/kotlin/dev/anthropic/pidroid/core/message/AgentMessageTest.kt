package dev.anthropic.pidroid.core.message

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentMessageTest {

    @Test
    fun `Message subtypes implement AgentMessage`() {
        val user: AgentMessage = Message.User("hello")
        assertEquals(Role.USER, user.role)
        assertEquals(0L, user.timestamp) // default from interface
    }

    @Test
    fun `all Message subtypes are AgentMessage instances`() {
        val messages: List<AgentMessage> = listOf(
            Message.User("text"),
            Message.Assistant(content = listOf(ContentBlock.Text("hi"))),
            Message.ToolResult(toolCallId = "id", content = "ok"),
            Message.System(content = "you are helpful"),
        )
        assertEquals(4, messages.size)
        // All should pass filterIsInstance<Message>
        assertEquals(4, messages.filterIsInstance<Message>().size)
    }

    @Test
    fun `CustomMessage has tag and payload`() {
        val payload = buildJsonObject { put("key", "value") }
        val custom = CustomMessage(tag = "notification", payload = payload)
        assertEquals("notification", custom.tag)
        assertEquals(payload, custom.payload)
        assertEquals(Role.USER, custom.role)
        assertTrue(custom.timestamp > 0)
    }

    @Test
    fun `CustomMessage with custom role`() {
        val custom = CustomMessage(tag = "status", role = Role.ASSISTANT)
        assertEquals(Role.ASSISTANT, custom.role)
    }

    @Test
    fun `filterIsInstance separates CustomMessage from standard messages`() {
        val messages: List<AgentMessage> = listOf(
            Message.User("hello"),
            CustomMessage(tag = "status", payload = JsonObject(emptyMap())),
            Message.User("world"),
        )

        // filterIsInstance<Message> excludes CustomMessage (not a Message subclass)
        val llmMessages = messages.filterIsInstance<Message>()
        assertEquals(2, llmMessages.size)
    }

    @Test
    fun `host-defined AgentMessage implementation excluded from LLM messages`() {
        // Simulates a host creating their own AgentMessage type
        val hostMessage = object : AgentMessage {
            override val role = Role.USER
            override val timestamp = 12345L
        }

        val messages: List<AgentMessage> = listOf(
            Message.User("hello"),
            hostMessage,
            CustomMessage(tag = "artifact"),
            Message.User("world"),
        )

        // filterIsInstance<Message> gives only standard LLM-compatible messages
        val llmMessages = messages.filterIsInstance<Message>()
        assertEquals(2, llmMessages.size)
        assertTrue(llmMessages[0] is Message.User)
        assertTrue(llmMessages[1] is Message.User)
    }

    @Test
    fun `Message backward compat - existing constructors unchanged`() {
        // These must compile and work exactly as before
        val user = Message.User("text")
        val userWithBlocks = Message.User(listOf(ContentBlock.Text("hi")))
        val assistant = Message.Assistant(content = listOf(ContentBlock.Text("hi")))
        val toolResult = Message.ToolResult(toolCallId = "id", content = "ok")
        val toolResultError = Message.ToolResult(toolCallId = "id", content = "err", isError = true)
        val system = Message.System(content = "you are helpful")

        assertNotNull(user)
        assertNotNull(userWithBlocks)
        assertNotNull(assistant)
        assertNotNull(toolResult)
        assertNotNull(toolResultError)
        assertNotNull(system)
    }

    @Test
    fun `timestamp defaults to 0 for backward compat on Message`() {
        val user = Message.User("hi")
        assertEquals(0L, user.timestamp)

        val assistant = Message.Assistant(content = emptyList())
        assertEquals(0L, assistant.timestamp)
    }

    @Test
    fun `CustomMessage timestamp captures creation time`() {
        val before = System.currentTimeMillis()
        val custom = CustomMessage(tag = "test")
        val after = System.currentTimeMillis()

        assertTrue(custom.timestamp in before..after)
    }

    @Test
    fun `AgentMessage list can hold mixed types`() {
        val mixed: MutableList<AgentMessage> = mutableListOf(
            Message.User("start"),
        )
        // Can add both Message and CustomMessage
        mixed.add(CustomMessage(tag = "metadata"))
        mixed.add(Message.Assistant(content = listOf(ContentBlock.Text("response"))))

        assertEquals(3, mixed.size)
        // Only 2 are standard Messages
        assertEquals(2, mixed.filterIsInstance<Message>().size)
    }
}
