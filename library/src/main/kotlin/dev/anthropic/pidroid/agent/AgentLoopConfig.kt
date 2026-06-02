package dev.anthropic.pidroid.agent

import dev.anthropic.pidroid.core.message.AgentMessage
import dev.anthropic.pidroid.core.message.Message

/**
 * Configuration for the agent loop.
 *
 * @property maxTurns Maximum turns before auto-stop (safety limit)
 * @property toolTimeoutMs Default timeout for individual tool executions
 * @property systemPrompt System prompt injected at conversation start
 * @property transformContext Suspend lambda invoked before each LLM call to allow hosts to
 *   prune, summarize, or inject context. Receives the full transcript (including custom
 *   [AgentMessage] types) and returns a (possibly modified) message list. Only [Message]
 *   instances in the returned list are sent to the LLM. The canonical transcript is
 *   unaffected. Null means no transformation (pass-through).
 */
data class AgentLoopConfig(
    val maxTurns: Int = 25,
    val toolTimeoutMs: Long = 30_000L,
    val systemPrompt: String? = null,
    val transformContext: (suspend (List<AgentMessage>) -> List<AgentMessage>)? = null,
)
