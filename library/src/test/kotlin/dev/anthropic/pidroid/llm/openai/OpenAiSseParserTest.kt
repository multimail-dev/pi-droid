package dev.anthropic.pidroid.llm.openai

import dev.anthropic.pidroid.core.model.StopReason
import dev.anthropic.pidroid.llm.AssistantMessageEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OpenAiSseParserTest {
    private lateinit var parser: OpenAiSseParser

    @Before
    fun setup() {
        parser = OpenAiSseParser()
    }

    @Test
    fun `first chunk produces Start event`() {
        val data = """{"id":"chatcmpl-123","object":"chat.completion.chunk","created":1234567890,"model":"gpt-4o","choices":[{"index":0,"delta":{"role":"assistant","content":""},"finish_reason":null}]}"""
        val events = parser.parse(data)
        assertTrue(events.any { it is AssistantMessageEvent.Start })
    }

    @Test
    fun `content delta produces TextDelta`() {
        // First chunk to trigger start
        parser.parse("""{"id":"chatcmpl-123","object":"chat.completion.chunk","created":1234567890,"model":"gpt-4o","choices":[{"index":0,"delta":{"role":"assistant","content":""},"finish_reason":null}]}""")
        val events = parser.parse("""{"id":"chatcmpl-123","object":"chat.completion.chunk","created":1234567890,"model":"gpt-4o","choices":[{"index":0,"delta":{"content":"Hello"},"finish_reason":null}]}""")
        assertTrue(events.any { it is AssistantMessageEvent.TextDelta })
        assertEquals("Hello", (events.first { it is AssistantMessageEvent.TextDelta } as AssistantMessageEvent.TextDelta).text)
    }

    @Test
    fun `DONE sentinel produces Done event`() {
        // Set up some state first
        parser.parse("""{"id":"chatcmpl-123","object":"chat.completion.chunk","created":1234567890,"model":"gpt-4o","choices":[{"index":0,"delta":{"role":"assistant","content":"Hi"},"finish_reason":null}]}""")
        parser.parse("""{"id":"chatcmpl-123","object":"chat.completion.chunk","created":1234567890,"model":"gpt-4o","choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}""")
        val events = parser.parse("[DONE]")
        assertTrue(events.any { it is AssistantMessageEvent.Done })
        val done = events.first { it is AssistantMessageEvent.Done } as AssistantMessageEvent.Done
        assertEquals(StopReason.STOP, done.stopReason)
    }

    @Test
    fun `tool_calls finish_reason maps to TOOL_USE`() {
        parser.parse("""{"id":"chatcmpl-123","object":"chat.completion.chunk","created":1234567890,"model":"gpt-4o","choices":[{"index":0,"delta":{"role":"assistant","content":null},"finish_reason":null}]}""")
        parser.parse("""{"id":"chatcmpl-123","object":"chat.completion.chunk","created":1234567890,"model":"gpt-4o","choices":[{"index":0,"delta":{},"finish_reason":"tool_calls"}]}""")
        val events = parser.parse("[DONE]")
        val done = events.first { it is AssistantMessageEvent.Done } as AssistantMessageEvent.Done
        assertEquals(StopReason.TOOL_USE, done.stopReason)
    }

    @Test
    fun `tool_calls delta produces ToolCallDelta`() {
        parser.parse("""{"id":"chatcmpl-123","object":"chat.completion.chunk","created":1234567890,"model":"gpt-4o","choices":[{"index":0,"delta":{"role":"assistant","content":null},"finish_reason":null}]}""")
        val events = parser.parse("""{"id":"chatcmpl-123","object":"chat.completion.chunk","created":1234567890,"model":"gpt-4o","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"call_abc","type":"function","function":{"name":"search","arguments":""}}]},"finish_reason":null}]}""")
        assertTrue(events.any { it is AssistantMessageEvent.ToolCallDelta })
    }

    @Test
    fun `tool call arguments accumulated across multiple deltas`() {
        parser.parse("""{"id":"chatcmpl-123","object":"chat.completion.chunk","created":1234567890,"model":"gpt-4o","choices":[{"index":0,"delta":{"role":"assistant","content":null},"finish_reason":null}]}""")
        parser.parse("""{"id":"chatcmpl-123","object":"chat.completion.chunk","created":1234567890,"model":"gpt-4o","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"call_abc","type":"function","function":{"name":"search","arguments":"{\"q"}}]},"finish_reason":null}]}""")
        parser.parse("""{"id":"chatcmpl-123","object":"chat.completion.chunk","created":1234567890,"model":"gpt-4o","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"uery\":\"hi\"}"}}]},"finish_reason":null}]}""")
        parser.parse("""{"id":"chatcmpl-123","object":"chat.completion.chunk","created":1234567890,"model":"gpt-4o","choices":[{"index":0,"delta":{},"finish_reason":"tool_calls"}]}""")
        val events = parser.parse("[DONE]")
        val done = events.first { it is AssistantMessageEvent.Done } as AssistantMessageEvent.Done
        val toolCall = done.message.toolCalls.first()
        assertEquals("search", toolCall.name)
        assertEquals("call_abc", toolCall.id)
    }

    @Test
    fun `malformed data produces Error event`() {
        val events = parser.parse("not valid json {{{")
        assertTrue(events.any { it is AssistantMessageEvent.Error })
    }

    @Test
    fun `empty data returns empty list`() {
        val events = parser.parse("")
        assertTrue(events.isEmpty())
    }

    @Test
    fun `usage parsed from final chunk`() {
        parser.parse("""{"id":"chatcmpl-123","object":"chat.completion.chunk","created":1234567890,"model":"gpt-4o","choices":[{"index":0,"delta":{"role":"assistant","content":"Hi"},"finish_reason":null}]}""")
        parser.parse("""{"id":"chatcmpl-123","object":"chat.completion.chunk","created":1234567890,"model":"gpt-4o","choices":[{"index":0,"delta":{},"finish_reason":"stop"}],"usage":{"prompt_tokens":50,"completion_tokens":25,"total_tokens":75}}""")
        val events = parser.parse("[DONE]")
        val done = events.first { it is AssistantMessageEvent.Done } as AssistantMessageEvent.Done
        assertNotNull(done.usage)
        assertEquals(50, done.usage!!.inputTokens)
        assertEquals(25, done.usage!!.outputTokens)
    }

    @Test
    fun `length finish_reason maps to LENGTH`() {
        parser.parse("""{"id":"chatcmpl-123","object":"chat.completion.chunk","created":1234567890,"model":"gpt-4o","choices":[{"index":0,"delta":{"role":"assistant","content":"x"},"finish_reason":null}]}""")
        parser.parse("""{"id":"chatcmpl-123","object":"chat.completion.chunk","created":1234567890,"model":"gpt-4o","choices":[{"index":0,"delta":{},"finish_reason":"length"}]}""")
        val events = parser.parse("[DONE]")
        val done = events.first { it is AssistantMessageEvent.Done } as AssistantMessageEvent.Done
        assertEquals(StopReason.LENGTH, done.stopReason)
    }
}
