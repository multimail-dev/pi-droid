package dev.anthropic.pidroid.tools

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ToolDefinitionTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun sampleTool(
        confirmationPolicy: ConfirmationPolicy? = null,
        riskLevel: RiskLevel = RiskLevel.READ_ONLY,
    ) = ToolDefinition(
        name = "test_tool",
        description = "A test tool",
        inputSchema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("query") {
                    put("type", "string")
                }
            }
        },
        category = ToolCategory.DEVICE,
        riskLevel = riskLevel,
        defaultConfirmationPolicy = confirmationPolicy,
    )

    @Test
    fun `effectiveConfirmationPolicy returns explicit policy when set`() {
        val tool = sampleTool(
            confirmationPolicy = ConfirmationPolicy.BIOMETRIC_CONFIRM,
            riskLevel = RiskLevel.READ_ONLY,
        )
        assertEquals(ConfirmationPolicy.BIOMETRIC_CONFIRM, tool.effectiveConfirmationPolicy)
    }

    @Test
    fun `effectiveConfirmationPolicy returns default for risk level when null`() {
        val tool = sampleTool(
            confirmationPolicy = null,
            riskLevel = RiskLevel.EXTERNAL_SEND,
        )
        assertEquals(ConfirmationPolicy.USER_CONFIRM_MODAL, tool.effectiveConfirmationPolicy)
    }

    @Test
    fun `ToolResult with isError true round-trips serialization`() {
        val result = ToolResult(
            toolCallId = "tc_err",
            content = "Permission denied: READ_CONTACTS not granted",
            isError = true,
        )
        val encoded = json.encodeToString(result)
        val decoded = json.decodeFromString<ToolResult>(encoded)
        assertEquals(result, decoded)
        assertEquals(true, decoded.isError)
    }

    @Test
    fun `ToolResult with metadata round-trips`() {
        val result = ToolResult(
            toolCallId = "tc_meta",
            content = "OK",
            metadata = buildJsonObject {
                put("duration_ms", 42)
                put("source", "content_provider")
            },
        )
        val encoded = json.encodeToString(result)
        val decoded = json.decodeFromString<ToolResult>(encoded)
        assertEquals(result, decoded)
    }

    @Test
    fun `ToolDefinition round-trips serialization`() {
        val tool = sampleTool()
        val encoded = json.encodeToString(tool)
        val decoded = json.decodeFromString<ToolDefinition>(encoded)
        assertEquals(tool, decoded)
    }
}
