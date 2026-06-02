package dev.anthropic.pidroid.tools

/**
 * Result of a user confirmation interaction.
 */
enum class ConfirmationResult {
    /** User approved the tool execution */
    APPROVED,
    /** User denied the tool execution */
    DENIED,
    /** Confirmation was cancelled (timeout, dismiss, etc.) */
    CANCELLED,
}
