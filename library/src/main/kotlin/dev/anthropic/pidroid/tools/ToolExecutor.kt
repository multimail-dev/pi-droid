package dev.anthropic.pidroid.tools

import dev.anthropic.pidroid.core.event.AgentEvent
import dev.anthropic.pidroid.core.message.ContentBlock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import java.util.UUID
import kotlin.coroutines.coroutineContext

/**
 * Orchestrates the tool execution pipeline with confirmation gates.
 *
 * Implements [ToolDispatcher] — the real implementation that replaces
 * FakeToolDispatcher in production. The agent loop depends only on the
 * ToolDispatcher interface.
 *
 * ## Execution Pipeline (Pi's 5-phase lifecycle)
 * 1. **Prepare**: find handler, validate arguments
 * 2. **beforeToolCall hook**: host-level interception before confirmation
 * 3. **Before hook**: check confirmation policy, await user confirmation if needed
 * 4. **Execute**: call handler with timeout
 * 5. **afterToolCall hook**: host-level transform/override after execution
 *
 * ## Concurrency
 * - Batch execution: all tools in a batch run concurrently (coroutineScope + async)
 * - Results returned in source order (same order as input)
 * - Confirmation suspends only the individual tool, not the batch
 */
class ToolExecutor(
    private val registry: ToolRegistry,
    private val confirmationGate: ConfirmationGate,
    private val handlers: Map<String, ToolHandler> = emptyMap(),
    private val hooks: ToolCallHooks = ToolCallHooks(),
) : ToolDispatcher {

    /** Set by AgentLoop or PiRuntime to receive tool progress events */
    var emitEvent: (suspend (AgentEvent) -> Unit)? = null

    override suspend fun dispatch(toolCalls: List<ContentBlock.ToolCall>): List<ToolResult> {
        val hasSequentialTool = toolCalls.any { tc ->
            registry.getToolByName(tc.name)?.dispatchMode == ToolDispatchMode.SEQUENTIAL
        }

        return if (hasSequentialTool) {
            dispatchSequential(toolCalls)
        } else {
            dispatchParallel(toolCalls)
        }
    }

    private suspend fun dispatchParallel(toolCalls: List<ContentBlock.ToolCall>): List<ToolResult> {
        return coroutineScope {
            val deferreds = toolCalls.map { toolCall ->
                async { executeSingle(toolCall) }
            }
            deferreds.map { it.await() }
        }
    }

    private suspend fun dispatchSequential(toolCalls: List<ContentBlock.ToolCall>): List<ToolResult> {
        return toolCalls.map { toolCall -> executeSingle(toolCall) }
    }

    private suspend fun executeSingle(toolCall: ContentBlock.ToolCall): ToolResult {
        val toolDef = registry.getToolByName(toolCall.name)
            ?: return ToolResult(
                toolCallId = toolCall.id,
                content = "Unknown tool: ${toolCall.name}",
                isError = true,
            )

        // Phase 1: Prepare — find handler
        val handler = handlers[toolCall.name]
            ?: return ToolResult(
                toolCallId = toolCall.id,
                content = "No handler registered for tool: ${toolCall.name}",
                isError = true,
            )

        // Phase 1.5: beforeToolCall hook
        val beforeResult = runBeforeHook(toolCall, toolDef)
        if (beforeResult != null) return beforeResult

        // Phase 2: Before hook — confirmation gate
        val confirmResult = checkConfirmation(toolDef, toolCall)
        if (confirmResult != null) return confirmResult

        // Phase 3: Execute with timeout
        val executionResult = executeWithTimeout(toolCall, toolDef, handler)

        // Phase 4: afterToolCall hook
        return runAfterHook(toolCall, toolDef, executionResult)
    }

    private suspend fun runBeforeHook(
        toolCall: ContentBlock.ToolCall,
        toolDef: ToolDefinition,
    ): ToolResult? {
        val hook = hooks.beforeToolCall ?: return null
        return try {
            val context = BeforeToolCallContext(
                toolCall = toolCall,
                toolDef = toolDef,
                messages = emptyList(), // messages not available at executor level
            )
            when (val decision = hook(context)) {
                is ToolCallDecision.Proceed -> null
                is ToolCallDecision.Block -> ToolResult(
                    toolCallId = toolCall.id,
                    content = decision.reason,
                    isError = true,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Hook threw — treat as block with exception message
            ToolResult(
                toolCallId = toolCall.id,
                content = "beforeToolCall hook failed: ${e.message}",
                isError = true,
            )
        }
    }

    private suspend fun runAfterHook(
        toolCall: ContentBlock.ToolCall,
        toolDef: ToolDefinition,
        result: ToolResult,
    ): ToolResult {
        val hook = hooks.afterToolCall ?: return result
        return try {
            val context = AfterToolCallContext(
                toolCall = toolCall,
                toolDef = toolDef,
                result = result,
                isError = result.isError,
            )
            val override = hook(context) ?: return result
            result.copy(
                content = override.content ?: result.content,
                isError = override.isError ?: result.isError,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Hook threw — keep original result
            result
        }
    }

    private suspend fun checkConfirmation(
        toolDef: ToolDefinition,
        toolCall: ContentBlock.ToolCall,
    ): ToolResult? {
        val policy = toolDef.effectiveConfirmationPolicy

        return when (policy) {
            ConfirmationPolicy.AUTOMATIC -> null // proceed
            ConfirmationPolicy.BLOCKED -> ToolResult(
                toolCallId = toolCall.id,
                content = "Tool '${toolCall.name}' is blocked by policy",
                isError = true,
            )
            ConfirmationPolicy.USER_CONFIRM_MODAL,
            ConfirmationPolicy.BIOMETRIC_CONFIRM -> {
                val request = ConfirmationRequest(
                    requestId = UUID.randomUUID().toString(),
                    toolName = toolCall.name,
                    toolDescription = toolDef.description,
                    arguments = toolCall.arguments.toString(),
                    policy = policy,
                )
                try {
                    val result = confirmationGate.requestConfirmation(request)
                    when (result) {
                        ConfirmationResult.APPROVED -> null // proceed
                        ConfirmationResult.DENIED -> ToolResult(
                            toolCallId = toolCall.id,
                            content = "User denied execution of '${toolCall.name}'",
                            isError = true,
                        )
                        ConfirmationResult.CANCELLED -> ToolResult(
                            toolCallId = toolCall.id,
                            content = "Confirmation cancelled for '${toolCall.name}'",
                            isError = true,
                        )
                    }
                } catch (e: CancellationException) {
                    ToolResult(
                        toolCallId = toolCall.id,
                        content = "Aborted: confirmation cancelled",
                        isError = true,
                    )
                }
            }
        }
    }

    private suspend fun executeWithTimeout(
        toolCall: ContentBlock.ToolCall,
        toolDef: ToolDefinition,
        handler: ToolHandler,
    ): ToolResult {
        val job = coroutineContext[Job]!!
        val context = LiveToolExecutionContext(toolCall.id, emitEvent, job)
        return try {
            withTimeout(toolDef.timeoutMs) {
                handler.execute(toolCall.id, toolCall.arguments, context)
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            ToolResult(
                toolCallId = toolCall.id,
                content = "Tool '${toolCall.name}' timed out after ${toolDef.timeoutMs}ms",
                isError = true,
            )
        } catch (e: CancellationException) {
            throw e // propagate cancellation
        } catch (e: Exception) {
            ToolResult(
                toolCallId = toolCall.id,
                content = "Tool '${toolCall.name}' failed: ${e.message}",
                isError = true,
            )
        }
    }
}

private class LiveToolExecutionContext(
    private val toolCallId: String,
    private val emitEvent: (suspend (AgentEvent) -> Unit)?,
    private val job: Job,
) : ToolExecutionContext {
    override val isCancelled: Boolean
        get() = !job.isActive

    override suspend fun reportProgress(message: String) {
        emitEvent?.invoke(
            AgentEvent.ToolExecutionUpdate(
                toolCallId = toolCallId,
                progress = message,
            )
        )
    }
}
