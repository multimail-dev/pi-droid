package dev.anthropic.pidroid.tools

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Confirmation policy for tool execution.
 *
 * Determines what user interaction is required before a tool runs.
 * Ordered from least to most restrictive — ordinal comparison is meaningful.
 */
@Serializable
enum class ConfirmationPolicy {
    /** Execute without any user interaction */
    @SerialName("automatic")
    AUTOMATIC,

    /** Show a modal confirmation dialog */
    @SerialName("user_confirm_modal")
    USER_CONFIRM_MODAL,

    /** Require biometric authentication (fingerprint/face) */
    @SerialName("biometric_confirm")
    BIOMETRIC_CONFIRM,

    /** Tool is blocked and cannot be executed */
    @SerialName("blocked")
    BLOCKED;

    companion object {
        /**
         * Risk levels that CANNOT have their confirmation policy loosened
         * via tool overrides. Security invariant.
         */
        val NON_LOOSABLE_RISK_LEVELS: Set<RiskLevel> = setOf(
            RiskLevel.EXTERNAL_DRAFT,
            RiskLevel.EXTERNAL_SEND,
            RiskLevel.FINANCIAL_LEGAL_MEDICAL,
            RiskLevel.DESTRUCTIVE,
        )

        /**
         * Default confirmation policy for a given risk level.
         *
         * This is the minimum policy unless explicitly overridden
         * (and only overridable for low-risk tools).
         */
        fun defaultForRiskLevel(riskLevel: RiskLevel): ConfirmationPolicy {
            return when (riskLevel) {
                RiskLevel.READ_ONLY -> AUTOMATIC
                RiskLevel.LOCAL_WRITE -> AUTOMATIC
                RiskLevel.EXTERNAL_DRAFT -> USER_CONFIRM_MODAL
                RiskLevel.EXTERNAL_SEND -> USER_CONFIRM_MODAL
                RiskLevel.FINANCIAL_LEGAL_MEDICAL -> BIOMETRIC_CONFIRM
                RiskLevel.DESTRUCTIVE -> BIOMETRIC_CONFIRM
            }
        }
    }
}
