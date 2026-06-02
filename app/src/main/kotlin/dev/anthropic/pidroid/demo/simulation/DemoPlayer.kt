package dev.anthropic.pidroid.demo.simulation

import dev.anthropic.pidroid.demo.ui.ChatMessage
import dev.anthropic.pidroid.demo.ui.MessageRole
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Plays a [DemoScenario] step-by-step with realistic timing.
 *
 * Produces the same UI state shape as [ChatViewModel] (messages, streaming text,
 * pending confirmation) so the existing composables render correctly.
 *
 * Call [play] from a coroutine scope. The player suspends on [DemoScenario.Step.Confirmation]
 * until the user taps approve/deny via [resolveConfirmation].
 */
class DemoPlayer {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _streamingText = MutableStateFlow("")
    val streamingText: StateFlow<String> = _streamingText.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _pendingConfirmation = MutableStateFlow<PendingConfirmation?>(null)
    val pendingConfirmation: StateFlow<PendingConfirmation?> = _pendingConfirmation.asStateFlow()

    private var confirmationDeferred: CompletableDeferred<Boolean>? = null

    /**
     * Play a scenario from start to finish.
     *
     * Suspends until all steps complete (or the coroutine is cancelled).
     */
    suspend fun play(scenario: DemoScenario) {
        reset()
        _isPlaying.value = true

        // Add the user message
        _messages.update { it + ChatMessage(MessageRole.USER, scenario.userPrompt) }
        delay(400) // Brief pause after user sends

        try {
            for (step in scenario.steps) {
                executeStep(step)
            }
        } catch (_: CancellationException) {
            // Player was stopped
        } finally {
            _isPlaying.value = false
            _streamingText.value = ""
            confirmationDeferred?.cancel()
            confirmationDeferred = null
            _pendingConfirmation.value = null
        }
    }

    /** Resolve the current confirmation dialog. */
    fun resolveConfirmation(approved: Boolean) {
        confirmationDeferred?.complete(approved)
    }

    /** Reset all state for a new scenario. */
    fun reset() {
        _messages.value = emptyList()
        _streamingText.value = ""
        _isPlaying.value = false
        _pendingConfirmation.value = null
        confirmationDeferred?.cancel()
        confirmationDeferred = null
    }

    private suspend fun executeStep(step: DemoScenario.Step) {
        when (step) {
            is DemoScenario.Step.AssistantStream -> streamText(step)
            is DemoScenario.Step.ToolExecution -> executeTool(step)
            is DemoScenario.Step.Confirmation -> showConfirmation(step)
            is DemoScenario.Step.Pause -> delay(step.ms)
        }
    }

    private suspend fun streamText(step: DemoScenario.Step.AssistantStream) {
        _streamingText.value = ""
        for (char in step.text) {
            _streamingText.value += char
            delay(step.charDelayMs)
        }
        // Commit the streamed text as a full message
        val finalText = _streamingText.value
        _streamingText.value = ""
        _messages.update { it + ChatMessage(MessageRole.ASSISTANT, finalText) }
    }

    private suspend fun executeTool(step: DemoScenario.Step.ToolExecution) {
        // Show "Executing: tool_name"
        _messages.update {
            it + ChatMessage(MessageRole.TOOL, "Executing: ${step.toolName}", toolName = step.toolName)
        }
        delay(step.durationMs)
        // Update last tool message to "Done"
        _messages.update { msgs ->
            val mutable = msgs.toMutableList()
            val lastToolIdx = mutable.indexOfLast { it.role == MessageRole.TOOL }
            if (lastToolIdx >= 0) {
                mutable[lastToolIdx] = mutable[lastToolIdx].copy(content = "Done")
            }
            mutable
        }
    }

    private suspend fun showConfirmation(step: DemoScenario.Step.Confirmation) {
        val deferred = CompletableDeferred<Boolean>()
        confirmationDeferred = deferred
        _pendingConfirmation.value = PendingConfirmation(
            toolName = step.toolName,
            description = step.description,
            args = step.args,
        )

        val approved = deferred.await()
        _pendingConfirmation.value = null
        confirmationDeferred = null

        if (approved) {
            _messages.update {
                it + ChatMessage(MessageRole.TOOL, "Done", toolName = step.toolName)
            }
        } else {
            _messages.update {
                it + ChatMessage(MessageRole.TOOL, "Denied by user", toolName = step.toolName)
            }
        }
    }
}

/**
 * A pending confirmation request shown during demo playback.
 * Mirrors [ConfirmationRequest] shape without depending on the library type.
 */
data class PendingConfirmation(
    val toolName: String,
    val description: String,
    val args: String,
)
