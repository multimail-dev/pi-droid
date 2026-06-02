package dev.anthropic.pidroid.tools

/**
 * Immutable snapshot of the active tool set.
 *
 * Emitted via StateFlow every time the registry recomputes.
 * Safe to hold across time — won't change.
 */
data class ToolRegistrySnapshot(
    val tools: List<ToolDefinition>,
    val version: Long,
    val computedAt: Long,
    val declaredCapabilityCount: Int,
    val grantedCapabilityCount: Int,
) {
    val toolCount: Int get() = tools.size

    companion object {
        val EMPTY = ToolRegistrySnapshot(
            tools = emptyList(),
            version = 0L,
            computedAt = 0L,
            declaredCapabilityCount = 0,
            grantedCapabilityCount = 0,
        )
    }
}
