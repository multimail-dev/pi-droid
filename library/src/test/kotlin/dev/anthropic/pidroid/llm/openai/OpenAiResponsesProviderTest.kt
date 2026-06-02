package dev.anthropic.pidroid.llm.openai

import dev.anthropic.pidroid.core.message.ContentBlock
import dev.anthropic.pidroid.core.message.Message
import dev.anthropic.pidroid.core.model.StopReason
import dev.anthropic.pidroid.llm.AssistantMessageEvent
import dev.anthropic.pidroid.llm.LlmConfig
import dev.anthropic.pidroid.tools.RiskLevel
import dev.anthropic.pidroid.tools.ToolCategory
import dev.anthropic.pidroid.tools.ToolDefinition
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for [ResponsesSseParser] and [OpenAiResponsesProvider] request building.
 *
 * Validates that the Responses API SSE event stream is correctly parsed into
 * [AssistantMessageEvent]s and that request bodies conform to the Responses API format.
 */
class OpenAiResponsesProviderTest {
    private lateinit var parser: ResponsesSseParser
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setup() {
        parser = ResponsesSseParser()
    }

    // =========================================================================
    // SSE Parser Tests
    // =========================================================================

    @Test
    fun `response_created produces Start event`() {
        val event = parser.parse(
            "response.created",
            """{"type":"response.created","response":{"id":"resp_01","status":"in_progress"}}"""
        )
        assertTrue(event is AssistantMessageEvent.Start)
    }

    @Test
    fun `text delta streams correctly through Responses API event format`() {
        // Start the response
        parser.parse("response.created", """{"type":"response.created","response":{"id":"resp_01","status":"in_progress"}}""")
        // Add output item (message)
        parser.parse("response.output_item.added", """{"type":"response.output_item.added","output_index":0,"item":{"type":"message","role":"assistant","id":"msg_01","content":[]}}""")
        // Add content part
        parser.parse("response.content_part.added", """{"type":"response.content_part.added","output_index":0,"content_index":0,"part":{"type":"output_text","text":""}}""")

        // Text deltas
        val event1 = parser.parse("response.output_text.delta", """{"type":"response.output_text.delta","output_index":0,"content_index":0,"delta":"Hello"}""")
        assertTrue(event1 is AssistantMessageEvent.TextDelta)
        assertEquals("Hello", (event1 as AssistantMessageEvent.TextDelta).text)

        val event2 = parser.parse("response.output_text.delta", """{"type":"response.output_text.delta","output_index":0,"content_index":0,"delta":" world"}""")
        assertTrue(event2 is AssistantMessageEvent.TextDelta)
        assertEquals(" world", (event2 as AssistantMessageEvent.TextDelta).text)

        // Complete
        val done = parser.parse("response.completed", """{"type":"response.completed","response":{"id":"resp_01","status":"completed","usage":{"input_tokens":10,"output_tokens":5}}}""")
        assertTrue(done is AssistantMessageEvent.Done)
        val doneEvent = done as AssistantMessageEvent.Done
        assertEquals("Hello world", doneEvent.message.text)
        assertEquals(StopReason.STOP, doneEvent.stopReason)
    }

    @Test
    fun `function call with arguments streams via function_call_arguments_delta`() {
        parser.parse("response.created", """{"type":"response.created","response":{"id":"resp_01","status":"in_progress"}}""")

        // Function call item added
        val addedEvent = parser.parse("response.output_item.added", """{"type":"response.output_item.added","output_index":0,"item":{"type":"function_call","id":"fc_01","call_id":"call_abc","name":"search","arguments":""}}""")
        assertTrue(addedEvent is AssistantMessageEvent.ToolCallDelta)
        val firstDelta = addedEvent as AssistantMessageEvent.ToolCallDelta
        assertEquals("call_abc", firstDelta.id)
        assertEquals("search", firstDelta.name)

        // Arguments delta
        val argEvent1 = parser.parse("response.function_call_arguments.delta", """{"type":"response.function_call_arguments.delta","output_index":0,"item_id":"fc_01","delta":"{\"q"}""")
        assertTrue(argEvent1 is AssistantMessageEvent.ToolCallDelta)
        assertEquals("{\"q", (argEvent1 as AssistantMessageEvent.ToolCallDelta).argumentsDelta)
        assertEquals("call_abc", argEvent1.id)

        val argEvent2 = parser.parse("response.function_call_arguments.delta", """{"type":"response.function_call_arguments.delta","output_index":0,"item_id":"fc_01","delta":"uery\":\"hello\"}"}""")
        assertTrue(argEvent2 is AssistantMessageEvent.ToolCallDelta)
        assertEquals("uery\":\"hello\"}", (argEvent2 as AssistantMessageEvent.ToolCallDelta).argumentsDelta)

        // Output item done with final arguments
        parser.parse("response.output_item.done", """{"type":"response.output_item.done","output_index":0,"item":{"type":"function_call","id":"fc_01","call_id":"call_abc","name":"search","arguments":"{\"query\":\"hello\"}"}}""")

        // Response completed
        val done = parser.parse("response.completed", """{"type":"response.completed","response":{"id":"resp_01","status":"completed","usage":{"input_tokens":20,"output_tokens":15}}}""")
        assertTrue(done is AssistantMessageEvent.Done)
        val doneEvent = done as AssistantMessageEvent.Done
        assertEquals(StopReason.TOOL_USE, doneEvent.stopReason)
        val toolCall = doneEvent.message.toolCalls.first()
        assertEquals("call_abc", toolCall.id)
        assertEquals("search", toolCall.name)
        assertEquals("hello", toolCall.arguments["query"]?.jsonPrimitive?.content)
    }

    @Test
    fun `reasoning summary events mapped to ThinkingDelta`() {
        parser.parse("response.created", """{"type":"response.created","response":{"id":"resp_01","status":"in_progress"}}""")
        parser.parse("response.output_item.added", """{"type":"response.output_item.added","output_index":0,"item":{"type":"reasoning","id":"rs_01"}}""")
        parser.parse("response.reasoning_summary_part.added", """{"type":"response.reasoning_summary_part.added","output_index":0,"part":{"type":"summary_text","text":""}}""")

        val event = parser.parse("response.reasoning_summary_text.delta", """{"type":"response.reasoning_summary_text.delta","output_index":0,"delta":"Let me think..."}""")
        assertTrue(event is AssistantMessageEvent.ThinkingDelta)
        assertEquals("Let me think...", (event as AssistantMessageEvent.ThinkingDelta).text)

        // Part done adds newline separator
        val partDone = parser.parse("response.reasoning_summary_part.done", """{"type":"response.reasoning_summary_part.done","output_index":0,"part":{"type":"summary_text","text":"Let me think..."}}""")
        assertTrue(partDone is AssistantMessageEvent.ThinkingDelta)
        assertEquals("\n\n", (partDone as AssistantMessageEvent.ThinkingDelta).text)
    }

    @Test
    fun `response_completed extracts usage`() {
        parser.parse("response.created", """{"type":"response.created","response":{"id":"resp_01","status":"in_progress"}}""")
        parser.parse("response.output_text.delta", """{"type":"response.output_text.delta","output_index":0,"content_index":0,"delta":"Hi"}""")

        val done = parser.parse("response.completed", """{"type":"response.completed","response":{"id":"resp_01","status":"completed","usage":{"input_tokens":50,"output_tokens":25}}}""")
        assertTrue(done is AssistantMessageEvent.Done)
        val doneEvent = done as AssistantMessageEvent.Done
        assertNotNull(doneEvent.usage)
        assertEquals(50, doneEvent.usage!!.inputTokens)
        assertEquals(25, doneEvent.usage!!.outputTokens)
    }

    @Test
    fun `incomplete status maps to LENGTH stop reason`() {
        parser.parse("response.created", """{"type":"response.created","response":{"id":"resp_01","status":"in_progress"}}""")
        parser.parse("response.output_text.delta", """{"type":"response.output_text.delta","output_index":0,"content_index":0,"delta":"truncated"}""")

        val done = parser.parse("response.completed", """{"type":"response.completed","response":{"id":"resp_01","status":"incomplete","usage":{"input_tokens":50,"output_tokens":4096}}}""")
        assertTrue(done is AssistantMessageEvent.Done)
        assertEquals(StopReason.LENGTH, (done as AssistantMessageEvent.Done).stopReason)
    }

    @Test
    fun `response_failed produces Error event`() {
        parser.parse("response.created", """{"type":"response.created","response":{"id":"resp_01","status":"in_progress"}}""")

        val event = parser.parse("response.failed", """{"type":"response.failed","response":{"id":"resp_01","status":"failed","error":{"code":"rate_limit_exceeded","message":"Rate limit reached"}}}""")
        assertTrue(event is AssistantMessageEvent.Error)
        assertEquals("rate_limit_exceeded: Rate limit reached", (event as AssistantMessageEvent.Error).error)
    }

    @Test
    fun `error event produces Error event`() {
        val event = parser.parse("error", """{"type":"error","code":"server_error","message":"Internal server error"}""")
        assertTrue(event is AssistantMessageEvent.Error)
        assertEquals("Error Code server_error: Internal server error", (event as AssistantMessageEvent.Error).error)
    }

    @Test
    fun `malformed data produces Error event not exception`() {
        val event = parser.parse("response.created", "not valid json {{{")
        assertTrue(event is AssistantMessageEvent.Error)
    }

    @Test
    fun `empty data returns null`() {
        val event = parser.parse("response.created", "")
        assertTrue(event == null)
    }

    @Test
    fun `unknown event type returns null`() {
        val event = parser.parse("response.some_future_event", """{"type":"response.some_future_event"}""")
        assertTrue(event == null)
    }

    // =========================================================================
    // Request Body Tests
    // =========================================================================

    @Test
    fun `system prompt sent as developer role input message`() {
        val provider = OpenAiResponsesProvider()
        val config = LlmConfig(
            apiKey = "test-key",
            model = "gpt-4o",
            systemPrompt = "You are a helpful assistant.",
        )

        val body = provider.buildRequestBody(
            messages = listOf(Message.User("Hello")),
            tools = emptyList(),
            config = config,
        )

        val parsed = json.parseToJsonElement(body).jsonObject
        val input = parsed["input"]!!.jsonArray

        // First input message should be developer role
        val developerMsg = input[0].jsonObject
        assertEquals("developer", developerMsg["role"]!!.jsonPrimitive.content)
        val content = developerMsg["content"]!!.jsonArray[0].jsonObject
        assertEquals("input_text", content["type"]!!.jsonPrimitive.content)
        assertEquals("You are a helpful assistant.", content["text"]!!.jsonPrimitive.content)
    }

    @Test
    fun `user message sent as input_text content`() {
        val provider = OpenAiResponsesProvider()
        val config = LlmConfig(apiKey = "test-key", model = "gpt-4o")

        val body = provider.buildRequestBody(
            messages = listOf(Message.User("Hello")),
            tools = emptyList(),
            config = config,
        )

        val parsed = json.parseToJsonElement(body).jsonObject
        val input = parsed["input"]!!.jsonArray
        val userMsg = input[0].jsonObject
        assertEquals("user", userMsg["role"]!!.jsonPrimitive.content)
        val content = userMsg["content"]!!.jsonArray[0].jsonObject
        assertEquals("input_text", content["type"]!!.jsonPrimitive.content)
        assertEquals("Hello", content["text"]!!.jsonPrimitive.content)
    }

    @Test
    fun `assistant text sent as output message item`() {
        val provider = OpenAiResponsesProvider()
        val config = LlmConfig(apiKey = "test-key", model = "gpt-4o")

        val body = provider.buildRequestBody(
            messages = listOf(
                Message.User("Hello"),
                Message.Assistant(content = listOf(ContentBlock.Text("Hi there!"))),
                Message.User("How are you?"),
            ),
            tools = emptyList(),
            config = config,
        )

        val parsed = json.parseToJsonElement(body).jsonObject
        val input = parsed["input"]!!.jsonArray
        // input[0] = user, input[1] = assistant message item, input[2] = user
        val assistantItem = input[1].jsonObject
        assertEquals("message", assistantItem["type"]!!.jsonPrimitive.content)
        assertEquals("assistant", assistantItem["role"]!!.jsonPrimitive.content)
        val outputText = assistantItem["content"]!!.jsonArray[0].jsonObject
        assertEquals("output_text", outputText["type"]!!.jsonPrimitive.content)
        assertEquals("Hi there!", outputText["text"]!!.jsonPrimitive.content)
    }

    @Test
    fun `assistant tool call sent as function_call item with call_id`() {
        val provider = OpenAiResponsesProvider()
        val config = LlmConfig(apiKey = "test-key", model = "gpt-4o")

        val body = provider.buildRequestBody(
            messages = listOf(
                Message.User("Search for events"),
                Message.Assistant(content = listOf(
                    ContentBlock.ToolCall(
                        id = "call_abc",
                        name = "search",
                        arguments = buildJsonObject { put("query", "events") },
                    )
                )),
                Message.ToolResult(toolCallId = "call_abc", content = "Found 3 events"),
            ),
            tools = emptyList(),
            config = config,
        )

        val parsed = json.parseToJsonElement(body).jsonObject
        val input = parsed["input"]!!.jsonArray
        // input[0] = user, input[1] = function_call, input[2] = function_call_output
        val fcItem = input[1].jsonObject
        assertEquals("function_call", fcItem["type"]!!.jsonPrimitive.content)
        assertEquals("call_abc", fcItem["call_id"]!!.jsonPrimitive.content)
        assertEquals("search", fcItem["name"]!!.jsonPrimitive.content)

        val fcOutput = input[2].jsonObject
        assertEquals("function_call_output", fcOutput["type"]!!.jsonPrimitive.content)
        assertEquals("call_abc", fcOutput["call_id"]!!.jsonPrimitive.content)
        assertEquals("Found 3 events", fcOutput["output"]!!.jsonPrimitive.content)
    }

    @Test
    fun `tool definitions sent as function type with strict true`() {
        val provider = OpenAiResponsesProvider()
        val config = LlmConfig(apiKey = "test-key", model = "gpt-4o")

        val toolDef = ToolDefinition(
            name = "search",
            description = "Search for info",
            inputSchema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    put("query", buildJsonObject {
                        put("type", "string")
                    })
                })
            },
            category = ToolCategory.DEVICE,
            riskLevel = RiskLevel.READ_ONLY,
        )

        val body = provider.buildRequestBody(
            messages = listOf(Message.User("Hello")),
            tools = listOf(toolDef),
            config = config,
        )

        val parsed = json.parseToJsonElement(body).jsonObject
        val tools = parsed["tools"]!!.jsonArray
        assertEquals(1, tools.size)
        val tool = tools[0].jsonObject
        assertEquals("function", tool["type"]!!.jsonPrimitive.content)
        assertEquals("search", tool["name"]!!.jsonPrimitive.content)
        assertEquals("Search for info", tool["description"]!!.jsonPrimitive.content)
        assertEquals("true", tool["strict"]!!.jsonPrimitive.content)
        assertNotNull(tool["parameters"])
    }

    @Test
    fun `request body uses input not messages`() {
        val provider = OpenAiResponsesProvider()
        val config = LlmConfig(apiKey = "test-key", model = "gpt-4o")

        val body = provider.buildRequestBody(
            messages = listOf(Message.User("Hello")),
            tools = emptyList(),
            config = config,
        )

        val parsed = json.parseToJsonElement(body).jsonObject
        assertNotNull(parsed["input"])
        assertTrue(parsed["messages"] == null)
        assertEquals("true", parsed["stream"]!!.jsonPrimitive.content)
        assertEquals("gpt-4o", parsed["model"]!!.jsonPrimitive.content)
    }

    @Test
    fun `tool call with text and tool call in same assistant message`() {
        val provider = OpenAiResponsesProvider()
        val config = LlmConfig(apiKey = "test-key", model = "gpt-4o")

        val body = provider.buildRequestBody(
            messages = listOf(
                Message.User("Hello"),
                Message.Assistant(content = listOf(
                    ContentBlock.Text("Let me search for that."),
                    ContentBlock.ToolCall(
                        id = "call_xyz",
                        name = "search",
                        arguments = buildJsonObject { put("q", "test") },
                    )
                )),
            ),
            tools = emptyList(),
            config = config,
        )

        val parsed = json.parseToJsonElement(body).jsonObject
        val input = parsed["input"]!!.jsonArray
        // input[0] = user, input[1] = message (text), input[2] = function_call
        assertEquals(3, input.size)
        assertEquals("message", input[1].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("function_call", input[2].jsonObject["type"]!!.jsonPrimitive.content)
    }
}
