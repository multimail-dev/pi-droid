package dev.anthropic.pidroid.tools

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmationPolicyTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun `defaultForRiskLevel maps READ_ONLY to AUTOMATIC`() {
        assertEquals(
            ConfirmationPolicy.AUTOMATIC,
            ConfirmationPolicy.defaultForRiskLevel(RiskLevel.READ_ONLY),
        )
    }

    @Test
    fun `defaultForRiskLevel maps LOCAL_WRITE to AUTOMATIC`() {
        assertEquals(
            ConfirmationPolicy.AUTOMATIC,
            ConfirmationPolicy.defaultForRiskLevel(RiskLevel.LOCAL_WRITE),
        )
    }

    @Test
    fun `defaultForRiskLevel maps EXTERNAL_DRAFT to USER_CONFIRM_MODAL`() {
        assertEquals(
            ConfirmationPolicy.USER_CONFIRM_MODAL,
            ConfirmationPolicy.defaultForRiskLevel(RiskLevel.EXTERNAL_DRAFT),
        )
    }

    @Test
    fun `defaultForRiskLevel maps EXTERNAL_SEND to USER_CONFIRM_MODAL`() {
        assertEquals(
            ConfirmationPolicy.USER_CONFIRM_MODAL,
            ConfirmationPolicy.defaultForRiskLevel(RiskLevel.EXTERNAL_SEND),
        )
    }

    @Test
    fun `defaultForRiskLevel maps FINANCIAL_LEGAL_MEDICAL to BIOMETRIC_CONFIRM`() {
        assertEquals(
            ConfirmationPolicy.BIOMETRIC_CONFIRM,
            ConfirmationPolicy.defaultForRiskLevel(RiskLevel.FINANCIAL_LEGAL_MEDICAL),
        )
    }

    @Test
    fun `defaultForRiskLevel maps DESTRUCTIVE to BIOMETRIC_CONFIRM`() {
        assertEquals(
            ConfirmationPolicy.BIOMETRIC_CONFIRM,
            ConfirmationPolicy.defaultForRiskLevel(RiskLevel.DESTRUCTIVE),
        )
    }

    @Test
    fun `NON_LOOSABLE_RISK_LEVELS contains exactly four high-risk levels`() {
        val expected = setOf(
            RiskLevel.EXTERNAL_DRAFT,
            RiskLevel.EXTERNAL_SEND,
            RiskLevel.FINANCIAL_LEGAL_MEDICAL,
            RiskLevel.DESTRUCTIVE,
        )
        assertEquals(expected, ConfirmationPolicy.NON_LOOSABLE_RISK_LEVELS)
    }

    @Test
    fun `NON_LOOSABLE_RISK_LEVELS does not contain READ_ONLY or LOCAL_WRITE`() {
        assertTrue(RiskLevel.READ_ONLY !in ConfirmationPolicy.NON_LOOSABLE_RISK_LEVELS)
        assertTrue(RiskLevel.LOCAL_WRITE !in ConfirmationPolicy.NON_LOOSABLE_RISK_LEVELS)
    }

    @Test
    fun `ConfirmationPolicy wire string round-trips`() {
        for (policy in ConfirmationPolicy.entries) {
            val encoded = json.encodeToString(policy)
            val decoded = json.decodeFromString<ConfirmationPolicy>(encoded)
            assertEquals(policy, decoded)
        }
    }

    @Test
    fun `ConfirmationPolicy ordinal ordering is least to most restrictive`() {
        assertTrue(ConfirmationPolicy.AUTOMATIC.ordinal < ConfirmationPolicy.USER_CONFIRM_MODAL.ordinal)
        assertTrue(ConfirmationPolicy.USER_CONFIRM_MODAL.ordinal < ConfirmationPolicy.BIOMETRIC_CONFIRM.ordinal)
        assertTrue(ConfirmationPolicy.BIOMETRIC_CONFIRM.ordinal < ConfirmationPolicy.BLOCKED.ordinal)
    }
}
