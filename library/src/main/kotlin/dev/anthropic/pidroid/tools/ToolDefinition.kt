package dev.anthropic.pidroid.tools

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Complete definition of a tool available to the agent.
 *
 * Adapted from CanonicalToolDefinition in the misadventure — removed `requiresBridge`,
 * added [executionMode] for native dispatch classification.
 *
 * @property name Unique tool name (wire format, e.g., "read_notifications")
 * @property description Human-readable description shown to the LLM
 * @property inputSchema JSON Schema for the tool's input parameters
 * @property category Tool category for grouping
 * @property riskLevel Risk classification
 * @property executionMode How this tool is dispatched
 * @property defaultConfirmationPolicy Default confirmation requirement (may be overridden)
 * @property timeoutMs Maximum execution time before timeout
 * @property requiredPermissions Android permissions required (capability IDs)
 * @property requiredCapabilities Additional capability requirements beyond permissions
 * @property isExtension Whether this tool was registered by an extension (vs. built-in catalog)
 */
@Serializable
data class ToolDefinition(
    val name: String,
    val description: String,
    @SerialName("input_schema")
    val inputSchema: JsonObject,
    val category: ToolCategory,
    @SerialName("risk_level")
    val riskLevel: RiskLevel,
    @SerialName("execution_mode")
    val executionMode: ToolExecutionMode = ToolExecutionMode.NATIVE,
    @SerialName("dispatch_mode")
    val dispatchMode: ToolDispatchMode = ToolDispatchMode.PARALLEL,
    @SerialName("default_confirmation_policy")
    val defaultConfirmationPolicy: ConfirmationPolicy? = null,
    @SerialName("timeout_ms")
    val timeoutMs: Long = 30_000L,
    @SerialName("required_permissions")
    val requiredPermissions: List<String> = emptyList(),
    @SerialName("required_capabilities")
    val requiredCapabilities: List<String> = emptyList(),
    @SerialName("is_extension")
    val isExtension: Boolean = false,
) {
    /**
     * The effective confirmation policy — explicit if set, otherwise derived from risk level.
     */
    val effectiveConfirmationPolicy: ConfirmationPolicy
        get() = defaultConfirmationPolicy
            ?: ConfirmationPolicy.defaultForRiskLevel(riskLevel)
}
