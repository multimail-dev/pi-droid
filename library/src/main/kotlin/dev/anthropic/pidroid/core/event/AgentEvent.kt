package dev.anthropic.pidroid.core.event

import dev.anthropic.pidroid.core.message.ContentBlock
import dev.anthropic.pidroid.core.message.Message
import dev.anthropic.pidroid.core.model.AgentConfig
import dev.anthropic.pidroid.core.model.StopReason
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Events emitted by the agent loop during execution.
 *
 * Maps to Pi's event model:
 * - agent_start/end bracket the entire agent invocation
 * - turn_start/end bracket each LLM turn
 * - message_start/update/end bracket streaming message assembly
 * - tool_execution_start/update/end bracket tool execution
 *
 * Consumers observe these via SharedFlow to build UI state.
 */
@Serializable
sealed class AgentEvent {
    /** Type discriminator for extension event subscriptions */
    abstract val type: AgentEventType

    @Serializable
    @SerialName("agent_start")
    data class AgentStart(
        val config: AgentConfig,
    ) : AgentEvent() {
        override val type: AgentEventType get() = AgentEventType.AGENT_START
    }

    @Serializable
    @SerialName("agent_end")
    data class AgentEnd(
        val reason: StopReason,
    ) : AgentEvent() {
        override val type: AgentEventType get() = AgentEventType.AGENT_END
    }

    @Serializable
    @SerialName("turn_start")
    data class TurnStart(
        @SerialName("turn_index")
        val turnIndex: Int,
    ) : AgentEvent() {
        override val type: AgentEventType get() = AgentEventType.TURN_START
    }

    @Serializable
    @SerialName("turn_end")
    data class TurnEnd(
        @SerialName("turn_index")
        val turnIndex: Int,
        @SerialName("stop_reason")
        val stopReason: StopReason,
    ) : AgentEvent() {
        override val type: AgentEventType get() = AgentEventType.TURN_END
    }

    @Serializable
    @SerialName("message_start")
    data class MessageStart(
        val message: Message.Assistant,
    ) : AgentEvent() {
        override val type: AgentEventType get() = AgentEventType.MESSAGE_START
    }

    @Serializable
    @SerialName("message_update")
    data class MessageUpdate(
        val delta: ContentBlock,
    ) : AgentEvent() {
        override val type: AgentEventType get() = AgentEventType.MESSAGE_UPDATE
    }

    @Serializable
    @SerialName("message_end")
    data class MessageEnd(
        val message: Message.Assistant,
    ) : AgentEvent() {
        override val type: AgentEventType get() = AgentEventType.MESSAGE_END
    }

    @Serializable
    @SerialName("tool_execution_start")
    data class ToolExecutionStart(
        @SerialName("tool_call_id")
        val toolCallId: String,
        @SerialName("tool_name")
        val toolName: String,
    ) : AgentEvent() {
        override val type: AgentEventType get() = AgentEventType.TOOL_EXECUTION_START
    }

    @Serializable
    @SerialName("tool_execution_update")
    data class ToolExecutionUpdate(
        @SerialName("tool_call_id")
        val toolCallId: String,
        val progress: String,
    ) : AgentEvent() {
        override val type: AgentEventType get() = AgentEventType.TOOL_EXECUTION_UPDATE
    }

    @Serializable
    @SerialName("tool_execution_end")
    data class ToolExecutionEnd(
        @SerialName("tool_call_id")
        val toolCallId: String,
        val result: Message.ToolResult,
    ) : AgentEvent() {
        override val type: AgentEventType get() = AgentEventType.TOOL_EXECUTION_END
    }
}
