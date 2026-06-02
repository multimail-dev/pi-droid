package dev.anthropic.pidroid.llm.registry

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Compatibility settings for OpenAI-compatible completions APIs.
 *
 * Mirrors pi-ai's `OpenAICompletionsCompat` interface. All fields are nullable
 * (opt-in override pattern) — when null, the provider uses its default behavior
 * or auto-detects from the base URL.
 */
@Serializable
data class OpenAiCompat(
    /** Whether the provider supports the `store` field. */
    @SerialName("supportsStore")
    val supportsStore: Boolean? = null,

    /** Whether the provider supports the `developer` role (vs `system`). */
    @SerialName("supportsDeveloperRole")
    val supportsDeveloperRole: Boolean? = null,

    /** Whether the provider supports `reasoning_effort`. */
    @SerialName("supportsReasoningEffort")
    val supportsReasoningEffort: Boolean? = null,

    /** Whether the provider supports `stream_options: { include_usage: true }`. */
    @SerialName("supportsUsageInStreaming")
    val supportsUsageInStreaming: Boolean? = null,

    /** Which field to use for max tokens — "max_completion_tokens" or "max_tokens". */
    @SerialName("maxTokensField")
    val maxTokensField: String? = null,

    /** Whether tool results require the `name` field. */
    @SerialName("requiresToolResultName")
    val requiresToolResultName: Boolean? = null,

    /** Whether a user message after tool results requires an assistant message in between. */
    @SerialName("requiresAssistantAfterToolResult")
    val requiresAssistantAfterToolResult: Boolean? = null,

    /** Whether thinking blocks must be converted to text with <thinking> delimiters. */
    @SerialName("requiresThinkingAsText")
    val requiresThinkingAsText: Boolean? = null,

    /**
     * Format for reasoning/thinking parameter.
     * "openai" uses reasoning_effort, "openrouter" uses reasoning: { effort },
     * "zai" / "qwen" use enable_thinking, "qwen-chat-template" uses chat_template_kwargs.
     */
    @SerialName("thinkingFormat")
    val thinkingFormat: String? = null,

    /** Whether the provider supports the `strict` field in tool definitions. */
    @SerialName("supportsStrictMode")
    val supportsStrictMode: Boolean? = null,
)
