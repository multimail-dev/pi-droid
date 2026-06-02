package dev.anthropic.pidroid

import dev.anthropic.pidroid.capabilities.CapabilityGrant
import dev.anthropic.pidroid.extension.PiExtension
import java.io.File

/**
 * Configuration for the Pi agent runtime.
 *
 * Created at initialization time and immutable thereafter. All fields
 * are resolved before the agent loop starts — no late-binding.
 *
 * @property llmProvider Configuration for the LLM provider (API key, model, etc.)
 * @property modelDir Optional directory containing the ONNX embedding model.
 *   If null, semantic memory features are disabled.
 * @property maxTurnsPerTask Maximum turns the agent loop will execute before
 *   stopping and returning control to the host. Safety limit.
 * @property capabilities Capability grants the host declares at init time.
 * @property extensions Compile-time extensions to register during init.
 * @property systemPrompt Optional system prompt sent to the LLM at the start
 *   of every agent loop invocation. Reaches the provider via [LlmConfig.systemPrompt].
 */
data class PiRuntimeConfig(
    val llmProvider: LlmProviderConfig,
    val modelDir: File? = null,
    val maxTurnsPerTask: Int = 25,
    val capabilities: List<CapabilityGrant> = emptyList(),
    val extensions: List<PiExtension> = emptyList(),
    val systemPrompt: String? = null,
)

/**
 * LLM provider connection configuration.
 *
 * Provider and model are identified by string keys that match the
 * [ModelRegistry][dev.anthropic.pidroid.llm.registry.ModelRegistry] entries.
 * The runtime resolves the full [ModelInfo][dev.anthropic.pidroid.llm.registry.ModelInfo]
 * at initialization time.
 *
 * @property provider Provider key (e.g., "anthropic", "openai", "google")
 * @property modelId Model identifier (e.g., "claude-sonnet-4-20250514", "gpt-4o")
 * @property apiKey API key for the provider
 * @property baseUrl Override base URL (for proxies, self-hosted, or custom providers).
 *   When set on an unrecognized provider/model, the runtime falls back to
 *   the openai-completions API type.
 * @property maxTokens Override maximum tokens per response (null = use registry default)
 * @property temperature Sampling temperature (0.0–1.0)
 */
data class LlmProviderConfig(
    val provider: String,
    val modelId: String,
    val apiKey: String,
    val baseUrl: String? = null,
    val maxTokens: Int? = null,
    val temperature: Float = 0.7f,
)
