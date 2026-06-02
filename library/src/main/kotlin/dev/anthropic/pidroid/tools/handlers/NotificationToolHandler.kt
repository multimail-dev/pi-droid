package dev.anthropic.pidroid.tools.handlers

import dev.anthropic.pidroid.android.NotificationAccessor
import dev.anthropic.pidroid.tools.PermissionChecker
import dev.anthropic.pidroid.tools.ToolExecutionContext
import dev.anthropic.pidroid.tools.ToolHandler
import dev.anthropic.pidroid.tools.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Handles `read_notifications` and `get_notification_channels` tool calls.
 *
 * Requires NotificationListenerService to be enabled by the user.
 */
class NotificationToolHandler(
    private val accessor: NotificationAccessor,
    private val permissionChecker: PermissionChecker,
) : ToolHandler {

    override suspend fun execute(
        toolCallId: String,
        arguments: JsonObject,
        context: ToolExecutionContext,
    ): ToolResult {
        if (!permissionChecker.isNotificationListenerEnabled()) {
            return ToolResult(
                toolCallId = toolCallId,
                content = "NotificationListenerService not enabled. User must grant notification access in Settings.",
                isError = true,
            )
        }

        // Determine which tool by inspecting arguments pattern
        // read_notifications has app_filter/since_minutes; get_notification_channels has package_name
        return if ("package_name" in arguments) {
            executeGetChannels(toolCallId, arguments)
        } else {
            executeReadNotifications(toolCallId, arguments)
        }
    }

    private fun executeReadNotifications(toolCallId: String, arguments: JsonObject): ToolResult {
        val appFilter = arguments["app_filter"]?.jsonArray?.map { it.jsonPrimitive.content }
        val sinceMinutes = arguments["since_minutes"]?.jsonPrimitive?.intOrNull

        val result = accessor.getNotifications(
            appFilter = appFilter,
            sinceMinutes = sinceMinutes,
        )

        return ToolResult(
            toolCallId = toolCallId,
            content = result.toString(),
        )
    }

    private fun executeGetChannels(toolCallId: String, arguments: JsonObject): ToolResult {
        val packageName = arguments["package_name"]?.jsonPrimitive?.content
            ?: return ToolResult(
                toolCallId = toolCallId,
                content = "Missing required field 'package_name'",
                isError = true,
            )

        val result = accessor.getNotificationChannels(packageName)
        return ToolResult(
            toolCallId = toolCallId,
            content = result.toString(),
        )
    }
}
