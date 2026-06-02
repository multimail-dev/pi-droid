package dev.anthropic.pidroid.tools

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Risk classification for tool operations.
 *
 * Determines the minimum confirmation policy a tool can have.
 * Ordered from least to most dangerous — ordinal comparison is meaningful.
 */
@Serializable
enum class RiskLevel {
    /** Only reads data, no side effects */
    @SerialName("read_only")
    READ_ONLY,

    /** Writes to local device state (alarms, preferences) */
    @SerialName("local_write")
    LOCAL_WRITE,

    /** Prepares external communication but doesn't send */
    @SerialName("external_draft")
    EXTERNAL_DRAFT,

    /** Sends data externally (messages, emails, API calls) */
    @SerialName("external_send")
    EXTERNAL_SEND,

    /** Financial transactions, legal documents, medical actions */
    @SerialName("financial_legal_medical")
    FINANCIAL_LEGAL_MEDICAL,

    /** Irreversible destructive actions (delete data, revoke access) */
    @SerialName("destructive")
    DESTRUCTIVE,
}
