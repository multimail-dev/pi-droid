package dev.anthropic.pidroid.tools

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RiskLevelTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun `RiskLevel wire string round-trips`() {
        for (level in RiskLevel.entries) {
            val encoded = json.encodeToString(level)
            val decoded = json.decodeFromString<RiskLevel>(encoded)
            assertEquals(level, decoded)
        }
    }

    @Test
    fun `RiskLevel has exactly 6 values`() {
        assertEquals(6, RiskLevel.entries.size)
    }

    @Test
    fun `RiskLevel ordinal ordering is least to most dangerous`() {
        assertTrue(RiskLevel.READ_ONLY.ordinal < RiskLevel.LOCAL_WRITE.ordinal)
        assertTrue(RiskLevel.LOCAL_WRITE.ordinal < RiskLevel.EXTERNAL_DRAFT.ordinal)
        assertTrue(RiskLevel.EXTERNAL_DRAFT.ordinal < RiskLevel.EXTERNAL_SEND.ordinal)
        assertTrue(RiskLevel.EXTERNAL_SEND.ordinal < RiskLevel.FINANCIAL_LEGAL_MEDICAL.ordinal)
        assertTrue(RiskLevel.FINANCIAL_LEGAL_MEDICAL.ordinal < RiskLevel.DESTRUCTIVE.ordinal)
    }

    @Test
    fun `RiskLevel serializes to snake_case wire names`() {
        assertEquals("\"read_only\"", json.encodeToString(RiskLevel.READ_ONLY))
        assertEquals("\"local_write\"", json.encodeToString(RiskLevel.LOCAL_WRITE))
        assertEquals("\"external_draft\"", json.encodeToString(RiskLevel.EXTERNAL_DRAFT))
        assertEquals("\"external_send\"", json.encodeToString(RiskLevel.EXTERNAL_SEND))
        assertEquals("\"financial_legal_medical\"", json.encodeToString(RiskLevel.FINANCIAL_LEGAL_MEDICAL))
        assertEquals("\"destructive\"", json.encodeToString(RiskLevel.DESTRUCTIVE))
    }
}
