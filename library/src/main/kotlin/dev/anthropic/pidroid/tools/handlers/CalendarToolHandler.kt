package dev.anthropic.pidroid.tools.handlers

import android.content.Context
import dev.anthropic.pidroid.android.CalendarAccessor
import dev.anthropic.pidroid.tools.PermissionChecker
import dev.anthropic.pidroid.tools.ToolExecutionContext
import dev.anthropic.pidroid.tools.ToolHandler
import dev.anthropic.pidroid.tools.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Handles `read_calendar_events` and `create_calendar_event` tool calls.
 *
 * Requires android.permission.READ_CALENDAR / WRITE_CALENDAR.
 */
class CalendarToolHandler(
    private val context: Context,
    private val accessor: CalendarAccessor,
    private val permissionChecker: PermissionChecker,
) : ToolHandler {

    override suspend fun execute(
        toolCallId: String,
        arguments: JsonObject,
        context: ToolExecutionContext,
    ): ToolResult {
        // Determine which tool based on arguments
        return if ("title" in arguments) {
            executeCreateEvent(toolCallId, arguments)
        } else {
            executeReadEvents(toolCallId, arguments)
        }
    }

    private fun executeReadEvents(toolCallId: String, arguments: JsonObject): ToolResult {
        if (!permissionChecker.isPermissionGranted("android.permission.READ_CALENDAR")) {
            return ToolResult(
                toolCallId = toolCallId,
                content = "Permission android.permission.READ_CALENDAR not granted",
                isError = true,
            )
        }

        val startTime = arguments["start_time"]?.jsonPrimitive?.content
            ?: return ToolResult(toolCallId, "Missing required field 'start_time'", isError = true)
        val endTime = arguments["end_time"]?.jsonPrimitive?.content
            ?: return ToolResult(toolCallId, "Missing required field 'end_time'", isError = true)
        val calendarId = arguments["calendar_id"]?.jsonPrimitive?.content

        val events = accessor.readEvents(startTime, endTime, calendarId)
        return ToolResult(toolCallId = toolCallId, content = events.toString())
    }

    private fun executeCreateEvent(toolCallId: String, arguments: JsonObject): ToolResult {
        if (!permissionChecker.isPermissionGranted("android.permission.WRITE_CALENDAR")) {
            return ToolResult(
                toolCallId = toolCallId,
                content = "Permission android.permission.WRITE_CALENDAR not granted",
                isError = true,
            )
        }

        val title = arguments["title"]?.jsonPrimitive?.content
            ?: return ToolResult(toolCallId, "Missing required field 'title'", isError = true)
        val startTime = arguments["start_time"]?.jsonPrimitive?.content
            ?: return ToolResult(toolCallId, "Missing required field 'start_time'", isError = true)
        val endTime = arguments["end_time"]?.jsonPrimitive?.content
            ?: return ToolResult(toolCallId, "Missing required field 'end_time'", isError = true)
        val description = arguments["description"]?.jsonPrimitive?.content
        val location = arguments["location"]?.jsonPrimitive?.content

        val intent = accessor.buildCreateEventIntent(title, startTime, endTime, description, location)
        context.startActivity(intent)

        return ToolResult(
            toolCallId = toolCallId,
            content = "Calendar event creation intent sent: '$title'",
        )
    }
}
