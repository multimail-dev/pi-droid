package dev.anthropic.pidroid.llm

import dev.anthropic.pidroid.core.message.ContentBlock
import dev.anthropic.pidroid.core.message.Message
import dev.anthropic.pidroid.core.model.StopReason
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageTransformerTest {

    // -- helpers --

    private fun assistantText(text: String, stopReason: StopReason? = StopReason.STOP) =
        Message.Assistant(
            content = listOf(ContentBlock.Text(text)),
            stopReason = stopReason,
        )

    private fun assistantWithThinking(thinking: String, text: String) =
        Message.Assistant(
            content = listOf(
                ContentBlock.Thinking(thinking),
                ContentBlock.Text(text),
            ),
            stopReason = StopReason.STOP,
        )

    private fun toolCall(id: String = "tc_1", name: String = "read_file") =
        ContentBlock.ToolCall(
            id = id,
            name = name,
            arguments = buildJsonObject { put("path", "/tmp/test") },
        )

    private fun assistantWithToolCall(
        id: String = "tc_1",
        toolName: String = "read_file",
        stopReason: StopReason? = StopReason.TOOL_USE,
    ) = Message.Assistant(
        content = listOf(
            ContentBlock.Text("Let me check that."),
            toolCall(id, toolName),
        ),
        stopReason = stopReason,
    )

    private fun toolResult(id: String = "tc_1", content: String = "file contents") =
        Message.ToolResult(toolCallId = id, content = content)

    // ── Same-provider pass-through ──────────────────────────────────────

    @Test
    fun `same-provider messages pass through unchanged`() {
        val messages = listOf(
            Message.User("hello"),
            assistantText("hi there"),
        )
        val result = MessageTransformer.transform(
            messages,
            targetProvider = "anthropic",
            targetApi = "messages",
            sourceProvider = "anthropic",
        )
        assertEquals(messages, result)
    }

    @Test
    fun `null source provider treated as same provider — pass through`() {
        val messages = listOf(
            Message.User("hello"),
            assistantText("hi"),
        )
        val result = MessageTransformer.transform(
            messages,
            targetProvider = "openai",
            targetApi = "chat",
            sourceProvider = null,
        )
        assertEquals(messages, result)
    }

    @Test
    fun `same-provider comparison is case-insensitive`() {
        val messages = listOf(Message.User("test"), assistantText("ok"))
        val result = MessageTransformer.transform(
            messages,
            targetProvider = "Anthropic",
            targetApi = "messages",
            sourceProvider = "ANTHROPIC",
        )
        assertEquals(messages, result)
    }

    // ── Thinking block conversion ───────────────────────────────────────

    @Test
    fun `thinking blocks from different provider converted to text with delimiters`() {
        val messages = listOf(
            Message.User("explain"),
            assistantWithThinking("I should think about this...", "Here is my answer."),
        )
        val result = MessageTransformer.transform(
            messages,
            targetProvider = "openai",
            targetApi = "chat",
            sourceProvider = "anthropic",
        )

        assertEquals(2, result.size)
        val assistant = result[1] as Message.Assistant
        assertEquals(2, assistant.content.size)

        val thinkingAsText = assistant.content[0] as ContentBlock.Text
        assertEquals("<thinking>\nI should think about this...\n</thinking>", thinkingAsText.text)

        val regularText = assistant.content[1] as ContentBlock.Text
        assertEquals("Here is my answer.", regularText.text)
    }

    @Test
    fun `empty thinking blocks are dropped during cross-provider transform`() {
        val messages = listOf(
            Message.User("test"),
            Message.Assistant(
                content = listOf(
                    ContentBlock.Thinking("   "),
                    ContentBlock.Text("answer"),
                ),
                stopReason = StopReason.STOP,
            ),
        )
        val result = MessageTransformer.transform(
            messages,
            targetProvider = "openai",
            targetApi = "chat",
            sourceProvider = "anthropic",
        )

        val assistant = result[1] as Message.Assistant
        assertEquals(1, assistant.content.size)
        assertTrue(assistant.content[0] is ContentBlock.Text)
        assertEquals("answer", (assistant.content[0] as ContentBlock.Text).text)
    }

    // ── Errored message stripping ───────────────────────────────────────

    @Test
    fun `errored assistant messages stripped from history`() {
        val messages = listOf(
            Message.User("do something"),
            assistantText("oops", StopReason.ERROR),
            Message.User("try again"),
            assistantText("here you go"),
        )
        val result = MessageTransformer.transform(
            messages,
            targetProvider = "openai",
            targetApi = "chat",
            sourceProvider = "anthropic",
        )

        assertEquals(3, result.size)
        assertEquals(Message.User("do something"), result[0])
        assertEquals(Message.User("try again"), result[1])
        val assistant = result[2] as Message.Assistant
        assertEquals("here you go", assistant.text)
    }

    @Test
    fun `aborted assistant messages stripped from history`() {
        val messages = listOf(
            Message.User("start"),
            assistantText("cancelled", StopReason.ABORTED),
            Message.User("retry"),
            assistantText("done"),
        )
        val result = MessageTransformer.transform(
            messages,
            targetProvider = "openai",
            targetApi = "chat",
            sourceProvider = "anthropic",
        )

        assertEquals(3, result.size)
        // The aborted assistant message should be gone
        assertTrue(result.none { it is Message.Assistant && (it as Message.Assistant).stopReason == StopReason.ABORTED })
    }

    // ── Tool call ID normalization ──────────────────────────────────────

    @Test
    fun `tool call IDs truncated for Mistral target`() {
        val longId = "call_abcdefghij_1234567890"
        val messages = listOf(
            Message.User("run tool"),
            assistantWithToolCall(id = longId),
            toolResult(id = longId, content = "result"),
        )
        val result = MessageTransformer.transform(
            messages,
            targetProvider = "mistral",
            targetApi = "chat",
            sourceProvider = "anthropic",
        )

        val assistant = result[1] as Message.Assistant
        val tc = assistant.toolCalls.first()
        assertEquals(9, tc.id.length)
        assertEquals("call_abcd", tc.id)

        // Tool result ID must match
        val tr = result[2] as Message.ToolResult
        assertEquals(tc.id, tr.toolCallId)
    }

    @Test
    fun `tool call IDs get toolu_ prefix for Anthropic target`() {
        val openaiId = "call_abc123"
        val messages = listOf(
            Message.User("run tool"),
            assistantWithToolCall(id = openaiId),
            toolResult(id = openaiId, content = "done"),
        )
        val result = MessageTransformer.transform(
            messages,
            targetProvider = "anthropic",
            targetApi = "messages",
            sourceProvider = "openai",
        )

        val assistant = result[1] as Message.Assistant
        val tc = assistant.toolCalls.first()
        assertTrue("ID should start with toolu_: ${tc.id}", tc.id.startsWith("toolu_"))

        val tr = result[2] as Message.ToolResult
        assertEquals(tc.id, tr.toolCallId)
    }

    @Test
    fun `tool call IDs already valid for target are not changed`() {
        val validId = "toolu_abc123"
        val messages = listOf(
            Message.User("run"),
            assistantWithToolCall(id = validId),
            toolResult(id = validId, content = "ok"),
        )
        val result = MessageTransformer.transform(
            messages,
            targetProvider = "anthropic",
            targetApi = "messages",
            sourceProvider = "openai",
        )

        val assistant = result[1] as Message.Assistant
        assertEquals(validId, assistant.toolCalls.first().id)
    }

    @Test
    fun `tool call IDs with special chars cleaned for Anthropic target`() {
        // OpenAI Responses API can produce IDs with pipes and long strings
        val dirtyId = "call|abc+def=ghi"
        val messages = listOf(
            Message.User("run"),
            assistantWithToolCall(id = dirtyId),
            toolResult(id = dirtyId, content = "ok"),
        )
        val result = MessageTransformer.transform(
            messages,
            targetProvider = "anthropic",
            targetApi = "messages",
            sourceProvider = "openai",
        )

        val assistant = result[1] as Message.Assistant
        val tc = assistant.toolCalls.first()
        // No special chars remain
        assertTrue("ID has no special chars: ${tc.id}", tc.id.matches(Regex("^[a-zA-Z0-9_-]+$")))
        assertTrue("ID starts with toolu_: ${tc.id}", tc.id.startsWith("toolu_"))
    }

    // ── Orphaned tool call handling ─────────────────────────────────────

    @Test
    fun `orphaned tool calls get synthetic error results`() {
        val messages = listOf(
            Message.User("run tool"),
            assistantWithToolCall(id = "tc_orphan"),
            // No tool result follows — conversation was interrupted
            Message.User("what happened?"),
            assistantText("Sorry about that."),
        )
        val result = MessageTransformer.transform(
            messages,
            targetProvider = "openai",
            targetApi = "chat",
            sourceProvider = "anthropic",
        )

        // Should have: user, assistant(tc), synthetic_tool_result, user, assistant
        assertEquals(5, result.size)
        val synthetic = result[2] as Message.ToolResult
        assertEquals("tc_orphan", synthetic.toolCallId)
        assertTrue(synthetic.isError)
        assertTrue(synthetic.content.contains("interrupted"))
    }

    @Test
    fun `tool calls with matching results are not treated as orphans`() {
        val messages = listOf(
            Message.User("run tool"),
            assistantWithToolCall(id = "tc_ok"),
            toolResult(id = "tc_ok", content = "success"),
            assistantText("Got the result."),
        )
        val result = MessageTransformer.transform(
            messages,
            targetProvider = "openai",
            targetApi = "chat",
            sourceProvider = "anthropic",
        )

        // No synthetic results inserted — same count as input
        assertEquals(4, result.size)
        // No error tool results
        val toolResults = result.filterIsInstance<Message.ToolResult>()
        assertEquals(1, toolResults.size)
        assertEquals(false, toolResults[0].isError)
    }

    @Test
    fun `trailing orphaned tool calls get synthetic results at end`() {
        val messages = listOf(
            Message.User("run tool"),
            assistantWithToolCall(id = "tc_trailing"),
            // Conversation ends without tool result
        )
        val result = MessageTransformer.transform(
            messages,
            targetProvider = "openai",
            targetApi = "chat",
            sourceProvider = "anthropic",
        )

        assertEquals(3, result.size)
        val synthetic = result[2] as Message.ToolResult
        assertEquals("tc_trailing", synthetic.toolCallId)
        assertTrue(synthetic.isError)
    }

    // ── Edge cases ──────────────────────────────────────────────────────

    @Test
    fun `empty message list returns empty list`() {
        val result = MessageTransformer.transform(
            emptyList(),
            targetProvider = "openai",
            targetApi = "chat",
            sourceProvider = "anthropic",
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `mixed message types processed correctly`() {
        val messages = listOf(
            Message.System("You are a helpful assistant."),
            Message.User("Hello"),
            assistantWithThinking("Let me think...", "Hi!"),
            Message.User("Run this tool"),
            assistantWithToolCall(id = "tc_mix"),
            toolResult(id = "tc_mix", content = "tool output"),
            assistantText("Here is the result."),
        )
        val result = MessageTransformer.transform(
            messages,
            targetProvider = "openai",
            targetApi = "chat",
            sourceProvider = "anthropic",
        )

        assertEquals(7, result.size)

        // System message preserved
        assertTrue(result[0] is Message.System)

        // Thinking converted
        val firstAssistant = result[2] as Message.Assistant
        val thinkingText = firstAssistant.content[0] as ContentBlock.Text
        assertTrue(thinkingText.text.startsWith("<thinking>"))
    }

    @Test
    fun `assistant message with only empty thinking blocks is dropped`() {
        val messages = listOf(
            Message.User("test"),
            Message.Assistant(
                content = listOf(ContentBlock.Thinking("")),
                stopReason = StopReason.STOP,
            ),
            Message.User("try again"),
            assistantText("ok"),
        )
        val result = MessageTransformer.transform(
            messages,
            targetProvider = "openai",
            targetApi = "chat",
            sourceProvider = "anthropic",
        )

        // The all-empty-thinking assistant should be dropped
        assertEquals(3, result.size)
        assertTrue(result[0] is Message.User)
        assertTrue(result[1] is Message.User)
        assertTrue(result[2] is Message.Assistant)
    }

    // ── normalizeToolCallId unit tests ──────────────────────────────────

    @Test
    fun `normalizeToolCallId — Mistral truncates long IDs`() {
        val result = MessageTransformer.normalizeToolCallId("abcdefghij", "mistral")
        assertEquals("abcdefghi", result)
    }

    @Test
    fun `normalizeToolCallId — Mistral short ID unchanged`() {
        val result = MessageTransformer.normalizeToolCallId("abc", "mistral")
        assertEquals("abc", result)
    }

    @Test
    fun `normalizeToolCallId — Anthropic adds toolu_ prefix`() {
        val result = MessageTransformer.normalizeToolCallId("call_123", "anthropic")
        assertEquals("toolu_call_123", result)
    }

    @Test
    fun `normalizeToolCallId — Anthropic keeps existing toolu_ prefix`() {
        val result = MessageTransformer.normalizeToolCallId("toolu_existing", "anthropic")
        assertEquals("toolu_existing", result)
    }

    @Test
    fun `normalizeToolCallId — unknown provider returns ID unchanged`() {
        val result = MessageTransformer.normalizeToolCallId("any_id_format", "google")
        assertEquals("any_id_format", result)
    }
}
