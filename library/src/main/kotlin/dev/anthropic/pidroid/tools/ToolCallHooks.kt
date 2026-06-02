package dev.anthropic.pidroid.tools

import dev.anthropic.pidroid.core.message.ContentBlock
import dev.anthropic.pidroid.core.message.Message

/**
 * Context provided to the beforeToolCall hook.
 */
data class BeforeToolCallContext(
    val toolCall: ContentBlock.ToolCall,
    val toolDef: ToolDefinition,
    val messages: List<Message>,
)

/**
 * Decision returned by beforeToolCall.
 */
sealed class ToolCallDecision {
    /** Allow the tool to proceed */
    data object Proceed : ToolCallDecision()
    /** Block the tool with a reason */
    data class Block(val reason: String) : ToolCallDecision()
}

/**
 * Context provided to the afterToolCall hook.
 */
data class AfterToolCallContext(
    val toolCall: ContentBlock.ToolCall,
    val toolDef: ToolDefinition,
    val result: ToolResult,
    val isError: Boolean,
)

/**
 * Override returned by afterToolCall.
 * null fields keep original values.
 */
data class ToolResultOverride(
    val content: String? = null,
    val isError: Boolean? = null,
)

/**
 * Tool call lifecycle hooks.
 * Passed to ToolExecutor to enable host-level interception.
 */
data class ToolCallHooks(
    val beforeToolCall: (suspend (BeforeToolCallContext) -> ToolCallDecision)? = null,
    val afterToolCall: (suspend (AfterToolCallContext) -> ToolResultOverride?)? = null,
)
