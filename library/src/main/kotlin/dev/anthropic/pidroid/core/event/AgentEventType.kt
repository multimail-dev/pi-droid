package dev.anthropic.pidroid.core.event

/**
 * Enumeration of all agent event types.
 *
 * Used by the extension system for event subscriptions.
 * Mirrors Pi's event model for protocol compatibility.
 */
enum class AgentEventType {
    AGENT_START,
    AGENT_END,
    TURN_START,
    TURN_END,
    MESSAGE_START,
    MESSAGE_UPDATE,
    MESSAGE_END,
    TOOL_EXECUTION_START,
    TOOL_EXECUTION_UPDATE,
    TOOL_EXECUTION_END,
}
