package dev.anthropic.pidroid.tools.handlers

import dev.anthropic.pidroid.android.ContactAccessor
import dev.anthropic.pidroid.tools.PermissionChecker
import dev.anthropic.pidroid.tools.ToolExecutionContext
import dev.anthropic.pidroid.tools.ToolHandler
import dev.anthropic.pidroid.tools.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Handles `search_contacts` and `get_contact_details` tool calls.
 *
 * Requires android.permission.READ_CONTACTS.
 */
class ContactToolHandler(
    private val accessor: ContactAccessor,
    private val permissionChecker: PermissionChecker,
) : ToolHandler {

    override suspend fun execute(
        toolCallId: String,
        arguments: JsonObject,
        context: ToolExecutionContext,
    ): ToolResult {
        if (!permissionChecker.isPermissionGranted("android.permission.READ_CONTACTS")) {
            return ToolResult(
                toolCallId = toolCallId,
                content = "Permission android.permission.READ_CONTACTS not granted",
                isError = true,
            )
        }

        return if ("contact_id" in arguments) {
            executeGetDetails(toolCallId, arguments)
        } else {
            executeSearch(toolCallId, arguments)
        }
    }

    private fun executeSearch(toolCallId: String, arguments: JsonObject): ToolResult {
        val query = arguments["query"]?.jsonPrimitive?.content
            ?: return ToolResult(toolCallId, "Missing required field 'query'", isError = true)

        if (query.isBlank()) {
            return ToolResult(toolCallId, "Field 'query' must not be empty", isError = true)
        }

        val limit = arguments["limit"]?.jsonPrimitive?.intOrNull ?: 20

        val results = accessor.searchContacts(query, limit)
        return ToolResult(toolCallId = toolCallId, content = results.toString())
    }

    private fun executeGetDetails(toolCallId: String, arguments: JsonObject): ToolResult {
        val contactId = arguments["contact_id"]?.jsonPrimitive?.content
            ?: return ToolResult(toolCallId, "Missing required field 'contact_id'", isError = true)

        val details = accessor.getContactDetails(contactId)
            ?: return ToolResult(
                toolCallId = toolCallId,
                content = "Contact not found: '$contactId'",
                isError = true,
            )

        return ToolResult(toolCallId = toolCallId, content = details.toString())
    }
}
