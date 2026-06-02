package dev.anthropic.pidroid.agent

import dev.anthropic.pidroid.core.message.Message
import dev.anthropic.pidroid.core.model.StopReason

/**
 * Result of a single agent loop turn.
 *
 * Captures what happened and whether more turns are needed.
 */
data class TurnResult(
    /** The assistant's response message for this turn */
    val message: Message.Assistant,
    /** Why this turn ended */
    val stopReason: StopReason,
    /** Tool results generated during this turn (empty if no tool calls) */
    val toolResults: List<Message.ToolResult>,
    /** Whether the loop should continue (tool calls pending or follow-ups) */
    val shouldContinue: Boolean,
)
