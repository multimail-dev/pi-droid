package dev.anthropic.pidroid.llm.mistral

import dev.anthropic.pidroid.core.message.ContentBlock
import dev.anthropic.pidroid.core.message.Message
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MistralProviderTest {

    // -- Provider metadata --

    @Test
    fun `provider name is mistral`() {
        val provider = MistralProvider()
        assertEquals("mistral", provider.name)
    }

    // -- Compat settings --

    @Test
    fun `compat uses max_tokens field`() {
        assertEquals("max_tokens", MistralProvider.MISTRAL_COMPAT.maxTokensField)
    }

    @Test
    fun `compat requires tool result name`() {
        assertEquals(true, MistralProvider.MISTRAL_COMPAT.requiresToolResultName)
    }

    @Test
    fun `compat does not support usage in streaming`() {
        assertEquals(false, MistralProvider.MISTRAL_COMPAT.supportsUsageInStreaming)
    }

    @Test
    fun `compat does not support developer role`() {
        assertEquals(false, MistralProvider.MISTRAL_COMPAT.supportsDeveloperRole)
    }

    @Test
    fun `compat does not support strict mode`() {
        assertEquals(false, MistralProvider.MISTRAL_COMPAT.supportsStrictMode)
    }

    // -- Tool call ID length constant --

    @Test
    fun `tool call ID max length is 9`() {
        assertEquals(9, MistralProvider.MISTRAL_TOOL_CALL_ID_LENGTH)
    }

    // -- truncateToolCallId --

    @Test
    fun `truncateToolCallId returns short IDs unchanged`() {
        assertEquals("abc", truncateToolCallId("abc"))
    }

    @Test
    fun `truncateToolCallId returns exactly 9-char IDs unchanged`() {
        assertEquals("123456789", truncateToolCallId("123456789"))
    }

    @Test
    fun `truncateToolCallId truncates long IDs to 9 chars`() {
        assertEquals("call_abc_", truncateToolCallId("call_abc_def_ghi"))
    }

    @Test
    fun `truncateToolCallId handles empty string`() {
        assertEquals("", truncateToolCallId(""))
    }

    @Test
    fun `truncateToolCallId handles single char`() {
        assertEquals("x", truncateToolCallId("x"))
    }

    // -- normalizeToolCallIds --

    @Test
    fun `normalizeToolCallIds returns original list when all IDs within limit`() {
        val messages = listOf(
            Message.User("hello"),
            Message.Assistant(
                content = listOf(
                    ContentBlock.ToolCall(
                        id = "abc123",
                        name = "search",
                        arguments = buildJsonObject { put("q", "test") },
                    ),
                ),
            ),
            Message.ToolResult(toolCallId = "abc123", content = "result"),
        )
        val result = normalizeToolCallIds(messages)
        // Should return the same list reference when no normalization needed
        assertTrue(result === messages)
    }

    @Test
    fun `normalizeToolCallIds truncates long tool call ID in assistant message`() {
        val longId = "call_abc_def_ghi_jkl"
        val messages = listOf(
            Message.Assistant(
                content = listOf(
                    ContentBlock.ToolCall(
                        id = longId,
                        name = "search",
                        arguments = buildJsonObject { put("q", "test") },
                    ),
                ),
            ),
        )
        val result = normalizeToolCallIds(messages)
        val assistantMsg = result[0] as Message.Assistant
        val toolCall = assistantMsg.content[0] as ContentBlock.ToolCall
        assertEquals(9, toolCall.id.length)
        assertEquals(longId.take(9), toolCall.id)
    }

    @Test
    fun `normalizeToolCallIds truncates matching tool result ID consistently`() {
        val longId = "call_abc_def_ghi_jkl"
        val messages = listOf(
            Message.Assistant(
                content = listOf(
                    ContentBlock.ToolCall(
                        id = longId,
                        name = "search",
                        arguments = buildJsonObject { put("q", "test") },
                    ),
                ),
            ),
            Message.ToolResult(toolCallId = longId, content = "result"),
        )
        val result = normalizeToolCallIds(messages)
        val assistantMsg = result[0] as Message.Assistant
        val toolCall = assistantMsg.content[0] as ContentBlock.ToolCall
        val toolResult = result[1] as Message.ToolResult

        // Both should be truncated to the same value
        assertEquals(toolCall.id, toolResult.toolCallId)
        assertEquals(9, toolCall.id.length)
    }

    @Test
    fun `normalizeToolCallIds preserves non-tool messages unchanged`() {
        val messages = listOf(
            Message.User("hello"),
            Message.Assistant(
                content = listOf(ContentBlock.Text("Hi there")),
            ),
            Message.System("system prompt"),
        )
        val result = normalizeToolCallIds(messages)
        assertTrue(result === messages)
    }

    @Test
    fun `normalizeToolCallIds handles mixed short and long IDs`() {
        val shortId = "abc"
        val longId = "toolcall_abcdef12345"
        val messages = listOf(
            Message.Assistant(
                content = listOf(
                    ContentBlock.ToolCall(
                        id = shortId,
                        name = "tool_a",
                        arguments = buildJsonObject {},
                    ),
                    ContentBlock.ToolCall(
                        id = longId,
                        name = "tool_b",
                        arguments = buildJsonObject {},
                    ),
                ),
            ),
            Message.ToolResult(toolCallId = shortId, content = "a"),
            Message.ToolResult(toolCallId = longId, content = "b"),
        )
        val result = normalizeToolCallIds(messages)
        val assistantMsg = result[0] as Message.Assistant
        val toolCallA = assistantMsg.content[0] as ContentBlock.ToolCall
        val toolCallB = assistantMsg.content[1] as ContentBlock.ToolCall
        val resultA = result[1] as Message.ToolResult
        val resultB = result[2] as Message.ToolResult

        // Short ID unchanged
        assertEquals(shortId, toolCallA.id)
        assertEquals(shortId, resultA.toolCallId)

        // Long ID truncated
        assertEquals(longId.take(9), toolCallB.id)
        assertEquals(longId.take(9), resultB.toolCallId)
    }

    @Test
    fun `normalizeToolCallIds preserves text content in assistant messages`() {
        val longId = "toolcall_abcdef12345"
        val messages = listOf(
            Message.Assistant(
                content = listOf(
                    ContentBlock.Text("Let me search for that"),
                    ContentBlock.ToolCall(
                        id = longId,
                        name = "search",
                        arguments = buildJsonObject { put("q", "test") },
                    ),
                ),
            ),
        )
        val result = normalizeToolCallIds(messages)
        val assistantMsg = result[0] as Message.Assistant
        val textBlock = assistantMsg.content[0] as ContentBlock.Text
        assertEquals("Let me search for that", textBlock.text)
    }

    @Test
    fun `normalizeToolCallIds preserves tool result content and error flag`() {
        val longId = "toolcall_abcdef12345"
        val messages = listOf(
            Message.Assistant(
                content = listOf(
                    ContentBlock.ToolCall(
                        id = longId,
                        name = "search",
                        arguments = buildJsonObject {},
                    ),
                ),
            ),
            Message.ToolResult(
                toolCallId = longId,
                content = "error: not found",
                isError = true,
            ),
        )
        val result = normalizeToolCallIds(messages)
        val toolResult = result[1] as Message.ToolResult
        assertEquals("error: not found", toolResult.content)
        assertTrue(toolResult.isError)
        assertEquals(longId.take(9), toolResult.toolCallId)
    }
}
