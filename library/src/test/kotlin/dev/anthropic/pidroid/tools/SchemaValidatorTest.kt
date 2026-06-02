package dev.anthropic.pidroid.tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SchemaValidatorTest {
    private lateinit var validator: SchemaValidator
    private lateinit var catalog: ToolCatalog

    @Before
    fun setup() {
        validator = SchemaValidator()
        catalog = ToolCatalog()
    }

    // --- Core validation rules ---

    @Test
    fun `valid args against read_notifications schema passes`() {
        val schema = catalog.getByName("read_notifications")!!.inputSchema
        val args = buildJsonObject {
            putJsonArray("app_filter") {
                add(kotlinx.serialization.json.JsonPrimitive("com.whatsapp"))
            }
            put("since_minutes", 30)
        }
        val result = validator.validate(args, schema)
        assertTrue(result is ValidationResult.Valid)
    }

    @Test
    fun `missing required field query in search_contacts returns error naming field`() {
        val schema = catalog.getByName("search_contacts")!!.inputSchema
        val args = buildJsonObject {
            put("limit", 10)
        }
        val result = validator.validate(args, schema)
        assertTrue(result is ValidationResult.Invalid)
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("query") })
    }

    @Test
    fun `since_minutes exceeds maximum 1440 returns validation error`() {
        val schema = catalog.getByName("read_notifications")!!.inputSchema
        val args = buildJsonObject {
            put("since_minutes", 2000)
        }
        val result = validator.validate(args, schema)
        assertTrue(result is ValidationResult.Invalid)
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("maximum") })
    }

    @Test
    fun `unknown extra fields pass (LLMs may add unexpected fields)`() {
        val schema = catalog.getByName("get_battery_state")!!.inputSchema
        val args = buildJsonObject {
            put("unexpected_field", "surprise")
            put("another_extra", 42)
        }
        val result = validator.validate(args, schema)
        assertTrue(result is ValidationResult.Valid)
    }

    @Test
    fun `empty object against schema with no required fields passes`() {
        val schema = catalog.getByName("get_battery_state")!!.inputSchema
        val args = JsonObject(emptyMap())
        val result = validator.validate(args, schema)
        assertTrue(result is ValidationResult.Valid)
    }

    @Test
    fun `type integer receives string returns type mismatch error`() {
        val schema = catalog.getByName("search_contacts")!!.inputSchema
        val args = buildJsonObject {
            put("query", "John")
            put("limit", "not_a_number")
        }
        val result = validator.validate(args, schema)
        assertTrue(result is ValidationResult.Invalid)
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("integer") && it.contains("limit") })
    }

    @Test
    fun `minimum constraint violation returns error`() {
        val schema = catalog.getByName("search_contacts")!!.inputSchema
        val args = buildJsonObject {
            put("query", "John")
            put("limit", 0)
        }
        val result = validator.validate(args, schema)
        assertTrue(result is ValidationResult.Invalid)
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("minimum") })
    }

    // --- Catalog-wide tests: each of the 18 tools with valid args ---

    @Test
    fun `read_notifications with valid args`() {
        assertValidArgs("read_notifications", buildJsonObject {
            putJsonArray("app_filter") {
                add(kotlinx.serialization.json.JsonPrimitive("com.slack"))
            }
            put("since_minutes", 60)
        })
    }

    @Test
    fun `get_notification_channels with valid args`() {
        assertValidArgs("get_notification_channels", buildJsonObject {
            put("package_name", "com.whatsapp")
        })
    }

    @Test
    fun `search_contacts with valid args`() {
        assertValidArgs("search_contacts", buildJsonObject {
            put("query", "Alice")
            put("limit", 10)
        })
    }

    @Test
    fun `get_contact_details with valid args`() {
        assertValidArgs("get_contact_details", buildJsonObject {
            put("contact_id", "lookup_key_123")
        })
    }

    @Test
    fun `read_calendar_events with valid args`() {
        assertValidArgs("read_calendar_events", buildJsonObject {
            put("start_time", "2026-05-08T09:00:00Z")
            put("end_time", "2026-05-08T17:00:00Z")
        })
    }

    @Test
    fun `create_calendar_event with valid args`() {
        assertValidArgs("create_calendar_event", buildJsonObject {
            put("title", "Team Standup")
            put("start_time", "2026-05-08T10:00:00Z")
            put("end_time", "2026-05-08T10:30:00Z")
            put("description", "Daily standup")
            put("location", "Room 4B")
        })
    }

    @Test
    fun `get_battery_state with valid args`() {
        assertValidArgs("get_battery_state", JsonObject(emptyMap()))
    }

    @Test
    fun `get_connectivity_state with valid args`() {
        assertValidArgs("get_connectivity_state", JsonObject(emptyMap()))
    }

    @Test
    fun `get_installed_apps with valid args`() {
        assertValidArgs("get_installed_apps", JsonObject(emptyMap()))
    }

    @Test
    fun `launch_app with valid args`() {
        assertValidArgs("launch_app", buildJsonObject {
            put("package_name", "com.spotify.music")
        })
    }

    @Test
    fun `open_url with valid args`() {
        assertValidArgs("open_url", buildJsonObject {
            put("url", "https://example.com")
        })
    }

    @Test
    fun `share_text with valid args`() {
        assertValidArgs("share_text", buildJsonObject {
            put("text", "Check this out!")
            put("subject", "Cool Link")
        })
    }

    @Test
    fun `send_intent with valid args`() {
        assertValidArgs("send_intent", buildJsonObject {
            put("action", "android.intent.action.VIEW")
            put("data", "geo:0,0?q=coffee")
        })
    }

    @Test
    fun `memory_store with valid args`() {
        assertValidArgs("memory_store", buildJsonObject {
            put("content", "User prefers dark mode")
        })
    }

    @Test
    fun `memory_search with valid args`() {
        assertValidArgs("memory_search", buildJsonObject {
            put("query", "user preferences")
            put("limit", 5)
        })
    }

    @Test
    fun `memory_delete with valid args`() {
        assertValidArgs("memory_delete", buildJsonObject {
            put("memory_id", "mem_abc123")
        })
    }

    @Test
    fun `set_alarm with valid args`() {
        assertValidArgs("set_alarm", buildJsonObject {
            put("time", "2026-05-08T07:00:00Z")
            put("message", "Wake up!")
        })
    }

    @Test
    fun `schedule_action with valid args`() {
        assertValidArgs("schedule_action", buildJsonObject {
            put("action", "check_weather")
            put("trigger_time", "2026-05-08T06:00:00Z")
            put("description", "Morning weather briefing")
        })
    }

    // --- Multiple errors ---

    @Test
    fun `multiple violations collected in single result`() {
        val schema = catalog.getByName("read_calendar_events")!!.inputSchema
        // Missing both required fields
        val args = JsonObject(emptyMap())
        val result = validator.validate(args, schema)
        assertTrue(result is ValidationResult.Invalid)
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.size >= 2)
        assertTrue(errors.any { it.contains("start_time") })
        assertTrue(errors.any { it.contains("end_time") })
    }

    @Test
    fun `array items with wrong type reports error`() {
        val schema = catalog.getByName("read_notifications")!!.inputSchema
        val args = buildJsonObject {
            putJsonArray("app_filter") {
                add(kotlinx.serialization.json.JsonPrimitive(123)) // should be string
            }
        }
        val result = validator.validate(args, schema)
        assertTrue(result is ValidationResult.Invalid)
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("string") })
    }

    // --- Helper ---

    private fun assertValidArgs(toolName: String, args: JsonObject) {
        val tool = catalog.getByName(toolName)
        assertTrue("Tool '$toolName' not found in catalog", tool != null)
        val result = validator.validate(args, tool!!.inputSchema)
        assertEquals(
            "Validation should pass for '$toolName' with valid args, got: $result",
            ValidationResult.Valid,
            result,
        )
    }
}
