package dev.anthropic.pidroid.tools

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages the confirmation handshake between tool execution and UI.
 *
 * When a tool requires confirmation:
 * 1. ToolExecutor calls [requestConfirmation] → suspends
 * 2. ConfirmationGate emits [ConfirmationRequest] to [requests] SharedFlow
 * 3. UI displays confirmation dialog
 * 4. UI calls [respond] with the result
 * 5. CompletableDeferred completes → tool executor resumes
 *
 * ## Concurrency
 * - [pendingConfirmations]: ConcurrentHashMap (thread-safe put/get/remove)
 * - Per-request handoff: CompletableDeferred (thread-safe complete/await)
 * - [requests]: SharedFlow with extraBufferCapacity=16, SUSPEND on overflow
 */
class ConfirmationGate {
    private val pendingConfirmations = ConcurrentHashMap<String, CompletableDeferred<ConfirmationResult>>()

    private val _requests = MutableSharedFlow<ConfirmationRequest>(
        extraBufferCapacity = 16,
    )
    val requests: SharedFlow<ConfirmationRequest> = _requests.asSharedFlow()

    /**
     * Request user confirmation for a tool execution.
     *
     * Suspends until the user responds or the coroutine is cancelled.
     *
     * @return The user's decision
     */
    suspend fun requestConfirmation(request: ConfirmationRequest): ConfirmationResult {
        val deferred = CompletableDeferred<ConfirmationResult>()
        pendingConfirmations[request.requestId] = deferred

        try {
            _requests.emit(request)
            return deferred.await()
        } finally {
            pendingConfirmations.remove(request.requestId)
        }
    }

    /**
     * Called by UI to respond to a confirmation request.
     *
     * @return true if the request was still pending and the response was delivered
     */
    fun respond(requestId: String, result: ConfirmationResult): Boolean {
        val deferred = pendingConfirmations[requestId] ?: return false
        return deferred.complete(result)
    }

    /**
     * Cancel all pending confirmations (e.g., on agent shutdown).
     */
    fun cancelAll() {
        for ((_, deferred) in pendingConfirmations) {
            deferred.complete(ConfirmationResult.CANCELLED)
        }
        pendingConfirmations.clear()
    }

    /** Number of pending confirmation requests */
    val pendingCount: Int get() = pendingConfirmations.size
}
