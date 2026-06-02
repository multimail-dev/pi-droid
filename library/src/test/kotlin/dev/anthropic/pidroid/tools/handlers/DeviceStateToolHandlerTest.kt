package dev.anthropic.pidroid.tools.handlers

import dev.anthropic.pidroid.android.DeviceStateReader
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DeviceStateToolHandlerTest {
    private lateinit var reader: DeviceStateReader
    private val context = FakeToolExecutionContext()

    @Before
    fun setup() {
        reader = DeviceStateReader(RuntimeEnvironment.getApplication())
    }

    @Test
    fun `get_battery_state returns JSON with level and charging fields`() = runTest {
        val handler = DeviceStateToolHandler(reader, "get_battery_state")
        val result = handler.execute("tc_1", JsonObject(emptyMap()), context)

        assertEquals(false, result.isError)
        val json = Json.parseToJsonElement(result.content).jsonObject
        assertTrue("level" in json)
        assertTrue("charging" in json)
        assertTrue("source" in json)
    }

    @Test
    fun `get_connectivity_state returns JSON with wifi and cellular fields`() = runTest {
        val handler = DeviceStateToolHandler(reader, "get_connectivity_state")
        val result = handler.execute("tc_2", JsonObject(emptyMap()), context)

        assertEquals(false, result.isError)
        val json = Json.parseToJsonElement(result.content).jsonObject
        assertTrue("wifi" in json)
        assertTrue("cellular" in json)
        assertTrue("connected" in json)
    }

    @Test
    fun `get_installed_apps returns JSON array`() = runTest {
        val handler = DeviceStateToolHandler(reader, "get_installed_apps")
        val result = handler.execute("tc_3", JsonObject(emptyMap()), context)

        assertEquals(false, result.isError)
        // Robolectric may or may not have launcher apps; just verify valid JSON
        assertTrue(result.content.startsWith("["))
    }

    @Test
    fun `unknown tool name returns error`() = runTest {
        val handler = DeviceStateToolHandler(reader, "nonexistent_tool")
        val result = handler.execute("tc_4", JsonObject(emptyMap()), context)

        assertTrue(result.isError)
        assertTrue(result.content.contains("Unknown"))
    }
}
