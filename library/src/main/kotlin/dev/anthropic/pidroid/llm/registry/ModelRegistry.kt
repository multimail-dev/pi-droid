package dev.anthropic.pidroid.llm.registry

import android.content.Context
import dev.anthropic.pidroid.R
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/**
 * Singleton registry of all known LLM models, loaded from the bundled
 * `pi_ai_models.json` raw resource.
 *
 * Thread-safe: the registry is parsed once on first access after [init]
 * and stored in an immutable map. All public methods are safe to call
 * from any thread.
 *
 * Usage:
 * ```
 * // Call once at app startup
 * ModelRegistry.init(applicationContext)
 *
 * // Then query anywhere
 * val providers = ModelRegistry.getProviders()
 * val model = ModelRegistry.getModel("anthropic", "claude-sonnet-4-20250514")
 * ```
 */
object ModelRegistry {
    private val json = Json { ignoreUnknownKeys = true }

    // Guarded by synchronized(this) during init; immutable after parse
    @Volatile
    private var registry: Map<String, Map<String, ModelInfo>>? = null

    /**
     * Initialize the registry with an Android context for resource loading.
     * Must be called once before any query methods. Subsequent calls are no-ops.
     */
    fun init(context: Context) {
        if (registry != null) return
        synchronized(this) {
            if (registry != null) return
            val rawJson = context.resources.openRawResource(R.raw.pi_ai_models)
                .bufferedReader()
                .use { it.readText() }
            registry = parseModels(rawJson)
        }
    }

    /**
     * Returns all provider names (e.g. "anthropic", "openai", "google").
     * @throws IllegalStateException if [init] has not been called.
     */
    fun getProviders(): List<String> {
        return requireRegistry().keys.toList()
    }

    /**
     * Returns all models for the given provider.
     * Returns an empty list if the provider is unknown.
     * @throws IllegalStateException if [init] has not been called.
     */
    fun getModels(provider: String): List<ModelInfo> {
        return requireRegistry()[provider]?.values?.toList() ?: emptyList()
    }

    /**
     * Returns a specific model by provider and model ID, or null if not found.
     * @throws IllegalStateException if [init] has not been called.
     */
    fun getModel(provider: String, modelId: String): ModelInfo? {
        return requireRegistry()[provider]?.get(modelId)
    }

    /**
     * Parse raw JSON into the registry map, filtering out any bedrock models
     * that may have slipped through the export script (belt-and-suspenders).
     */
    internal fun parseModels(rawJson: String): Map<String, Map<String, ModelInfo>> {
        val root = json.parseToJsonElement(rawJson).jsonObject
        val result = mutableMapOf<String, Map<String, ModelInfo>>()

        for ((provider, modelsElement) in root) {
            val modelsObject = modelsElement.jsonObject
            val providerModels = mutableMapOf<String, ModelInfo>()

            for ((modelId, modelElement) in modelsObject) {
                val model = json.decodeFromJsonElement(ModelInfo.serializer(), modelElement)
                // Belt-and-suspenders: filter bedrock at parse time too
                if (model.api == ApiType.BEDROCK_CONVERSE_STREAM) continue
                providerModels[modelId] = model
            }

            if (providerModels.isNotEmpty()) {
                result[provider] = providerModels
            }
        }

        return result
    }

    private fun requireRegistry(): Map<String, Map<String, ModelInfo>> {
        return registry ?: throw IllegalStateException(
            "ModelRegistry.init(context) must be called before querying models"
        )
    }

    /** Reset for testing only. */
    internal fun reset() {
        registry = null
    }
}
