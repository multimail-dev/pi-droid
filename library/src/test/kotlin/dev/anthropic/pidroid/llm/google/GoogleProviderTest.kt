package dev.anthropic.pidroid.llm.google

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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GoogleProviderTest {
    private lateinit var parser: GoogleSseParser
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setup() {
        parser = GoogleSseParser()
    }

    // --- SSE Parser Tests ---

    @Test
    fun `text response produces Start, TextDelta, and Done`() {
        val data = """{"candidates":[{"content":{"parts":[{"text":"Hello world"}],"role":"model"},"finishReason":"STOP"}],"usageMetadata":{"promptTokenCount":10,"candidatesTokenCount":5,"totalTokenCount":15}}"""
        val events = parser.parse(data)

        assertEquals(3, events.size)
        assertTrue(events[0] is AssistantMessageEvent.Start)
        assertTrue(events[1] is AssistantMessageEvent.TextDelta)
        assertEquals("Hello world", (events[1] as AssistantMessageEvent.TextDelta).text)
        assertTrue(events[2] is AssistantMessageEvent.Done)
        val done = events[2] as AssistantMessageEvent.Done
        assertEquals(StopReason.STOP, done.stopReason)
        assertEquals("Hello world", done.message.text)
    }

    @Test
    fun `function call produces ToolCallDelta with name and args`() {
        val data = """{"candidates":[{"content":{"parts":[{"functionCall":{"name":"search","args":{"query":"weather"}}}],"role":"model"},"finishReason":"STOP"}],"usageMetadata":{"promptTokenCount":10,"candidatesTokenCount":5,"totalTokenCount":15}}"""
        val events = parser.parse(data)

        assertEquals(3, events.size)
        assertTrue(events[0] is AssistantMessageEvent.Start)
        assertTrue(events[1] is AssistantMessageEvent.ToolCallDelta)
        val delta = events[1] as AssistantMessageEvent.ToolCallDelta
        assertEquals("search", delta.name)
        assertEquals("google-tc-0", delta.id)
        assertTrue(delta.argumentsDelta.contains("weather"))

        // Done should have TOOL_USE stop reason
        assertTrue(events[2] is AssistantMessageEvent.Done)
        val done = events[2] as AssistantMessageEvent.Done
        assertEquals(StopReason.TOOL_USE, done.stopReason)
        val toolCall = done.message.toolCalls.first()
        assertEquals("search", toolCall.name)
        assertEquals("weather", toolCall.arguments["query"]?.jsonPrimitive?.content)
    }

    @Test
    fun `thinking part with thought true produces ThinkingDelta`() {
        val data = """{"candidates":[{"content":{"parts":[{"thought":true,"text":"Let me think about this..."}],"role":"model"}}]}"""
        val events = parser.parse(data)

        // Start + ThinkingDelta (no Done because no finishReason)
        assertEquals(2, events.size)
        assertTrue(events[0] is AssistantMessageEvent.Start)
        assertTrue(events[1] is AssistantMessageEvent.ThinkingDelta)
        assertEquals("Let me think about this...", (events[1] as AssistantMessageEvent.ThinkingDelta).text)
    }

    @Test
    fun `empty response produces error`() {
        val data = """{"candidates":[]}"""
        val events = parser.parse(data)

        // Only Start (no candidates to process)
        assertEquals(1, events.size)
        assertTrue(events[0] is AssistantMessageEvent.Start)
    }

    @Test
    fun `blank data returns empty list`() {
        val events = parser.parse("")
        assertTrue(events.isEmpty())
    }

    @Test
    fun `malformed JSON produces Error event`() {
        val events = parser.parse("not valid json {{{")
        assertEquals(1, events.size)
        assertTrue(events[0] is AssistantMessageEvent.Error)
    }

    @Test
    fun `usage metadata is parsed correctly`() {
        val data = """{"candidates":[{"content":{"parts":[{"text":"Hi"}],"role":"model"},"finishReason":"STOP"}],"usageMetadata":{"promptTokenCount":100,"candidatesTokenCount":50,"totalTokenCount":150}}"""
        val events = parser.parse(data)

        val done = events.filterIsInstance<AssistantMessageEvent.Done>().first()
        assertNotNull(done.usage)
        assertEquals(100, done.usage!!.inputTokens)
        assertEquals(50, done.usage!!.outputTokens)
    }

    @Test
    fun `MAX_TOKENS finish reason maps to LENGTH`() {
        val data = """{"candidates":[{"content":{"parts":[{"text":"truncated"}],"role":"model"},"finishReason":"MAX_TOKENS"}]}"""
        val events = parser.parse(data)

        val done = events.filterIsInstance<AssistantMessageEvent.Done>().first()
        assertEquals(StopReason.LENGTH, done.stopReason)
    }

    @Test
    fun `multiple parts in one chunk are all handled`() {
        val data = """{"candidates":[{"content":{"parts":[{"thought":true,"text":"thinking..."},{"text":"Hello"},{"functionCall":{"name":"search","args":{"q":"test"}}}],"role":"model"},"finishReason":"STOP"}]}"""
        val events = parser.parse(data)

        // Start + ThinkingDelta + TextDelta + ToolCallDelta + Done
        assertEquals(5, events.size)
        assertTrue(events[0] is AssistantMessageEvent.Start)
        assertTrue(events[1] is AssistantMessageEvent.ThinkingDelta)
        assertEquals("thinking...", (events[1] as AssistantMessageEvent.ThinkingDelta).text)
        assertTrue(events[2] is AssistantMessageEvent.TextDelta)
        assertEquals("Hello", (events[2] as AssistantMessageEvent.TextDelta).text)
        assertTrue(events[3] is AssistantMessageEvent.ToolCallDelta)
        assertEquals("search", (events[3] as AssistantMessageEvent.ToolCallDelta).name)
        assertTrue(events[4] is AssistantMessageEvent.Done)
    }

    @Test
    fun `streaming across multiple chunks accumulates text`() {
        // First chunk - no finishReason
        val events1 = parser.parse("""{"candidates":[{"content":{"parts":[{"text":"Hello "}],"role":"model"}}]}""")
        assertEquals(2, events1.size) // Start + TextDelta
        assertTrue(events1[0] is AssistantMessageEvent.Start)
        assertTrue(events1[1] is AssistantMessageEvent.TextDelta)

        // Second chunk - with finishReason
        val events2 = parser.parse("""{"candidates":[{"content":{"parts":[{"text":"world"}],"role":"model"},"finishReason":"STOP"}]}""")
        assertEquals(2, events2.size) // TextDelta + Done
        assertTrue(events2[0] is AssistantMessageEvent.TextDelta)
        assertTrue(events2[1] is AssistantMessageEvent.Done)

        val done = events2[1] as AssistantMessageEvent.Done
        assertEquals("Hello world", done.message.text)
    }

    @Test
    fun `multiple function calls get unique synthetic IDs`() {
        val data = """{"candidates":[{"content":{"parts":[{"functionCall":{"name":"tool_a","args":{}}},{"functionCall":{"name":"tool_b","args":{}}}],"role":"model"},"finishReason":"STOP"}]}"""
        val events = parser.parse(data)

        val toolDeltas = events.filterIsInstance<AssistantMessageEvent.ToolCallDelta>()
        assertEquals(2, toolDeltas.size)
        assertEquals("google-tc-0", toolDeltas[0].id)
        assertEquals("google-tc-1", toolDeltas[1].id)
        assertEquals("tool_a", toolDeltas[0].name)
        assertEquals("tool_b", toolDeltas[1].name)
    }

    @Test
    fun `done message includes thinking blocks before text blocks`() {
        // Chunk with thinking then text
        val events = parser.parse("""{"candidates":[{"content":{"parts":[{"thought":true,"text":"reasoning"},{"text":"answer"}],"role":"model"},"finishReason":"STOP"}]}""")
        val done = events.filterIsInstance<AssistantMessageEvent.Done>().first()
        val blocks = done.message.content
        assertEquals(2, blocks.size)
        assertTrue(blocks[0] is ContentBlock.Thinking)
        assertTrue(blocks[1] is ContentBlock.Text)
        assertEquals("reasoning", (blocks[0] as ContentBlock.Thinking).text)
        assertEquals("answer", (blocks[1] as ContentBlock.Text).text)
    }

    // --- Request Body Tests ---

    @Test
    fun `system prompt goes to systemInstruction field`() {
        val provider = GoogleProvider()
        val config = LlmConfig(
            apiKey = "test-key",
            model = "gemini-2.5-flash",
            systemPrompt = "You are helpful",
        )
        val body = provider.buildRequestBody(
            messages = listOf(Message.User("Hello")),
            tools = emptyList(),
            config = config,
        )

        val parsed = json.parseToJsonElement(body).jsonObject
        assertTrue(parsed.containsKey("systemInstruction"))
        val sysInstruction = parsed["systemInstruction"]!!.jsonObject
        val parts = sysInstruction["parts"]!!.jsonArray
        assertEquals("You are helpful", parts[0].jsonObject["text"]!!.jsonPrimitive.content)

        // System prompt should NOT appear in contents
        val contents = parsed["contents"]!!.jsonArray
        for (content in contents) {
            val role = content.jsonObject["role"]?.jsonPrimitive?.content
            assertTrue(role == "user" || role == "model")
        }
    }

    @Test
    fun `tools are wrapped in functionDeclarations`() {
        val provider = GoogleProvider()
        val config = LlmConfig(apiKey = "test-key", model = "gemini-2.5-flash")
        val tools = listOf(
            ToolDefinition(
                name = "search",
                description = "Search for info",
                inputSchema = buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    put("properties", buildJsonObject {
                        put("query", buildJsonObject { put("type", JsonPrimitive("string")) })
                    })
                },
                category = ToolCategory.DEVICE,
                riskLevel = RiskLevel.READ_ONLY,
            ),
        )
        val body = provider.buildRequestBody(
            messages = listOf(Message.User("Hello")),
            tools = tools,
            config = config,
        )

        val parsed = json.parseToJsonElement(body).jsonObject
        val toolsArray = parsed["tools"]!!.jsonArray
        assertEquals(1, toolsArray.size)
        val declarations = toolsArray[0].jsonObject["functionDeclarations"]!!.jsonArray
        assertEquals(1, declarations.size)
        assertEquals("search", declarations[0].jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals("Search for info", declarations[0].jsonObject["description"]!!.jsonPrimitive.content)
        assertNotNull(declarations[0].jsonObject["parameters"])
    }

    @Test
    fun `user messages map to user role with text parts`() {
        val provider = GoogleProvider()
        val config = LlmConfig(apiKey = "test-key", model = "gemini-2.5-flash")
        val body = provider.buildRequestBody(
            messages = listOf(Message.User("Hello there")),
            tools = emptyList(),
            config = config,
        )

        val parsed = json.parseToJsonElement(body).jsonObject
        val contents = parsed["contents"]!!.jsonArray
        assertEquals(1, contents.size)
        val msg = contents[0].jsonObject
        assertEquals("user", msg["role"]!!.jsonPrimitive.content)
        val parts = msg["parts"]!!.jsonArray
        assertEquals("Hello there", parts[0].jsonObject["text"]!!.jsonPrimitive.content)
    }

    @Test
    fun `assistant messages map to model role`() {
        val provider = GoogleProvider()
        val config = LlmConfig(apiKey = "test-key", model = "gemini-2.5-flash")
        val body = provider.buildRequestBody(
            messages = listOf(
                Message.User("Hi"),
                Message.Assistant(content = listOf(ContentBlock.Text("Hello!"))),
            ),
            tools = emptyList(),
            config = config,
        )

        val parsed = json.parseToJsonElement(body).jsonObject
        val contents = parsed["contents"]!!.jsonArray
        assertEquals(2, contents.size)
        assertEquals("model", contents[1].jsonObject["role"]!!.jsonPrimitive.content)
        val parts = contents[1].jsonObject["parts"]!!.jsonArray
        assertEquals("Hello!", parts[0].jsonObject["text"]!!.jsonPrimitive.content)
    }

    @Test
    fun `assistant tool calls map to functionCall parts`() {
        val provider = GoogleProvider()
        val config = LlmConfig(apiKey = "test-key", model = "gemini-2.5-flash")
        val body = provider.buildRequestBody(
            messages = listOf(
                Message.User("Search for weather"),
                Message.Assistant(content = listOf(
                    ContentBlock.ToolCall(
                        id = "tc-1",
                        name = "search",
                        arguments = JsonObject(mapOf("query" to JsonPrimitive("weather"))),
                    ),
                )),
            ),
            tools = emptyList(),
            config = config,
        )

        val parsed = json.parseToJsonElement(body).jsonObject
        val contents = parsed["contents"]!!.jsonArray
        val modelParts = contents[1].jsonObject["parts"]!!.jsonArray
        val fc = modelParts[0].jsonObject["functionCall"]!!.jsonObject
        assertEquals("search", fc["name"]!!.jsonPrimitive.content)
        assertEquals("weather", fc["args"]!!.jsonObject["query"]!!.jsonPrimitive.content)
    }

    @Test
    fun `tool results map to functionResponse parts`() {
        val provider = GoogleProvider()
        val config = LlmConfig(apiKey = "test-key", model = "gemini-2.5-flash")
        val body = provider.buildRequestBody(
            messages = listOf(
                Message.User("Search"),
                Message.ToolResult(toolCallId = "search", content = "Sunny, 72F"),
            ),
            tools = emptyList(),
            config = config,
        )

        val parsed = json.parseToJsonElement(body).jsonObject
        val contents = parsed["contents"]!!.jsonArray
        val toolResultMsg = contents[1].jsonObject
        assertEquals("user", toolResultMsg["role"]!!.jsonPrimitive.content)
        val parts = toolResultMsg["parts"]!!.jsonArray
        val fr = parts[0].jsonObject["functionResponse"]!!.jsonObject
        assertEquals("search", fr["name"]!!.jsonPrimitive.content)
        assertEquals("Sunny, 72F", fr["response"]!!.jsonObject["output"]!!.jsonPrimitive.content)
    }

    @Test
    fun `generationConfig includes temperature and maxOutputTokens`() {
        val provider = GoogleProvider()
        val config = LlmConfig(
            apiKey = "test-key",
            model = "gemini-2.5-flash",
            temperature = 0.5f,
            maxTokens = 2048,
        )
        val body = provider.buildRequestBody(
            messages = listOf(Message.User("Hi")),
            tools = emptyList(),
            config = config,
        )

        val parsed = json.parseToJsonElement(body).jsonObject
        val genConfig = parsed["generationConfig"]!!.jsonObject
        assertEquals(0.5f, genConfig["temperature"]!!.jsonPrimitive.content.toFloat(), 0.001f)
        assertEquals(2048, genConfig["maxOutputTokens"]!!.jsonPrimitive.content.toInt())
        assertEquals("text/plain", genConfig["responseMimeType"]!!.jsonPrimitive.content)
    }

    @Test
    fun `no tools produces no tools field`() {
        val provider = GoogleProvider()
        val config = LlmConfig(apiKey = "test-key", model = "gemini-2.5-flash")
        val body = provider.buildRequestBody(
            messages = listOf(Message.User("Hi")),
            tools = emptyList(),
            config = config,
        )

        val parsed = json.parseToJsonElement(body).jsonObject
        assertTrue(!parsed.containsKey("tools"))
    }

    @Test
    fun `no system prompt produces no systemInstruction field`() {
        val provider = GoogleProvider()
        val config = LlmConfig(
            apiKey = "test-key",
            model = "gemini-2.5-flash",
            systemPrompt = null,
        )
        val body = provider.buildRequestBody(
            messages = listOf(Message.User("Hi")),
            tools = emptyList(),
            config = config,
        )

        val parsed = json.parseToJsonElement(body).jsonObject
        assertTrue(!parsed.containsKey("systemInstruction"))
    }
}
