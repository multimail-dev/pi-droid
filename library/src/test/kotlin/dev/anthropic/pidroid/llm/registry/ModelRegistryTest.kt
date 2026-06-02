package dev.anthropic.pidroid.llm.registry

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ModelRegistryTest {

    @Before
    fun setup() {
        ModelRegistry.reset()
        ModelRegistry.init(RuntimeEnvironment.getApplication())
    }

    @After
    fun tearDown() {
        ModelRegistry.reset()
    }

    @Test
    fun `getProviders returns known providers`() {
        val providers = ModelRegistry.getProviders()
        assertTrue("should contain anthropic", providers.contains("anthropic"))
        assertTrue("should contain openai", providers.contains("openai"))
        assertTrue("should contain google", providers.contains("google"))
        assertTrue("should contain xai", providers.contains("xai"))
        assertTrue("should contain mistral", providers.contains("mistral"))
    }

    @Test
    fun `getModels returns Anthropic models with correct metadata`() {
        val models = ModelRegistry.getModels("anthropic")
        assertTrue("anthropic should have models", models.isNotEmpty())

        val sonnet = models.find { it.id == "claude-sonnet-4-20250514" }
        assertNotNull("should find Claude Sonnet 4", sonnet)
        sonnet!!
        assertEquals("Claude Sonnet 4", sonnet.name)
        assertEquals("anthropic-messages", sonnet.api)
        assertEquals("anthropic", sonnet.provider)
        assertEquals("https://api.anthropic.com", sonnet.baseUrl)
        assertTrue("Sonnet 4 supports reasoning", sonnet.reasoning)
        assertTrue("cost.input should be positive", sonnet.cost.input > 0)
        assertTrue("contextWindow should be positive", sonnet.contextWindow > 0)
        assertTrue("maxTokens should be positive", sonnet.maxTokens > 0)
    }

    @Test
    fun `getModel returns specific model by provider and id`() {
        val model = ModelRegistry.getModel("openai", "gpt-4o")
        assertNotNull("should find gpt-4o", model)
        model!!
        assertEquals("gpt-4o", model.id)
        assertEquals("openai", model.provider)
    }

    @Test
    fun `getModel returns null for nonexistent provider`() {
        val model = ModelRegistry.getModel("nonexistent", "model")
        assertNull("nonexistent provider should return null", model)
    }

    @Test
    fun `getModel returns null for nonexistent model`() {
        val model = ModelRegistry.getModel("anthropic", "nonexistent-model")
        assertNull("nonexistent model should return null", model)
    }

    @Test
    fun `bedrock models are filtered out`() {
        val providers = ModelRegistry.getProviders()
        assertTrue(
            "amazon-bedrock provider should be filtered out",
            !providers.contains("amazon-bedrock")
        )

        // Also verify no model in any provider has bedrock API type
        for (provider in providers) {
            val models = ModelRegistry.getModels(provider)
            for (model in models) {
                assertTrue(
                    "model ${model.id} should not have bedrock API, got ${model.api}",
                    model.api != ApiType.BEDROCK_CONVERSE_STREAM
                )
            }
        }
    }

    @Test
    fun `registry parses actual JSON without errors`() {
        // Load the raw JSON from the Android resource via Robolectric context,
        // proving the real bundled file is valid and parseable
        val context = RuntimeEnvironment.getApplication()
        val resId = dev.anthropic.pidroid.R.raw.pi_ai_models
        val rawJson = context.resources.openRawResource(resId)
            .bufferedReader()
            .use { it.readText() }
        assertTrue("JSON should not be empty", rawJson.isNotEmpty())

        // Parse it through the same code path as the real registry
        val parsed = ModelRegistry.parseModels(rawJson)
        assertTrue("parsed registry should have providers", parsed.isNotEmpty())
        assertTrue("parsed registry should have anthropic", parsed.containsKey("anthropic"))
        assertTrue("parsed registry should have openai", parsed.containsKey("openai"))

        // Spot-check a model to verify full deserialization
        val anthropicModels = parsed["anthropic"]!!
        assertTrue("anthropic should have models", anthropicModels.isNotEmpty())
        val anyModel = anthropicModels.values.first()
        assertTrue("model id should not be blank", anyModel.id.isNotBlank())
        assertTrue("model name should not be blank", anyModel.name.isNotBlank())
    }

    @Test
    fun `getModels returns empty list for unknown provider`() {
        val models = ModelRegistry.getModels("unknown-provider")
        assertTrue("unknown provider should return empty list", models.isEmpty())
    }

    @Test
    fun `init is idempotent`() {
        // init was already called in setup; calling again should be a no-op
        ModelRegistry.init(RuntimeEnvironment.getApplication())
        val providers = ModelRegistry.getProviders()
        assertTrue("should still have providers after double init", providers.isNotEmpty())
    }
}
