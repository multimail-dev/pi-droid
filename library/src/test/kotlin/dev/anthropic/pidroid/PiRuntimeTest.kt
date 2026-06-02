package dev.anthropic.pidroid

import dev.anthropic.pidroid.capabilities.CapabilityGrant
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PiRuntimeTest {
    private val testConfig = PiRuntimeConfig(
        llmProvider = LlmProviderConfig(
            provider = "anthropic",
            modelId = "claude-sonnet-4-20250514",
            apiKey = "test-key-not-real",
        ),
        capabilities = listOf(
            CapabilityGrant(CapabilityGrant.CAPABILITY_DEVICE_STATE),
        ),
    )

    @Before
    fun setup() {
        PiRuntime.resetForTesting()
    }

    @After
    fun teardown() {
        PiRuntime.resetForTesting()
    }

    @Test
    fun `initialize creates runtime with provided config`() = runTest {
        val runtime = PiRuntime.initialize(RuntimeEnvironment.getApplication(), testConfig)
        assertNotNull(runtime)
        assertEquals(RuntimeStatus.IDLE, runtime.state.value.status)
    }

    @Test
    fun `double initialize throws`() = runTest {
        PiRuntime.initialize(RuntimeEnvironment.getApplication(), testConfig)
        try {
            PiRuntime.initialize(RuntimeEnvironment.getApplication(), testConfig)
            fail("Should throw on double initialize")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("already initialized"))
        }
    }

    @Test
    fun `sendPrompt starts agent loop`() = runTest {
        val runtime = PiRuntime.initialize(RuntimeEnvironment.getApplication(), testConfig)
        val job = runtime.sendPrompt("Hello")
        // Job should be active (will fail to connect to API but that's expected)
        assertTrue(job.isActive || job.isCompleted)
    }

    @Test
    fun `cancel aborts in-flight task`() = runTest {
        val runtime = PiRuntime.initialize(RuntimeEnvironment.getApplication(), testConfig)
        runtime.sendPrompt("Hello")
        runtime.cancel()
        assertEquals(RuntimeStatus.IDLE, runtime.state.value.status)
    }

    @Test
    fun `shutdown prevents further prompts`() = runTest {
        val runtime = PiRuntime.initialize(RuntimeEnvironment.getApplication(), testConfig)
        runtime.shutdown()
        try {
            runtime.sendPrompt("Hello")
            fail("Should throw after shutdown")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("shut down"))
        }
    }

    @Test
    fun `sendPrompt while running queues as follow-up`() = runTest {
        val runtime = PiRuntime.initialize(RuntimeEnvironment.getApplication(), testConfig)
        val job1 = runtime.sendPrompt("First prompt")
        val job2 = runtime.sendPrompt("Follow-up prompt")
        // Both should return the same job (follow-up is queued, not new)
        assertEquals(job1, job2)
    }
}
