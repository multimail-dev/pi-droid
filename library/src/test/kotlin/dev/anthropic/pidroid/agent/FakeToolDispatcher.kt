package dev.anthropic.pidroid.agent

import dev.anthropic.pidroid.core.message.ContentBlock
import dev.anthropic.pidroid.tools.ToolDispatcher
import dev.anthropic.pidroid.tools.ToolResult

/**
 * Fake tool dispatcher for agent loop testing.
 *
 * Returns canned results mapped by tool name. Preserves source order as
 * required by the ToolDispatcher contract.
 */
class FakeToolDispatcher : ToolDispatcher {
    /** Canned results by tool name. Falls back to error result if not found. */
    val results = mutableMapOf<String, (ContentBlock.ToolCall) -> ToolResult>()

    /** Record of dispatched batches */
    val dispatches = mutableListOf<List<ContentBlock.ToolCall>>()

    override suspend fun dispatch(toolCalls: List<ContentBlock.ToolCall>): List<ToolResult> {
        dispatches.add(toolCalls)
        return toolCalls.map { tc ->
            val handler = results[tc.name]
            if (handler != null) {
                handler(tc)
            } else {
                ToolResult(
                    toolCallId = tc.id,
                    content = "Fake result for ${tc.name}",
                    isError = false,
                )
            }
        }
    }
}
