package dev.anthropic.pidroid.llm.registry

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Metadata for an LLM model, mirroring pi-ai's `Model<TApi>` interface.
 *
 * Deserialized from the bundled `pi_ai_models.json` resource. Immutable
 * after parse. Used by [ModelRegistry] for lookups and by [ProviderFactory]
 * for provider routing and configuration.
 */
@Serializable
data class ModelInfo(
    /** Model identifier, e.g. "claude-sonnet-4-20250514", "gpt-4o" */
    val id: String,

    /** Human-readable model name, e.g. "Claude Sonnet 4" */
    val name: String,

    /** API type string, e.g. "anthropic-messages", "openai-completions" */
    val api: String,

    /** Provider name, e.g. "anthropic", "openai", "google" */
    val provider: String,

    /** Default base URL for the provider's API */
    @SerialName("baseUrl")
    val baseUrl: String,

    /** Whether the model supports reasoning/thinking */
    val reasoning: Boolean = false,

    /** Supported input modalities, e.g. ["text"], ["text", "image"] */
    val input: List<String> = listOf("text"),

    /** Token cost per million tokens */
    val cost: ModelCost = ModelCost(),

    /** Maximum context window in tokens */
    @SerialName("contextWindow")
    val contextWindow: Int = 0,

    /** Maximum output tokens */
    @SerialName("maxTokens")
    val maxTokens: Int = 4096,

    /** Custom HTTP headers to include in API requests */
    val headers: Map<String, String>? = null,

    /** Compatibility settings for OpenAI-compatible APIs */
    val compat: OpenAiCompat? = null,
)

/**
 * Token cost per million tokens.
 */
@Serializable
data class ModelCost(
    /** Input cost in $/million tokens */
    val input: Double = 0.0,

    /** Output cost in $/million tokens */
    val output: Double = 0.0,

    /** Cache read cost in $/million tokens */
    @SerialName("cacheRead")
    val cacheRead: Double = 0.0,

    /** Cache write cost in $/million tokens */
    @SerialName("cacheWrite")
    val cacheWrite: Double = 0.0,
)
