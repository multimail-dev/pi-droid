package dev.anthropic.pidroid.tools.handlers

import dev.anthropic.pidroid.android.IntentDispatcher
import dev.anthropic.pidroid.tools.ToolExecutionContext
import dev.anthropic.pidroid.tools.ToolHandler
import dev.anthropic.pidroid.tools.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.ZoneId

/**
 * Handles `launch_app`, `open_url`, `share_text`, `send_intent`, `set_alarm` tool calls.
 *
 * Delegates to [IntentDispatcher] for actual Intent construction and firing.
 */
class IntentToolHandler(
    private val dispatcher: IntentDispatcher,
    private val toolName: String,
) : ToolHandler {

    override suspend fun execute(
        toolCallId: String,
        arguments: JsonObject,
        context: ToolExecutionContext,
    ): ToolResult {
        return when (toolName) {
            "launch_app" -> executeLaunchApp(toolCallId, arguments)
            "open_url" -> executeOpenUrl(toolCallId, arguments)
            "share_text" -> executeShareText(toolCallId, arguments)
            "send_intent" -> executeSendIntent(toolCallId, arguments)
            "set_alarm" -> executeSetAlarm(toolCallId, arguments)
            else -> ToolResult(toolCallId, "Unknown intent tool: $toolName", isError = true)
        }
    }

    private fun executeLaunchApp(toolCallId: String, arguments: JsonObject): ToolResult {
        val packageName = arguments["package_name"]?.jsonPrimitive?.content
            ?: return ToolResult(toolCallId, "Missing required field 'package_name'", isError = true)

        val error = dispatcher.launchApp(packageName)
        return if (error != null) {
            ToolResult(toolCallId, error, isError = true)
        } else {
            ToolResult(toolCallId, "Launched $packageName")
        }
    }

    private fun executeOpenUrl(toolCallId: String, arguments: JsonObject): ToolResult {
        val url = arguments["url"]?.jsonPrimitive?.content
            ?: return ToolResult(toolCallId, "Missing required field 'url'", isError = true)

        val error = dispatcher.openUrl(url)
        return if (error != null) {
            ToolResult(toolCallId, error, isError = true)
        } else {
            ToolResult(toolCallId, "Opened URL: $url")
        }
    }

    private fun executeShareText(toolCallId: String, arguments: JsonObject): ToolResult {
        val text = arguments["text"]?.jsonPrimitive?.content
            ?: return ToolResult(toolCallId, "Missing required field 'text'", isError = true)
        val subject = arguments["subject"]?.jsonPrimitive?.content

        dispatcher.shareText(text, subject)
        return ToolResult(toolCallId, "Share sheet opened")
    }

    private fun executeSendIntent(toolCallId: String, arguments: JsonObject): ToolResult {
        val action = arguments["action"]?.jsonPrimitive?.content
            ?: return ToolResult(toolCallId, "Missing required field 'action'", isError = true)
        val data = arguments["data"]?.jsonPrimitive?.content
        val type = arguments["type"]?.jsonPrimitive?.content
        val extras = arguments["extras"]?.jsonObject?.let { obj ->
            obj.mapValues { (_, v) -> v.jsonPrimitive.content }
        }

        dispatcher.sendIntent(action, data, type, extras)
        return ToolResult(toolCallId, "Intent sent: $action")
    }

    private fun executeSetAlarm(toolCallId: String, arguments: JsonObject): ToolResult {
        val timeStr = arguments["time"]?.jsonPrimitive?.content
            ?: return ToolResult(toolCallId, "Missing required field 'time'", isError = true)
        val message = arguments["message"]?.jsonPrimitive?.content

        val instant = try {
            Instant.parse(timeStr)
        } catch (e: Exception) {
            return ToolResult(toolCallId, "Invalid time format: '$timeStr'", isError = true)
        }

        val localTime = instant.atZone(ZoneId.systemDefault()).toLocalTime()
        dispatcher.setAlarm(localTime.hour, localTime.minute, message)
        return ToolResult(toolCallId, "Alarm set for ${localTime.hour}:${localTime.minute.toString().padStart(2, '0')}")
    }
}
