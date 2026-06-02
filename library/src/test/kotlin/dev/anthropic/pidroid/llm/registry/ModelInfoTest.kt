package dev.anthropic.pidroid.llm.registry

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelInfoTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `ModelInfo round-trips through JSON serialization`() {
        val model = ModelInfo(
            id = "claude-sonnet-4-20250514",
            name = "Claude Sonnet 4",
            api = ApiType.ANTHROPIC_MESSAGES,
            provider = "anthropic",
            baseUrl = "https://api.anthropic.com",
            reasoning = true,
            input = listOf("text", "image"),
            cost = ModelCost(input = 3.0, output = 15.0, cacheRead = 0.3, cacheWrite = 3.75),
            contextWindow = 200000,
            maxTokens = 8192,
            headers = mapOf("anthropic-version" to "2023-06-01"),
        )

        val encoded = json.encodeToString(ModelInfo.serializer(), model)
        val decoded = json.decodeFromString(ModelInfo.serializer(), encoded)
        assertEquals(model, decoded)
    }

    @Test
    fun `OpenAiCompat round-trips with all fields null (defaults)`() {
        val compat = OpenAiCompat()
        val encoded = json.encodeToString(OpenAiCompat.serializer(), compat)
        val decoded = json.decodeFromString(OpenAiCompat.serializer(), encoded)
        assertEquals(compat, decoded)
        assertNull(decoded.maxTokensField)
        assertNull(decoded.supportsDeveloperRole)
        assertNull(decoded.supportsUsageInStreaming)
    }

    @Test
    fun `ModelInfo without compat field deserializes correctly`() {
        val jsonStr = """
            {
                "id": "gpt-4o",
                "name": "GPT-4o",
                "api": "openai-responses",
                "provider": "openai",
                "baseUrl": "https://api.openai.com"
            }
        """.trimIndent()
        val model = json.decodeFromString(ModelInfo.serializer(), jsonStr)
        assertEquals("gpt-4o", model.id)
        assertEquals("openai-responses", model.api)
        assertNull(model.compat)
    }

    @Test
    fun `ModelInfo with unknown api string deserializes without error`() {
        val jsonStr = """
            {
                "id": "custom-model",
                "name": "Custom Model",
                "api": "custom-unknown-api-v2",
                "provider": "custom-provider",
                "baseUrl": "https://custom.example.com"
            }
        """.trimIndent()
        val model = json.decodeFromString(ModelInfo.serializer(), jsonStr)
        assertEquals("custom-unknown-api-v2", model.api)
        assertEquals("custom-provider", model.provider)
    }

    @Test
    fun `ModelInfo with empty headers map deserializes correctly`() {
        val jsonStr = """
            {
                "id": "test-model",
                "name": "Test Model",
                "api": "openai-completions",
                "provider": "test",
                "baseUrl": "https://test.example.com",
                "headers": {}
            }
        """.trimIndent()
        val model = json.decodeFromString(ModelInfo.serializer(), jsonStr)
        assertTrue(model.headers!!.isEmpty())
    }

    @Test
    fun `ModelInfo with compat fields deserializes all compat settings`() {
        val jsonStr = """
            {
                "id": "llama-3.3-70b",
                "name": "Llama 3.3 70B",
                "api": "openai-completions",
                "provider": "groq",
                "baseUrl": "https://api.groq.com/openai",
                "compat": {
                    "supportsStore": false,
                    "supportsDeveloperRole": false,
                    "supportsUsageInStreaming": false,
                    "maxTokensField": "max_tokens",
                    "requiresToolResultName": false,
                    "supportsStrictMode": false,
                    "thinkingFormat": "openai"
                }
            }
        """.trimIndent()
        val model = json.decodeFromString(ModelInfo.serializer(), jsonStr)
        val compat = model.compat!!
        assertEquals(false, compat.supportsStore)
        assertEquals(false, compat.supportsDeveloperRole)
        assertEquals(false, compat.supportsUsageInStreaming)
        assertEquals("max_tokens", compat.maxTokensField)
        assertEquals(false, compat.supportsStrictMode)
        assertEquals("openai", compat.thinkingFormat)
    }

    @Test
    fun `ModelInfo defaults for optional numeric fields`() {
        val jsonStr = """
            {
                "id": "minimal",
                "name": "Minimal Model",
                "api": "openai-completions",
                "provider": "test",
                "baseUrl": "https://test.example.com"
            }
        """.trimIndent()
        val model = json.decodeFromString(ModelInfo.serializer(), jsonStr)
        assertEquals(false, model.reasoning)
        assertEquals(listOf("text"), model.input)
        assertEquals(0, model.contextWindow)
        assertEquals(4096, model.maxTokens)
        assertEquals(0.0, model.cost.input, 0.001)
    }
}
