package dev.anthropic.pidroid.agent

import dev.anthropic.pidroid.core.message.AgentMessage
import dev.anthropic.pidroid.core.message.Message
import dev.anthropic.pidroid.llm.LlmConfig
import dev.anthropic.pidroid.tools.ToolDefinition

/**
 * Immutable context for a single agent invocation.
 *
 * Created at the start of each agent run (sendPrompt) and passed
 * to the agent loop. Contains everything the loop needs to operate.
 */
data class AgentContext(
    /** LLM configuration for this run */
    val llmConfig: LlmConfig,
    /** Available tools for this run */
    val tools: List<ToolDefinition>,
    /** Initial messages (system prompt + user prompt). Accepts both core [Message]
     *  types and custom [AgentMessage] implementations. Only [Message] instances
     *  are sent to the LLM; custom messages are filtered out before each API call. */
    val initialMessages: List<AgentMessage>,
    /** Loop configuration */
    val loopConfig: AgentLoopConfig,
)
