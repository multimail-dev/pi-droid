package dev.anthropic.pidroid.tools

import dev.anthropic.pidroid.core.message.ContentBlock

/**
 * Interface for batch tool dispatch.
 *
 * The agent loop depends on this interface to execute tool calls. The real
 * implementation (ToolExecutor, IU-3.2) handles confirmation gates, permission
 * checks, and actual handler dispatch. In Phase 2 tests, FakeToolDispatcher
 * returns canned results.
 *
 * ## Behavioral Contract (from Pi)
 * - Preflight: sequentially validate each tool call (schema, permissions, risk)
 * - Execute: concurrently run all approved tool calls
 * - Results: return in source order (same order as input tool calls),
 *   regardless of completion order
 *
 * ## Cancellation
 * If the parent coroutine is cancelled, all in-flight tool executions
 * are cancelled cooperatively.
 */
interface ToolDispatcher {
    /**
     * Execute a batch of tool calls.
     *
     * @param toolCalls The tool calls from the assistant message (in source order)
     * @return Results in the same order as input tool calls
     */
    suspend fun dispatch(toolCalls: List<ContentBlock.ToolCall>): List<ToolResult>
}
