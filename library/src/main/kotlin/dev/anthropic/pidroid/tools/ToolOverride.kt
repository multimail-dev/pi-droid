package dev.anthropic.pidroid.tools

/**
 * Host-specified override for a specific tool.
 *
 * Allows the host to customize tool behavior within security constraints.
 * Overrides CANNOT loosen confirmation policy for NON_LOOSABLE_RISK_LEVELS.
 */
data class ToolOverride(
    val enabled: Boolean = true,
    val confirmationPolicy: ConfirmationPolicy? = null,
    val timeoutMs: Long? = null,
    val description: String? = null,
)
