package dev.anthropic.pidroid.extension

import dev.anthropic.pidroid.core.event.AgentEventType
import dev.anthropic.pidroid.tools.ToolDefinition
import dev.anthropic.pidroid.tools.ToolHandler

/**
 * API surface available to extensions during registration.
 *
 * Extensions use this to register tools and subscribe to events.
 * The API is only valid during [PiExtension.onRegister] — calls after
 * initialization completes will throw [IllegalStateException].
 */
interface ExtensionApi {
    /**
     * Register a tool that this extension provides.
     *
     * The tool will be added to the registry with [ToolDefinition.isExtension] = true
     * and subject to the host's generated tool policy.
     *
     * @param definition The tool definition (must have unique name)
     * @param handler The handler that executes this tool
     * @throws IllegalArgumentException if a tool with this name is already registered
     */
    fun registerTool(definition: ToolDefinition, handler: ToolHandler)

    /**
     * Subscribe to agent events of a specific type.
     *
     * Multiple handlers can subscribe to the same event type.
     * Handlers are called in registration order.
     *
     * @param eventType The event type to listen for
     * @param handler The handler to invoke
     */
    fun on(eventType: AgentEventType, handler: EventHandler)
}
