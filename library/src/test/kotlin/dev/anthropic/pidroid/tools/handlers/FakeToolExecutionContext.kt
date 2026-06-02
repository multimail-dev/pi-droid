package dev.anthropic.pidroid.tools.handlers

import dev.anthropic.pidroid.tools.ToolExecutionContext

/**
 * No-op implementation of ToolExecutionContext for unit tests.
 */
class FakeToolExecutionContext : ToolExecutionContext {
    override suspend fun reportProgress(message: String) { /* no-op */ }
    override val isCancelled: Boolean = false
}
