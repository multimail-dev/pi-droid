package dev.anthropic.pidroid.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Configuration snapshot emitted at agent_start.
 *
 * Captures the resolved configuration for observability and debugging.
 * Immutable after creation.
 */
@Serializable
data class AgentConfig(
    /** LLM provider name (e.g., "anthropic", "openai") */
    val provider: String,
    /** Model identifier */
    val model: String,
    /** Maximum turns before auto-stop */
    @SerialName("max_turns")
    val maxTurns: Int,
    /** Number of tools available */
    @SerialName("tool_count")
    val toolCount: Int,
    /** Whether semantic memory is enabled */
    @SerialName("memory_enabled")
    val memoryEnabled: Boolean = false,
)
