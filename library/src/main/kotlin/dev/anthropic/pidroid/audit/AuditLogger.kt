package dev.anthropic.pidroid.audit

import dev.anthropic.pidroid.tools.ConfirmationResult
import dev.anthropic.pidroid.tools.RiskLevel

/**
 * Records metadata-only audit entries for tool executions.
 *
 * Privacy-safe by default: tool arguments are NOT logged.
 * Only tool name, risk level, confirmation result, duration, and error state.
 */
class AuditLogger(private val dao: AuditDao) {

    /**
     * Log a tool execution.
     */
    suspend fun logToolExecution(
        toolName: String,
        riskLevel: RiskLevel,
        confirmationResult: ConfirmationResult? = null,
        durationMs: Long,
        isError: Boolean = false,
        metadataJson: String? = null,
    ) {
        dao.insert(
            AuditEntry(
                eventType = "tool_execution",
                toolName = toolName,
                riskLevel = riskLevel.name,
                confirmationResult = confirmationResult?.name,
                durationMs = durationMs,
                isError = isError,
                metadataJson = metadataJson,
            )
        )
    }

    /**
     * Log a confirmation decision.
     */
    suspend fun logConfirmation(
        toolName: String,
        result: ConfirmationResult,
    ) {
        dao.insert(
            AuditEntry(
                eventType = "confirmation",
                toolName = toolName,
                confirmationResult = result.name,
            )
        )
    }

    /**
     * Get recent audit entries.
     */
    suspend fun getRecent(limit: Int = 100): List<AuditEntry> = dao.getRecent(limit)
}
