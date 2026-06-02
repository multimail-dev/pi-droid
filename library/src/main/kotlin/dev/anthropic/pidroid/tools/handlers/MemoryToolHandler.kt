package dev.anthropic.pidroid.tools.handlers

import dev.anthropic.pidroid.memory.MemoryStore
import dev.anthropic.pidroid.tools.ToolExecutionContext
import dev.anthropic.pidroid.tools.ToolHandler
import dev.anthropic.pidroid.tools.ToolResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Handles `memory_store`, `memory_search`, `memory_delete` tool calls.
 */
class MemoryToolHandler(
    private val memoryStore: MemoryStore,
    private val toolName: String,
) : ToolHandler {

    override suspend fun execute(
        toolCallId: String,
        arguments: JsonObject,
        context: ToolExecutionContext,
    ): ToolResult {
        return when (toolName) {
            "memory_store" -> executeStore(toolCallId, arguments)
            "memory_search" -> executeSearch(toolCallId, arguments)
            "memory_delete" -> executeDelete(toolCallId, arguments)
            else -> ToolResult(toolCallId, "Unknown memory tool: $toolName", isError = true)
        }
    }

    private suspend fun executeStore(toolCallId: String, arguments: JsonObject): ToolResult {
        val content = arguments["content"]?.jsonPrimitive?.content
            ?: return ToolResult(toolCallId, "Missing required field 'content'", isError = true)
        val metadata = arguments["metadata"]?.jsonObject?.toString()

        val id = memoryStore.store(content, metadata)
        return ToolResult(
            toolCallId = toolCallId,
            content = buildJsonObject { put("id", id); put("stored", true) }.toString(),
        )
    }

    private suspend fun executeSearch(toolCallId: String, arguments: JsonObject): ToolResult {
        val query = arguments["query"]?.jsonPrimitive?.content
            ?: return ToolResult(toolCallId, "Missing required field 'query'", isError = true)
        val limit = arguments["limit"]?.jsonPrimitive?.intOrNull ?: 10

        val results = memoryStore.search(query, limit)
        val json = buildJsonArray {
            for (result in results) {
                add(buildJsonObject {
                    put("id", result.id)
                    put("content", result.content)
                    put("similarity", result.similarity.toDouble())
                })
            }
        }
        return ToolResult(toolCallId = toolCallId, content = json.toString())
    }

    private suspend fun executeDelete(toolCallId: String, arguments: JsonObject): ToolResult {
        val memoryId = arguments["memory_id"]?.jsonPrimitive?.content
            ?: return ToolResult(toolCallId, "Missing required field 'memory_id'", isError = true)

        val deleted = memoryStore.delete(memoryId)
        return ToolResult(
            toolCallId = toolCallId,
            content = buildJsonObject { put("deleted", deleted) }.toString(),
        )
    }
}
