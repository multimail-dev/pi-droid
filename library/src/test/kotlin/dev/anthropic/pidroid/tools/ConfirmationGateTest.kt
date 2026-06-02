package dev.anthropic.pidroid.tools

import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmationGateTest {

    @Test
    fun `requestConfirmation suspends until respond is called`() = runTest {
        val gate = ConfirmationGate()
        val request = ConfirmationRequest(
            requestId = "req_1",
            toolName = "send_intent",
            toolDescription = "Fire an intent",
            arguments = "{}",
            policy = ConfirmationPolicy.USER_CONFIRM_MODAL,
        )

        val deferred = async(UnconfinedTestDispatcher(testScheduler)) {
            gate.requestConfirmation(request)
        }

        // Should be pending
        assertEquals(1, gate.pendingCount)

        // Respond
        gate.respond("req_1", ConfirmationResult.APPROVED)

        val result = deferred.await()
        assertEquals(ConfirmationResult.APPROVED, result)
        assertEquals(0, gate.pendingCount)
    }

    @Test
    fun `respond with DENIED returns denied result`() = runTest {
        val gate = ConfirmationGate()
        val request = ConfirmationRequest(
            requestId = "req_2",
            toolName = "share_text",
            toolDescription = "Share text",
            arguments = """{"text":"hi"}""",
            policy = ConfirmationPolicy.USER_CONFIRM_MODAL,
        )

        val deferred = async(UnconfinedTestDispatcher(testScheduler)) {
            gate.requestConfirmation(request)
        }

        gate.respond("req_2", ConfirmationResult.DENIED)
        assertEquals(ConfirmationResult.DENIED, deferred.await())
    }

    @Test
    fun `cancelAll completes all pending with CANCELLED`() = runTest {
        val gate = ConfirmationGate()

        val results = mutableListOf<ConfirmationResult>()
        val jobs = (1..3).map { i ->
            async(UnconfinedTestDispatcher(testScheduler)) {
                val req = ConfirmationRequest(
                    requestId = "req_$i",
                    toolName = "tool_$i",
                    toolDescription = "desc",
                    arguments = "{}",
                    policy = ConfirmationPolicy.USER_CONFIRM_MODAL,
                )
                gate.requestConfirmation(req)
            }
        }

        assertEquals(3, gate.pendingCount)
        gate.cancelAll()

        for (job in jobs) {
            results.add(job.await())
        }
        assertTrue(results.all { it == ConfirmationResult.CANCELLED })
    }

    @Test
    fun `respond to unknown request returns false`() {
        val gate = ConfirmationGate()
        val result = gate.respond("nonexistent", ConfirmationResult.APPROVED)
        assertTrue(!result)
    }

    @Test
    fun `requests flow emits ConfirmationRequest`() = runTest {
        val gate = ConfirmationGate()
        val emitted = mutableListOf<ConfirmationRequest>()

        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            gate.requests.collect { emitted.add(it) }
        }

        val request = ConfirmationRequest(
            requestId = "req_flow",
            toolName = "test",
            toolDescription = "desc",
            arguments = "{}",
            policy = ConfirmationPolicy.BIOMETRIC_CONFIRM,
            requiresBiometric = true,
        )

        launch(UnconfinedTestDispatcher(testScheduler)) {
            gate.requestConfirmation(request)
        }

        // Give flow time to propagate
        assertTrue(emitted.size >= 1)
        assertEquals("req_flow", emitted[0].requestId)
        assertTrue(emitted[0].requiresBiometric)

        gate.respond("req_flow", ConfirmationResult.APPROVED)
        collectJob.cancel()
    }
}
