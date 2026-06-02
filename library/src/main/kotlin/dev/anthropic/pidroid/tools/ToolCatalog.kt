package dev.anthropic.pidroid.tools

import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The canonical tool catalog — all built-in tools the agent can use.
 *
 * Adapted from misadventure's CanonicalToolCatalog. Removed `requiresBridge`.
 * Each tool has a complete JSON Schema `inputSchema`.
 */
class ToolCatalog {
    private val tools = mutableMapOf<String, ToolDefinition>()

    init {
        registerBuiltins()
    }

    fun allTools(): List<ToolDefinition> = tools.values.toList()

    fun getByName(name: String): ToolDefinition? = tools[name]

    private fun registerBuiltins() {
        // --- Communication / Notifications ---
        register(
            ToolDefinition(
                name = "read_notifications",
                description = "Read recent notifications from the device notification shade",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("app_filter") {
                            put("type", "array")
                            putJsonObject("items") { put("type", "string") }
                            put("description", "Filter to specific package names")
                        }
                        putJsonObject("since_minutes") {
                            put("type", "integer")
                            put("minimum", 1)
                            put("maximum", 1440)
                            put("description", "Only notifications from the last N minutes")
                        }
                    }
                },
                category = ToolCategory.COMMUNICATION,
                riskLevel = RiskLevel.READ_ONLY,
                executionMode = ToolExecutionMode.ANDROID_SERVICE,
                requiredCapabilities = listOf("pidroid://notification_listener"),
            )
        )

        register(
            ToolDefinition(
                name = "get_notification_channels",
                description = "List notification channels and their importance levels for an app",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("package_name") {
                            put("type", "string")
                            put("description", "App package name")
                        }
                    }
                    putJsonArray("required") { add("package_name") }
                },
                category = ToolCategory.COMMUNICATION,
                riskLevel = RiskLevel.READ_ONLY,
                executionMode = ToolExecutionMode.ANDROID_SERVICE,
                requiredCapabilities = listOf("pidroid://notification_listener"),
            )
        )

        // --- Contacts ---
        register(
            ToolDefinition(
                name = "search_contacts",
                description = "Search the user's contacts by name, email, or phone number",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("query") {
                            put("type", "string")
                            put("description", "Search query")
                        }
                        putJsonObject("limit") {
                            put("type", "integer")
                            put("minimum", 1)
                            put("maximum", 50)
                        }
                    }
                    putJsonArray("required") { add("query") }
                },
                category = ToolCategory.CONTACTS,
                riskLevel = RiskLevel.READ_ONLY,
                executionMode = ToolExecutionMode.ANDROID_SERVICE,
                requiredPermissions = listOf("android.permission.READ_CONTACTS"),
            )
        )

        register(
            ToolDefinition(
                name = "get_contact_details",
                description = "Get full details for a specific contact by ID",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("contact_id") {
                            put("type", "string")
                            put("description", "Contact lookup key")
                        }
                    }
                    putJsonArray("required") { add("contact_id") }
                },
                category = ToolCategory.CONTACTS,
                riskLevel = RiskLevel.READ_ONLY,
                executionMode = ToolExecutionMode.ANDROID_SERVICE,
                requiredPermissions = listOf("android.permission.READ_CONTACTS"),
            )
        )

        // --- Calendar ---
        register(
            ToolDefinition(
                name = "read_calendar_events",
                description = "Read calendar events within a time range",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("start_time") {
                            put("type", "string")
                            put("format", "date-time")
                            put("description", "ISO 8601 start time")
                        }
                        putJsonObject("end_time") {
                            put("type", "string")
                            put("format", "date-time")
                            put("description", "ISO 8601 end time")
                        }
                        putJsonObject("calendar_id") {
                            put("type", "string")
                            put("description", "Specific calendar ID (optional)")
                        }
                    }
                    putJsonArray("required") { add("start_time"); add("end_time") }
                },
                category = ToolCategory.CALENDAR,
                riskLevel = RiskLevel.READ_ONLY,
                executionMode = ToolExecutionMode.ANDROID_SERVICE,
                requiredPermissions = listOf("android.permission.READ_CALENDAR"),
            )
        )

        register(
            ToolDefinition(
                name = "create_calendar_event",
                description = "Create a new calendar event",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("title") { put("type", "string") }
                        putJsonObject("start_time") { put("type", "string"); put("format", "date-time") }
                        putJsonObject("end_time") { put("type", "string"); put("format", "date-time") }
                        putJsonObject("description") { put("type", "string") }
                        putJsonObject("location") { put("type", "string") }
                    }
                    putJsonArray("required") { add("title"); add("start_time"); add("end_time") }
                },
                category = ToolCategory.CALENDAR,
                riskLevel = RiskLevel.LOCAL_WRITE,
                defaultConfirmationPolicy = ConfirmationPolicy.USER_CONFIRM_MODAL,
                executionMode = ToolExecutionMode.ANDROID_SERVICE,
                requiredPermissions = listOf("android.permission.WRITE_CALENDAR"),
            )
        )

        // --- Device ---
        register(
            ToolDefinition(
                name = "get_battery_state",
                description = "Get current battery level, charging state, and temperature",
                inputSchema = buildJsonObject { put("type", "object") },
                category = ToolCategory.DEVICE,
                riskLevel = RiskLevel.READ_ONLY,
                executionMode = ToolExecutionMode.ANDROID_SERVICE,
                requiredCapabilities = listOf("pidroid://device_state"),
            )
        )

        register(
            ToolDefinition(
                name = "get_connectivity_state",
                description = "Get current network connectivity (WiFi, cellular, VPN)",
                inputSchema = buildJsonObject { put("type", "object") },
                category = ToolCategory.DEVICE,
                riskLevel = RiskLevel.READ_ONLY,
                executionMode = ToolExecutionMode.ANDROID_SERVICE,
                requiredCapabilities = listOf("pidroid://device_state"),
            )
        )

        register(
            ToolDefinition(
                name = "get_installed_apps",
                description = "List user-installed applications on the device",
                inputSchema = buildJsonObject { put("type", "object") },
                category = ToolCategory.DEVICE,
                riskLevel = RiskLevel.READ_ONLY,
                executionMode = ToolExecutionMode.ANDROID_SERVICE,
            )
        )

        // --- Navigation / Intents ---
        register(
            ToolDefinition(
                name = "launch_app",
                description = "Launch an app by package name",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("package_name") { put("type", "string") }
                    }
                    putJsonArray("required") { add("package_name") }
                },
                category = ToolCategory.NAVIGATION,
                riskLevel = RiskLevel.LOCAL_WRITE,
                executionMode = ToolExecutionMode.ACTIVITY_RESULT,
            )
        )

        register(
            ToolDefinition(
                name = "open_url",
                description = "Open a URL in the default browser or associated app",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("url") { put("type", "string"); put("format", "uri") }
                    }
                    putJsonArray("required") { add("url") }
                },
                category = ToolCategory.NAVIGATION,
                riskLevel = RiskLevel.LOCAL_WRITE,
                executionMode = ToolExecutionMode.ACTIVITY_RESULT,
            )
        )

        register(
            ToolDefinition(
                name = "share_text",
                description = "Share text content via Android's share sheet",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("text") { put("type", "string") }
                        putJsonObject("subject") { put("type", "string") }
                    }
                    putJsonArray("required") { add("text") }
                },
                category = ToolCategory.NAVIGATION,
                riskLevel = RiskLevel.EXTERNAL_DRAFT,
                executionMode = ToolExecutionMode.ACTIVITY_RESULT,
            )
        )

        register(
            ToolDefinition(
                name = "send_intent",
                description = "Fire a custom Android Intent (advanced)",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("action") { put("type", "string") }
                        putJsonObject("data") { put("type", "string") }
                        putJsonObject("type") { put("type", "string") }
                        putJsonObject("extras") { put("type", "object") }
                    }
                    putJsonArray("required") { add("action") }
                },
                category = ToolCategory.NAVIGATION,
                riskLevel = RiskLevel.EXTERNAL_SEND,
                executionMode = ToolExecutionMode.ACTIVITY_RESULT,
            )
        )

        // --- Memory ---
        register(
            ToolDefinition(
                name = "memory_store",
                description = "Store a piece of information in semantic memory",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("content") { put("type", "string") }
                        putJsonObject("metadata") { put("type", "object") }
                    }
                    putJsonArray("required") { add("content") }
                },
                category = ToolCategory.MEMORY,
                riskLevel = RiskLevel.LOCAL_WRITE,
                executionMode = ToolExecutionMode.NATIVE,
            )
        )

        register(
            ToolDefinition(
                name = "memory_search",
                description = "Search semantic memory for relevant information",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("query") { put("type", "string") }
                        putJsonObject("limit") {
                            put("type", "integer")
                            put("minimum", 1)
                            put("maximum", 20)
                        }
                    }
                    putJsonArray("required") { add("query") }
                },
                category = ToolCategory.MEMORY,
                riskLevel = RiskLevel.READ_ONLY,
                executionMode = ToolExecutionMode.NATIVE,
            )
        )

        register(
            ToolDefinition(
                name = "memory_delete",
                description = "Delete a memory entry by ID",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("memory_id") { put("type", "string") }
                    }
                    putJsonArray("required") { add("memory_id") }
                },
                category = ToolCategory.MEMORY,
                riskLevel = RiskLevel.LOCAL_WRITE,
                executionMode = ToolExecutionMode.NATIVE,
            )
        )

        // --- Scheduling ---
        register(
            ToolDefinition(
                name = "set_alarm",
                description = "Set a system alarm or timer",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("time") { put("type", "string"); put("format", "date-time") }
                        putJsonObject("message") { put("type", "string") }
                    }
                    putJsonArray("required") { add("time") }
                },
                category = ToolCategory.SCHEDULING,
                riskLevel = RiskLevel.LOCAL_WRITE,
                executionMode = ToolExecutionMode.ANDROID_SERVICE,
                requiredCapabilities = listOf("pidroid://alarm"),
            )
        )

        register(
            ToolDefinition(
                name = "schedule_action",
                description = "Schedule a future agent action (reminder, check, follow-up)",
                inputSchema = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("action") { put("type", "string") }
                        putJsonObject("trigger_time") { put("type", "string"); put("format", "date-time") }
                        putJsonObject("description") { put("type", "string") }
                    }
                    putJsonArray("required") { add("action"); add("trigger_time") }
                },
                category = ToolCategory.SCHEDULING,
                riskLevel = RiskLevel.LOCAL_WRITE,
                executionMode = ToolExecutionMode.NATIVE,
            )
        )
    }

    private fun register(tool: ToolDefinition) {
        tools[tool.name] = tool
    }
}
