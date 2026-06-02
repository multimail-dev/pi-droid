package dev.anthropic.pidroid.tools

/**
 * Configuration for the tool registry.
 *
 * @property maxToolCount Maximum tools allowed (catalog + extensions). Safety cap.
 * @property denyList Tool names that are unconditionally filtered out
 * @property generatedToolPolicy How extension/generated tools are treated
 * @property approvedExtensions Extension tool names pre-approved for activation
 * @property elevatedExtensionAllowList Extensions allowed above LOCAL_WRITE risk
 */
data class ToolRegistryConfig(
    val maxToolCount: Int = 50,
    val denyList: Set<String> = emptySet(),
    val generatedToolPolicy: GeneratedToolPolicy = GeneratedToolPolicy.REQUIRE_APPROVAL,
    val approvedExtensions: Set<String> = emptySet(),
    val elevatedExtensionAllowList: Set<String> = emptySet(),
)

/**
 * Policy for handling extension/generated tools.
 */
enum class GeneratedToolPolicy {
    /** All extension tools are disabled */
    DISABLED,
    /** Extension tools require explicit host approval */
    REQUIRE_APPROVAL,
    /** Read-only extension tools auto-approve; others require approval */
    AUTO_APPROVE_READ_ONLY,
}
