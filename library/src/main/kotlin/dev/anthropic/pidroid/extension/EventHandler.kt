package dev.anthropic.pidroid.extension

import dev.anthropic.pidroid.core.event.AgentEvent

/**
 * Handler for agent events, registered by extensions.
 *
 * Event handlers are notification-only — they cannot modify the event or
 * block the agent loop. Use for logging, analytics, UI updates, etc.
 */
fun interface EventHandler {
    /**
     * Called when a matching event is emitted.
     *
     * @param event The agent event
     */
    suspend fun handle(event: AgentEvent)
}
