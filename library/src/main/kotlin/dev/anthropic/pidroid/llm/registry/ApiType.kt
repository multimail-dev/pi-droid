package dev.anthropic.pidroid.llm.registry

/**
 * Known API type constants for LLM provider routing.
 *
 * API types are strings (not an enum) to allow custom OpenAI-compatible
 * endpoints without modifying the type system. These constants cover the
 * API types from pi-ai's registry.
 */
object ApiType {
    const val ANTHROPIC_MESSAGES = "anthropic-messages"
    const val OPENAI_COMPLETIONS = "openai-completions"
    const val OPENAI_RESPONSES = "openai-responses"
    const val AZURE_OPENAI_RESPONSES = "azure-openai-responses"
    const val GOOGLE_GENERATIVE_AI = "google-generative-ai"
    const val GOOGLE_VERTEX = "google-vertex"
    const val MISTRAL_CONVERSATIONS = "mistral-conversations"
    const val BEDROCK_CONVERSE_STREAM = "bedrock-converse-stream"
    const val OPENAI_CODEX_RESPONSES = "openai-codex-responses"
    const val GOOGLE_GEMINI_CLI = "google-gemini-cli"
}
