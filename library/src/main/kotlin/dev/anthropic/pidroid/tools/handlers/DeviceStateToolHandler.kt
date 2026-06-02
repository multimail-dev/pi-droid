package dev.anthropic.pidroid.tools.handlers

import dev.anthropic.pidroid.android.DeviceStateReader
import dev.anthropic.pidroid.tools.ToolExecutionContext
import dev.anthropic.pidroid.tools.ToolHandler
import dev.anthropic.pidroid.tools.ToolResult
import kotlinx.serialization.json.JsonObject

/**
 * Handles `get_battery_state`, `get_connectivity_state`, `get_installed_apps` tool calls.
 *
 * No special permissions required — these use standard system services.
 */
class DeviceStateToolHandler(
    private val reader: DeviceStateReader,
    private val toolName: String,
) : ToolHandler {

    override suspend fun execute(
        toolCallId: String,
        arguments: JsonObject,
        context: ToolExecutionContext,
    ): ToolResult {
        return when (toolName) {
            "get_battery_state" -> {
                val state = reader.getBatteryState()
                ToolResult(toolCallId = toolCallId, content = state.toString())
            }
            "get_connectivity_state" -> {
                val state = reader.getConnectivityState()
                ToolResult(toolCallId = toolCallId, content = state.toString())
            }
            "get_installed_apps" -> {
                val apps = reader.getInstalledApps()
                ToolResult(toolCallId = toolCallId, content = apps.toString())
            }
            else -> ToolResult(toolCallId, "Unknown device state tool: $toolName", isError = true)
        }
    }
}
