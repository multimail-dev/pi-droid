package dev.anthropic.pidroid.demo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.anthropic.pidroid.PiRuntime
import dev.anthropic.pidroid.PiRuntimeState
import dev.anthropic.pidroid.RuntimeStatus
import dev.anthropic.pidroid.core.event.AgentEvent
import dev.anthropic.pidroid.core.message.ContentBlock
import dev.anthropic.pidroid.tools.ConfirmationRequest
import dev.anthropic.pidroid.tools.ConfirmationResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the chat surface.
 *
 * Bridges PiRuntime events to UI state for Compose consumption.
 */
class ChatViewModel : ViewModel() {

    private var runtime: PiRuntime? = null
    private var observerJobs = mutableListOf<Job>()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _streamingText = MutableStateFlow("")
    val streamingText: StateFlow<String> = _streamingText.asStateFlow()

    private val _pendingConfirmation = MutableStateFlow<ConfirmationRequest?>(null)
    val pendingConfirmation: StateFlow<ConfirmationRequest?> = _pendingConfirmation.asStateFlow()

    fun attachRuntime(runtime: PiRuntime) {
        if (this.runtime === runtime) return // already attached
        // Cancel old observers before re-attaching
        observerJobs.forEach { it.cancel() }
        observerJobs.clear()
        this.runtime = runtime
        observeEvents()
        observeState()
        observeConfirmations()
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        val userMessage = ChatMessage(role = MessageRole.USER, content = text)
        _messages.value = _messages.value + userMessage
        _streamingText.value = ""

        runtime?.sendPrompt(text)
    }

    fun approveConfirmation(requestId: String) {
        runtime?.respondToConfirmation(requestId, ConfirmationResult.APPROVED)
        _pendingConfirmation.value = null
    }

    fun denyConfirmation(requestId: String) {
        runtime?.respondToConfirmation(requestId, ConfirmationResult.DENIED)
        _pendingConfirmation.value = null
    }

    fun cancelAgent() {
        runtime?.cancel()
        _isProcessing.value = false
    }

    private fun observeEvents() {
        val rt = runtime ?: return
        observerJobs += viewModelScope.launch {
            rt.events.collect { event ->
                when (event) {
                    is AgentEvent.MessageUpdate -> {
                        val delta = event.delta
                        if (delta is ContentBlock.Text) {
                            _streamingText.value += delta.text
                        }
                    }
                    is AgentEvent.MessageEnd -> {
                        if (_streamingText.value.isNotEmpty()) {
                            val assistantMessage = ChatMessage(
                                role = MessageRole.ASSISTANT,
                                content = _streamingText.value,
                            )
                            _messages.value = _messages.value + assistantMessage
                            _streamingText.value = ""
                        }
                    }
                    is AgentEvent.ToolExecutionStart -> {
                        val toolMessage = ChatMessage(
                            role = MessageRole.TOOL,
                            content = "Executing: ${event.toolName}",
                            toolName = event.toolName,
                        )
                        _messages.value = _messages.value + toolMessage
                    }
                    is AgentEvent.ToolExecutionEnd -> {
                        // Update the last tool message with result
                        val msgs = _messages.value.toMutableList()
                        val toolResult = event.result
                        val toolName = event.toolCallId
                        val lastToolIdx = msgs.indexOfLast { it.role == MessageRole.TOOL }
                        if (lastToolIdx >= 0) {
                            msgs[lastToolIdx] = msgs[lastToolIdx].copy(
                                content = if (toolResult.isError) "Error: ${toolResult.content}"
                                else "Done",
                            )
                            _messages.value = msgs
                        }
                    }
                    is AgentEvent.AgentEnd -> {
                        _isProcessing.value = false
                    }
                    else -> { /* other events handled by state flow */ }
                }
            }
        }
    }

    private fun observeState() {
        val rt = runtime ?: return
        observerJobs += viewModelScope.launch {
            rt.state.collect { state ->
                _isProcessing.value = state.status == RuntimeStatus.PROCESSING
            }
        }
    }

    private fun observeConfirmations() {
        val rt = runtime ?: return
        observerJobs += viewModelScope.launch {
            rt.confirmationRequests.collect { request ->
                _pendingConfirmation.value = request
            }
        }
    }
}

data class ChatMessage(
    val role: MessageRole,
    val content: String,
    val toolName: String? = null,
)

enum class MessageRole { USER, ASSISTANT, TOOL }
