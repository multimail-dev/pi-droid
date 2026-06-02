package dev.anthropic.pidroid.agent

import dev.anthropic.pidroid.core.event.AgentEvent
import dev.anthropic.pidroid.core.event.AgentEventType
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AgentLoopTest {
    private lateinit var fakeLlm: FakeLlmProvider
    private lateinit var fakeDispatcher: FakeToolDispatcher
    private lateinit var loop: AgentLoop

    @Before
    fun setup() {
        fakeLlm = FakeLlmProvider()
        fakeDispatcher = FakeToolDispatcher()
        loop = AgentLoop(fakeLlm, fakeDispatcher)
    }

    private fun context(messages: List<Message> = listOf(Message.User("Hello"))) = AgentContext(
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

    private fun toolCallResponse(toolId: String, toolName: String, args: JsonObject) = listOf(
        AssistantMessageEvent.Start(Message.Assistant(content = emptyList())),
        AssistantMessageEvent.ToolCallDelta(id = toolId, name = toolName, argumentsDelta = args.toString()),
        AssistantMessageEvent.Done(
            message = Message.Assistant(
                content = listOf(ContentBlock.ToolCall(toolId, toolName, args)),
                stopReason = StopReason.TOOL_USE,
            ),
            stopReason = StopReason.TOOL_USE,
            usage = null,
        ),
    )

    @Test
    fun `simple text response produces correct event sequence`() = runTest {
        fakeLlm.responses.add(textResponse("Hello!"))
        val events = mutableListOf<AgentEvent>()

        // Use UnconfinedTestDispatcher so the collector runs eagerly
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            loop.events.collect { events.add(it) }
        }

        val result = loop.run(context())
        collectJob.cancel()

        assertEquals(StopReason.STOP, result)

        val eventTypes = events.map { it.type }
        assertTrue("Missing AGENT_START", AgentEventType.AGENT_START in eventTypes)
        assertTrue("Missing TURN_START", AgentEventType.TURN_START in eventTypes)
        assertTrue("Missing MESSAGE_START", AgentEventType.MESSAGE_START in eventTypes)
        assertTrue("Missing MESSAGE_UPDATE", AgentEventType.MESSAGE_UPDATE in eventTypes)
        assertTrue("Missing MESSAGE_END", AgentEventType.MESSAGE_END in eventTypes)
        assertTrue("Missing TURN_END", AgentEventType.TURN_END in eventTypes)
        assertTrue("Missing AGENT_END", AgentEventType.AGENT_END in eventTypes)
    }

    @Test
    fun `tool call produces two turns`() = runTest {
        val args = buildJsonObject { put("query", "tomorrow") }

        // Turn 1: tool call
        fakeLlm.responses.add(toolCallResponse("tc_1", "search_calendar", args))
        // Turn 2: text response after tool result
        fakeLlm.responses.add(textResponse("You have a meeting tomorrow."))

        fakeDispatcher.results["search_calendar"] = { tc ->
            ToolResult(toolCallId = tc.id, content = "Meeting at 3pm")
        }

        val events = mutableListOf<AgentEvent>()
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            loop.events.collect { events.add(it) }
        }

        val result = loop.run(context())
        collectJob.cancel()

        assertEquals(StopReason.STOP, result)

        // Verify two turns
        val turnStarts = events.filterIsInstance<AgentEvent.TurnStart>()
        assertEquals(2, turnStarts.size)
        assertEquals(0, turnStarts[0].turnIndex)
        assertEquals(1, turnStarts[1].turnIndex)

        // Verify tool execution events
        val toolStarts = events.filterIsInstance<AgentEvent.ToolExecutionStart>()
        assertEquals(1, toolStarts.size)
        assertEquals("search_calendar", toolStarts[0].toolName)

        val toolEnds = events.filterIsInstance<AgentEvent.ToolExecutionEnd>()
        assertEquals(1, toolEnds.size)
        assertEquals("Meeting at 3pm", toolEnds[0].result.content)
    }

    @Test
    fun `error stop reason ends agent immediately`() = runTest {
        fakeLlm.responses.add(
            listOf(
                AssistantMessageEvent.Start(Message.Assistant(content = emptyList())),
                AssistantMessageEvent.Error(
                    partial = Message.Assistant(content = emptyList()),
                    error = "API error",
                    stopReason = StopReason.ERROR,
                ),
            )
        )

        val events = mutableListOf<AgentEvent>()
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            loop.events.collect { events.add(it) }
        }

        val result = loop.run(context())
        collectJob.cancel()

        assertEquals(StopReason.ERROR, result)

        val agentEnd = events.filterIsInstance<AgentEvent.AgentEnd>().first()
        assertEquals(StopReason.ERROR, agentEnd.reason)
    }

    @Test
    fun `max turns limit stops loop`() = runTest {
        val args = buildJsonObject { put("x", 1) }

        // Queue 3 tool call responses (but max turns is 2)
        repeat(3) {
            fakeLlm.responses.add(toolCallResponse("tc_$it", "tool", args))
        }
        // Final text response (won't be reached)
        fakeLlm.responses.add(textResponse("done"))

        val shortContext = AgentContext(
            llmConfig = LlmConfig(apiKey = "test", model = "test"),
            tools = emptyList(),
            initialMessages = listOf(Message.User("go")),
            loopConfig = AgentLoopConfig(maxTurns = 2),
        )

        val events = mutableListOf<AgentEvent>()
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            loop.events.collect { events.add(it) }
        }

        loop.run(shortContext)
        collectJob.cancel()

        val turnStarts = events.filterIsInstance<AgentEvent.TurnStart>()
        assertTrue("Should not exceed max turns", turnStarts.size <= 2)
    }

    @Test
    fun `follow-up messages inject new turn`() = runTest {
        // First response: text (would normally end)
        fakeLlm.responses.add(textResponse("Initial response"))
        // After follow-up: another text response
        fakeLlm.responses.add(textResponse("Follow-up response"))

        var followUpCalled = false

        val events = mutableListOf<AgentEvent>()
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            loop.events.collect { events.add(it) }
        }

        loop.run(context()) {
            if (!followUpCalled) {
                followUpCalled = true
                listOf(Message.User("What about tomorrow?"))
            } else {
                null
            }
        }
        collectJob.cancel()

        // Should have 2 turns (initial + follow-up)
        val turnStarts = events.filterIsInstance<AgentEvent.TurnStart>()
        assertEquals(2, turnStarts.size)
    }

    @Test
    fun `multiple tool calls dispatched together`() = runTest {
        val args1 = buildJsonObject { put("q", "a") }
        val args2 = buildJsonObject { put("q", "b") }

        // Response with two tool calls
        fakeLlm.responses.add(
            listOf(
                AssistantMessageEvent.Start(Message.Assistant(content = emptyList())),
                AssistantMessageEvent.ToolCallDelta("tc_1", "tool_a", args1.toString()),
                AssistantMessageEvent.ToolCallDelta("tc_2", "tool_b", args2.toString()),
                AssistantMessageEvent.Done(
                    message = Message.Assistant(
                        content = listOf(
                            ContentBlock.ToolCall("tc_1", "tool_a", args1),
                            ContentBlock.ToolCall("tc_2", "tool_b", args2),
                        ),
                        stopReason = StopReason.TOOL_USE,
                    ),
                    stopReason = StopReason.TOOL_USE,
                    usage = null,
                ),
            )
        )
        // Follow-up text response
        fakeLlm.responses.add(textResponse("Both done"))

        val events = mutableListOf<AgentEvent>()
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            loop.events.collect { events.add(it) }
        }

        loop.run(context())
        collectJob.cancel()

        // Verify dispatcher received both tool calls in one batch
        assertEquals(1, fakeDispatcher.dispatches.size)
        assertEquals(2, fakeDispatcher.dispatches[0].size)
        assertEquals("tool_a", fakeDispatcher.dispatches[0][0].name)
        assertEquals("tool_b", fakeDispatcher.dispatches[0][1].name)
    }

    @Test
    fun `tool results returned in source order`() = runTest {
        fakeLlm.responses.add(
            listOf(
                AssistantMessageEvent.Start(Message.Assistant(content = emptyList())),
                AssistantMessageEvent.ToolCallDelta("tc_1", "first", "{}"),
                AssistantMessageEvent.ToolCallDelta("tc_2", "second", "{}"),
                AssistantMessageEvent.Done(
                    message = Message.Assistant(
                        content = listOf(
                            ContentBlock.ToolCall("tc_1", "first", JsonObject(emptyMap())),
                            ContentBlock.ToolCall("tc_2", "second", JsonObject(emptyMap())),
                        ),
                        stopReason = StopReason.TOOL_USE,
                    ),
                    stopReason = StopReason.TOOL_USE,
                    usage = null,
                ),
            )
        )
        fakeLlm.responses.add(textResponse("done"))

        fakeDispatcher.results["first"] = { ToolResult(it.id, "result_1") }
        fakeDispatcher.results["second"] = { ToolResult(it.id, "result_2") }

        val events = mutableListOf<AgentEvent>()
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            loop.events.collect { events.add(it) }
        }

        loop.run(context())
        collectJob.cancel()

        val toolEnds = events.filterIsInstance<AgentEvent.ToolExecutionEnd>()
        assertEquals(2, toolEnds.size)
        assertEquals("tc_1", toolEnds[0].toolCallId)
        assertEquals("tc_2", toolEnds[1].toolCallId)
    }

    @Test
    fun `AgentStart contains correct config`() = runTest {
        fakeLlm.responses.add(textResponse("hi"))

        val events = mutableListOf<AgentEvent>()
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            loop.events.collect { events.add(it) }
        }

        loop.run(context())
        collectJob.cancel()

        val start = events.filterIsInstance<AgentEvent.AgentStart>().first()
        assertEquals("fake", start.config.provider)
        assertEquals("test-model", start.config.model)
        assertEquals(10, start.config.maxTurns)
    }
}
