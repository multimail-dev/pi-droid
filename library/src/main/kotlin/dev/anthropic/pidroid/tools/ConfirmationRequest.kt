package dev.anthropic.pidroid.tools

/**
 * Emitted to the UI when a tool requires user confirmation before execution.
 */
data class ConfirmationRequest(
    val requestId: String,
    val toolName: String,
    val toolDescription: String,
    val arguments: String,
    val policy: ConfirmationPolicy,
    val requiresBiometric: Boolean = policy == ConfirmationPolicy.BIOMETRIC_CONFIRM,
)
