package dev.anthropic.pidroid.agent

import dev.anthropic.pidroid.core.event.AgentEvent
import dev.anthropic.pidroid.core.message.ContentBlock
import dev.anthropic.pidroid.core.message.Message
import dev.anthropic.pidroid.core.model.StopReason
import dev.anthropic.pidroid.llm.AssistantMessageEvent
import dev.anthropic.pidroid.llm.FakeLlmProvider
import dev.anthropic.pidroid.llm.LlmConfig
import dev.anthropic.pidroid.llm.TokenUsage
import dev.anthropic.pidroid.tools.ToolResult
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class AgentLoopContextTransformTest {
    private lateinit var fakeLlm: FakeLlmProvider
    private lateinit var fakeDispatcher: FakeToolDispatcher
    private lateinit var loop: AgentLoop

    @Before
    fun setup() {
        fakeLlm = FakeLlmProvider()
        fakeDispatcher = FakeToolDispatcher()
        loop = AgentLoop(fakeLlm, fakeDispatcher)
    }

    private fun textResponse(text: String) = listOf(
        AssistantMessageEvent.Start(Message.Assistant(content = emptyList())),
        AssistantMessageEvent.TextDelta(text),
        AssistantMessageEvent.Done(
            message = Message.Assistant(
                content = listOf(ContentBlock.Text(text)),
                stopReason = StopReason.STOP,
            ),
            stopReason = StopReason.STOP,
            usage = TokenUsage(10, 20),
        ),
    )

    @Test
    fun `transformContext prunes messages for LLM call`() = runTest {
        // Transform that only keeps the last message
        val config = AgentLoopConfig(
            transformContext = { messages -> messages.takeLast(1) }
        )

        fakeLlm.responses.add(textResponse("response"))

        val context = AgentContext(
            llmConfig = LlmConfig(apiKey = "test", model = "test-model"),
            tools = emptyList(),
            initialMessages = listOf(
                Message.User("first message"),
                Message.User("second message"),
                Message.User("third message"),
            ),
            loopConfig = config,
        )

        loop.run(context)

        // LLM should have received only the last message (due to transform)
        assertEquals(1, fakeLlm.calls.size)
        assertEquals(1, fakeLlm.calls[0].messages.size)
    }

    @Test
    fun `null transformContext passes all messages unchanged`() = runTest {
        val config = AgentLoopConfig(transformContext = null)

        fakeLlm.responses.add(textResponse("response"))

        val context = AgentContext(
            llmConfig = LlmConfig(apiKey = "test", model = "test-model"),
            tools = emptyList(),
            initialMessages = listOf(Message.User("hello"), Message.User("world")),
            loopConfig = config,
        )

        loop.run(context)

        assertEquals(1, fakeLlm.calls.size)
        assertEquals(2, fakeLlm.calls[0].messages.size)
    }

    @Test
    fun `transformContext runs on every turn`() = runTest {
        var transformCallCount = 0
        val config = AgentLoopConfig(
            transformContext = { messages ->
                transformCallCount++
                messages
            }
        )

        // Turn 1: tool call
        fakeLlm.responses.add(listOf(
            AssistantMessageEvent.Start(Message.Assistant(content = emptyList())),
            AssistantMessageEvent.ToolCallDelta(id = "tc1", name = "test_tool", argumentsDelta = "{}"),
            AssistantMessageEvent.Done(
                message = Message.Assistant(
                    content = listOf(ContentBlock.ToolCall("tc1", "test_tool", JsonObject(emptyMap()))),
                    stopReason = StopReason.TOOL_USE,
                ),
                stopReason = StopReason.TOOL_USE,
                usage = null,
            ),
        ))
        // Turn 2: text response
        fakeLlm.responses.add(textResponse("done"))

        val context = AgentContext(
            llmConfig = LlmConfig(apiKey = "test", model = "test-model"),
            tools = emptyList(),
            initialMessages = listOf(Message.User("hello")),
            loopConfig = config,
        )

        loop.run(context)

        assertEquals(2, transformCallCount) // Called for each of the 2 turns
    }

    @Test
    fun `canonical transcript retains all messages even when transform prunes`() = runTest {
        val config = AgentLoopConfig(
            transformContext = { messages -> messages.takeLast(1) }
        )

        fakeLlm.responses.add(textResponse("response"))

        val events = mutableListOf<AgentEvent>()
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            loop.events.collect { events.add(it) }
        }

        val context = AgentContext(
            llmConfig = LlmConfig(apiKey = "test", model = "test-model"),
            tools = emptyList(),
            initialMessages = listOf(Message.User("first"), Message.User("second")),
            loopConfig = config,
        )

        loop.run(context)
        collectJob.cancel()

        // MessageEnd should still show the full assistant response (loop isn't pruned)
        val messageEnd = events.filterIsInstance<AgentEvent.MessageEnd>().firstOrNull()
        assertNotNull(messageEnd)

        // The LLM only saw 1 message (the transform pruned), but the canonical transcript
        // retained all messages - verify the LLM call received the pruned version
        assertEquals(1, fakeLlm.calls[0].messages.size)
    }
}
