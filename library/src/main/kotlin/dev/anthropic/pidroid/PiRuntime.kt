package dev.anthropic.pidroid

import android.content.Context
import dev.anthropic.pidroid.agent.AgentContext
import dev.anthropic.pidroid.agent.AgentLoop
import dev.anthropic.pidroid.agent.AgentLoopConfig
import dev.anthropic.pidroid.agent.DrainMode
import dev.anthropic.pidroid.agent.MessageQueue
import dev.anthropic.pidroid.core.event.AgentEvent
import dev.anthropic.pidroid.core.message.AgentMessage
import dev.anthropic.pidroid.core.message.Message
import dev.anthropic.pidroid.llm.LlmConfig
import dev.anthropic.pidroid.llm.LlmProvider
import dev.anthropic.pidroid.llm.MessageTransformer
import dev.anthropic.pidroid.llm.ProviderFactory
import dev.anthropic.pidroid.llm.registry.ApiType
import dev.anthropic.pidroid.llm.registry.ModelInfo
import dev.anthropic.pidroid.llm.registry.ModelRegistry
import dev.anthropic.pidroid.tools.ConfirmationGate
import dev.anthropic.pidroid.tools.ConfirmationRequest
import dev.anthropic.pidroid.tools.ConfirmationResult
import dev.anthropic.pidroid.tools.ToolExecutor
import dev.anthropic.pidroid.tools.ToolHandler
import dev.anthropic.pidroid.tools.ToolRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Main entry point for the Pi agent runtime.
 *
 * Host apps create a PiRuntime via [initialize], then interact with it through:
 * - [sendPrompt] to start agent tasks
 * - [events] to observe agent events
 * - [confirmationRequests] to handle tool confirmations
 * - [state] to observe runtime status
 *
 * Thread safety: all state mutations go through the [scope] dispatcher.
 */
class PiRuntime private constructor(
    private val config: PiRuntimeConfig,
    private val resolvedModel: ModelInfo,
    private val llmProvider: LlmProvider,
    private val toolRegistry: ToolRegistry,
    private val confirmationGate: ConfirmationGate,
    private val scope: CoroutineScope,
    handlers: Map<String, ToolHandler> = emptyMap(),
) {
    private val _state = MutableStateFlow(PiRuntimeState())
    private var currentJob: Job? = null
    private val steeringQueue = MessageQueue(DrainMode.ONE_AT_A_TIME)
    private val followUpQueue = MessageQueue(DrainMode.ONE_AT_A_TIME)
    private val isShutdown = AtomicBoolean(false)

    /** Observable runtime state */
    val state: StateFlow<PiRuntimeState> = _state.asStateFlow()

    /** Agent event stream (turns, messages, tool calls) */
    val events: SharedFlow<AgentEvent>
        get() = agentLoop.events

    /** Confirmation requests requiring user/biometric approval */
    val confirmationRequests: SharedFlow<ConfirmationRequest>
        get() = confirmationGate.requests

    /** The tool registry (for observing active tools) */
    val registry: ToolRegistry get() = toolRegistry

    private val toolExecutor = ToolExecutor(toolRegistry, confirmationGate, handlers)
    private val agentLoop = AgentLoop(llmProvider, toolExecutor)

    /**
     * Send a prompt to the agent.
     *
     * If the agent is already running, the prompt is enqueued as a follow-up
     * (Pi's steering behavior — allows the user to inject context mid-task).
     *
     * Returns immediately with the Job for cancellation.
     */
    fun sendPrompt(text: String): Job {
        check(!isShutdown.get()) { "PiRuntime has been shut down" }

        if (currentJob?.isActive == true) {
            // Agent is already running — queue as follow-up
            followUpQueue.enqueue(Message.User(text))
            return currentJob!!
        }

        val job = scope.launch {
            _state.value = _state.value.copy(
                status = RuntimeStatus.PROCESSING,
                turnCount = 0,
                lastError = null,
            )

            try {
                val context = buildAgentContext(text)
                agentLoop.run(
                    context,
                    getSteeringMessages = { steeringQueue.drain() },
                    getFollowUpMessages = {
                        val msgs = followUpQueue.drain()
                        msgs.ifEmpty { null }
                    },
                )
                _state.value = _state.value.copy(status = RuntimeStatus.IDLE)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    status = RuntimeStatus.IDLE,
                    lastError = e.message,
                )
            }
        }

        currentJob = job
        return job
    }

    /**
     * Continue the agent from the current transcript without adding a new message.
     *
     * Use when:
     * - Retrying after an error
     * - The last message in the transcript is a tool result or user message
     *   that should trigger the next assistant response
     *
     * @param transcript The existing conversation messages to continue from
     * @throws IllegalStateException if PiRuntime has been shut down
     * @throws IllegalStateException if agent is currently running
     * @throws IllegalArgumentException if transcript is empty or ends with assistant
     */
    fun continueConversation(transcript: List<AgentMessage>): Job {
        check(!isShutdown.get()) { "PiRuntime has been shut down" }
        check(currentJob?.isActive != true) { "Agent is already running. Wait for completion or cancel first." }

        val job = scope.launch {
            _state.value = _state.value.copy(
                status = RuntimeStatus.PROCESSING,
                turnCount = 0,
                lastError = null,
            )

            try {
                val transformedTranscript = MessageTransformer.transform(
                    messages = transcript.filterIsInstance<Message>(),
                    targetProvider = resolvedModel.provider,
                    targetApi = resolvedModel.api,
                )
                val context = AgentContext(
                    llmConfig = buildLlmConfig(),
                    tools = toolRegistry.activeTools.value.tools,
                    initialMessages = transformedTranscript,
                    loopConfig = AgentLoopConfig(
                        maxTurns = config.maxTurnsPerTask,
                    ),
                )
                agentLoop.continueFrom(
                    context,
                    getSteeringMessages = { steeringQueue.drain() },
                    getFollowUpMessages = {
                        val msgs = followUpQueue.drain()
                        msgs.ifEmpty { null }
                    },
                )
                _state.value = _state.value.copy(status = RuntimeStatus.IDLE)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    status = RuntimeStatus.IDLE,
                    lastError = e.message,
                )
            }
        }

        currentJob = job
        return job
    }

    /**
     * Inject a steering message into the currently running agent loop.
     *
     * Steering messages are polled at the start of each turn, allowing the user
     * to redirect the agent mid-task without waiting for follow-up.
     *
     * @throws IllegalStateException if the runtime has been shut down
     */
    fun steer(message: Message) {
        check(!isShutdown.get()) { "PiRuntime has been shut down" }
        steeringQueue.enqueue(message)
    }

    /**
     * Respond to a confirmation request.
     */
    fun respondToConfirmation(requestId: String, result: ConfirmationResult) {
        confirmationGate.respond(requestId, result)
    }

    /**
     * Cancel the currently running agent task.
     */
    fun cancel() {
        currentJob?.cancel()
        currentJob = null
        confirmationGate.cancelAll()
        _state.value = _state.value.copy(status = RuntimeStatus.IDLE)
    }

    /**
     * Shut down the runtime, releasing all resources.
     */
    suspend fun shutdown() {
        if (isShutdown.getAndSet(true)) return
        cancel()
        scope.cancel()
    }

    private fun buildLlmConfig(): LlmConfig = LlmConfig(
        apiKey = config.llmProvider.apiKey,
        model = resolvedModel.id,
        baseUrl = config.llmProvider.baseUrl ?: resolvedModel.baseUrl,
        maxTokens = config.llmProvider.maxTokens ?: resolvedModel.maxTokens ?: 4096,
        temperature = config.llmProvider.temperature,
        systemPrompt = config.systemPrompt,
        compat = resolvedModel.compat,
        headers = resolvedModel.headers,
    )

    private fun buildAgentContext(prompt: String): AgentContext {
        val snapshot = toolRegistry.activeTools.value
        val messages = listOf(Message.User(prompt))
        val transformed = MessageTransformer.transform(
            messages = messages,
            targetProvider = resolvedModel.provider,
            targetApi = resolvedModel.api,
        )
        return AgentContext(
            llmConfig = buildLlmConfig(),
            tools = snapshot.tools,
            initialMessages = transformed,
            loopConfig = AgentLoopConfig(
                maxTurns = config.maxTurnsPerTask,
            ),
        )
    }

    companion object {
        private val initialized = AtomicBoolean(false)

        /**
         * Initialize the Pi runtime.
         *
         * @param context Android application context
         * @param config Runtime configuration (LLM provider, capabilities, system prompt)
         * @param handlers Tool handler map — keys must match [ToolCatalog] tool names exactly.
         *   Handlers are immutable after init. Tools without a registered handler return
         *   a "No handler registered" error result.
         * @throws IllegalStateException if already initialized (singleton per process)
         */
        suspend fun initialize(
            context: Context,
            config: PiRuntimeConfig,
            handlers: Map<String, ToolHandler> = emptyMap(),
        ): PiRuntime {
            check(!initialized.getAndSet(true)) {
                "PiRuntime already initialized. Only one instance per process is supported."
            }

            ModelRegistry.init(context)

            val modelInfo = resolveModelInfo(config.llmProvider)
            val llmProvider = ProviderFactory.create(modelInfo)
            val toolRegistry = ToolRegistry(
                permissionChecker = AndroidPermissionChecker(context),
            )

            // Declare initial capabilities
            for (capability in config.capabilities) {
                toolRegistry.declareCapability(capability)
            }

            val confirmationGate = ConfirmationGate()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

            return PiRuntime(config, modelInfo, llmProvider, toolRegistry, confirmationGate, scope, handlers)
        }

        /**
         * Reset singleton state (for testing only).
         */
        internal fun resetForTesting() {
            initialized.set(false)
            ModelRegistry.reset()
        }

        /**
         * Resolve a [ModelInfo] from the registry, falling back to a synthetic
         * openai-completions entry when the model is unknown but a custom
         * [LlmProviderConfig.baseUrl] is provided.
         */
        internal fun resolveModelInfo(config: LlmProviderConfig): ModelInfo {
            val registered = ModelRegistry.getModel(config.provider, config.modelId)
            if (registered != null) return registered

            // Fallback: unknown model with custom base URL → assume openai-completions
            if (config.baseUrl != null) {
                return ModelInfo(
                    id = config.modelId,
                    name = config.modelId,
                    api = ApiType.OPENAI_COMPLETIONS,
                    provider = config.provider,
                    baseUrl = config.baseUrl,
                )
            }

            throw IllegalArgumentException(
                "Unknown model '${config.modelId}' for provider '${config.provider}'. " +
                    "Provide a baseUrl to use a custom/self-hosted provider."
            )
        }
    }
}
