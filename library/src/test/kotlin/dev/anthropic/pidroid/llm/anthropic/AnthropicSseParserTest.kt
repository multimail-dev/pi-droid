package dev.anthropic.pidroid.llm.anthropic

import dev.anthropic.pidroid.llm.AssistantMessageEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AnthropicSseParserTest {
    private lateinit var parser: AnthropicSseParser

    @Before
    fun setup() {
        parser = AnthropicSseParser()
    }

    @Test
    fun `message_start produces Start event`() {
        val data = """{"type":"message_start","message":{"id":"msg_01","type":"message","role":"assistant","content":[],"model":"claude-sonnet-4-20250514","stop_reason":null,"usage":{"input_tokens":25,"output_tokens":1}}}"""
        val event = parser.parse("message_start", data)
        assertTrue(event is AssistantMessageEvent.Start)
    }

    @Test
    fun `text content_block_delta produces TextDelta`() {
        // First set up a text block
        parser.parse("content_block_start", """{"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}""")
        val event = parser.parse("content_block_delta", """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hello world"}}""")
        assertTrue(event is AssistantMessageEvent.TextDelta)
        assertEquals("Hello world", (event as AssistantMessageEvent.TextDelta).text)
    }

    @Test
    fun `tool_use content_block_start produces ToolCallDelta with name`() {
        val event = parser.parse("content_block_start", """{"type":"content_block_start","index":1,"content_block":{"type":"tool_use","id":"toolu_01","name":"search_calendar"}}""")
        assertTrue(event is AssistantMessageEvent.ToolCallDelta)
        val delta = event as AssistantMessageEvent.ToolCallDelta
        assertEquals("toolu_01", delta.id)
        assertEquals("search_calendar", delta.name)
    }

    @Test
    fun `input_json_delta produces ToolCallDelta with arguments`() {
        // Set up tool block
        parser.parse("content_block_start", """{"type":"content_block_start","index":0,"content_block":{"type":"tool_use","id":"toolu_01","name":"test"}}""")
        val event = parser.parse("content_block_delta", """{"type":"content_block_delta","index":0,"delta":{"type":"input_json_delta","partial_json":"{\"query\":"}}""")
        assertTrue(event is AssistantMessageEvent.ToolCallDelta)
        assertEquals("{\"query\":", (event as AssistantMessageEvent.ToolCallDelta).argumentsDelta)
    }

    @Test
    fun `message_stop produces Done event`() {
        // Need message_delta first for stop reason
        parser.parse("message_delta", """{"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":15}}""")
        val event = parser.parse("message_stop", """{"type":"message_stop"}""")
        assertTrue(event is AssistantMessageEvent.Done)
    }

    @Test
    fun `error event produces Error event`() {
        val event = parser.parse("error", """{"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}""")
        assertTrue(event is AssistantMessageEvent.Error)
        assertEquals("Overloaded", (event as AssistantMessageEvent.Error).error)
    }

    @Test
    fun `malformed data produces Error event not exception`() {
        val event = parser.parse("message_start", "not valid json {{{")
        assertTrue(event is AssistantMessageEvent.Error)
    }

    @Test
    fun `empty data returns null`() {
        val event = parser.parse("message_start", "")
        assertTrue(event == null)
    }

    @Test
    fun `ping event returns null`() {
        val event = parser.parse("ping", """{"type":"ping"}""")
        assertTrue(event == null)
    }

    @Test
    fun `thinking content_block_delta produces ThinkingDelta`() {
        parser.parse("content_block_start", """{"type":"content_block_start","index":0,"content_block":{"type":"thinking","thinking":""}}""")
        val event = parser.parse("content_block_delta", """{"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"Let me think..."}}""")
        assertTrue(event is AssistantMessageEvent.ThinkingDelta)
        assertEquals("Let me think...", (event as AssistantMessageEvent.ThinkingDelta).text)
    }

    @Test
    fun `tool_use stop reason maps to TOOL_USE`() {
        parser.parse("message_delta", """{"type":"message_delta","delta":{"stop_reason":"tool_use"},"usage":{"output_tokens":10}}""")
        val event = parser.parse("message_stop", """{"type":"message_stop"}""")
        assertTrue(event is AssistantMessageEvent.Done)
        assertEquals(
            dev.anthropic.pidroid.core.model.StopReason.TOOL_USE,
            (event as AssistantMessageEvent.Done).stopReason
        )
    }

    @Test
    fun `token usage is parsed from message_start and message_delta`() {
        parser.parse("message_start", """{"type":"message_start","message":{"id":"msg_01","type":"message","role":"assistant","content":[],"model":"claude-sonnet-4-20250514","stop_reason":null,"usage":{"input_tokens":100,"output_tokens":0}}}""")
        parser.parse("message_delta", """{"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":50}}""")
        val event = parser.parse("message_stop", """{"type":"message_stop"}""")
        assertTrue(event is AssistantMessageEvent.Done)
        val done = event as AssistantMessageEvent.Done
        assertNotNull(done.usage)
        assertEquals(100, done.usage!!.inputTokens)
        assertEquals(50, done.usage!!.outputTokens)
    }

    @Test
    fun `multiple tool call arguments accumulated across deltas`() {
        parser.parse("content_block_start", """{"type":"content_block_start","index":0,"content_block":{"type":"tool_use","id":"toolu_01","name":"search"}}""")
        parser.parse("content_block_delta", """{"type":"content_block_delta","index":0,"delta":{"type":"input_json_delta","partial_json":"{\"q"}}""")
        parser.parse("content_block_delta", """{"type":"content_block_delta","index":0,"delta":{"type":"input_json_delta","partial_json":"uery\":\"hello\"}"}}""")
        parser.parse("message_delta", """{"type":"message_delta","delta":{"stop_reason":"tool_use"},"usage":{"output_tokens":10}}""")
        val event = parser.parse("message_stop", """{"type":"message_stop"}""")
        assertTrue(event is AssistantMessageEvent.Done)
        val done = event as AssistantMessageEvent.Done
        val toolCall = done.message.toolCalls.first()
        assertEquals("hello", toolCall.arguments["query"]?.let {
            (it as kotlinx.serialization.json.JsonPrimitive).content
        })
    }
}
