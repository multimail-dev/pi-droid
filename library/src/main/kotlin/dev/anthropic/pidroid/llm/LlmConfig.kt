package dev.anthropic.pidroid.llm

import dev.anthropic.pidroid.llm.registry.OpenAiCompat

/**
 * Per-request LLM configuration.
 *
 * Resolved from [PiRuntimeConfig.llmProvider] with optional per-request overrides.
 */
data class LlmConfig(
    val apiKey: String,
    val model: String,
    val baseUrl: String? = null,
    val maxTokens: Int = 4096,
    val temperature: Float = 0.7f,
    val systemPrompt: String? = null,
    val compat: OpenAiCompat? = null,
    val headers: Map<String, String>? = null,
)
