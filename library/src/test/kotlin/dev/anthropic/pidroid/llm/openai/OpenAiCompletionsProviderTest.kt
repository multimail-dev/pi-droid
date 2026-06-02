package dev.anthropic.pidroid.llm.openai

import dev.anthropic.pidroid.core.message.ContentBlock
import dev.anthropic.pidroid.core.message.Message
import dev.anthropic.pidroid.llm.LlmConfig
import dev.anthropic.pidroid.llm.registry.OpenAiCompat
import dev.anthropic.pidroid.tools.RiskLevel
import dev.anthropic.pidroid.tools.ToolCategory
import dev.anthropic.pidroid.tools.ToolDefinition
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Tests for [OpenAiCompletionsProvider.buildRequestBody] verifying compat settings
 * produce the correct JSON request body for various OpenAI-compatible providers.
 */
class OpenAiCompletionsProviderTest {
    private lateinit var provider: OpenAiCompletionsProvider
    private val json = Json { ignoreUnknownKeys = true }

    private val baseConfig = LlmConfig(
        apiKey = "test-key",
        model = "gpt-4o",
        maxTokens = 1024,
        temperature = 0.5f,
        systemPrompt = "You are a helpful assistant.",
    )

    private val simpleMessages = listOf(Message.User("Hello"))

    private fun testTool() = ToolDefinition(
        name = "search",
        description = "Search the web",
        inputSchema = buildJsonObject {
            put("type", kotlinx.serialization.json.JsonPrimitive("object"))
            put("properties", buildJsonObject {
                put("query", buildJsonObject {
                    put("type", kotlinx.serialization.json.JsonPrimitive("string"))
                })
            })
        },
        category = ToolCategory.DEVICE,
        riskLevel = RiskLevel.READ_ONLY,
    )

    @Before
    fun setup() {
        provider = OpenAiCompletionsProvider()
    }

    private fun parseBody(bodyStr: String): JsonObject =
        json.decodeFromString(JsonObject.serializer(), bodyStr)

    @Test
    fun `default compat produces backward-compatible request body`() {
        val body = parseBody(provider.buildRequestBody(simpleMessages, emptyList(), baseConfig))

        assertEquals("gpt-4o", body["model"]!!.jsonPrimitive.content)
        assertEquals(1024, body["max_tokens"]!!.jsonPrimitive.content.toInt())
        assertEquals(true, body["stream"]!!.jsonPrimitive.content.toBoolean())

        // stream_options should be present by default
        val streamOptions = body["stream_options"]!!.jsonObject
        assertEquals(true, streamOptions["include_usage"]!!.jsonPrimitive.content.toBoolean())

        // System prompt should use "system" role
        val messages = body["messages"]!!.jsonArray
        val systemMsg = messages[0].jsonObject
        assertEquals("system", systemMsg["role"]!!.jsonPrimitive.content)
        assertEquals("You are a helpful assistant.", systemMsg["content"]!!.jsonPrimitive.content)

        // User message
        val userMsg = messages[1].jsonObject
        assertEquals("user", userMsg["role"]!!.jsonPrimitive.content)
        assertEquals("Hello", userMsg["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun `null compat behaves identically to no compat`() {
        val configWithNullCompat = baseConfig.copy(compat = null)
        val configDefault = baseConfig

        val bodyNull = provider.buildRequestBody(simpleMessages, emptyList(), configWithNullCompat)
        val bodyDefault = provider.buildRequestBody(simpleMessages, emptyList(), configDefault)

        assertEquals(bodyNull, bodyDefault)
    }

    @Test
    fun `compat with all fields null behaves identically to no compat`() {
        val configWithEmptyCompat = baseConfig.copy(compat = OpenAiCompat())
        val configDefault = baseConfig

        val bodyEmpty = provider.buildRequestBody(simpleMessages, emptyList(), configWithEmptyCompat)
        val bodyDefault = provider.buildRequestBody(simpleMessages, emptyList(), configDefault)

        assertEquals(bodyEmpty, bodyDefault)
    }

    @Test
    fun `maxTokensField changes the JSON key`() {
        val config = baseConfig.copy(
            compat = OpenAiCompat(maxTokensField = "max_completion_tokens")
        )
        val body = parseBody(provider.buildRequestBody(simpleMessages, emptyList(), config))

        // Should have "max_completion_tokens" instead of "max_tokens"
        assertNull(body["max_tokens"])
        assertEquals(1024, body["max_completion_tokens"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun `supportsDeveloperRole false sends system role`() {
        val config = baseConfig.copy(
            compat = OpenAiCompat(supportsDeveloperRole = false)
        )
        val body = parseBody(provider.buildRequestBody(simpleMessages, emptyList(), config))
        val messages = body["messages"]!!.jsonArray
        val systemMsg = messages[0].jsonObject
        assertEquals("system", systemMsg["role"]!!.jsonPrimitive.content)
    }

    @Test
    fun `supportsDeveloperRole true sends developer role`() {
        val config = baseConfig.copy(
            compat = OpenAiCompat(supportsDeveloperRole = true)
        )
        val body = parseBody(provider.buildRequestBody(simpleMessages, emptyList(), config))
        val messages = body["messages"]!!.jsonArray
        val systemMsg = messages[0].jsonObject
        assertEquals("developer", systemMsg["role"]!!.jsonPrimitive.content)
    }

    @Test
    fun `supportsUsageInStreaming false omits stream_options`() {
        val config = baseConfig.copy(
            compat = OpenAiCompat(supportsUsageInStreaming = false)
        )
        val body = parseBody(provider.buildRequestBody(simpleMessages, emptyList(), config))
        assertNull(body["stream_options"])
    }

    @Test
    fun `supportsUsageInStreaming true includes stream_options`() {
        val config = baseConfig.copy(
            compat = OpenAiCompat(supportsUsageInStreaming = true)
        )
        val body = parseBody(provider.buildRequestBody(simpleMessages, emptyList(), config))
        val streamOptions = body["stream_options"]!!.jsonObject
        assertEquals(true, streamOptions["include_usage"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun `requiresToolResultName true adds name to tool result message`() {
        val toolResultMessages = listOf(
            Message.User("Hello"),
            Message.ToolResult(
                toolCallId = "call_123",
                content = "Result here",
                toolName = "search",
            ),
        )
        val config = baseConfig.copy(
            compat = OpenAiCompat(requiresToolResultName = true)
        )
        val body = parseBody(provider.buildRequestBody(toolResultMessages, emptyList(), config))
        val messages = body["messages"]!!.jsonArray

        // Find the tool message (after system and user)
        val toolMsg = messages.first { it.jsonObject["role"]?.jsonPrimitive?.content == "tool" }.jsonObject
        assertEquals("call_123", toolMsg["tool_call_id"]!!.jsonPrimitive.content)
        assertEquals("search", toolMsg["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun `requiresToolResultName false does not add name to tool result`() {
        val toolResultMessages = listOf(
            Message.User("Hello"),
            Message.ToolResult(
                toolCallId = "call_123",
                content = "Result here",
                toolName = "search",
            ),
        )
        val config = baseConfig.copy(
            compat = OpenAiCompat(requiresToolResultName = false)
        )
        val body = parseBody(provider.buildRequestBody(toolResultMessages, emptyList(), config))
        val messages = body["messages"]!!.jsonArray
        val toolMsg = messages.first { it.jsonObject["role"]?.jsonPrimitive?.content == "tool" }.jsonObject
        assertNull(toolMsg["name"])
    }

    @Test
    fun `custom baseUrl in config is used by default`() {
        val config = baseConfig.copy(baseUrl = "https://api.groq.com/openai")
        // buildRequestBody doesn't use baseUrl — it's used in stream().
        // We just verify the body builds without error.
        val body = parseBody(provider.buildRequestBody(simpleMessages, emptyList(), config))
        assertEquals("gpt-4o", body["model"]!!.jsonPrimitive.content)
    }

    @Test
    fun `no system prompt omits system message`() {
        val config = baseConfig.copy(systemPrompt = null)
        val body = parseBody(provider.buildRequestBody(simpleMessages, emptyList(), config))
        val messages = body["messages"]!!.jsonArray
        // Should only have the user message, no system
        assertEquals(1, messages.size)
        assertEquals("user", messages[0].jsonObject["role"]!!.jsonPrimitive.content)
    }

    @Test
    fun `tools are included in request body`() {
        val body = parseBody(provider.buildRequestBody(simpleMessages, listOf(testTool()), baseConfig))
        val tools = body["tools"]!!.jsonArray
        assertEquals(1, tools.size)
        val tool = tools[0].jsonObject
        assertEquals("function", tool["type"]!!.jsonPrimitive.content)
        val fn = tool["function"]!!.jsonObject
        assertEquals("search", fn["name"]!!.jsonPrimitive.content)
        assertEquals("Search the web", fn["description"]!!.jsonPrimitive.content)
    }

    @Test
    fun `assistant message with tool calls is serialized correctly`() {
        val assistantMsg = Message.Assistant(
            content = listOf(
                ContentBlock.ToolCall(
                    id = "call_abc",
                    name = "search",
                    arguments = buildJsonObject {
                        put("query", kotlinx.serialization.json.JsonPrimitive("test"))
                    },
                )
            )
        )
        val messages = listOf(Message.User("Hello"), assistantMsg)
        val body = parseBody(provider.buildRequestBody(messages, emptyList(), baseConfig))
        val msgArray = body["messages"]!!.jsonArray

        // Find assistant message
        val assistantObj = msgArray.first { it.jsonObject["role"]?.jsonPrimitive?.content == "assistant" }.jsonObject
        val toolCalls = assistantObj["tool_calls"]!!.jsonArray
        assertEquals(1, toolCalls.size)
        val tc = toolCalls[0].jsonObject
        assertEquals("call_abc", tc["id"]!!.jsonPrimitive.content)
        assertEquals("function", tc["type"]!!.jsonPrimitive.content)
        assertEquals("search", tc["function"]!!.jsonObject["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun `provider name is openai for backward compatibility`() {
        assertEquals("openai", provider.name)
    }
}
