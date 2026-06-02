package dev.anthropic.pidroid.extension

import dev.anthropic.pidroid.core.event.AgentEvent
import dev.anthropic.pidroid.core.event.AgentEventType
import dev.anthropic.pidroid.tools.ToolDefinition
import dev.anthropic.pidroid.tools.ToolHandler

/**
 * Internal implementation of [ExtensionApi] that collects registrations.
 *
 * Created during initialization, populated by extensions, then frozen.
 * After [freeze], all mutation methods throw [IllegalStateException].
 *
 * ## Thread Safety
 * This is NOT thread-safe — it is only accessed from a single coroutine
 * during the sequential extension registration phase. After [freeze],
 * the state is effectively immutable (readers only).
 */
internal class ExtensionRegistry : ExtensionApi {
    private val _tools = mutableMapOf<String, Pair<ToolDefinition, ToolHandler>>()
    private val _eventHandlers = mutableMapOf<AgentEventType, MutableList<EventHandler>>()
    private var frozen = false

    /** All registered extension tools (read-only after freeze) */
    val tools: Map<String, Pair<ToolDefinition, ToolHandler>>
        get() = _tools

    /** All registered event handlers by type (read-only after freeze) */
    val eventHandlers: Map<AgentEventType, List<EventHandler>>
        get() = _eventHandlers

    override fun registerTool(definition: ToolDefinition, handler: ToolHandler) {
        check(!frozen) { "ExtensionApi is frozen — registration is only allowed during onRegister()" }
        require(definition.name !in _tools) {
            "Duplicate tool name: '${definition.name}' is already registered"
        }
        _tools[definition.name] = definition to handler
    }

    override fun on(eventType: AgentEventType, handler: EventHandler) {
        check(!frozen) { "ExtensionApi is frozen — registration is only allowed during onRegister()" }
        _eventHandlers.getOrPut(eventType) { mutableListOf() }.add(handler)
    }

    /**
     * Freeze the registry — no more registrations allowed.
     * Called after all extensions have completed [PiExtension.onRegister].
     */
    fun freeze() {
        frozen = true
    }

    /**
     * Dispatch an event to all registered handlers for its type.
     *
     * Called from the agent loop after freeze. Handlers are invoked
     * in registration order. Exceptions in handlers are caught and logged
     * (they do NOT propagate to the agent loop).
     */
    suspend fun dispatchEvent(event: AgentEvent) {
        val handlers = _eventHandlers[event.type] ?: return
        for (handler in handlers) {
            try {
                handler.handle(event)
            } catch (e: Exception) {
                // Log but don't propagate — extensions can't break the agent loop
                // TODO: structured logging in Phase 6
            }
        }
    }
}
