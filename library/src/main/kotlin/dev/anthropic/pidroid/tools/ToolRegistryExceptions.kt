package dev.anthropic.pidroid.tools

/**
 * Thrown when a tool override attempts to loosen confirmation policy
 * for a high-risk tool.
 */
class ToolOverrideSecurityException(
    val toolName: String,
    val riskLevel: RiskLevel,
    val currentPolicy: ConfirmationPolicy,
    val attemptedPolicy: ConfirmationPolicy,
) : SecurityException(
    "Cannot loosen confirmation policy for tool '$toolName' " +
        "(risk_level=$riskLevel, current=$currentPolicy, attempted=$attemptedPolicy)"
)

/**
 * Thrown when adding a tool would exceed the maximum tool count.
 */
class ToolCapExceededException(
    val current: Int,
    val max: Int,
    val proposed: String,
) : IllegalStateException(
    "Adding tool '$proposed' would exceed max tool count ($current/$max)"
)

/**
 * Thrown when an extension tool attempts to declare a risk level
 * above LOCAL_WRITE without being in the elevated allow-list.
 */
class ExtensionElevationException(
    val toolName: String,
    val riskLevel: RiskLevel,
) : SecurityException(
    "Extension tool '$toolName' cannot have risk_level=$riskLevel " +
        "without being in elevatedExtensionAllowList"
)
