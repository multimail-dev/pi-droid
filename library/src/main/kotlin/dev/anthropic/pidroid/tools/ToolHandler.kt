package dev.anthropic.pidroid.tools

import kotlinx.serialization.json.JsonObject

/**
 * Interface for tool execution implementations.
 *
 * Each tool in the catalog has a corresponding [ToolHandler] that performs
 * the actual work. Handlers are registered in the tool registry and dispatched
 * by the tool executor.
 *
 * ## Contract
 * - Implementations MUST be cancellation-cooperative (check `currentCoroutineContext().isActive`)
 * - Implementations MUST NOT throw — errors are reported via [ToolResult.isError]
 * - Implementations MAY emit progress via the [ToolExecutionContext]
 *
 * ## Thread Safety
 * Handlers may be called concurrently for different tool call IDs.
 * Handlers for the same tool name will NOT be called concurrently
 * (tool-level serialization is handled by the executor).
 */
interface ToolHandler {
    /**
     * Execute the tool with the given arguments.
     *
     * @param toolCallId Unique ID for this invocation (for correlation)
     * @param arguments The tool input as a JSON object
     * @param context Execution context for progress reporting and cancellation
     * @return The tool result (success or error)
     */
    suspend fun execute(
        toolCallId: String,
        arguments: JsonObject,
        context: ToolExecutionContext,
    ): ToolResult
}

/**
 * Context provided to tool handlers during execution.
 *
 * Allows handlers to report progress and check cancellation state.
 */
interface ToolExecutionContext {
    /** Report incremental progress (displayed in UI) */
    suspend fun reportProgress(message: String)

    /** Check if execution has been cancelled */
    val isCancelled: Boolean
}
