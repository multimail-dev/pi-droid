package dev.anthropic.pidroid.core.message

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Message role in the conversation.
 */
@Serializable
enum class Role {
    @SerialName("user")
    USER,

    @SerialName("assistant")
    ASSISTANT,

    @SerialName("tool")
    TOOL,
}
