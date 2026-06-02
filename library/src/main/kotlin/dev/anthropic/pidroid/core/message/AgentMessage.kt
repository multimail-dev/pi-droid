package dev.anthropic.pidroid.core.message

import kotlinx.serialization.json.JsonObject

/**
 * Marker interface for all messages in the agent conversation.
 *
 * The core [Message] sealed class implements this for standard LLM messages.
 * Host apps can create their own implementations for custom message types
 * that participate in the transcript (steering, UI-only notifications, etc.)
 * but are invisible to the LLM.
 *
 * ## Contract
 * - Standard LLM messages: extend [Message] (which implements AgentMessage)
 * - Custom host messages: implement [AgentMessage] directly, or use [CustomMessage]
 * - The agent loop filters to [Message] instances before each LLM call
 * - transformContext operates on [AgentMessage] so hosts can see/filter custom messages
 */
interface AgentMessage {
    val role: Role

    /**
     * When this message was created. Defaults to 0L for backward compatibility
     * with existing [Message] subtypes that don't store timestamps.
     */
    val timestamp: Long get() = 0L
}

/**
 * Custom message for host-defined message types.
 *
 * Not a [Message] subclass — naturally excluded from LLM calls via
 * `filterIsInstance<Message>()`. Use this for UI notifications, metadata,
 * routing markers, or any host-specific message that should appear in the
 * transcript but remain invisible to the model.
 *
 * @property tag Host-defined type discriminator (e.g., "notification", "artifact")
 * @property payload Arbitrary JSON payload
 * @property role Role for ordering/display purposes (defaults to USER)
 * @property timestamp When this message was created
 */
data class CustomMessage(
    val tag: String,
    val payload: JsonObject = JsonObject(emptyMap()),
    override val role: Role = Role.USER,
    override val timestamp: Long = System.currentTimeMillis(),
) : AgentMessage
