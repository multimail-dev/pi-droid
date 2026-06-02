package dev.anthropic.pidroid.agent

import dev.anthropic.pidroid.core.event.AgentEvent
import dev.anthropic.pidroid.core.message.AgentMessage
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AgentLoopContinueTest {
    private lateinit var fakeLlm: FakeLlmProvider
    private lateinit var fakeDispatcher: FakeToolDispatcher
    private lateinit var loop: AgentLoop

    @Before
    fun setup() {
        fakeLlm = FakeLlmProvider()
        fakeDispatcher = FakeToolDispatcher()
        loop = AgentLoop(fakeLlm, fakeDispatcher)
    }

    private fun context(messages: List<AgentMessage>) = AgentContext(
        llmConfig = LlmConfig(apiKey = "test", model = "test-model"),
        tools = emptyList(),
        initialMessages = messages,
        loopConfig = AgentLoopConfig(maxTurns = 10),
    )

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
    fun `continue from user message produces assistant response`() = runTest {
        fakeLlm.responses.add(textResponse("I can help!"))

        val events = mutableListOf<AgentEvent>()
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            loop.events.collect { events.add(it) }
        }

        val result = loop.continueFrom(context(listOf(Message.User("hello"))))
        collectJob.cancel()

        assertEquals(StopReason.STOP, result)
        assertTrue(events.any { it is AgentEvent.MessageEnd })
    }

    @Test
    fun `continue from tool result produces assistant response`() = runTest {
        fakeLlm.responses.add(textResponse("Got the result"))

        val transcript: List<AgentMessage> = listOf(
            Message.User("do something"),
            Message.Assistant(
                content = listOf(ContentBlock.ToolCall("tc1", "test_tool", JsonObject(emptyMap()))),
                stopReason = StopReason.TOOL_USE,
            ),
            Message.ToolResult(toolCallId = "tc1", content = "done", isError = false),
        )

        val result = loop.continueFrom(context(transcript))
        assertEquals(StopReason.STOP, result)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `continue from assistant message throws`() = runTest {
        val transcript: List<AgentMessage> = listOf(
            Message.User("hello"),
            Message.Assistant(content = listOf(ContentBlock.Text("hi")), stopReason = StopReason.STOP),
        )

        loop.continueFrom(context(transcript))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `continue from empty transcript throws`() = runTest {
        loop.continueFrom(context(emptyList()))
    }

    @Test
    fun `continue respects steering queue`() = runTest {
        // First response triggers tool use, second is final
        fakeLlm.responses.add(
            listOf(
                AssistantMessageEvent.Start(Message.Assistant(content = emptyList())),
                AssistantMessageEvent.ToolCallDelta(id = "tc1", name = "test", argumentsDelta = "{}"),
                AssistantMessageEvent.Done(
                    message = Message.Assistant(
                        content = listOf(ContentBlock.ToolCall("tc1", "test", JsonObject(emptyMap()))),
                        stopReason = StopReason.TOOL_USE,
                    ),
                    stopReason = StopReason.TOOL_USE,
                    usage = null,
                ),
            )
        )
        fakeLlm.responses.add(textResponse("done with steering"))

        fakeDispatcher.results["test"] = { tc ->
            ToolResult(toolCallId = tc.id, content = "ok", isError = false)
        }

        var steeringCalled = false
        val result = loop.continueFrom(
            context(listOf(Message.User("start"))),
            getSteeringMessages = {
                if (!steeringCalled) {
                    steeringCalled = true
                    listOf(Message.User("steering injection"))
                } else {
                    emptyList()
                }
            },
        )

        assertEquals(StopReason.STOP, result)
        assertTrue(steeringCalled)
    }
}
