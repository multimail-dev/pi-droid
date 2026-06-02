package dev.anthropic.pidroid.demo.simulation

import dev.anthropic.pidroid.demo.ui.MessageRole
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DemoPlayerTest {

    private val simpleScenario = DemoScenario(
        id = "test",
        title = "Test",
        subtitle = "A test scenario",
        userPrompt = "Hello",
        steps = listOf(
            DemoScenario.Step.AssistantStream("Hi there!", charDelayMs = 1),
        ),
    )

    @Test
    fun `play adds user message then streams assistant response`() = runTest {
        val player = DemoPlayer()
        player.play(simpleScenario)

        val messages = player.messages.value
        assertEquals(2, messages.size)
        assertEquals(MessageRole.USER, messages[0].role)
        assertEquals("Hello", messages[0].content)
        assertEquals(MessageRole.ASSISTANT, messages[1].role)
        assertEquals("Hi there!", messages[1].content)
    }

    @Test
    fun `isPlaying is true during playback and false after`() = runTest {
        val player = DemoPlayer()
        assertFalse(player.isPlaying.value)

        val job = launch { player.play(simpleScenario) }
        // Give the coroutine time to start
        advanceTimeBy(100)
        assertTrue(player.isPlaying.value)

        advanceUntilIdle()
        job.join()
        assertFalse(player.isPlaying.value)
    }

    @Test
    fun `tool execution produces tool messages`() = runTest {
        val scenario = DemoScenario(
            id = "tool-test",
            title = "Tool Test",
            subtitle = "Tests tool execution",
            userPrompt = "Check battery",
            steps = listOf(
                DemoScenario.Step.ToolExecution(
                    toolName = "get_battery_state",
                    args = "{}",
                    result = """{"level": 85}""",
                    durationMs = 1,
                ),
            ),
        )

        val player = DemoPlayer()
        player.play(scenario)

        val messages = player.messages.value
        assertEquals(2, messages.size) // user + tool
        assertEquals(MessageRole.TOOL, messages[1].role)
        assertEquals("get_battery_state", messages[1].toolName)
        assertEquals("Done", messages[1].content)
    }

    @Test
    fun `confirmation suspends until resolved`() = runTest {
        val scenario = DemoScenario(
            id = "confirm-test",
            title = "Confirm Test",
            subtitle = "Tests confirmation",
            userPrompt = "Navigate",
            steps = listOf(
                DemoScenario.Step.Confirmation(
                    toolName = "send_intent",
                    description = "Open maps",
                    args = """{"action": "VIEW"}""",
                ),
            ),
        )

        val player = DemoPlayer()
        val job = launch { player.play(scenario) }

        // Give time for the user message + confirmation to appear
        advanceTimeBy(1000)

        // Should be waiting for confirmation
        assertTrue(player.isPlaying.value)
        assertNotNull(player.pendingConfirmation.value)
        assertEquals("send_intent", player.pendingConfirmation.value?.toolName)

        // Approve
        player.resolveConfirmation(true)
        advanceUntilIdle()
        job.join()

        assertFalse(player.isPlaying.value)
        assertNull(player.pendingConfirmation.value)

        // Should have user + tool(Done) messages
        val messages = player.messages.value
        assertEquals(2, messages.size)
        assertEquals("Done", messages[1].content)
    }

    @Test
    fun `deny confirmation produces denied message`() = runTest {
        val scenario = DemoScenario(
            id = "deny-test",
            title = "Deny Test",
            subtitle = "Tests denial",
            userPrompt = "Share",
            steps = listOf(
                DemoScenario.Step.Confirmation(
                    toolName = "share_text",
                    description = "Share message",
                    args = "{}",
                ),
            ),
        )

        val player = DemoPlayer()
        val job = launch { player.play(scenario) }
        advanceTimeBy(1000)

        player.resolveConfirmation(false)
        advanceUntilIdle()
        job.join()

        val messages = player.messages.value
        assertEquals(2, messages.size)
        assertEquals("Denied by user", messages[1].content)
    }

    @Test
    fun `reset clears all state`() = runTest {
        val player = DemoPlayer()
        player.play(simpleScenario)

        assertTrue(player.messages.value.isNotEmpty())

        player.reset()

        assertTrue(player.messages.value.isEmpty())
        assertEquals("", player.streamingText.value)
        assertFalse(player.isPlaying.value)
        assertNull(player.pendingConfirmation.value)
    }

    @Test
    fun `cancellation stops playback cleanly`() = runTest {
        val slowScenario = DemoScenario(
            id = "slow",
            title = "Slow",
            subtitle = "Takes a while",
            userPrompt = "Slow task",
            steps = listOf(
                DemoScenario.Step.Pause(10_000),
                DemoScenario.Step.AssistantStream("Should not appear", charDelayMs = 1),
            ),
        )

        val player = DemoPlayer()
        val job = launch { player.play(slowScenario) }
        advanceTimeBy(500) // In the pause
        assertTrue(player.isPlaying.value)

        job.cancel()
        advanceUntilIdle()

        assertFalse(player.isPlaying.value)
        // Only the user message should be present
        assertEquals(1, player.messages.value.size)
    }

    @Test
    fun `all pre-built scenarios are valid`() {
        val scenarios = DemoScenario.ALL
        assertTrue(scenarios.size >= 3)

        for (scenario in scenarios) {
            assertTrue("Scenario ${scenario.id} has no steps", scenario.steps.isNotEmpty())
            assertTrue("Scenario ${scenario.id} has no title", scenario.title.isNotBlank())
            assertTrue("Scenario ${scenario.id} has no prompt", scenario.userPrompt.isNotBlank())
        }
    }

    @Test
    fun `dinner prep scenario has expected flow`() {
        val scenario = DemoScenario.dinnerPrep()
        assertEquals("dinner-prep", scenario.id)

        // Should contain: calendar read, contact lookups, device state, confirmation
        val toolSteps = scenario.steps.filterIsInstance<DemoScenario.Step.ToolExecution>()
        val confirmSteps = scenario.steps.filterIsInstance<DemoScenario.Step.Confirmation>()
        val streamSteps = scenario.steps.filterIsInstance<DemoScenario.Step.AssistantStream>()

        assertTrue("Should have tool executions", toolSteps.size >= 4)
        assertEquals("Should have 1 confirmation (navigation)", 1, confirmSteps.size)
        assertTrue("Should have assistant text", streamSteps.size >= 3)

        // Verify tool names
        val toolNames = toolSteps.map { it.toolName }
        assertTrue("read_calendar_events" in toolNames)
        assertTrue("search_contacts" in toolNames)
        assertTrue("get_battery_state" in toolNames)
    }

    @Test
    fun `multi-step scenario plays all steps in order`() = runTest {
        val scenario = DemoScenario(
            id = "multi",
            title = "Multi",
            subtitle = "Multiple step types",
            userPrompt = "Do everything",
            steps = listOf(
                DemoScenario.Step.AssistantStream("Step 1", charDelayMs = 1),
                DemoScenario.Step.Pause(1),
                DemoScenario.Step.ToolExecution("tool_a", "{}", "ok", durationMs = 1),
                DemoScenario.Step.AssistantStream("Step 2", charDelayMs = 1),
            ),
        )

        val player = DemoPlayer()
        player.play(scenario)

        val messages = player.messages.value
        // user + assistant("Step 1") + tool("Done") + assistant("Step 2")
        assertEquals(4, messages.size)
        assertEquals(MessageRole.USER, messages[0].role)
        assertEquals(MessageRole.ASSISTANT, messages[1].role)
        assertEquals("Step 1", messages[1].content)
        assertEquals(MessageRole.TOOL, messages[2].role)
        assertEquals(MessageRole.ASSISTANT, messages[3].role)
        assertEquals("Step 2", messages[3].content)
    }
}
