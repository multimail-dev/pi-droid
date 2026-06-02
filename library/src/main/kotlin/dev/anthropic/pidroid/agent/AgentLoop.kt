package dev.anthropic.pidroid.agent

import dev.anthropic.pidroid.core.event.AgentEvent
import dev.anthropic.pidroid.core.message.AgentMessage
import dev.anthropic.pidroid.core.message.ContentBlock
import dev.anthropic.pidroid.core.message.Message
import dev.anthropic.pidroid.core.model.AgentConfig
import dev.anthropic.pidroid.core.model.StopReason
import dev.anthropic.pidroid.llm.AssistantMessageEvent
import dev.anthropic.pidroid.llm.LlmProvider
import dev.anthropic.pidroid.tools.ToolDispatcher
import dev.anthropic.pidroid.tools.ToolResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * The agent loop state machine.
 *
 * Ports Pi's agent-loop.ts behavioral contracts:
 * - Outer follow-up loop: runs while getFollowUpMessages() returns messages
 * - Inner turn loop: runs while there are tool calls
 * - Each turn: stream assistant → extract tool calls → execute batch → emit events
 *
 * ## State Machine
 * ```
 * IDLE → agent_start → TURN_START
 * TURN_START → turn_start → STREAMING
 * STREAMING → (accumulate deltas) ��� TOOL_DISPATCH | TURN_END
 * TOOL_DISPATCH → (execute tools) → TURN_START (next turn)
 * TURN_END → turn_end → FOLLOW_UP_CHECK
 * FOLLOW_UP_CHECK → (pending?) → TURN_START | AGENT_END
 * AGENT_END → agent_end → IDLE
 * ```
 *
 * ## Cancellation
 * Job.cancel() propagates to all child coroutines (structured concurrency).
 * The loop cooperatively checks for cancellation between turns and after streaming.
 */
class AgentLoop(
    private val llmProvider: LlmProvider,
    private val toolDispatcher: ToolDispatcher,
) {
    private val _events = MutableSharedFlow<AgentEvent>(
        replay = 0,
        extraBufferCapacity = 64,
    )

    /** Observable event stream for UI and extensions */
    val events: SharedFlow<AgentEvent> = _events.asSharedFlow()

    /**
     * Run the agent loop to completion.
     *
     * @param context The agent context (config, tools, initial messages)
     * @param getSteeringMessages Callback to poll steering messages before each turn.
     *   Steering messages are injected into the conversation before the next LLM call.
     * @param getFollowUpMessages Callback to check for follow-up messages after each "stop".
     *   Returns null when no more follow-ups exist.
     * @return The final stop reason
     */
    suspend fun run(
        context: AgentContext,
        getSteeringMessages: suspend () -> List<Message> = { emptyList() },
        getFollowUpMessages: suspend () -> List<Message>? = { null },
    ): StopReason {
        val messages: MutableList<AgentMessage> = context.initialMessages.toMutableList()
        var turnIndex = 0

        // Emit agent_start
        emit(
            AgentEvent.AgentStart(
                config = AgentConfig(
                    provider = llmProvider.name,
                    model = context.llmConfig.model,
                    maxTurns = context.loopConfig.maxTurns,
                    toolCount = context.tools.size,
                )
            )
        )

        try {
            // Outer follow-up loop
            var finalReason = StopReason.STOP
            var continueOuter = true

            while (continueOuter) {
                // Inner turn loop
                var continueInner = true

                while (continueInner && turnIndex < context.loopConfig.maxTurns) {
                    currentCoroutineContext().ensureActive()

                    // Inject steering messages before the turn
                    val steeringMessages = getSteeringMessages()
                    if (steeringMessages.isNotEmpty()) {
                        messages.addAll(steeringMessages)
                    }

                    emit(AgentEvent.TurnStart(turnIndex))
                    val turnResult = executeTurn(messages, context, turnIndex)
                    finalReason = turnResult.stopReason

                    // Add assistant message to history
                    messages.add(turnResult.message)

                    // Add tool results to history
                    for (toolResult in turnResult.toolResults) {
                        messages.add(toolResult)
                    }

                    emit(AgentEvent.TurnEnd(turnIndex, turnResult.stopReason))
                    turnIndex++

                    continueInner = turnResult.shouldContinue
                }

                // Follow-up check
                if (!continueInner || turnIndex >= context.loopConfig.maxTurns) {
                    val followUps = getFollowUpMessages()
                    if (followUps != null && followUps.isNotEmpty()) {
                        messages.addAll(followUps)
                        continueOuter = true
                    } else {
                        continueOuter = false
                    }
                }
            }

            emit(AgentEvent.AgentEnd(finalReason))
            return finalReason
        } catch (e: CancellationException) {
            emit(AgentEvent.AgentEnd(StopReason.ABORTED))
            throw e
        }
    }

    /**
     * Continue the agent loop from an existing transcript.
     *
     * Unlike [run], this does NOT inject new messages — it continues from whatever
     * the transcript's current state is. The last message must NOT be an assistant message
     * (the next action is always to stream a new assistant response).
     *
     * Use cases:
     * - Retry after error (last message is user or tool_result)
     * - Resume after injecting tool results externally
     * - Continue after steering injection
     *
     * @param context The agent context with existing transcript in initialMessages
     * @param getSteeringMessages Same as [run]
     * @param getFollowUpMessages Same as [run]
     * @return The final stop reason
     * @throws IllegalArgumentException if transcript is empty or ends with assistant message
     */
    suspend fun continueFrom(
        context: AgentContext,
        getSteeringMessages: suspend () -> List<Message> = { emptyList() },
        getFollowUpMessages: suspend () -> List<Message>? = { null },
    ): StopReason {
        require(context.initialMessages.isNotEmpty()) {
            "Cannot continue: no messages in context"
        }

        val lastMessage = context.initialMessages.last()
        require(lastMessage !is Message.Assistant) {
            "Cannot continue from message role: assistant"
        }

        // Use the same loop body as run(), but the messages already contain the transcript
        val messages: MutableList<AgentMessage> = context.initialMessages.toMutableList()
        var turnIndex = 0

        emit(
            AgentEvent.AgentStart(
                config = AgentConfig(
                    provider = llmProvider.name,
                    model = context.llmConfig.model,
                    maxTurns = context.loopConfig.maxTurns,
                    toolCount = context.tools.size,
                )
            )
        )

        try {
            var finalReason = StopReason.STOP
            var continueOuter = true

            while (continueOuter) {
                var continueInner = true

                while (continueInner && turnIndex < context.loopConfig.maxTurns) {
                    currentCoroutineContext().ensureActive()

                    // Inject steering messages before the turn
                    val steeringMessages = getSteeringMessages()
                    if (steeringMessages.isNotEmpty()) {
                        messages.addAll(steeringMessages)
                    }

                    emit(AgentEvent.TurnStart(turnIndex))
                    val turnResult = executeTurn(messages, context, turnIndex)
                    finalReason = turnResult.stopReason

                    messages.add(turnResult.message)

                    for (toolResult in turnResult.toolResults) {
                        messages.add(toolResult)
                    }

                    emit(AgentEvent.TurnEnd(turnIndex, turnResult.stopReason))
                    turnIndex++

                    continueInner = turnResult.shouldContinue
                }

                // Follow-up check
                if (!continueInner || turnIndex >= context.loopConfig.maxTurns) {
                    val followUps = getFollowUpMessages()
                    if (followUps != null && followUps.isNotEmpty()) {
                        messages.addAll(followUps)
                        continueOuter = true
                    } else {
                        continueOuter = false
                    }
                }
            }

            emit(AgentEvent.AgentEnd(finalReason))
            return finalReason
        } catch (e: CancellationException) {
            emit(AgentEvent.AgentEnd(StopReason.ABORTED))
            throw e
        }
    }

    /**
     * Execute a single turn: stream LLM → extract tool calls → dispatch tools.
     */
    private suspend fun executeTurn(
        messages: List<AgentMessage>,
        context: AgentContext,
        turnIndex: Int,
    ): TurnResult {
        // Stream the assistant response
        val contentBlocks = mutableListOf<ContentBlock>()
        val textAccumulator = StringBuilder()
        val thinkingAccumulator = StringBuilder()
        val toolCallAccumulators = mutableMapOf<String, ToolCallAccumulator>()
        var stopReason = StopReason.STOP
        var assistantMessage: Message.Assistant? = null

        emit(AgentEvent.MessageStart(Message.Assistant(content = emptyList())))

        // Apply context transform (operates on full AgentMessage list including custom types)
        val transformedMessages = context.loopConfig.transformContext?.invoke(messages)
            ?: messages

        // Filter to standard Message types only for LLM (custom messages invisible to model)
        val llmMessages = transformedMessages.filterIsInstance<Message>()

        llmProvider.stream(llmMessages, context.tools, context.llmConfig).collect { event ->
            when (event) {
                is AssistantMessageEvent.Start -> {}
                is AssistantMessageEvent.TextDelta -> {
                    textAccumulator.append(event.text)
                    emit(AgentEvent.MessageUpdate(ContentBlock.Text(event.text)))
                }
                is AssistantMessageEvent.ThinkingDelta -> {
                    thinkingAccumulator.append(event.text)
                    emit(AgentEvent.MessageUpdate(ContentBlock.Thinking(event.text)))
                }
                is AssistantMessageEvent.ToolCallDelta -> {
                    val acc = toolCallAccumulators.getOrPut(event.id) {
                        ToolCallAccumulator(id = event.id, name = event.name ?: "")
                    }
                    if (event.name != null && acc.name.isEmpty()) acc.name = event.name
                    acc.arguments.append(event.argumentsDelta)
                }
                is AssistantMessageEvent.Done -> {
                    stopReason = event.stopReason
                    assistantMessage = event.message
                }
                is AssistantMessageEvent.Error -> {
                    stopReason = event.stopReason
                    assistantMessage = event.partial
                }
            }
        }

        // Build final content blocks
        if (thinkingAccumulator.isNotEmpty()) {
            contentBlocks.add(ContentBlock.Thinking(thinkingAccumulator.toString()))
        }
        if (textAccumulator.isNotEmpty()) {
            contentBlocks.add(ContentBlock.Text(textAccumulator.toString()))
        }
        for ((id, acc) in toolCallAccumulators) {
            val argsJson = try {
                kotlinx.serialization.json.Json.parseToJsonElement(acc.arguments.toString())
                    as? kotlinx.serialization.json.JsonObject
                    ?: kotlinx.serialization.json.JsonObject(emptyMap())
            } catch (_: Exception) {
                kotlinx.serialization.json.JsonObject(emptyMap())
            }
            contentBlocks.add(ContentBlock.ToolCall(id = id, name = acc.name, arguments = argsJson))
        }

        val finalMessage = Message.Assistant(content = contentBlocks, stopReason = stopReason)
        emit(AgentEvent.MessageEnd(finalMessage))

        // Execute tool calls if present
        val toolCalls = contentBlocks.filterIsInstance<ContentBlock.ToolCall>()
        val toolResults = if (toolCalls.isNotEmpty() && stopReason == StopReason.TOOL_USE) {
            executeTools(toolCalls)
        } else {
            emptyList()
        }

        val shouldContinue = toolResults.isNotEmpty()
        return TurnResult(
            message = finalMessage,
            stopReason = stopReason,
            toolResults = toolResults.map { result ->
                Message.ToolResult(
                    toolCallId = result.toolCallId,
                    content = result.content,
                    isError = result.isError,
                )
            },
            shouldContinue = shouldContinue,
        )
    }

    /**
     * Execute tool calls via the dispatcher.
     * Emits tool execution events.
     */
    private suspend fun executeTools(toolCalls: List<ContentBlock.ToolCall>): List<ToolResult> {
        // Emit start events
        for (tc in toolCalls) {
            emit(AgentEvent.ToolExecutionStart(toolCallId = tc.id, toolName = tc.name))
        }

        // Dispatch (dispatcher handles concurrent execution + source order)
        val results = toolDispatcher.dispatch(toolCalls)

        // Emit end events in source order
        for (result in results) {
            emit(
                AgentEvent.ToolExecutionEnd(
                    toolCallId = result.toolCallId,
                    result = Message.ToolResult(
                        toolCallId = result.toolCallId,
                        content = result.content,
                        isError = result.isError,
                    ),
                )
            )
        }

        return results
    }

    private suspend fun emit(event: AgentEvent) {
        _events.emit(event)
    }

    /** Accumulator for tool call deltas during streaming */
    private class ToolCallAccumulator(
        val id: String,
        var name: String,
        val arguments: StringBuilder = StringBuilder(),
    )
}
